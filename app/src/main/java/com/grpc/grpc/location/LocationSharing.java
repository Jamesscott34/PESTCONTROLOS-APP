package com.grpc.grpc.location;

import com.grpc.grpc.location.worker.LastLocationUpdateWorker;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Location sharing helper:
 * - Logged-in devices publish last-known location at any time of day.
 * - Updates are scheduled approximately every 15 minutes (WorkManager minimum / best effort).
 * - Saved locations stay available until the next update, including across days.
 * - Admin Location Finder reads Firestore {@code last_locations}/{authUid}.
 */
public final class LocationSharing {
    private LocationSharing() {}

    public static final String TAG = "LocationSharing";

    public static final String COLLECTION_LAST_LOCATIONS = "last_locations";

    /** Local tracking window start (inclusive), device local time. */
    public static final int TRACK_START_HOUR = 8;
    public static final int TRACK_START_MINUTE = 0;
    /** Local tracking window end (inclusive final upload attempt), device local time. */
    public static final int TRACK_END_HOUR = 17;
    public static final int TRACK_END_MINUTE = 30;

    /** Age after which a location must not be shown as current. */
    public static final long STALE_AFTER_MS = 30L * 60L * 1000L;

    /** WorkManager periodic interval (Android minimum is 15 minutes). */
    public static final long UPDATE_INTERVAL_MINUTES = 15L;

    private static final String PREFS = "GRPC_LAST_LOC_CACHE";
    private static final String PREF_ACTIVE_UID = "active_tracking_uid";

    private static final String WORK_PERIODIC = "location_update_";
    private static final String WORK_NOW = "location_update_now_";
    private static final String WORK_WINDOW_START = "location_window_start_";
    private static final String WORK_WINDOW_END = "location_window_end_";
    private static final String WORK_CLEANUP = "location_cleanup_";

    /** Clears local location cache (required on logout/shared devices). */
    public static void clearLocalCache(Context context) {
        if (context == null) return;
        try {
            context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        } catch (Exception ignored) {}
    }

    public static boolean hasLocationPermission(Context context) {
        if (context == null) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns the key for last_locations and local cache.
     * If the input looks like a Firebase Auth UID (length >= 15), returns it as-is (case-sensitive).
     * Otherwise returns trimmed lowercased string (e.g. contractKey) for backward compatibility.
     */
    public static String userKey(String userNameOrUid) {
        if (userNameOrUid == null) return "";
        String s = userNameOrUid.trim();
        if (s.length() >= 15) return s; // Firebase UID – do not lowercase
        return s.toLowerCase(Locale.getDefault());
    }

    /** True if local device time is within 08:00–17:30 inclusive. */
    public static boolean isWithinTrackingWindow() {
        return isWithinTrackingWindow(System.currentTimeMillis());
    }

    public static boolean isWithinTrackingWindow(long nowMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(nowMs);
        int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int start = TRACK_START_HOUR * 60 + TRACK_START_MINUTE;
        int end = TRACK_END_HOUR * 60 + TRACK_END_MINUTE;
        return minutes >= start && minutes <= end;
    }

    /** True if {@code clientTimestampMs} is older than {@link #STALE_AFTER_MS}. */
    public static boolean isStaleTimestamp(long clientTimestampMs) {
        if (clientTimestampMs <= 0L) return true;
        return (System.currentTimeMillis() - clientTimestampMs) >= STALE_AFTER_MS;
    }

    /** True if local device time is after today's 17:30. */
    public static boolean isAfterTrackingWindowEnd() {
        Calendar c = Calendar.getInstance();
        int minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int end = TRACK_END_HOUR * 60 + TRACK_END_MINUTE;
        return minutes > end;
    }

    /**
     * Milliseconds until the next 08:00 local time (0 if already at/after today's 08:00 and before end,
     * otherwise delay to next window start).
     */
    public static long millisUntilNextWindowStart() {
        Calendar now = Calendar.getInstance();
        Calendar start = (Calendar) now.clone();
        start.set(Calendar.HOUR_OF_DAY, TRACK_START_HOUR);
        start.set(Calendar.MINUTE, TRACK_START_MINUTE);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        if (now.after(start)) {
            // If still inside today's window, no delay for "start"; caller uses immediate.
            if (isWithinTrackingWindow()) return 0L;
            start.add(Calendar.DAY_OF_YEAR, 1);
        }
        return Math.max(0L, start.getTimeInMillis() - now.getTimeInMillis());
    }

    /** Milliseconds until today's 17:30, or -1 if that time has already passed. */
    public static long millisUntilTodayWindowEnd() {
        Calendar now = Calendar.getInstance();
        Calendar end = (Calendar) now.clone();
        end.set(Calendar.HOUR_OF_DAY, TRACK_END_HOUR);
        end.set(Calendar.MINUTE, TRACK_END_MINUTE);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        long delta = end.getTimeInMillis() - now.getTimeInMillis();
        return delta >= 0L ? delta : -1L;
    }

    /**
     * Schedules location publishing for the given auth UID (or legacy key).
     * Safe to call repeatedly; uses unique work names per user.
     */
    public static void ensureScheduled(Context context, String userName) {
        if (context == null) return;
        String key = userKey(userName);
        if (key.isEmpty()) return;

        Context app = context.getApplicationContext();
        persistActiveUid(app, key);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        WorkManager wm = WorkManager.getInstance(app);

        // Periodic updates every 15 minutes, any time of day.
        PeriodicWorkRequest updateWork = new PeriodicWorkRequest.Builder(
                LastLocationUpdateWorker.class,
                UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putString(LastLocationUpdateWorker.KEY_USER_NAME, key)
                        .putBoolean(LastLocationUpdateWorker.KEY_FORCE_UPLOAD, true)
                        .build())
                .build();

        wm.cancelUniqueWork(WORK_CLEANUP + key);
        wm.cancelUniqueWork(WORK_WINDOW_START + key);
        wm.cancelUniqueWork(WORK_WINDOW_END + key);

        // UPDATE refreshes interval/constraints/input for already-enqueued unique work.
        wm.enqueueUniquePeriodicWork(
                WORK_PERIODIC + key,
                ExistingPeriodicWorkPolicy.UPDATE,
                updateWork
        );

        enqueueImmediateUpdate(wm, constraints, key, true);
        Log.i(TAG, "Location tracking scheduled; immediate update enqueued for " + redact(key));
    }

    private static void enqueueImmediateUpdate(WorkManager wm, Constraints constraints, String key,
                                               boolean forceOutsideWindow) {
        OneTimeWorkRequest immediateUpdate = new OneTimeWorkRequest.Builder(LastLocationUpdateWorker.class)
                .setConstraints(constraints)
                .setInputData(new androidx.work.Data.Builder()
                        .putString(LastLocationUpdateWorker.KEY_USER_NAME, key)
                        .putBoolean(LastLocationUpdateWorker.KEY_FORCE_UPLOAD, forceOutsideWindow)
                        .build())
                .build();
        wm.enqueueUniqueWork(WORK_NOW + key, ExistingWorkPolicy.REPLACE, immediateUpdate);
    }

    /**
     * Re-schedules tracking for the currently authenticated Firebase user, if any.
     * Used after reboot / app update.
     */
    public static void ensureScheduledForLoggedInUser(Context context) {
        if (context == null) return;
        try {
            com.google.firebase.auth.FirebaseUser u =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (u != null && u.getUid() != null && !u.getUid().trim().isEmpty()) {
                ensureScheduled(context, u.getUid());
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "ensureScheduledForLoggedInUser: auth unavailable", e);
        }
        String saved = getPersistedActiveUid(context);
        if (saved != null && !saved.isEmpty()) {
            Log.w(TAG, "No Firebase user on resume; not restarting location for cached uid");
        }
    }

    public static void cancelScheduled(Context context, String userName) {
        if (context == null) return;
        String key = userKey(userName);
        if (key.isEmpty()) return;
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        wm.cancelUniqueWork(WORK_PERIODIC + key);
        wm.cancelUniqueWork(WORK_NOW + key);
        wm.cancelUniqueWork(WORK_WINDOW_START + key);
        wm.cancelUniqueWork(WORK_WINDOW_END + key);
        wm.cancelUniqueWork(WORK_CLEANUP + key);
        clearPersistedActiveUid(context, key);
        Log.i(TAG, "Location tracking stopped for " + redact(key));
    }

    public static void cacheLastLocation(Context context, String userName, String json) {
        if (context == null) return;
        String key = userKey(userName);
        if (key.isEmpty()) return;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString("loc_" + key, json).apply();
    }

    @Nullable
    public static String getCachedLastLocation(Context context, String userName) {
        if (context == null) return null;
        String key = userKey(userName);
        if (key.isEmpty()) return null;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("loc_" + key, null);
    }

    private static void persistActiveUid(Context context, String uid) {
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_ACTIVE_UID, uid)
                    .apply();
        } catch (Exception ignored) {}
    }

    @Nullable
    private static String getPersistedActiveUid(Context context) {
        try {
            return context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(PREF_ACTIVE_UID, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void clearPersistedActiveUid(Context context, String key) {
        try {
            SharedPreferences p = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String active = p.getString(PREF_ACTIVE_UID, null);
            if (active != null && active.equals(key)) {
                p.edit().remove(PREF_ACTIVE_UID).apply();
            }
        } catch (Exception ignored) {}
    }

    /** Redact UID for logs (keep prefix only). */
    public static String redact(String uid) {
        if (uid == null || uid.length() < 6) return "(short)";
        return uid.substring(0, 4) + "…";
    }
}
