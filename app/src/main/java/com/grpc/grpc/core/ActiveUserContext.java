package com.grpc.grpc.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Single source of truth for the active user (technician) context.
 * Persists so that swiping between views/tabs or app restore never loses the selected user.
 */
public final class ActiveUserContext {

    private static final String PREFS_NAME = "GRPC";
    private static final String KEY_USER_NAME = "USER_NAME";
    private static final String KEY_USER_EMAIL = "USER_EMAIL";
    private static final String KEY_AUTH_UID = "ACTIVE_AUTH_UID";

    /**
     * Get the active user name, or null if not set.
     */
    public static String getActiveUserName(Context context) {
        if (context == null) return null;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Prevent cross-login identity leakage on shared devices:
        // only reuse cached identity if it belongs to the currently authenticated Firebase UID.
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            String cachedUid = sp.getString(KEY_AUTH_UID, "");
            if (u != null) {
                String uid = u.getUid();
                if (cachedUid == null || cachedUid.isEmpty() || !uid.equals(cachedUid)) return null;
            } else {
                // If we're not authenticated, don't reuse an identity that was cached for an online account.
                if (cachedUid != null && !cachedUid.isEmpty()) return null;
            }
        } catch (Exception ignored) {}
        return sp.getString(KEY_USER_NAME, null);
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
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null && u.getUid() != null) editor.putString(KEY_AUTH_UID, u.getUid());
            else editor.remove(KEY_AUTH_UID);
        } catch (Exception ignored) {}
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

    /** Clears persisted active user identity (used for forced logout). */
    public static void clear(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_AUTH_UID)
                .apply();
    }
}
