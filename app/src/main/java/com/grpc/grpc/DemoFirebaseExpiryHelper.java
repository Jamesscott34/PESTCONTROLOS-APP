package com.grpc.grpc;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

/**
 * Demo flavor only (not grpc, not offline): per-user profile controls access.
 * Each user has DemoRelease (boolean) and DemoDaysNumber (int) in Firestore.
 * - demoRelease true: profile is closed after demoDaysNumber days from first launch (all roles including super_admin).
 * - demoRelease false: constant access (no time limit).
 * GRPC and any other company flavors have continuous use and are never affected.
 */
public final class DemoFirebaseExpiryHelper {

    private static final String PREFS_NAME = "GRPC";
    private static final String PREFIX_FIRST_LAUNCH_MS = "demo_first_launch_ms_";

    /**
     * Call when demo session is loaded. Records first launch time for this user (keyed by staffId).
     * Only runs when IS_DEMO and staffId is non-empty. Each user gets their own day counter.
     */
    public static void recordDemoLaunchIfNeeded(Context context, String staffId) {
        if (!BuildConfig.IS_DEMO) return;
        if (staffId == null || staffId.trim().isEmpty()) return;
        try {
            String key = PREFIX_FIRST_LAUNCH_MS + staffId.trim();
            long existing = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(key, 0L);
            if (existing == 0L) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong(key, System.currentTimeMillis())
                        .apply();
            }
        } catch (Exception ignored) {}
    }

    /**
     * True if the current user should be blocked (profile closed): demo flavor, demoRelease true,
     * and elapsed days from first launch >= demoDaysNumber. Applies to all roles including super_admin.
     * GRPC and offline always false.
     */
    public static boolean isFirebaseBlockedForCurrentUser(Context context) {
        if (!BuildConfig.IS_DEMO) return false;
        SessionManager.Session s = SessionManager.getCached(context);
        if (s == null) return false;
        if (!s.demoRelease || s.demoDaysNumber <= 0) return false;  // constant access or no limit set
        try {
            String key = PREFIX_FIRST_LAUNCH_MS + s.staffId;
            long firstMs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(key, 0L);
            if (firstMs == 0L) return false;  // not recorded yet, don't block
            long elapsedDays = (System.currentTimeMillis() - firstMs) / (24L * 60 * 60 * 1000);
            return elapsedDays >= s.demoDaysNumber;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * If demo profile is closed for current user: show toast, finish activity, return true.
     * Call at start of onCreate in Firebase-dependent activities.
     */
    public static boolean finishIfBlocked(Activity activity) {
        if (!isFirebaseBlockedForCurrentUser(activity)) return false;
        Toast.makeText(activity, activity.getString(R.string.demo_expired_message), Toast.LENGTH_LONG).show();
        activity.finish();
        return true;
    }
}
