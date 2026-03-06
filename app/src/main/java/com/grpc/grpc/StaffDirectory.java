package com.grpc.grpc;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StaffDirectory
 *
 * Fetches staff details from Firestore (users collection) by StaffID document ID (3 digits).
 * Names and numbers are loaded from the database and cached so the app is scalable
 * without hardcoded names/phones. Fallback maps are used only until Firestore is loaded.
 *
 * Expected Firestore document fields (e.g. users/<StaffID>):
 * - Name, Email, Title, Number (or mobile/Mobile/phone/Phone)
 *
 * Call refreshFromFirestore(context) on app startup (e.g. MainActivity) to load from DB.
 */
public class StaffDirectory {
    private static final String TAG = "StaffDirectory";

    private static final String[] COLLECTIONS = new String[] {"Staff", "staff", "Users", "users"};

    // Hard rule: no hardcoded staff names or StaffIDs. All mappings come from Firestore.
    // Cache ContractKey <-> StaffID mappings after refreshFromFirestore()/fetchAllStaffProfiles().
    private static final Map<String, String> CONTRACTKEY_TO_ID = new ConcurrentHashMap<>();

    // Best-effort cached admin IDs (derived from Role/capability fields in users docs).
    private static final Set<String> CACHED_ADMIN_STAFF_IDS = Collections.synchronizedSet(new HashSet<>());

    /** Resolve ContractKey or StaffID to StaffID. Returns null if unknown. */
    @Nullable
    public static String getUserId(String userName) {
        if (userName == null || userName.trim().isEmpty()) return null;
        String raw = userName.trim();
        // If caller already has a numeric StaffID.
        if (raw.matches("\\d{1,3}")) {
            try {
                int n = Integer.parseInt(raw);
                return String.format(Locale.US, "%03d", n);
            } catch (Exception ignored) {
                return raw;
            }
        }
        String key = raw.toLowerCase(Locale.getDefault());
        String id = CONTRACTKEY_TO_ID.get(key);
        if (id != null && !id.trim().isEmpty()) return id.trim();
        // As a fallback, try matching cached staff profiles by ContractKey (case-insensitive).
        List<StaffProfile> profiles = getCachedStaffProfiles();
        for (StaffProfile p : profiles) {
            if (p == null || p.id == null) continue;
            String ck = p.contractKey != null ? p.contractKey.trim() : "";
            if (!ck.isEmpty() && ck.equalsIgnoreCase(raw)) return p.id.trim();
        }
        return null;
    }

    /** Returns the login key for an ID (for collection names and notification targets). */
    public static String getUserNameKey(String id) {
        if (id == null) return null;
        String key = ID_TO_CONTRACT_KEY.get(id);
        return (key != null && !key.trim().isEmpty()) ? key.trim() : id;
    }

    /** First name style for fallback when Firestore Name not yet loaded; prefer Firestore Name. */
    public static String getFallbackDisplayName(String id) {
        String key = getUserNameKey(id);
        if (key == null || key.trim().isEmpty()) return id != null ? id : "";
        return key.trim();
    }

    /** Display names for the given IDs (for spinners/dropdowns); no hardcoded names in callers. */
    public static String[] getDisplayNamesForIds(String[] ids) {
        if (ids == null) return new String[0];
        String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) names[i] = getFallbackDisplayName(ids[i]);
        return names;
    }

    /** Capitalize ContractKey for collection names: first letter upper, rest lower (e.g. "dean" -> "Dean") so Firestore "Dean Contracts" / "James Contracts" match. */
    public static String capitalizeContractKey(String key) {
        if (key == null || key.trim().isEmpty()) return key != null ? key : "";
        String s = key.trim();
        if (s.length() == 1) return s.toUpperCase(Locale.getDefault());
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1).toLowerCase(Locale.getDefault());
    }

    /** Contracts collection for this user: ContractKey-based (e.g. "Ian Contracts"). Falls back to StaffID if no ContractKey. Uses capitalized key so "dean" -> "Dean Contracts". */
    public static String getContractsCollectionName(String staffId) {
        if (staffId == null || staffId.trim().isEmpty()) return " Contracts";
        String id = staffId.trim();
        String key = ID_TO_CONTRACT_KEY.get(id);
        if (key != null && !key.trim().isEmpty()) return capitalizeContractKey(key) + " Contracts";
        return id + " Contracts";
    }

    /** Resolve any key (StaffID or ContractKey) to the contracts collection name (ContractKey-based). Capitalizes key so lowercase from auth/UI still matches Firestore "Dean Contracts" / "James Contracts". */
    public static String getContractsCollectionNameFromAnyKey(String keyOrId) {
        if (keyOrId == null) return "";
        String v = keyOrId.trim();
        if (v.isEmpty()) return "";
        if (v.matches("\\d{3}")) return getContractsCollectionName(v);
        String staffId = getUserId(v);
        if (staffId != null && !staffId.trim().isEmpty()) return getContractsCollectionName(staffId);
        return capitalizeContractKey(v) + " Contracts";
    }

    /** Mobile number for WhatsApp/reports. Filled only from Firestore (users.Number) via refreshFromFirestore(). */
    private static final Map<String, String> ID_TO_MOBILE = new ConcurrentHashMap<>();
    /** Full name for report footer. Filled only from Firestore (users.Name) via refreshFromFirestore(). */
    private static final Map<String, String> ID_TO_REPORT_NAME = new ConcurrentHashMap<>();
    /** Job title for technician (users.Title) via refreshFromFirestore(). */
    private static final Map<String, String> ID_TO_TITLE = new ConcurrentHashMap<>();
    /** Contract collection key for this user (users.ContractKey) via refreshFromFirestore(). */
    private static final Map<String, String> ID_TO_CONTRACT_KEY = new ConcurrentHashMap<>();

    /** In-memory snapshot of staff profiles loaded from Firestore (best-effort). */
    private static volatile List<StaffProfile> CACHED_STAFF_PROFILES = null;

    public interface StaffListCallback {
        void onResult(List<StaffProfile> profiles);
    }

    /** Option for assign-to / technician / user spinners. Use ownerKey (ContractKey) for logic and display. */
    public static final class OwnerOption {
        public final String staffId;      // StaffID (3 digits), for internal lookup
        public final String ownerKey;     // ContractKey – use for contracts collection, workview, assign-to, notifications
        public final String display;      // Label for spinner (ContractKey, same as ownerKey)

        public OwnerOption(String staffId, String ownerKey, String display) {
            this.staffId = staffId != null ? staffId : "";
            this.ownerKey = ownerKey != null ? ownerKey : "";
            this.display = display != null ? display : "";
        }
    }

    public interface OwnerOptionsCallback {
        void onResult(List<OwnerOption> options);
    }

    /**
     * Refreshes names and numbers from Firestore (users collection). Call on app startup
     * so getReportDisplayName and getMobileForUserId return database values and stay scalable.
     */
    public static void refreshFromFirestore(Context context) {
        if (context == null) return;
        // Load ALL staff profiles (not just the legacy hardcoded list),
        // so dropdowns like Assign-to and View Contracts can scale.
        fetchAllStaffProfiles(context, profiles -> {
            // no-op: caches are updated in fetchAllStaffProfiles
        });
    }

    /**
     * Fetch all staff profile docs from Firestore `users/{StaffID}`.
     * Filters out UID mapping docs by requiring docId (or StaffID field) to match 3 digits.
     */
    public static void fetchAllStaffProfiles(Context context, StaffListCallback callback) {
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        if (db == null) {
            if (callback != null) callback.onResult(Collections.emptyList());
            return;
        }
        db.collection("users").limit(300).get()
                .addOnSuccessListener(qs -> {
                    List<StaffProfile> out = new ArrayList<>();
                    // Rebuild caches from Firestore to avoid stale cross-login identity.
                    try {
                        CONTRACTKEY_TO_ID.clear();
                        ID_TO_MOBILE.clear();
                        ID_TO_REPORT_NAME.clear();
                        ID_TO_TITLE.clear();
                        ID_TO_CONTRACT_KEY.clear();
                        CACHED_ADMIN_STAFF_IDS.clear();
                    } catch (Exception ignored) {}
                    if (qs != null) {
                        for (DocumentSnapshot doc : qs.getDocuments()) {
                            if (doc == null || !doc.exists()) continue;
                            String docId = doc.getId();
                            String staffId = (docId != null && docId.matches("\\d{3}")) ? docId : null;
                            if (staffId == null) {
                                String staffField = pickString(doc, "StaffID", "staffId", "staffID", "staff_id");
                                if (staffField != null && staffField.trim().matches("\\d{3}")) staffId = staffField.trim();
                            }
                            if (staffId == null) continue; // skip UID mapping docs

                            String userKey = staffId.toLowerCase(Locale.getDefault());
                            StaffProfile profile = fromDoc(doc, staffId, userKey);
                            if (profile == null) continue;
                            out.add(profile);

                            // Update caches for fast lookups elsewhere in app.
                            if (profile.name != null && !profile.name.trim().isEmpty()) {
                                ID_TO_REPORT_NAME.put(profile.id, profile.name.trim());
                            }
                            if (profile.mobile != null && !profile.mobile.trim().isEmpty()) {
                                ID_TO_MOBILE.put(profile.id, normalizeMobile(profile.mobile.trim()));
                            }
                            if (profile.title != null && !profile.title.trim().isEmpty()) {
                                ID_TO_TITLE.put(profile.id, profile.title.trim());
                            }
                            if (profile.contractKey != null && !profile.contractKey.trim().isEmpty()) {
                                ID_TO_CONTRACT_KEY.put(profile.id, profile.contractKey.trim());
                            }

                            // Build dynamic mapping: ContractKey -> StaffID (no hardcoded names/IDs).
                            try {
                                String key = profile.contractKey != null ? profile.contractKey.trim().toLowerCase(Locale.getDefault()) : "";
                                if (!key.isEmpty()) {
                                    CONTRACTKEY_TO_ID.put(key, profile.id);
                                }
                            } catch (Exception ignored) {}

                            // Cache admins for notification fan-out (derived from Firestore Role/capabilities).
                            try {
                                if (profile.roleNorm != null && (profile.roleNorm.contains("admin") || "super_admin".equals(profile.roleNorm))) {
                                    CACHED_ADMIN_STAFF_IDS.add(profile.id);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    // Sort by display key (ContractKey preferred, else fallback).
                    try {
                        out.sort((a, b) -> {
                            String ka = a.contractKey != null && !a.contractKey.trim().isEmpty()
                                    ? a.contractKey.trim()
                                    : getFallbackDisplayName(a.id);
                            String kb = b.contractKey != null && !b.contractKey.trim().isEmpty()
                                    ? b.contractKey.trim()
                                    : getFallbackDisplayName(b.id);
                            return ka.compareToIgnoreCase(kb);
                        });
                    } catch (Exception ignored) {}

                    CACHED_STAFF_PROFILES = out;
                    if (callback != null) callback.onResult(out);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onResult(Collections.emptyList());
                });
    }

    /** Return cached profiles immediately if available; may be empty. */
    public static List<StaffProfile> getCachedStaffProfiles() {
        List<StaffProfile> v = CACHED_STAFF_PROFILES;
        return v != null ? v : Collections.emptyList();
    }

    /** Best-effort: map ContractKey -> StaffID from cache (no network). */
    @Nullable
    public static String getStaffIdForContractKey(String contractKey) {
        if (contractKey == null) return null;
        String key = contractKey.trim().toLowerCase(Locale.getDefault());
        if (key.isEmpty()) return null;
        String id = CONTRACTKEY_TO_ID.get(key);
        return (id != null && !id.trim().isEmpty()) ? id.trim() : null;
    }

    /** Cached admin StaffIDs derived from Firestore Role/capabilities. */
    public static Set<String> getCachedAdminStaffIds() {
        synchronized (CACHED_ADMIN_STAFF_IDS) {
            return new HashSet<>(CACHED_ADMIN_STAFF_IDS);
        }
    }

    /** Clear all in-memory staff caches (required on logout/shared device). */
    public static void clearCache() {
        try {
            CONTRACTKEY_TO_ID.clear();
            ID_TO_MOBILE.clear();
            ID_TO_REPORT_NAME.clear();
            ID_TO_TITLE.clear();
            ID_TO_CONTRACT_KEY.clear();
            CACHED_ADMIN_STAFF_IDS.clear();
            CACHED_STAFF_PROFILES = null;
        } catch (Exception ignored) {}
    }

    /** Convenience for dropdowns: list of (staffId -> ContractKey). One entry per person (deduplicated by ContractKey). Display label = ContractKey (not full name). Only includes users that have a ContractKey in Firestore; users without ContractKey are not shown (no name or StaffID). */
    public static void fetchOwnerOptions(Context context, OwnerOptionsCallback callback) {
        fetchAllStaffProfiles(context, profiles -> {
            List<OwnerOption> opts = new ArrayList<>();
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            for (StaffProfile p : profiles) {
                if (p == null) continue;
                // Only show users that have a ContractKey; do not display name or StaffID when ContractKey is missing.
                if (p.contractKey == null || p.contractKey.trim().isEmpty()) continue;
                String ownerKey = p.contractKey.trim();
                String normalized = ownerKey.toLowerCase(Locale.getDefault());
                if (seenKeys.contains(normalized)) continue;
                seenKeys.add(normalized);
                String displayLabel = ownerKey;
                opts.add(new OwnerOption(p.id, ownerKey, displayLabel));
            }
            if (callback != null) callback.onResult(opts);
        });
    }

    /** For Messages: include all users; when ContractKey is missing use user's Name (else ContractKey) for display. ownerKey = ContractKey when present, else StaffID for stable conversation ID. */
    public static void fetchOwnerOptionsForMessaging(Context context, OwnerOptionsCallback callback) {
        fetchAllStaffProfiles(context, profiles -> {
            List<OwnerOption> opts = new ArrayList<>();
            java.util.Set<String> seenKeys = new java.util.HashSet<>();
            for (StaffProfile p : profiles) {
                if (p == null || p.id == null) continue;
                String ownerKey;
                String displayLabel;
                if (p.contractKey != null && !p.contractKey.trim().isEmpty()) {
                    ownerKey = p.contractKey.trim();
                    displayLabel = ownerKey;
                    String normalized = ownerKey.toLowerCase(Locale.getDefault());
                    if (seenKeys.contains(normalized)) continue;
                    seenKeys.add(normalized);
                } else {
                    ownerKey = p.id;
                    displayLabel = (p.name != null && !p.name.trim().isEmpty()) ? p.name.trim() : p.id;
                    if (seenKeys.contains(p.id)) continue;
                    seenKeys.add(p.id);
                }
                opts.add(new OwnerOption(p.id, ownerKey, displayLabel));
            }
            if (callback != null) callback.onResult(opts);
        });
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

    /** Returns title for user (from Firestore cache when available). */
    public static String getTitleForUserId(String id) {
        if (id == null) return "";
        String title = ID_TO_TITLE.get(id);
        return title != null ? title : "";
    }

    /**
     * Returns composite technician label: "Name -- Number -- Title" (skips empty segments).
     */
    public static String getTechnicianDisplayLabel(String id) {
        if (id == null) return "";
        String name = getReportDisplayName(id);
        String mobile = getMobileForUserId(id);
        String title = getTitleForUserId(id);

        java.util.List<String> parts = new java.util.ArrayList<>();
        if (name != null && !name.trim().isEmpty()) parts.add(name.trim());
        if (mobile != null && !mobile.trim().isEmpty()) parts.add(mobile.trim());
        if (title != null && !title.trim().isEmpty()) parts.add(title.trim());

        if (parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" -- ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public interface Callback {
        void onResult(@Nullable StaffProfile profile);
    }

    public static class StaffProfile {
        public final String id;
        public final String userName;
        public final String roleNorm;
        public final String name;
        public final String title;
        public final String email;
        public final String mobile;
        public final String contractKey;

        public StaffProfile(String id, String userName, String roleNorm, String name, String title, String email, String mobile, String contractKey) {
            this.id = id;
            this.userName = userName;
            this.roleNorm = roleNorm;
            this.name = name;
            this.title = title;
            this.email = email;
            this.mobile = mobile;
            this.contractKey = contractKey;
        }
    }

    public static void fetchByUserName(Context context, String userName, Callback callback) {
        if (userName == null) {
            if (callback != null) callback.onResult(null);
            return;
        }

        String key = userName.trim();
        String id = getUserId(key);
        if (id == null) {
            if (callback != null) callback.onResult(null);
            return;
        }

        FirebaseFirestore db = FirebaseHelper.getFirestore();
        fetchFromCollections(db, id, key.trim().toLowerCase(Locale.getDefault()), 0, callback);
    }

    /** Fetches staff by StaffID document id from Staff/Users collection. */
    public static void fetchById(Context context, String id, Callback callback) {
        if (id == null || id.trim().isEmpty()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        String userKey = getUserNameKey(id.trim());
        if (userKey == null || userKey.trim().isEmpty()) userKey = id.trim();
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        fetchFromCollections(db, id.trim(), userKey.trim().toLowerCase(Locale.getDefault()), 0, callback);
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
        String roleNorm = SessionManager.normalizeRole(pickString(ds, "Role", "role"));
        String name = pickString(ds, "name", "Name");
        String title = pickString(ds, "title", "Title");
        String mobile = pickString(ds, "mobile", "Mobile", "phone", "Phone", "number", "Number");
        String contractKey = pickString(ds, "ContractKey", "contractKey", "contract_key");
        return new StaffProfile(id, userKey, roleNorm, safe(name), safe(title), safe(email), safe(mobile), safe(contractKey));
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

