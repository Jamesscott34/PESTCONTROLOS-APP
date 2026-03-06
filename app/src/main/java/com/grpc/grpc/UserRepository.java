package com.grpc.grpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository for user profile documents keyed by Firebase Auth UID.
 *
 * Schema (online):
 *   users/{authUid} {
 *     uid, name, email, number, title,
 *     staffId, contractKey, role,
 *     canSearch, canAccessCommission, canHardPressContracts,
 *     canMarkPaidLeads, canUseLocationFinder, seesAllJobs,
 *     createdAt, lastLogin, active
 *   }
 *
 * Offline flavor:
 *   - No Firestore calls.
 *   - Profile is stored in SharedPreferences with a compatible schema.
 */
public final class UserRepository {

    private static final String TAG = "UserRepository";
    private static final String PREFS = "GRPC_USER_PROFILE";
    private static final String KEY_OFFLINE_UID = "offline_uid";

    private UserRepository() {}

    public static final class UserProfile {
        public final String uid;
        public final String name;
        public final String email;
        public final String number;
        public final String title;
        public final String staffId;
        public final String contractKey;
        public final String role;
        public final boolean canSearch;
        public final boolean canAccessCommission;
        public final boolean canHardPressContracts;
        public final boolean canMarkPaidLeads;
        public final boolean canUseLocationFinder;
        public final boolean seesAllJobs;

        public UserProfile(String uid,
                           String name,
                           String email,
                           String number,
                           String title,
                           String staffId,
                           String contractKey,
                           String role,
                           boolean canSearch,
                           boolean canAccessCommission,
                           boolean canHardPressContracts,
                           boolean canMarkPaidLeads,
                           boolean canUseLocationFinder,
                           boolean seesAllJobs) {
            this.uid = uid != null ? uid : "";
            this.name = name != null ? name : "";
            this.email = email != null ? email : "";
            this.number = number != null ? number : "";
            this.title = title != null ? title : "";
            this.staffId = staffId != null ? staffId : "";
            this.contractKey = contractKey != null ? contractKey : "";
            this.role = role != null ? role : "";
            this.canSearch = canSearch;
            this.canAccessCommission = canAccessCommission;
            this.canHardPressContracts = canHardPressContracts;
            this.canMarkPaidLeads = canMarkPaidLeads;
            this.canUseLocationFinder = canUseLocationFinder;
            this.seesAllJobs = seesAllJobs;
        }
    }

    public interface ProfileCallback {
        void onProfile(@Nullable UserProfile profile);
    }

    public static final class AssignableUser {
        public final String uid;
        public final String name;
        public final String staffId;
        public final String contractKey;
        public final String role;

        public AssignableUser(String uid,
                              String name,
                              String staffId,
                              String contractKey,
                              String role) {
            this.uid = uid != null ? uid : "";
            this.name = name != null ? name : "";
            this.staffId = staffId != null ? staffId : "";
            this.contractKey = contractKey != null ? contractKey : "";
            this.role = role != null ? role : "";
        }
    }

    public interface AssignableUsersCallback {
        void onResult(java.util.List<AssignableUser> users);
    }

    /**
     * Ensure that users/{uid} exists for the currently signed-in Firebase user,
     * merging in any missing or empty fields from the given SessionManager.Session.
     *
     * Does nothing in offline flavor (profile is kept in SharedPreferences instead).
     */
    public static void ensureProfileForCurrentUser(Context context, @Nullable SessionManager.Session session, @Nullable ProfileCallback cb) {
        if (BuildConfig.IS_OFFLINE) {
            UserProfile offline = ensureOfflineProfile(context, session);
            if (cb != null) cb.onProfile(offline);
            return;
        }

        FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
        if (authUser == null) {
            if (cb != null) cb.onProfile(null);
            return;
        }
        String uid = authUser.getUid();
        if (TextUtils.isEmpty(uid)) {
            if (cb != null) cb.onProfile(null);
            return;
        }

        FirebaseFirestore db = FirebaseHelper.getFirestore();
        DocumentReference docRef = db.collection(FirestorePaths.USERS).document(uid);
        docRef.get().addOnSuccessListener(snapshot -> {
            Map<String, Object> updates = buildProfileSeed(snapshot, uid, authUser, session);
            if (updates.isEmpty()) {
                if (cb != null) cb.onProfile(fromSnapshotOrSession(snapshot, uid, session));
                return;
            }
            docRef.set(updates, SetOptions.merge())
                    .addOnSuccessListener(v -> {
                        if (cb != null) cb.onProfile(fromSnapshotOrSession(snapshot, uid, session));
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to seed users/" + uid + ": " + (e != null ? e.getMessage() : ""));
                        if (cb != null) cb.onProfile(fromSnapshotOrSession(snapshot, uid, session));
                    });
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Failed to read users/" + uid + ": " + (e != null ? e.getMessage() : ""));
            if (cb != null) cb.onProfile(fromSnapshotOrSession(null, uid, session));
        });
    }

    /**
     * Load users/{uid} profile. In offline flavor, returns the cached offline profile.
     */
    public static void getUserProfile(String uid, @Nullable SessionManager.Session fallbackSession, ProfileCallback cb) {
        if (cb == null) return;

        if (BuildConfig.IS_OFFLINE) {
            cb.onProfile(loadOfflineProfile(null, fallbackSession));
            return;
        }

        if (TextUtils.isEmpty(uid)) {
            cb.onProfile(null);
            return;
        }
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.USERS).document(uid).get()
                .addOnSuccessListener(snapshot -> cb.onProfile(fromSnapshotOrSession(snapshot, uid, fallbackSession)))
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load users/" + uid + ": " + (e != null ? e.getMessage() : ""));
                    cb.onProfile(fromSnapshotOrSession(null, uid, fallbackSession));
                });
    }

    /**
     * Fetch assignable users from Firestore `users` collection (UID docs only).
     * Used for admin dropdowns (contracts, jobs, workview, messaging).
     *
     * Selection rules:
     * - Include users whose document ID is NOT a 3-digit StaffID.
     * - Include roles admin, super_admin, tech (case-insensitive; supports Role/role fields).
     * - Require a non-empty ContractKey; entries without ContractKey are excluded from spinners.
     * - Sorted by contractKey, then name, then staffId.
     */
    public static void fetchAssignableUsers(AssignableUsersCallback cb) {
        if (cb == null) return;
        if (BuildConfig.IS_OFFLINE) {
            cb.onResult(new java.util.ArrayList<>());
            return;
        }
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.USERS).get()
                .addOnSuccessListener(qs -> {
                    java.util.List<AssignableUser> out = new java.util.ArrayList<>();
                    if (qs != null) {
                        for (DocumentSnapshot ds : qs.getDocuments()) {
                            if (ds == null || !ds.exists()) continue;
                            String docId = ds.getId() != null ? ds.getId().trim() : "";
                            if (docId.matches("\\d{3}")) continue; // skip numeric staff docs

                            String role = valueAsString(ds.get("role"), ds.get("Role"));
                            String roleNorm = SessionManager.normalizeRole(role);
                            if (!"admin".equals(roleNorm) && !"super_admin".equals(roleNorm) && !"tech".equals(roleNorm)) {
                                continue;
                            }
                            // In demo/other company flavors, hide super_admin from contractKey-based spinners.
                            if (!"grpc".equals(BuildConfig.FLAVOR) && "super_admin".equals(roleNorm)) {
                                continue;
                            }

                            String contractKey = valueAsString(ds.get("contractKey"), ds.get("ContractKey"));
                            if (TextUtils.isEmpty(contractKey)) {
                                // Do not show users without a ContractKey in assign-to / technician spinners.
                                continue;
                            }

                            String name = valueAsString(ds.get("name"), ds.get("Name"));
                            String email = valueAsString(ds.get("email"), ds.get("Email"));
                            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(email)) {
                                int at = email.indexOf('@');
                                name = at > 0 ? email.substring(0, at) : email;
                            }
                            String staffId = valueAsString(ds.get("staffId"), ds.get("StaffID"), ds.get("staffID"));

                            out.add(new AssignableUser(docId, name, staffId, contractKey, roleNorm));
                        }
                    }
                    java.util.Collections.sort(out, (a, b) -> {
                        String ak = a.contractKey != null ? a.contractKey : "";
                        String bk = b.contractKey != null ? b.contractKey : "";
                        int c = ak.compareToIgnoreCase(bk);
                        if (c != 0) return c;
                        String an = !TextUtils.isEmpty(a.name) ? a.name : a.uid;
                        String bn = !TextUtils.isEmpty(b.name) ? b.name : b.uid;
                        c = an.compareToIgnoreCase(bn);
                        if (c != 0) return c;
                        String asId = a.staffId != null ? a.staffId : "";
                        String bsId = b.staffId != null ? b.staffId : "";
                        return asId.compareToIgnoreCase(bsId);
                    });
                    cb.onResult(out);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to fetch assignable users: " + (e != null ? e.getMessage() : ""));
                    cb.onResult(new java.util.ArrayList<>());
                });
    }

    // ---------------------------------------------------------------------
    // Offline profile helpers
    // ---------------------------------------------------------------------

    private static UserProfile ensureOfflineProfile(Context context, @Nullable SessionManager.Session session) {
        UserProfile existing = loadOfflineProfile(context, session);
        if (existing != null) return existing;

        // Seed a minimal offline profile; use session fields when available.
        String uid = "offline";
        if (context != null) {
            SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            e.putString(KEY_OFFLINE_UID, uid);
            if (session != null) {
                e.putString("name", session.name);
                e.putString("email", session.email);
                e.putString("number", session.number);
                e.putString("title", session.title);
                e.putString("staffId", session.staffId);
                e.putString("contractKey", session.contractKey);
                e.putString("role", session.roleNorm);
                e.putBoolean("canSearch", session.canSearch);
                e.putBoolean("canAccessCommission", session.canAccessCommissionLeads);
                e.putBoolean("canHardPressContracts", session.canHardPressContracts);
                e.putBoolean("canMarkPaidLeads", session.canMarkPaidLeads);
                e.putBoolean("canUseLocationFinder", session.canUseLocationFinder);
                e.putBoolean("seesAllJobs", session.seesAllJobs);
            }
            e.apply();
        }
        return loadOfflineProfile(context, session);
    }

    private static UserProfile loadOfflineProfile(@Nullable Context context, @Nullable SessionManager.Session session) {
        if (context == null) {
            // Best-effort: build from session if available.
            if (session == null) return null;
            return fromSession("offline", session);
        }
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uid = sp.getString(KEY_OFFLINE_UID, null);
        if (uid == null && session != null) {
            return fromSession("offline", session);
        }
        String name = sp.getString("name", session != null ? session.name : "");
        String email = sp.getString("email", session != null ? session.email : "");
        String number = sp.getString("number", session != null ? session.number : "");
        String title = sp.getString("title", session != null ? session.title : "");
        String staffId = sp.getString("staffId", session != null ? session.staffId : "");
        String contractKey = sp.getString("contractKey", session != null ? session.contractKey : "");
        String role = sp.getString("role", session != null ? session.roleNorm : "");
        boolean canSearch = sp.getBoolean("canSearch", session != null && session.canSearch);
        boolean canAccessCommission = sp.getBoolean("canAccessCommission", session != null && session.canAccessCommissionLeads);
        boolean canHardPressContracts = sp.getBoolean("canHardPressContracts", session != null && session.canHardPressContracts);
        boolean canMarkPaidLeads = sp.getBoolean("canMarkPaidLeads", session != null && session.canMarkPaidLeads);
        boolean canUseLocationFinder = sp.getBoolean("canUseLocationFinder", session != null && session.canUseLocationFinder);
        boolean seesAllJobs = sp.getBoolean("seesAllJobs", session != null && session.seesAllJobs);

        return new UserProfile(uid != null ? uid : "offline",
                name, email, number, title, staffId, contractKey, role,
                canSearch, canAccessCommission, canHardPressContracts,
                canMarkPaidLeads, canUseLocationFinder, seesAllJobs);
    }

    // ---------------------------------------------------------------------
    // Mapping helpers
    // ---------------------------------------------------------------------

    private static Map<String, Object> buildProfileSeed(@Nullable DocumentSnapshot existing,
                                                        String uid,
                                                        FirebaseUser authUser,
                                                        @Nullable SessionManager.Session session) {
        Map<String, Object> out = new HashMap<>();
        boolean exists = existing != null && existing.exists();

        String existingName = exists ? valueAsString(existing.get("name"), existing.get("Name")) : "";
        String existingEmail = exists ? valueAsString(existing.get("email"), existing.get("Email")) : "";
        String existingNumber = exists ? valueAsString(existing.get("number"), existing.get("Number"), existing.get("mobile"), existing.get("Mobile")) : "";
        String existingTitle = exists ? valueAsString(existing.get("title"), existing.get("Title")) : "";
        String existingStaffId = exists ? valueAsString(existing.get("staffId"), existing.get("StaffID"), existing.get("staffID")) : "";
        String existingContractKey = exists ? valueAsString(existing.get("contractKey"), existing.get("ContractKey")) : "";
        String existingRole = exists ? valueAsString(existing.get("role"), existing.get("Role")) : "";

        // Always ensure uid field is set correctly.
        out.put("uid", uid);

        if (session != null) {
            if (TextUtils.isEmpty(existingName) && !TextUtils.isEmpty(session.name)) out.put("name", session.name);
            if (TextUtils.isEmpty(existingEmail) && !TextUtils.isEmpty(session.email)) out.put("email", session.email);
            if (TextUtils.isEmpty(existingNumber) && !TextUtils.isEmpty(session.number)) out.put("number", session.number);
            if (TextUtils.isEmpty(existingTitle) && !TextUtils.isEmpty(session.title)) out.put("title", session.title);
            if (TextUtils.isEmpty(existingStaffId) && !TextUtils.isEmpty(session.staffId)) out.put("staffId", session.staffId);
            if (TextUtils.isEmpty(existingContractKey) && !TextUtils.isEmpty(session.contractKey)) out.put("contractKey", session.contractKey);
            if (TextUtils.isEmpty(existingRole) && !TextUtils.isEmpty(session.roleNorm)) out.put("role", session.roleNorm);

            maybePutBoolean(existing, out, "canSearch", session.canSearch);
            maybePutBoolean(existing, out, "canAccessCommission", session.canAccessCommissionLeads);
            maybePutBoolean(existing, out, "canHardPressContracts", session.canHardPressContracts);
            maybePutBoolean(existing, out, "canMarkPaidLeads", session.canMarkPaidLeads);
            maybePutBoolean(existing, out, "canUseLocationFinder", session.canUseLocationFinder);
            maybePutBoolean(existing, out, "seesAllJobs", session.seesAllJobs);
        }

        // Seed contract visibility flags based on role, without overwriting existing values.
        String roleForFlags;
        if (!TextUtils.isEmpty(existingRole)) {
            roleForFlags = SessionManager.normalizeRole(existingRole);
        } else if (session != null && !TextUtils.isEmpty(session.roleNorm)) {
            roleForFlags = SessionManager.normalizeRole(session.roleNorm);
        } else {
            roleForFlags = "";
        }

        // Safe defaults: everyone can see their own contracts; only admin/super_admin see all.
        boolean defaultCanSeeContracts = true;
        boolean defaultCanViewAllContracts = false;
        if (!TextUtils.isEmpty(roleForFlags)) {
            defaultCanSeeContracts =
                    "tech".equals(roleForFlags) || "admin".equals(roleForFlags) || "super_admin".equals(roleForFlags);
            defaultCanViewAllContracts =
                    "admin".equals(roleForFlags) || "super_admin".equals(roleForFlags);
        }
        maybePutBoolean(existing, out, "canSeeContracts", defaultCanSeeContracts);
        maybePutBoolean(existing, out, "canViewAllContracts", defaultCanViewAllContracts);

        // Seed createdAt on first create only.
        if (!exists) {
            out.put("createdAt", FieldValue.serverTimestamp());
            out.put("active", true);
        }
        // Always bump lastLogin to track activity.
        out.put("lastLogin", FieldValue.serverTimestamp());

        // Ensure email is populated from auth if still missing.
        if (TextUtils.isEmpty((String) out.get("email")) && authUser.getEmail() != null) {
            out.put("email", authUser.getEmail());
        }

        // Remove entries that ended up empty.
        out.values().removeIf(v -> v instanceof String && ((String) v).trim().isEmpty());
        return out;
    }

    private static void maybePutBoolean(@Nullable DocumentSnapshot existing, Map<String, Object> out, String key, boolean value) {
        if (existing == null || !existing.contains(key)) {
            out.put(key, value);
        }
    }

    private static String valueAsString(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private static UserProfile fromSnapshotOrSession(@Nullable DocumentSnapshot snapshot,
                                                     String uid,
                                                     @Nullable SessionManager.Session session) {
        if (snapshot != null && snapshot.exists()) {
            String name = valueAsString(snapshot.get("name"), snapshot.get("Name"));
            String email = valueAsString(snapshot.get("email"), snapshot.get("Email"));
            String number = valueAsString(snapshot.get("number"), snapshot.get("Number"), snapshot.get("mobile"), snapshot.get("Mobile"));
            String title = valueAsString(snapshot.get("title"), snapshot.get("Title"));
            String staffId = valueAsString(snapshot.get("staffId"), snapshot.get("StaffID"), snapshot.get("staffID"));
            String contractKey = valueAsString(snapshot.get("contractKey"), snapshot.get("ContractKey"));
            String role = valueAsString(snapshot.get("role"), snapshot.get("Role"));

            boolean canSearch = snapshot.getBoolean("canSearch") != null ? snapshot.getBoolean("canSearch") : session != null && session.canSearch;
            Boolean canAccessCommissionVal = snapshot.getBoolean("canAccessCommission");
            boolean canAccessCommission = canAccessCommissionVal != null ? canAccessCommissionVal : session != null && session.canAccessCommissionLeads;
            Boolean canHardPressContractsVal = snapshot.getBoolean("canHardPressContracts");
            boolean canHardPressContracts = canHardPressContractsVal != null ? canHardPressContractsVal : session != null && session.canHardPressContracts;
            Boolean canMarkPaidLeadsVal = snapshot.getBoolean("canMarkPaidLeads");
            boolean canMarkPaidLeads = canMarkPaidLeadsVal != null ? canMarkPaidLeadsVal : session != null && session.canMarkPaidLeads;
            Boolean canUseLocationVal = snapshot.getBoolean("canUseLocationFinder");
            boolean canUseLocationFinder = canUseLocationVal != null ? canUseLocationVal : session != null && session.canUseLocationFinder;
            Boolean seesAllJobsVal = snapshot.getBoolean("seesAllJobs");
            boolean seesAllJobs = seesAllJobsVal != null ? seesAllJobsVal : session != null && session.seesAllJobs;

            return new UserProfile(uid, name, email, number, title, staffId, contractKey, role,
                    canSearch, canAccessCommission, canHardPressContracts,
                    canMarkPaidLeads, canUseLocationFinder, seesAllJobs);
        }
        if (session == null) return null;
        return fromSession(uid, session);
    }

    private static UserProfile fromSession(String uid, SessionManager.Session session) {
        return new UserProfile(
                uid,
                session.name,
                session.email,
                session.number,
                session.title,
                session.staffId,
                session.contractKey,
                session.roleNorm,
                session.canSearch,
                session.canAccessCommissionLeads,
                session.canHardPressContracts,
                session.canMarkPaidLeads,
                session.canUseLocationFinder,
                session.seesAllJobs
        );
    }
}

