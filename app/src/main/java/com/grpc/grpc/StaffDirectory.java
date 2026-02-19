package com.grpc.grpc;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StaffDirectory
 *
 * Fetches staff details from Firestore (users collection) by document ID (001, 002, …).
 * Names and numbers are loaded from the database and cached so the app is scalable
 * without hardcoded names/phones. Fallback maps are used only until Firestore is loaded.
 *
 * Expected Firestore document fields (e.g. users/001):
 * - Name, Email, Title, Number (or mobile/Mobile/phone/Phone)
 *
 * Call refreshFromFirestore(context) on app startup (e.g. MainActivity) to load from DB.
 */
public class StaffDirectory {
    private static final String TAG = "StaffDirectory";

    private static final String[] COLLECTIONS = new String[] {"Staff", "staff", "Users", "users"};

    /** Admin user ID 001. Used for API key update and admin-only UI. */
    public static final String ADMIN_USER_ID = "001";

    /** Admin user IDs (001, 002, 004). Notified when technician 003 adds contracts. */
    public static final Set<String> ADMIN_USER_IDS = new HashSet<>();
    /** User IDs to notify when 001 or 003 adds a job (oversight). */
    public static final String[] JOB_OVERSIGHT_USER_IDS = new String[] {"002", "004"};
    /** User ID to notify when 001 or 003 adds a lead (oversight). */
    public static final String LEAD_OVERSIGHT_USER_ID = "002";
    /** User IDs allowed to mark leads as paid / edit materials: 001, 002, 004. */
    public static final Set<String> LEADS_MARK_PAID_USER_IDS = new HashSet<>();
    /** User IDs allowed to access Commission/Leads screen: 001, 002, 003, 004. */
    public static final Set<String> COMMISSION_ACCESS_USER_IDS = new HashSet<>();

    private static final Map<String, String> USERNAME_TO_ID = new HashMap<>();
    private static final Map<String, String> ID_TO_USERNAME = new HashMap<>();
    /** Ordered list of user IDs for dropdowns; names from Firestore or getFallbackDisplayName. */
    public static final String[] ORDERED_USER_IDS = new String[] {"001", "002", "003", "004"};
    /** IDs for contract technician selector (001, 002, 003). */
    public static final String[] CONTRACT_TECHNICIAN_IDS = new String[] {"001", "002", "003"};

    static {
        USERNAME_TO_ID.put("james", "001");
        USERNAME_TO_ID.put("ian", "002");
        USERNAME_TO_ID.put("dean", "003");
        USERNAME_TO_ID.put("kristine", "004");
        ID_TO_USERNAME.put("001", "james");
        ID_TO_USERNAME.put("002", "ian");
        ID_TO_USERNAME.put("003", "dean");
        ID_TO_USERNAME.put("004", "kristine");
        ADMIN_USER_IDS.add("001");
        ADMIN_USER_IDS.add("002");
        ADMIN_USER_IDS.add("004");
        LEADS_MARK_PAID_USER_IDS.add("001");
        LEADS_MARK_PAID_USER_IDS.add("002");
        LEADS_MARK_PAID_USER_IDS.add("004");
        COMMISSION_ACCESS_USER_IDS.add("001");
        COMMISSION_ACCESS_USER_IDS.add("002");
        COMMISSION_ACCESS_USER_IDS.add("003");
        COMMISSION_ACCESS_USER_IDS.add("004");
    }

    /** Resolve display/user name to user ID (001, 002, …). Returns null if unknown. */
    @Nullable
    public static String getUserId(String userName) {
        if (userName == null || userName.trim().isEmpty()) return null;
        return USERNAME_TO_ID.get(userName.trim().toLowerCase(Locale.getDefault()));
    }

    public static boolean isJamesUserId(String id) {
        return ADMIN_USER_ID.equals(id);
    }

    /** True if user ID is an admin: 001, 002, 004. */
    public static boolean isAdminUserId(String id) {
        return id != null && ADMIN_USER_IDS.contains(id);
    }

    /** True if user can mark leads as paid / edit materials: 001, 002, 004. */
    public static boolean canMarkPaidLeadsUserId(String id) {
        return id != null && LEADS_MARK_PAID_USER_IDS.contains(id);
    }

    /** True if user can access Commission/Leads: 001, 002, 003, 004. */
    public static boolean canAccessCommissionLeadsUserId(String id) {
        return id != null && COMMISSION_ACCESS_USER_IDS.contains(id);
    }

    /** True if user sees all jobs (002, 004); 001 and 003 see only their own. */
    public static boolean seesAllJobsUserId(String id) {
        return "002".equals(id) || "004".equals(id);
    }

    /** Returns the login key for an ID (for collection names and notification targets). */
    public static String getUserNameKey(String id) {
        if (id == null) return null;
        String key = ID_TO_USERNAME.get(id);
        return key != null ? key : id;
    }

    /** First name style for fallback when Firestore Name not yet loaded; prefer Firestore Name. */
    public static String getFallbackDisplayName(String id) {
        String key = getUserNameKey(id);
        if (key == null || key.isEmpty()) return id != null ? id : "";
        return key.substring(0, 1).toUpperCase(Locale.getDefault()) + key.substring(1);
    }

    /** Display names for the given IDs (for spinners/dropdowns); no hardcoded names in callers. */
    public static String[] getDisplayNamesForIds(String[] ids) {
        if (ids == null) return new String[0];
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) names[i] = getFallbackDisplayName(ids[i]);
        return names;
    }

    /** Firestore collection name for contracts for this user (e.g. "[User] Contracts"). */
    public static String getContractsCollectionName(String id) {
        return getFallbackDisplayName(id) + " Contracts";
    }

    /** Mobile number for WhatsApp/reports. Filled only from Firestore (users.Number) via refreshFromFirestore(). */
    private static final Map<String, String> ID_TO_MOBILE = new ConcurrentHashMap<>();
    /** Full name for report footer. Filled only from Firestore (users.Name) via refreshFromFirestore(). */
    private static final Map<String, String> ID_TO_REPORT_NAME = new ConcurrentHashMap<>();

    /**
     * Refreshes names and numbers from Firestore (users collection). Call on app startup
     * so getReportDisplayName and getMobileForUserId return database values and stay scalable.
     */
    public static void refreshFromFirestore(Context context) {
        if (context == null) return;
        for (String id : ORDERED_USER_IDS) {
            fetchById(context, id, profile -> {
                if (profile == null) return;
                if (profile.name != null && !profile.name.trim().isEmpty()) {
                    ID_TO_REPORT_NAME.put(profile.id, profile.name.trim());
                }
                if (profile.mobile != null && !profile.mobile.trim().isEmpty()) {
                    String normalized = normalizeMobile(profile.mobile.trim());
                    ID_TO_MOBILE.put(profile.id, normalized);
                }
            });
        }
    }

    /** Normalize phone from DB (e.g. 9-digit number → 0-prefix for Irish format). */
    private static String normalizeMobile(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 9 && !value.startsWith("0")) {
            return "0" + digits;
        }
        return value;
    }

    /** Returns mobile for user (from Firestore cache when available, else fallback). */
    public static String getMobileForUserId(String id) {
        if (id == null) return null;
        return ID_TO_MOBILE.get(id);
    }

    /** Returns full display name for reports (from Firestore cache when available, else fallback). */
    public static String getReportDisplayName(String id) {
        if (id == null) return "";
        String name = ID_TO_REPORT_NAME.get(id);
        return name != null ? name : getFallbackDisplayName(id);
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

    /** Fetches staff by document id (e.g. "001") from Staff/Users collection. */
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

