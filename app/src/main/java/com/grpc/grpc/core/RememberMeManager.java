package com.grpc.grpc.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RememberMeManager {
    private static final String PREFS = "GRPC";
    private static final String KEY_ENABLED = "REMEMBER_ME_ENABLED";
    private static final String KEY_UID = "REMEMBER_ME_UID";
    private static final String KEY_EMAIL = "REMEMBER_ME_EMAIL";
    private static final String KEY_EXPIRES_AT = "REMEMBER_ME_EXPIRES_AT";
    public static final int REMEMBER_ME_DAYS = 7;
    private static final long REMEMBER_ME_WINDOW_MS = REMEMBER_ME_DAYS * 24L * 60L * 60L * 1000L;

    private RememberMeManager() {}

    public static long enable(Context context, String uid, String email) {
        long expiresAt = System.currentTimeMillis() + REMEMBER_ME_WINDOW_MS;
        prefs(context).edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_UID, uid != null ? uid : "")
                .putString(KEY_EMAIL, email != null ? email : "")
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply();

        if (uid != null && !uid.trim().isEmpty()) {
            Map<String, Object> update = new HashMap<>();
            update.put("rememberMeUntilMs", expiresAt);
            FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.USERS)
                    .document(uid.trim())
                    .set(update, SetOptions.merge());
        }

        return expiresAt;
    }

    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_ENABLED)
                .remove(KEY_UID)
                .remove(KEY_EMAIL)
                .remove(KEY_EXPIRES_AT)
                .apply();
    }

    public static void disableForCurrentUser(Context context) {
        String uid = "";
        try {
            com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getUid() != null) {
                uid = user.getUid().trim();
            }
        } catch (Exception ignored) {}

        if (uid.isEmpty()) {
            uid = prefs(context).getString(KEY_UID, "");
        }

        clear(context);

        if (uid != null && !uid.trim().isEmpty()) {
            Map<String, Object> update = new HashMap<>();
            update.put("canRemember", false);
            update.put("rememberMeUntilMs", FieldValue.delete());
            FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.USERS)
                    .document(uid.trim())
                    .set(update, SetOptions.merge());
        }
    }

    public static String getRememberedEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static boolean isActive(Context context) {
        if (!prefs(context).getBoolean(KEY_ENABLED, false)) return false;
        long expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L);
        if (expiresAt <= System.currentTimeMillis()) return false;
        String rememberedUid = prefs(context).getString(KEY_UID, "");
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null
                && user.getUid() != null
                && user.getUid().equals(rememberedUid);
    }

    public static boolean handleExpiryIfNeeded(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false;

        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
        if (expiresAt > System.currentTimeMillis()) return false;

        String uid = prefs.getString(KEY_UID, "");
        clear(context);

        if (uid != null && !uid.trim().isEmpty()) {
            Map<String, Object> update = new HashMap<>();
            update.put("canRemember", false);
            update.put("rememberMeUntilMs", FieldValue.delete());
            FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.USERS)
                    .document(uid.trim())
                    .set(update, SetOptions.merge());
        }

        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {}

        return true;
    }

    public static boolean profileAllowsRemember(Map<String, Object> data) {
        if (data == null) return false;
        return pickBoolean(data, "canRemember", "CanRemember", "can_remember");
    }

    private static boolean pickBoolean(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object raw = data.get(key);
            if (raw == null) continue;
            if (raw instanceof Boolean) return (Boolean) raw;
            if (raw instanceof Number) return ((Number) raw).intValue() != 0;
            String s = String.valueOf(raw).trim().toLowerCase(Locale.getDefault());
            if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        }
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
