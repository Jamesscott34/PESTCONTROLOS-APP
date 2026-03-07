package com.grpc.grpc.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * Central RBAC/session authority for the current logged-in user.
 *
 * Source of truth: Firestore users/{authUid} only. No dependency on numeric users/001-style docs.
 * Session.staffId is set to authUid (or optional staffId field from doc for display).
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
    private static final String KEY_CAN_SEE_CONTRACTS = "SESSION_CAN_SEE_CONTRACTS";
    private static final String KEY_CAN_VIEW_ALL_CONTRACTS = "SESSION_CAN_VIEW_ALL_CONTRACTS";
    private static final String KEY_CAN_MESSAGE = "SESSION_CAN_MESSAGE";
    private static final String KEY_CAN_MAP = "SESSION_CAN_MAP";
    private static final String KEY_CAN_BUG_REPORT = "SESSION_CAN_BUG_REPORT";
    private static final String KEY_DEMO_RELEASE = "SESSION_DEMO_RELEASE";
    private static final String KEY_DEMO_DAYS_NUMBER = "SESSION_DEMO_DAYS_NUMBER";

    private static volatile Session current = null;
    private static volatile boolean isLoading = false;

    private SessionManager() {}

    public static final class Session {
        /** Auth UID (Firebase Auth); same as users/{authUid} doc id. Used for notifications and lookups. */
        public final String staffId;
        public final String contractKey; // users/{authUid}.contractKey (internal identifier for assignedTech etc.)
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
        public final boolean canSeeContracts;
        public final boolean canViewAllContracts;
        public final boolean canMessage;
        public final boolean canMap;
        /** True if user can submit/view bug reports and feature requests (super_admin/admin = true, tech = false unless set). */
        public final boolean canBugReport;

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
                boolean canSeeContracts,
                boolean canViewAllContracts,
                boolean canMessage,
                boolean canMap,
                boolean canBugReport,
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
            this.canSeeContracts = canSeeContracts;
            this.canViewAllContracts = canViewAllContracts;
            this.canMessage = canMessage;
            this.canMap = canMap;
            this.canBugReport = canBugReport;
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
                sp.getBoolean(KEY_CAN_SEE_CONTRACTS, true),
                sp.getBoolean(KEY_CAN_VIEW_ALL_CONTRACTS, false),
                sp.getBoolean(KEY_CAN_MESSAGE, false),
                sp.getBoolean(KEY_CAN_MAP, false),
                sp.getBoolean(KEY_CAN_BUG_REPORT, false),
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
                    .remove(KEY_CAN_SEE_CONTRACTS)
                    .remove(KEY_CAN_VIEW_ALL_CONTRACTS)
                    .remove(KEY_CAN_MESSAGE)
                    .remove(KEY_CAN_MAP)
                    .remove(KEY_CAN_BUG_REPORT)
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

        // Load session only from users/{authUid}. No numeric users/001-style docs.
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(uidDoc -> {
                    Session session = null;
                    if (uidDoc != null && uidDoc.exists()) {
                        session = buildSessionFromDocument(uidDoc, uid, email);
                        // Ensure bugReport field exists on doc (for Firestore rules). If missing, write default: admin/super_admin = true, tech = false.
                        if (session != null && uidDoc.get("bugReport") == null && uidDoc.get("BugReport") == null) {
                            Map<String, Object> patch = new HashMap<>();
                            patch.put("bugReport", session.canBugReport);
                            db.collection("users").document(uid).set(patch, SetOptions.merge())
                                    .addOnFailureListener(e -> Log.w(TAG, "Failed to set bugReport on users/" + uid + ": " + (e != null ? e.getMessage() : "")));
                        }
                    }
                    if (session == null) {
                        // No profile or missing role: build minimal session so login can block with required-flags check.
                        session = buildMinimalSession(uid, email);
                        Log.w(TAG, "No users/{authUid} profile or missing role; using minimal session");
                    } else {
                        Log.d(TAG, "Session loaded from users/{authUid}: role=" + safe(session.roleNorm) + " canBugReport=" + session.canBugReport);
                    }
                    isLoading = false;
                    current = session;
                    persist(context, session);
                    if (callback != null) callback.onSessionReady(session);
                })
                .addOnFailureListener(e -> {
                    Session fallback = getCached(context);
                    if (fallback == null) fallback = buildMinimalSession(uid, email);
                    isLoading = false;
                    current = fallback;
                    persist(context, fallback);
                    if (callback != null) callback.onSessionReady(fallback);
                    Log.w(TAG, "Failed to load users/{authUid}: " + (e != null ? e.getMessage() : ""));
                });
    }

    /** Minimal session when users/{authUid} is missing or has no role (login will block if required flags not met). */
    private static Session buildMinimalSession(String uid, String email) {
        return new Session(uid, "", "unknown", "", email != null ? email : "", "", "",
                false, false, false, false, false, false, false, false, false, false, false, false, 0);
    }

    /**
     * Build Session from users/{authUid} document (only source of truth).
     */
    @Nullable
    private static Session buildSessionFromDocument(DocumentSnapshot ds, String uid, String fallbackEmail) {
        if (ds == null || !ds.exists()) return null;
        String roleRaw = pickString(ds, "Role", "role");
        if (TextUtils.isEmpty(roleRaw)) return null;
        String roleNorm = normalizeRole(roleRaw);
        // Use authUid as session identity everywhere (notifications, lookups). No numeric staffId.
        String staffId = uid;
        String name = pickString(ds, "Name", "name");
        String email = pickString(ds, "Email", "email");
        if (TextUtils.isEmpty(email)) email = fallbackEmail != null ? fallbackEmail : "";
        String number = pickString(ds, "Number", "number", "Mobile", "mobile", "Phone", "phone");
        String title = pickString(ds, "Title", "title");
        String contractKey = pickString(ds, "ContractKey", "contractKey", "contract_key");
        Boolean canSearchFlag = pickBoolean(ds, "CanSearch", "canSearch");
        Boolean canLocationFlag = pickBoolean(ds, "CanLocationFinder", "canLocationFinder", "CanUseLocationFinder");
        Boolean hardPressFlag = pickBoolean(ds, "CanHardPressContracts", "canHardPressContracts");
        Boolean markPaidFlag = pickBoolean(ds, "CanMarkPaidLeads", "canMarkPaidLeads");
        Boolean commissionFlag = pickBoolean(ds, "CanAccessCommission", "canAccessCommission", "CanAccessCommissionLeads");
        Boolean seesAllJobsFlag = pickBoolean(ds, "SeesAllJobs", "seesAllJobs");
        Boolean canSeeContractsFlag = pickBoolean(ds, "CanSeeContracts", "canSeeContracts");
        Boolean canViewAllContractsFlag = pickBoolean(ds, "CanViewAllContracts", "canViewAllContracts");
        Boolean canMessageFlag = pickBoolean(ds, "CanMessage", "canMessage");
        Boolean canMapFlag = pickBoolean(ds, "CanMap", "canMap");
        Boolean canBugReportFlag = pickBoolean(ds, "BugReport", "bugReport");
        boolean isSuperAdmin = "super_admin".equals(roleNorm);
        boolean isAdmin = isSuperAdmin || "admin".equals(roleNorm);
        boolean defaultCanBugReport = isAdmin;
        boolean defaultCanSearch = isSuperAdmin;
        boolean defaultCanLocation = isSuperAdmin;
        boolean defaultHardPressContracts = isAdmin;
        boolean defaultMarkPaidLeads = isSuperAdmin;
        boolean defaultCanAccessCommission = true;
        boolean defaultSeesAllJobs = isAdmin;
        boolean defaultCanSeeContracts = isSuperAdmin || "admin".equals(roleNorm) || "tech".equals(roleNorm);
        boolean defaultCanViewAllContracts = isAdmin;
        boolean canSearch = canSearchFlag != null ? canSearchFlag : defaultCanSearch;
        boolean canLocation = canLocationFlag != null ? canLocationFlag : defaultCanLocation;
        boolean canHardPressContracts = hardPressFlag != null ? hardPressFlag : defaultHardPressContracts;
        boolean canMarkPaidLeads = markPaidFlag != null ? markPaidFlag : defaultMarkPaidLeads;
        boolean canAccessCommission = commissionFlag != null ? commissionFlag : defaultCanAccessCommission;
        boolean seesAllJobs = seesAllJobsFlag != null ? seesAllJobsFlag : defaultSeesAllJobs;
        boolean canSeeContracts = canSeeContractsFlag != null ? canSeeContractsFlag : defaultCanSeeContracts;
        boolean canViewAllContracts = canViewAllContractsFlag != null ? canViewAllContractsFlag : defaultCanViewAllContracts;
        boolean canMessage = canMessageFlag != null ? canMessageFlag : false;
        boolean canMap = canMapFlag != null ? canMapFlag : false;
        boolean canBugReport = canBugReportFlag != null ? canBugReportFlag : defaultCanBugReport;
        Boolean demoReleaseFlag = pickBoolean(ds, "DemoRelease", "demoRelease", "demo_release");
        Integer demoDaysNumberVal = pickInteger(ds, "DemoDaysNumber", "demoDaysNumber", "demo_days_number");
        boolean demoRelease = demoReleaseFlag != null ? demoReleaseFlag : false;
        int demoDaysNumber = demoDaysNumberVal != null && demoDaysNumberVal > 0 ? demoDaysNumberVal : 0;
        return new Session(staffId, contractKey, roleNorm, name, email, number, title,
                canSearch, canLocation, canHardPressContracts, canMarkPaidLeads, canAccessCommission,
                seesAllJobs, canSeeContracts, canViewAllContracts, canMessage, canMap, canBugReport, demoRelease, demoDaysNumber);
    }

    private static void persist(Context context, Session s) {
        if (context == null || s == null) return;
        try {
            FirebaseUser u = null;
            try { u = FirebaseAuth.getInstance().getCurrentUser(); } catch (Exception ignored) {}
            SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            e.putString(KEY_STAFF_ID, s.staffId != null ? s.staffId : "");
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
            e.putBoolean(KEY_CAN_SEE_CONTRACTS, s.canSeeContracts);
            e.putBoolean(KEY_CAN_VIEW_ALL_CONTRACTS, s.canViewAllContracts);
            e.putBoolean(KEY_CAN_MESSAGE, s.canMessage);
            e.putBoolean(KEY_CAN_MAP, s.canMap);
            e.putBoolean(KEY_CAN_BUG_REPORT, s.canBugReport);
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
                true,
                false,
                false,
                false,
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

    /** True if the current user can submit/view bug reports and feature requests (super_admin/admin = true, tech = false unless set on profile). */
    public static boolean canBugReport(Context context) {
        Session s = getCached(context);
        return s != null && s.canBugReport;
    }

    /** True if the current user is allowed to see and use the Messaging button. */
    public static boolean canMessage(Context context) {
        Session s = getCached(context);
        return s != null && s.canMessage;
    }

    /** True if the current user is allowed to see the Maps button (placeholder for future feature). */
    public static boolean canMap(Context context) {
        Session s = getCached(context);
        return s != null && s.canMap;
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

    public static boolean canSeeContracts(Context context) {
        Session s = getCached(context);
        return s != null && s.canSeeContracts;
    }

    public static boolean canViewAllContracts(Context context) {
        Session s = getCached(context);
        return s != null && s.canViewAllContracts;
    }

    /**
     * Login gate: for admin, super_admin, and tech, all permission flags must be true or login is blocked.
     * Checks: canSearch, canSeeContracts, canViewAllContracts, canAccessCommissionLeads,
     * canHardPressContracts, canMarkPaidLeads, canUseLocationFinder, seesAllJobs.
     * viewProfile is checked separately from users/{uid} in LoginActivity.
     */
    public static boolean sessionHasRequiredLoginFlags(@Nullable Session session) {
        if (session == null) return false;
        return session.canSearch
                && session.canSeeContracts
                && session.canViewAllContracts
                && session.canAccessCommissionLeads
                && session.canHardPressContracts
                && session.canMarkPaidLeads
                && session.canUseLocationFinder
                && session.seesAllJobs;
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

