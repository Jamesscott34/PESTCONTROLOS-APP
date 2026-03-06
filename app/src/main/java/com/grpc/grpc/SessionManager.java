package com.grpc.grpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * Central RBAC/session authority for the current logged-in user.
 *
 * Source of truth:
 * - Staff profile: Firestore users/{StaffID} (authoritative for app permissions + autofill)
 * - UID mapping: Firestore users/{uid} (website compatibility; only used to map uid -> StaffID)
 */
public final class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS = "GRPC";

    private static final String KEY_STAFF_ID = "SESSION_STAFF_ID";
    private static final String KEY_CONTRACT_KEY = "SESSION_CONTRACT_KEY";
    private static final String KEY_ROLE_NORM = "SESSION_ROLE_NORM";
    private static final String KEY_NAME = "SESSION_NAME";
    private static final String KEY_EMAIL = "SESSION_EMAIL";
    private static final String KEY_NUMBER = "SESSION_NUMBER";
    private static final String KEY_TITLE = "SESSION_TITLE";
    private static final String KEY_AUTH_UID = "SESSION_AUTH_UID";
    private static final String KEY_AUTH_EMAIL = "SESSION_AUTH_EMAIL";

    private static final String KEY_CAN_SEARCH = "SESSION_CAN_SEARCH";
    private static final String KEY_CAN_LOCATION = "SESSION_CAN_LOCATION";
    private static final String KEY_CAN_HARDPRESS_CONTRACTS = "SESSION_CAN_HARDPRESS_CONTRACTS";
    private static final String KEY_CAN_MARK_PAID_LEADS = "SESSION_CAN_MARK_PAID_LEADS";
    private static final String KEY_CAN_ACCESS_COMMISSION = "SESSION_CAN_ACCESS_COMMISSION";
    private static final String KEY_SEES_ALL_JOBS = "SESSION_SEES_ALL_JOBS";
    private static final String KEY_DEMO_RELEASE = "SESSION_DEMO_RELEASE";
    private static final String KEY_DEMO_DAYS_NUMBER = "SESSION_DEMO_DAYS_NUMBER";

    private static volatile Session current = null;
    private static volatile boolean isLoading = false;

    private SessionManager() {}

    public static final class Session {
        public final String staffId;     // StaffID (3 digits)
        public final String contractKey; // users/{StaffID}.ContractKey (internal identifier)
        public final String roleNorm;    // "super_admin" | "admin" | "tech" | "unknown"

        public final String name;
        public final String email;
        public final String number;
        public final String title;

        public final boolean isSuperAdmin;
        public final boolean isAdmin;
        public final boolean isTech;

        public final boolean canSearch;
        public final boolean canUseLocationFinder;
        public final boolean canHardPressContracts;
        public final boolean canMarkPaidLeads;
        public final boolean canAccessCommissionLeads;
        public final boolean seesAllJobs;

        public final boolean demoRelease;
        public final int demoDaysNumber;

        Session(String staffId,
                String contractKey,
                String roleNorm,
                String name,
                String email,
                String number,
                String title,
                boolean canSearch,
                boolean canUseLocationFinder,
                boolean canHardPressContracts,
                boolean canMarkPaidLeads,
                boolean canAccessCommissionLeads,
                boolean seesAllJobs,
                boolean demoRelease,
                int demoDaysNumber) {

            this.staffId = staffId != null ? staffId : "";
            this.contractKey = contractKey != null ? contractKey : "";
            String norm = roleNorm != null ? roleNorm : "unknown";
            this.name = name != null ? name : "";
            this.email = email != null ? email : "";
            this.number = number != null ? number : "";
            this.title = title != null ? title : "";

            this.canSearch = canSearch;
            this.canUseLocationFinder = canUseLocationFinder;
            this.canHardPressContracts = canHardPressContracts;
            this.canMarkPaidLeads = canMarkPaidLeads;
            this.canAccessCommissionLeads = canAccessCommissionLeads;
            this.seesAllJobs = seesAllJobs;
            this.demoRelease = demoRelease;
            this.demoDaysNumber = demoDaysNumber <= 0 ? 0 : demoDaysNumber;

            // If a profile has all "super_admin-only" capabilities enabled (by role or explicit flags),
            // treat it as super_admin for UI gating even if the stored Role field is mis-set.
            // This prevents "full access but missing super_admin UI" situations.
            boolean inferredSuperAdmin =
                    "super_admin".equals(norm)
                            || (this.canSearch && this.canUseLocationFinder && this.canMarkPaidLeads && this.seesAllJobs);
            if (inferredSuperAdmin) {
                norm = "super_admin";
            }
            this.roleNorm = norm;

            this.isSuperAdmin = "super_admin".equals(this.roleNorm);
            this.isAdmin = this.isSuperAdmin || "admin".equals(this.roleNorm);
            // Strict tech: prevents unexpected gating from typos/unknown roles.
            this.isTech = "tech".equals(this.roleNorm);
        }
    }

    public interface Callback {
        void onSessionReady(@Nullable Session session);
    }

    /** Returns cached session (from memory), otherwise loads from SharedPreferences. */
    @Nullable
    public static Session getCached(Context context) {
        if (current != null) return current;
        if (context == null) return null;

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Prevent cross-user cache leaks on shared devices:
        // only reuse a cached session if it belongs to the currently authenticated Firebase UID.
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            String cachedUid = sp.getString(KEY_AUTH_UID, "");
            if (u != null) {
                String uid = u.getUid();
                // If cache is missing a UID or belongs to a different UID, do not reuse it.
                if (TextUtils.isEmpty(cachedUid) || !uid.equals(cachedUid)) {
                    return null;
                }
            }
        } catch (Exception ignored) {}

        String staffId = normalizeStaffId(sp.getString(KEY_STAFF_ID, ""));
        String contractKey = sp.getString(KEY_CONTRACT_KEY, "");
        String roleNorm = sp.getString(KEY_ROLE_NORM, "unknown");
        if (TextUtils.isEmpty(staffId) && "unknown".equals(roleNorm)) return null;

        Session s = new Session(
                staffId,
                contractKey,
                roleNorm,
                sp.getString(KEY_NAME, ""),
                sp.getString(KEY_EMAIL, ""),
                sp.getString(KEY_NUMBER, ""),
                sp.getString(KEY_TITLE, ""),
                sp.getBoolean(KEY_CAN_SEARCH, false),
                sp.getBoolean(KEY_CAN_LOCATION, false),
                sp.getBoolean(KEY_CAN_HARDPRESS_CONTRACTS, false),
                sp.getBoolean(KEY_CAN_MARK_PAID_LEADS, false),
                sp.getBoolean(KEY_CAN_ACCESS_COMMISSION, true),
                sp.getBoolean(KEY_SEES_ALL_JOBS, false),
                sp.getBoolean(KEY_DEMO_RELEASE, false),
                sp.getInt(KEY_DEMO_DAYS_NUMBER, 0)
        );
        current = s;
        return s;
    }

    public static boolean isLoading() {
        return isLoading;
    }

    /** Clears in-memory + persisted session cache (used for forced logout). */
    public static void clear(Context context) {
        current = null;
        isLoading = false;
        if (context == null) return;
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_STAFF_ID)
                    .remove(KEY_CONTRACT_KEY)
                    .remove(KEY_ROLE_NORM)
                    .remove(KEY_NAME)
                    .remove(KEY_EMAIL)
                    .remove(KEY_NUMBER)
                    .remove(KEY_TITLE)
                    .remove(KEY_AUTH_UID)
                    .remove(KEY_AUTH_EMAIL)
                    .remove(KEY_CAN_SEARCH)
                    .remove(KEY_CAN_LOCATION)
                    .remove(KEY_CAN_HARDPRESS_CONTRACTS)
                    .remove(KEY_CAN_MARK_PAID_LEADS)
                    .remove(KEY_CAN_ACCESS_COMMISSION)
                    .remove(KEY_SEES_ALL_JOBS)
                    .remove(KEY_DEMO_RELEASE)
                    .remove(KEY_DEMO_DAYS_NUMBER)
                    .apply();
        } catch (Exception ignored) {}
    }

    /** Ensure session is loaded (async). Safe to call multiple times; uses cached results. */
    public static void ensureLoaded(Context context, @Nullable Callback callback) {
        if (context == null) {
            if (callback != null) callback.onSessionReady(null);
            return;
        }

        Session cached = getCached(context);
        if (cached != null) {
            if (callback != null) callback.onSessionReady(cached);
            // still attempt refresh in background if not currently loading
        }

        if (isLoading) return;
        isLoading = true;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Offline / logged-out mode: do not crash, keep minimal safe permissions.
            Session offline = buildOfflineFallback(context);
            persist(context, offline);
            current = offline;
            isLoading = false;
            if (callback != null) callback.onSessionReady(offline);
            Log.d(TAG, "Session loaded: offline mode");
            return;
        }

        final String uid = user.getUid();
        final String email = user.getEmail() != null ? user.getEmail() : "";

        resolveStaffId(context, uid, email, staffId -> {
            if (TextUtils.isEmpty(staffId)) {
                // Fall back to cached session only; don't lock user out unexpectedly.
                Session s = getCached(context);
                isLoading = false;
                if (callback != null) callback.onSessionReady(s);
                Log.w(TAG, "Could not resolve StaffID; using cached session only");
                return;
            }

            loadAuthoritativeStaffProfile(context, staffId, email, session -> {
                isLoading = false;
                if (session != null) {
                    current = session;
                    persist(context, session);
                    Log.d(TAG, "Session loaded: staffId=" + safe(staffId) + " role=" + safe(session.roleNorm));
                } else {
                    Log.w(TAG, "Staff profile load failed for staffId=" + safe(staffId) + " (using cached)");
                }
                if (callback != null) callback.onSessionReady(session != null ? session : getCached(context));
            });
        });
    }

    private interface StaffIdCallback { void onStaffId(@Nullable String staffId); }

    /**
     * Resolve StaffID for current FirebaseAuth user.
     * Resolution order:
     * - SharedPreferences cache
     * - users/{uid} doc: field StaffID
     * - query users where Email/email matches current email and has StaffID (client-side filter)
     */
    private static void resolveStaffId(Context context, String uid, String email, StaffIdCallback cb) {
        FirebaseFirestore db = FirebaseHelper.getFirestore();

        // Cache only counts if it was created for this UID.
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String cachedUid = sp.getString(KEY_AUTH_UID, "");
            String cached = normalizeStaffId(sp.getString(KEY_STAFF_ID, ""));
            if (!TextUtils.isEmpty(cached) && !TextUtils.isEmpty(cachedUid) && cachedUid.equals(uid)) {
                // Validate cached mapping to avoid persisting an incorrect staffId for a UID.
                validateResolvedStaffId(db, cached, email, cb);
                return;
            }
        } catch (Exception ignored) {}

        // 1) UID mapping doc
        db.collection("users").document(uid).get()
                .addOnSuccessListener(ds -> {
                    String staffId = normalizeStaffId(pickString(ds, "StaffID", "staffId", "staffID", "staff_id"));
                    if (!TextUtils.isEmpty(staffId)) {
                        // Validate UID mapping against authoritative users/{StaffID} doc,
                        // to avoid accidentally mapping to the wrong staff profile.
                        validateResolvedStaffId(db, staffId.trim(), email, cb);
                        return;
                    }
                    // 2) Email lookup
                    resolveStaffIdByEmail(db, email, cb);
                })
                .addOnFailureListener(e -> resolveStaffIdByEmail(db, email, cb));
    }

    private static void validateResolvedStaffId(FirebaseFirestore db, String staffId, String email, StaffIdCallback cb) {
        String normalized = normalizeStaffId(staffId);
        if (TextUtils.isEmpty(normalized) || db == null) {
            if (cb != null) cb.onStaffId(null);
            return;
        }
        // If we don't have an email (rare), accept the mapping as-is.
        if (TextUtils.isEmpty(email)) {
            if (cb != null) cb.onStaffId(staffId);
            return;
        }

        final String normEmail = email.trim().toLowerCase(Locale.getDefault());
        db.collection("users").document(normalized).get()
                .addOnSuccessListener(staffDoc -> {
                    if (staffDoc == null || !staffDoc.exists()) {
                        // invalid staffId -> fall back to email-based resolution
                        resolveStaffIdByEmail(db, email, cb);
                        return;
                    }
                    String staffEmail = pickString(staffDoc, "Email", "email");
                    String staffEmailNorm = staffEmail != null ? staffEmail.trim().toLowerCase(Locale.getDefault()) : "";
                    if (!TextUtils.isEmpty(staffEmailNorm) && staffEmailNorm.equals(normEmail)) {
                        if (cb != null) cb.onStaffId(normalized);
                    } else {
                        // UID doc is pointing at wrong staff profile; fall back to email.
                        resolveStaffIdByEmail(db, email, cb);
                    }
                })
                .addOnFailureListener(e -> resolveStaffIdByEmail(db, email, cb));
    }

    private static void resolveStaffIdByEmail(FirebaseFirestore db, String email, StaffIdCallback cb) {
        if (TextUtils.isEmpty(email) || db == null) {
            if (cb != null) cb.onStaffId(null);
            return;
        }
        final String rawEmail = email.trim();
        final String normEmail = rawEmail.toLowerCase(Locale.getDefault());

        // Because the "users" collection contains both staff docs and UID docs,
        // we try multiple common field/casing variants in a safe order.
        tryResolveStaffIdByEmailField(db, "Email", normEmail, staffId -> {
            if (cb != null) cb.onStaffId(staffId);
        }, () -> tryResolveStaffIdByEmailField(db, "Email", rawEmail, staffId -> {
            if (cb != null) cb.onStaffId(staffId);
        }, () -> tryResolveStaffIdByEmailField(db, "email", normEmail, staffId -> {
            if (cb != null) cb.onStaffId(staffId);
        }, () -> tryResolveStaffIdByEmailField(db, "email", rawEmail, staffId -> {
            if (cb != null) cb.onStaffId(staffId);
        }, () -> {
            if (cb != null) cb.onStaffId(null);
        }))));
    }

    private interface Run { void run(); }
    private interface StaffIdResult { void onResult(@Nullable String staffId); }

    private static void tryResolveStaffIdByEmailField(
            FirebaseFirestore db,
            String field,
            String value,
            StaffIdResult onFound,
            Run onNext
    ) {
        if (db == null || TextUtils.isEmpty(field) || TextUtils.isEmpty(value)) {
            if (onNext != null) onNext.run();
            return;
        }
        db.collection("users").whereEqualTo(field, value).limit(5).get()
                .addOnSuccessListener(qs -> {
                    String staffId = findStaffIdFromQuery(qs);
                    if (!TextUtils.isEmpty(staffId)) {
                        if (onFound != null) onFound.onResult(staffId);
                    } else {
                        if (onNext != null) onNext.run();
                    }
                })
                .addOnFailureListener(e -> {
                    if (onNext != null) onNext.run();
                });
    }

    @Nullable
    private static String findStaffIdFromQuery(@Nullable QuerySnapshot qs) {
        if (qs == null || qs.isEmpty()) return null;
        for (DocumentSnapshot doc : qs.getDocuments()) {
            if (doc == null || !doc.exists()) continue;
            String staffId = normalizeStaffId(pickString(doc, "StaffID", "staffId", "staffID", "staff_id"));
            if (!TextUtils.isEmpty(staffId)) return staffId.trim();
            // Some staff docs might use documentId itself as StaffID
            String docId = doc.getId();
            if (docId != null && docId.matches("\\d{1,3}")) return normalizeStaffId(docId);
        }
        return null;
    }

    private interface SessionCallback { void onSession(@Nullable Session session); }

    /** Load authoritative staff profile from users/{StaffID}. */
    private static void loadAuthoritativeStaffProfile(Context context, String staffId, String fallbackEmail, SessionCallback cb) {
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        String staffIdNorm = normalizeStaffId(staffId);
        db.collection("users").document(staffIdNorm).get()
                .addOnSuccessListener(ds -> {
                    if (ds == null || !ds.exists()) {
                        if (cb != null) cb.onSession(null);
                        return;
                    }

                    String roleRaw = pickString(ds, "Role", "role");
                    String roleNorm = normalizeRole(roleRaw);

                    // Staff fields (authoritative)
                    String name = pickString(ds, "Name", "name");
                    String email = pickString(ds, "Email", "email");
                    if (TextUtils.isEmpty(email)) email = fallbackEmail != null ? fallbackEmail : "";
                    String number = pickString(ds, "Number", "number", "Mobile", "mobile", "Phone", "phone");
                    String title = pickString(ds, "Title", "title");
                    String contractKey = pickString(ds, "ContractKey", "contractKey", "contract_key");

                    boolean isSuperAdmin = "super_admin".equals(roleNorm);
                    boolean isAdmin = isSuperAdmin || "admin".equals(roleNorm);

                    // Optional fine-grained flags (preferred). Defaults are role-based and chosen to preserve access.
                    Boolean canSearchFlag = pickBoolean(ds, "CanSearch", "canSearch");
                    Boolean canLocationFlag = pickBoolean(ds, "CanLocationFinder", "canLocationFinder", "CanUseLocationFinder");
                    Boolean hardPressFlag = pickBoolean(ds, "CanHardPressContracts", "canHardPressContracts");
                    Boolean markPaidFlag = pickBoolean(ds, "CanMarkPaidLeads", "canMarkPaidLeads");
                    Boolean commissionFlag = pickBoolean(ds, "CanAccessCommission", "canAccessCommission", "CanAccessCommissionLeads");
                    Boolean seesAllJobsFlag = pickBoolean(ds, "SeesAllJobs", "seesAllJobs");
                    Boolean canSeeContractsFlag = pickBoolean(ds, "CanSeeContracts", "canSeeContracts");
                    Boolean canViewAllContractsFlag = pickBoolean(ds, "CanViewAllContracts", "canViewAllContracts");

                    // Role-based defaults (applied when flags are missing).
                    // Requested behavior:
                    // - super_admin: full access
                    // - admin: can hardpress contracts, can access commission, sees all jobs
                    // - tech: can access commission but sees only own leads (screen already filters by isAdmin)
                    boolean defaultCanSearch = isSuperAdmin;
                    boolean defaultCanLocation = isSuperAdmin;
                    boolean defaultHardPressContracts = isAdmin; // admin + super_admin
                    boolean defaultMarkPaidLeads = isSuperAdmin; // super_admin only by default
                    boolean defaultCanAccessCommission = true; // all staff can access; non-admin sees only their own commission/leads
                    boolean defaultSeesAllJobs = isAdmin; // admin + super_admin
                    boolean defaultCanSeeContracts = isSuperAdmin || "admin".equals(roleNorm) || "tech".equals(roleNorm);
                    boolean defaultCanViewAllContracts = isAdmin; // admin + super_admin only; tech = false

                    boolean canSearch = canSearchFlag != null ? canSearchFlag : defaultCanSearch;
                    boolean canLocation = canLocationFlag != null ? canLocationFlag : defaultCanLocation;
                    boolean canHardPressContracts = hardPressFlag != null ? hardPressFlag : defaultHardPressContracts;
                    boolean canMarkPaidLeads = markPaidFlag != null ? markPaidFlag : defaultMarkPaidLeads;
                    boolean canAccessCommission = commissionFlag != null ? commissionFlag : defaultCanAccessCommission;
                    boolean seesAllJobs = seesAllJobsFlag != null ? seesAllJobsFlag : defaultSeesAllJobs;

                    // Demo app only: per-profile demo time limit. Default false = constant access (no limit).
                    Boolean demoReleaseFlag = pickBoolean(ds, "DemoRelease", "demoRelease", "demo_release");
                    Integer demoDaysNumberVal = pickInteger(ds, "DemoDaysNumber", "demoDaysNumber", "demo_days_number");
                    boolean demoRelease = demoReleaseFlag != null ? demoReleaseFlag : false;
                    int demoDaysNumber = demoDaysNumberVal != null && demoDaysNumberVal > 0 ? demoDaysNumberVal : 0;

                    // First-login auto-seed: write missing flags to users/{StaffID} (merge-only).
                    // This ensures the profile doc reflects the correct default permissions without hardcoding IDs elsewhere.
                    try {
                        Map<String, Object> seed = new HashMap<>();
                        if (canSearchFlag == null) seed.put("CanSearch", defaultCanSearch);
                        if (canLocationFlag == null) seed.put("CanLocationFinder", defaultCanLocation);
                        if (hardPressFlag == null) seed.put("CanHardPressContracts", defaultHardPressContracts);
                        if (markPaidFlag == null) seed.put("CanMarkPaidLeads", defaultMarkPaidLeads);
                        if (commissionFlag == null) seed.put("CanAccessCommission", defaultCanAccessCommission);
                        if (seesAllJobsFlag == null) seed.put("SeesAllJobs", defaultSeesAllJobs);
                        if (canSeeContractsFlag == null) seed.put("canSeeContracts", defaultCanSeeContracts);
                        if (canViewAllContractsFlag == null) seed.put("canViewAllContracts", defaultCanViewAllContracts);

                        if (!seed.isEmpty()) {
                            db.collection("users")
                                    .document(staffIdNorm)
                                    .set(seed, SetOptions.merge())
                                    .addOnSuccessListener(v -> Log.d(TAG, "Seeded default permissions for staffId=" + safe(staffIdNorm)))
                                    .addOnFailureListener(e -> Log.w(TAG, "Failed to seed permissions for staffId=" + safe(staffIdNorm) + ": " + (e != null ? e.getMessage() : "")));
                        }
                    } catch (Exception ignored) {}

                    Session session = new Session(
                            staffIdNorm,
                            contractKey,
                            roleNorm,
                            name,
                            email,
                            number,
                            title,
                            canSearch,
                            canLocation,
                            canHardPressContracts,
                            canMarkPaidLeads,
                            canAccessCommission,
                            seesAllJobs,
                            demoRelease,
                            demoDaysNumber
                    );

                    if (cb != null) cb.onSession(session);
                })
                .addOnFailureListener(e -> {
                    if (cb != null) cb.onSession(null);
                });
    }

    private static void persist(Context context, Session s) {
        if (context == null || s == null) return;
        try {
            FirebaseUser u = null;
            try { u = FirebaseAuth.getInstance().getCurrentUser(); } catch (Exception ignored) {}
            SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            e.putString(KEY_STAFF_ID, normalizeStaffId(s.staffId));
            e.putString(KEY_CONTRACT_KEY, s.contractKey != null ? s.contractKey : "");
            e.putString(KEY_ROLE_NORM, s.roleNorm);
            e.putString(KEY_NAME, s.name);
            e.putString(KEY_EMAIL, s.email);
            e.putString(KEY_NUMBER, s.number);
            e.putString(KEY_TITLE, s.title);
            if (u != null) {
                e.putString(KEY_AUTH_UID, u.getUid());
                String em = u.getEmail() != null ? u.getEmail() : "";
                e.putString(KEY_AUTH_EMAIL, em);
            } else {
                e.remove(KEY_AUTH_UID);
                e.remove(KEY_AUTH_EMAIL);
            }

            e.putBoolean(KEY_CAN_SEARCH, s.canSearch);
            e.putBoolean(KEY_CAN_LOCATION, s.canUseLocationFinder);
            e.putBoolean(KEY_CAN_HARDPRESS_CONTRACTS, s.canHardPressContracts);
            e.putBoolean(KEY_CAN_MARK_PAID_LEADS, s.canMarkPaidLeads);
            e.putBoolean(KEY_CAN_ACCESS_COMMISSION, s.canAccessCommissionLeads);
            e.putBoolean(KEY_SEES_ALL_JOBS, s.seesAllJobs);
            e.putBoolean(KEY_DEMO_RELEASE, s.demoRelease);
            e.putInt(KEY_DEMO_DAYS_NUMBER, s.demoDaysNumber);
            e.apply();
        } catch (Exception ignored) {}
    }

    private static Session buildOfflineFallback(Context context) {
        // Offline user: minimal safe permissions; keep legacy behavior (most admin features hidden).
        String email = "";
        try {
            email = context != null ? context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("USER_EMAIL", "") : "";
        } catch (Exception ignored) {}
        return new Session(
                "",
                "",
                "tech",
                "Offline User",
                email,
                "",
                "",
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                0
        );
    }

    /** Normalizes role field to stable keys: admin/super_admin/tech/unknown. */
    public static String normalizeRole(@Nullable String raw) {
        if (raw == null) return "unknown";
        String s = raw.trim().toLowerCase(Locale.getDefault());
        if (s.isEmpty()) return "unknown";
        s = s.replace('-', '_').replace(' ', '_');
        while (s.contains("__")) s = s.replace("__", "_");
        if ("superadmin".equals(s) || "super_admin".equals(s) || "super_admin_".equals(s) || "super_admins".equals(s)) return "super_admin";
        if ("super".equals(s) || "super_admin".equals(s)) return "super_admin";
        if ("admin".equals(s) || "administrator".equals(s) || "admins".equals(s)) return "admin";
        if ("tech".equals(s) || "technician".equals(s) || "worker".equals(s)) return "tech";
        if (s.contains("super") && s.contains("admin")) return "super_admin";
        if (s.contains("admin")) return "admin";
        if (s.contains("tech")) return "tech";
        return s;
    }

    /** Normalize StaffID to 3 digits (zero-padded) to avoid mismatches across UID/staff docs. */
    private static String normalizeStaffId(@Nullable String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        try {
            if (s.matches("\\d{1,3}")) {
                int n = Integer.parseInt(s);
                return String.format(Locale.US, "%03d", n);
            }
        } catch (Exception ignored) {}
        return s;
    }

    // Convenience getters for call sites (safe defaults preserve access).
    public static boolean isSuperAdmin(Context context) {
        Session s = getCached(context);
        return s != null && s.isSuperAdmin;
    }

    public static boolean isAdmin(Context context) {
        Session s = getCached(context);
        return s != null && s.isAdmin;
    }

    public static boolean isTech(Context context) {
        Session s = getCached(context);
        return s != null && s.isTech;
    }

    public static boolean canSearch(Context context) {
        Session s = getCached(context);
        return s != null && s.canSearch;
    }

    public static boolean canUseLocationFinder(Context context) {
        Session s = getCached(context);
        return s != null && s.canUseLocationFinder;
    }

    public static boolean canHardPressContracts(Context context) {
        Session s = getCached(context);
        return s != null && s.canHardPressContracts;
    }

    public static boolean canMarkPaidLeads(Context context) {
        Session s = getCached(context);
        return s != null && s.canMarkPaidLeads;
    }

    public static boolean canAccessCommissionLeads(Context context) {
        Session s = getCached(context);
        return s != null && s.canAccessCommissionLeads;
    }

    public static boolean seesAllJobs(Context context) {
        Session s = getCached(context);
        return s != null && s.seesAllJobs;
    }

    public static String getName(Context context) {
        Session s = getCached(context);
        return s != null ? s.name : "";
    }

    public static String getEmail(Context context) {
        Session s = getCached(context);
        return s != null ? s.email : "";
    }

    public static String getNumber(Context context) {
        Session s = getCached(context);
        return s != null ? s.number : "";
    }

    public static String getTitle(Context context) {
        Session s = getCached(context);
        return s != null ? s.title : "";
    }

    public static String getStaffId(Context context) {
        Session s = getCached(context);
        return s != null ? s.staffId : "";
    }

    /** ContractKey is the internal identifier used for assignment/routing. */
    public static String getContractKey(Context context) {
        Session s = getCached(context);
        return s != null ? s.contractKey : "";
    }

    @Nullable
    private static Boolean pickBoolean(DocumentSnapshot ds, String... keys) {
        if (ds == null || keys == null) return null;
        for (String k : keys) {
            try {
                Object raw = ds.get(k);
                if (raw == null) continue;
                if (raw instanceof Boolean) return (Boolean) raw;
                if (raw instanceof Number) return ((Number) raw).intValue() != 0;
                String s = String.valueOf(raw).trim().toLowerCase(Locale.getDefault());
                if ("true".equals(s) || "yes".equals(s) || "1".equals(s)) return true;
                if ("false".equals(s) || "no".equals(s) || "0".equals(s)) return false;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String pickString(DocumentSnapshot ds, String... keys) {
        if (ds == null || keys == null) return "";
        for (String k : keys) {
            try {
                Object raw = ds.get(k);
                if (raw == null) continue;
                String v = String.valueOf(raw).trim();
                if (!v.isEmpty()) return v;
            } catch (Exception ignored) {}
        }
        return "";
    }

    @Nullable
    private static Integer pickInteger(DocumentSnapshot ds, String... keys) {
        if (ds == null || keys == null) return null;
        for (String k : keys) {
            try {
                Object raw = ds.get(k);
                if (raw == null) continue;
                if (raw instanceof Number) return ((Number) raw).intValue();
                String s = String.valueOf(raw).trim();
                if (!s.isEmpty()) return Integer.parseInt(s);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String safe(String v) {
        return v != null ? v : "";
    }
}

