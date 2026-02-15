package com.grpc.grpc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single source of truth for the active user (technician) context.
 * Persists so that swiping between views/tabs or app restore never loses the selected user.
 */
public final class ActiveUserContext {

    private static final String PREFS_NAME = "GRPC";
    private static final String KEY_USER_NAME = "USER_NAME";
    private static final String KEY_USER_EMAIL = "USER_EMAIL";

    /**
     * Get the active user name, or null if not set.
     */
    public static String getActiveUserName(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_USER_NAME, null);
    }

    /**
     * Get the active user name, or defaultName if not set.
     */
    public static String getActiveUserName(Context context, String defaultName) {
        String name = getActiveUserName(context);
        return (name != null && !name.isEmpty()) ? name : defaultName;
    }

    /**
     * Set and persist the active user (e.g. after login or when entering an activity with USER_NAME).
     */
    public static void setActiveUser(Context context, String userName, String userEmail) {
        if (context == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        if (userName != null) editor.putString(KEY_USER_NAME, userName);
        if (userEmail != null) editor.putString(KEY_USER_EMAIL, userEmail);
        editor.apply();
    }

    /**
     * Set only the active user name.
     */
    public static void setActiveUserName(Context context, String userName) {
        if (context == null) return;
        if (userName != null && !userName.isEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_USER_NAME, userName).apply();
        }
    }
}
