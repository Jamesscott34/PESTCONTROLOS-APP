package com.grpc.grpc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Demo and offline only (not grpc): after OFFLINE_TRIAL_DAYS, certain features (Create Report, Custom template)
 * redirect the user to the configured website. GRPC has OFFLINE_TRIAL_DAYS=0 so is never affected.
 */
public final class OfflineTrialHelper {

    private static final String PREFS_NAME = "GRPC";
    private static final String KEY_FIRST_LAUNCH_MS = "offline_first_launch_ms";

    /**
     * Call from MainActivity so the first open sets the trial start (demo and offline only; grpc has 0 days).
     */
    public static void recordLaunchIfNeeded(Context context) {
        if (BuildConfig.OFFLINE_TRIAL_DAYS <= 0) return;
        try {
            long existing = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_FIRST_LAUNCH_MS, 0L);
            if (existing == 0L) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis())
                        .apply();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Returns true if trial applies (demo/offline) and the trial period has expired. GRPC always false.
     */
    public static boolean isTrialExpired(Context context) {
        try {
            int trialDays = BuildConfig.OFFLINE_TRIAL_DAYS;
            if (trialDays <= 0) return false;
            long firstMs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_FIRST_LAUNCH_MS, 0L);
            if (firstMs == 0L) return false;
            long elapsedDays = (System.currentTimeMillis() - firstMs) / (24L * 60 * 60 * 1000);
            return elapsedDays >= trialDays;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * If trial is expired (demo/offline only): shows a toast, opens the website, finishes the activity, and returns true.
     * Otherwise returns false and the caller can continue as normal. GRPC is never redirected.
     *
     * @param activity    the activity that would have shown the feature (will be finished if expired)
     * @param websiteUrl  URL to open (e.g. from getString(R.string.main_website_url))
     * @param toastMessage optional message before redirect (null to skip toast)
     */
    public static boolean openWebsiteIfExpired(Activity activity, String websiteUrl, String toastMessage) {
        if (!isTrialExpired(activity)) return false;
        if (toastMessage != null && !toastMessage.isEmpty()) {
            Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show();
        }
        String url = (websiteUrl != null && !websiteUrl.trim().isEmpty()) ? websiteUrl.trim() : "https://www.pestcontrolos.ie";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
        activity.finish();
        return true;
    }

    /**
     * Opens the website, shows an optional toast, and finishes the activity.
     * Used when offline users try to use custom templates (they must sign up for demo or download updated app).
     */
    public static void openWebsiteAndFinish(Activity activity, String websiteUrl, String toastMessage) {
        if (toastMessage != null && !toastMessage.isEmpty()) {
            Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show();
        }
        String url = (websiteUrl != null && !websiteUrl.trim().isEmpty()) ? websiteUrl.trim() : "https://www.pestcontrolos.ie";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
        activity.finish();
    }
}
