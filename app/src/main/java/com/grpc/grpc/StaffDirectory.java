package com.grpc.grpc;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * StaffDirectory
 *
 * Minimal helper to fetch staff details from Firestore using fixed IDs:
 * - James = 001
 * - Ian = 002
 * - Dean = 003
 * - Kristine = 004
 *
 * Expected Firestore document fields (case-insensitive handling in code):
 * - email
 * - name
 * - title
 * - mobile (optional)
 *
 * NOTE: This does not change Firebase structure. It only reads.
 */
public class StaffDirectory {
    private static final String TAG = "StaffDirectory";

    // Try a small set of likely collection names to avoid forcing a restructure.
    private static final String[] COLLECTIONS = new String[] {"Staff", "staff", "Users", "users"};

    /** Admin user id in users collection (James = 001). Used for API key update and admin-only UI. */
    public static final String ADMIN_USER_ID = "001";

    private static final Map<String, String> USERNAME_TO_ID = new HashMap<>();
    private static final Map<String, String> ID_TO_USERNAME = new HashMap<>();
    static {
        USERNAME_TO_ID.put("james", "001");
        USERNAME_TO_ID.put("ian", "002");
        USERNAME_TO_ID.put("dean", "003");
        USERNAME_TO_ID.put("kristine", "004");
        ID_TO_USERNAME.put("001", "james");
        ID_TO_USERNAME.put("002", "ian");
        ID_TO_USERNAME.put("003", "dean");
        ID_TO_USERNAME.put("004", "kristine");
    }

    public interface Callback {
        void onResult(@Nullable StaffProfile profile);
    }

    public static class StaffProfile {
        public final String id;
        public final String userName;
        public final String name;
        public final String title;
        public final String email;
        public final String mobile;

        public StaffProfile(String id, String userName, String name, String title, String email, String mobile) {
            this.id = id;
            this.userName = userName;
            this.name = name;
            this.title = title;
            this.email = email;
            this.mobile = mobile;
        }
    }

    public static void fetchByUserName(Context context, String userName, Callback callback) {
        if (userName == null) {
            if (callback != null) callback.onResult(null);
            return;
        }

        String key = userName.trim().toLowerCase(Locale.getDefault());
        String id = USERNAME_TO_ID.get(key);
        if (id == null) {
            if (callback != null) callback.onResult(null);
            return;
        }

        FirebaseFirestore db = FirebaseHelper.getFirestore();
        fetchFromCollections(db, id, key, 0, callback);
    }

    /** Fetches staff by document id (e.g. "001" for James) from Staff/Users collection. */
    public static void fetchById(Context context, String id, Callback callback) {
        if (id == null || id.trim().isEmpty()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        String userKey = ID_TO_USERNAME.get(id.trim());
        if (userKey == null) userKey = id.trim().toLowerCase(Locale.getDefault());
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        fetchFromCollections(db, id.trim(), userKey, 0, callback);
    }

    private static void fetchFromCollections(FirebaseFirestore db, String id, String userKey, int idx, Callback callback) {
        if (idx >= COLLECTIONS.length) {
            if (callback != null) callback.onResult(null);
            return;
        }
        String collection = COLLECTIONS[idx];
        db.collection(collection).document(id).get()
                .addOnSuccessListener(ds -> {
                    if (ds != null && ds.exists()) {
                        if (callback != null) callback.onResult(fromDoc(ds, id, userKey));
                    } else {
                        fetchFromCollections(db, id, userKey, idx + 1, callback);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Fetch failed from " + collection + "/" + id + ": " + e.getMessage());
                    fetchFromCollections(db, id, userKey, idx + 1, callback);
                });
    }

    private static StaffProfile fromDoc(DocumentSnapshot ds, String id, String userKey) {
        String email = pickString(ds, "email", "Email");
        String name = pickString(ds, "name", "Name");
        String title = pickString(ds, "title", "Title");
        String mobile = pickString(ds, "mobile", "Mobile", "phone", "Phone", "number", "Number");
        return new StaffProfile(id, userKey, safe(name), safe(title), safe(email), safe(mobile));
    }

    private static String pickString(DocumentSnapshot ds, String... keys) {
        for (String k : keys) {
            try {
                // Use generic get() so we work even if the field is stored as a number, etc.
                Object raw = ds.get(k);
                if (raw == null) continue;
                String v = String.valueOf(raw).trim();
                if (!v.isEmpty()) return v;
            } catch (Exception ignored) {
                // If Firestore complains about type, just try next key
            }
        }
        return "";
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}

