package com.grpc.grpc.location;

import com.grpc.grpc.core.*;
import com.grpc.grpc.location.worker.LastLocationUpdateWorker;
import com.grpc.grpc.location.worker.LastLocationCleanupWorker;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Location sharing helper:
 * - Every technician device publishes last-known location every 15 minutes (best effort).
 * - Location records expire: cleanup deletes a location doc after 30 minutes.
 * - Admin/oversight UI can read Firestore + local cache for offline access.
 */
public final class LocationSharing {
    private LocationSharing() {}

    public static final String COLLECTION_LAST_LOCATIONS = "last_locations";

    // SharedPreferences cache (used by location finder; also safe for any client)
    private static final String PREFS = "GRPC_LAST_LOC_CACHE";

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

    public static void ensureScheduled(Context context, String userName) {
        if (context == null) return;
        String key = userKey(userName);
        if (key.isEmpty()) return;

        // Run once immediately on login (best effort).
        OneTimeWorkRequest immediateUpdate = new OneTimeWorkRequest.Builder(LastLocationUpdateWorker.class)
                .setInputData(new androidx.work.Data.Builder()
                        .putString(LastLocationUpdateWorker.KEY_USER_NAME, userName)
                        .build())
                .build();

        // Update: every 15 minutes (WorkManager minimum)
        PeriodicWorkRequest updateWork = new PeriodicWorkRequest.Builder(
                LastLocationUpdateWorker.class,
                10, TimeUnit.MINUTES   // was 15
        )
                .setInputData(new androidx.work.Data.Builder()
                        .putString(LastLocationUpdateWorker.KEY_USER_NAME, userName)
                        .build())
                .build();

        // Cleanup: every 30 minutes (deletes docs older than 30 minutes)
        PeriodicWorkRequest cleanupWork = new PeriodicWorkRequest.Builder(
                LastLocationCleanupWorker.class,
                30, TimeUnit.MINUTES
        )
                .setInputData(new androidx.work.Data.Builder()
                        .putString(LastLocationCleanupWorker.KEY_USER_NAME, userName)
                        .build())
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                "location_update_" + key,
                ExistingPeriodicWorkPolicy.UPDATE,
                updateWork
        );

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                "location_update_now_" + key,
                ExistingWorkPolicy.REPLACE,
                immediateUpdate
        );

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                "location_cleanup_" + key,
                ExistingPeriodicWorkPolicy.UPDATE,
                cleanupWork
        );
    }

    public static void cancelScheduled(Context context, String userName) {
        if (context == null) return;
        String key = userKey(userName);
        if (key.isEmpty()) return;
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        wm.cancelUniqueWork("location_update_" + key);
        wm.cancelUniqueWork("location_update_now_" + key);
        wm.cancelUniqueWork("location_cleanup_" + key);
    }

    public static void cacheLastLocation(Context context, String userName, String json) {
        if (context == null) return;
        String key = userKey(userName);
        if (key.isEmpty()) return;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString("loc_" + key, json).apply();
    }

    public static String getCachedLastLocation(Context context, String userName) {
        if (context == null) return null;
        String key = userKey(userName);
        if (key.isEmpty()) return null;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("loc_" + key, null);
    }
}

