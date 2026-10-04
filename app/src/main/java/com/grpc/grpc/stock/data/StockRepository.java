package com.grpc.grpc.stock.data;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.audit.data.AuditLogRepository;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.stock.model.StockRequest;
import com.grpc.grpc.stock.model.StockRequestItem;
import com.grpc.grpc.stock.model.StockUserSummary;
import com.grpc.grpc.stock.model.StockWeeklyCheck;
import com.grpc.grpc.stock.util.StockWeekHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore access for stock requests and weekly checks.
 * Uses the flavour's default Firebase project.
 */
public final class StockRepository {

    public interface WriteCallback {
        void onComplete(boolean success, @Nullable String message);
    }

    public interface WeeklyCallback {
        void onResult(boolean loaded, @Nullable StockWeeklyCheck check);
    }

    public interface RequestCallback {
        void onResult(boolean loaded, @Nullable StockRequest request);
    }

    public interface RequestListCallback {
        void onResult(boolean loaded, @NonNull List<StockRequest> requests);
    }

    public interface UserListCallback {
        void onResult(boolean loaded, @NonNull List<StockUserSummary> users);
    }

    private StockRepository() {
    }

    public static void loadWeeklyCheck(@NonNull String uid, @NonNull String weekKey, @NonNull WeeklyCallback cb) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(uid) || TextUtils.isEmpty(weekKey)) {
            cb.onResult(false, null);
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onResult(false, null);
            return;
        }
        db.collection(FirestorePaths.STOCK_WEEKLY_CHECKS)
                .document(StockWeekHelper.documentId(uid, weekKey))
                .get()
                .addOnSuccessListener(snap -> cb.onResult(true, StockWeeklyCheck.fromSnapshot(snap)))
                .addOnFailureListener(e -> cb.onResult(false, null));
    }

    public static void submitRequest(@NonNull Context context,
                                     @NonNull List<StockRequestItem> items,
                                     @Nullable String notes,
                                     @NonNull WriteCallback cb) {
        if (BuildConfig.IS_OFFLINE) {
            cb.onComplete(false, context.getString(R.string.stock_unavailable_offline));
            return;
        }
        String uid = authUid();
        if (TextUtils.isEmpty(uid)) {
            cb.onComplete(false, context.getString(R.string.stock_not_signed_in));
            return;
        }
        if (items.isEmpty()) {
            cb.onComplete(false, context.getString(R.string.stock_need_items));
            return;
        }
        if (items.size() > 30) {
            cb.onComplete(false, context.getString(R.string.stock_too_many_items));
            return;
        }
        String name = displayName(context);
        String weekKey = StockWeekHelper.currentWeekKey();
        String note = notes != null ? notes.trim() : "";
        if (note.length() > 500) {
            cb.onComplete(false, context.getString(R.string.stock_notes_too_long));
            return;
        }

        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onComplete(false, context.getString(R.string.stock_save_failed));
            return;
        }
        DocumentReference requestRef = db.collection(FirestorePaths.STOCK_REQUESTS).document();
        String requestId = requestRef.getId();
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", requestId);
        data.put("requesterUid", uid);
        data.put("requesterName", name);
        data.put("weekKey", weekKey);
        data.put("status", StockRequest.STATUS_PENDING);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("items", itemMaps(items));
        if (!note.isEmpty()) data.put("notes", note);

        DocumentReference weekRef = db.collection(FirestorePaths.STOCK_WEEKLY_CHECKS)
                .document(StockWeekHelper.documentId(uid, weekKey));

        db.runTransaction(transaction -> {
            DocumentSnapshot week = transaction.get(weekRef);
            transaction.set(requestRef, data);
            if (week != null && week.exists()) {
                Map<String, Object> patch = new HashMap<>();
                patch.put("response", StockWeeklyCheck.RESPONSE_REQUESTED);
                patch.put("requestId", requestId);
                transaction.update(weekRef, patch);
            } else {
                Map<String, Object> weekData = new HashMap<>();
                weekData.put("userUid", uid);
                weekData.put("userName", name);
                weekData.put("weekKey", weekKey);
                weekData.put("response", StockWeeklyCheck.RESPONSE_REQUESTED);
                weekData.put("createdAt", FieldValue.serverTimestamp());
                weekData.put("requestId", requestId);
                transaction.set(weekRef, weekData);
            }
            return null;
        }).addOnSuccessListener(v -> {
            cb.onComplete(true, null);
            int count = items.size();
            AuditLogRepository.stock("stock_request_submitted", "Stock Request Submitted", requestId,
                    name + " - " + count + (count == 1 ? " item" : " items"));
        }).addOnFailureListener(e -> cb.onComplete(false, context.getString(R.string.stock_save_failed)));
    }

    public static void markNothingNeeded(@NonNull Context context, @NonNull WriteCallback cb) {
        if (BuildConfig.IS_OFFLINE) {
            cb.onComplete(false, context.getString(R.string.stock_unavailable_offline));
            return;
        }
        String uid = authUid();
        if (TextUtils.isEmpty(uid)) {
            cb.onComplete(false, context.getString(R.string.stock_not_signed_in));
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onComplete(false, context.getString(R.string.stock_save_failed));
            return;
        }
        String name = displayName(context);
        String weekKey = StockWeekHelper.currentWeekKey();
        DocumentReference weekRef = db.collection(FirestorePaths.STOCK_WEEKLY_CHECKS)
                .document(StockWeekHelper.documentId(uid, weekKey));
        db.runTransaction(transaction -> {
            DocumentSnapshot week = transaction.get(weekRef);
            if (week != null && week.exists()) {
                return null;
            }
            Map<String, Object> weekData = new HashMap<>();
            weekData.put("userUid", uid);
            weekData.put("userName", name);
            weekData.put("weekKey", weekKey);
            weekData.put("response", StockWeeklyCheck.RESPONSE_NOT_NEEDED);
            weekData.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(weekRef, weekData);
            return null;
        }).addOnSuccessListener(v -> cb.onComplete(true, null))
                .addOnFailureListener(e -> cb.onComplete(false, context.getString(R.string.stock_save_failed)));
    }

    public static void loadRequestsForUser(@NonNull String uid, @NonNull RequestListCallback cb) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(uid)) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        db.collection(FirestorePaths.STOCK_REQUESTS)
                .whereEqualTo("requesterUid", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    List<StockRequest> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            StockRequest request = StockRequest.fromSnapshot(doc);
                            if (request != null) out.add(request);
                        }
                    }
                    sortNewestFirst(out);
                    cb.onResult(true, out);
                })
                .addOnFailureListener(e -> cb.onResult(false, Collections.emptyList()));
    }

    public static void loadRequestsByStatus(@NonNull String status, @NonNull RequestListCallback cb) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(status)) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        db.collection(FirestorePaths.STOCK_REQUESTS)
                .whereEqualTo("status", status)
                .get()
                .addOnSuccessListener(snap -> {
                    List<StockRequest> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            StockRequest request = StockRequest.fromSnapshot(doc);
                            if (request != null) out.add(request);
                        }
                    }
                    sortNewestFirst(out);
                    cb.onResult(true, out);
                })
                .addOnFailureListener(e -> cb.onResult(false, Collections.emptyList()));
    }

    public static void loadRequest(@NonNull String requestId, @NonNull RequestCallback cb) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(requestId)) {
            cb.onResult(false, null);
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onResult(false, null);
            return;
        }
        db.collection(FirestorePaths.STOCK_REQUESTS)
                .document(requestId)
                .get()
                .addOnSuccessListener(snap -> cb.onResult(true, StockRequest.fromSnapshot(snap)))
                .addOnFailureListener(e -> cb.onResult(false, null));
    }

    public static void loadUserSummaries(@NonNull Context context, @NonNull UserListCallback cb) {
        if (BuildConfig.IS_OFFLINE) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        if (!SessionManager.isAdmin(context)) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onResult(false, Collections.emptyList());
            return;
        }
        db.collection(FirestorePaths.USERS).limit(300).get()
                .addOnSuccessListener(users -> {
                    List<StockUserSummary> people = new ArrayList<>();
                    if (users != null) {
                        for (DocumentSnapshot ds : users.getDocuments()) {
                            StockUserSummary summary = summaryFromUser(ds);
                            if (summary != null) people.add(summary);
                        }
                    }
                    db.collection(FirestorePaths.STOCK_REQUESTS)
                            .whereEqualTo("status", StockRequest.STATUS_PENDING)
                            .get()
                            .addOnSuccessListener(pending -> {
                                Map<String, Integer> counts = new HashMap<>();
                                if (pending != null) {
                                    for (DocumentSnapshot doc : pending.getDocuments()) {
                                        String uid = doc.getString("requesterUid");
                                        if (TextUtils.isEmpty(uid)) continue;
                                        Integer current = counts.get(uid);
                                        counts.put(uid, current == null ? 1 : current + 1);
                                    }
                                }
                                for (StockUserSummary person : people) {
                                    Integer count = counts.get(person.uid);
                                    person.pendingCount = count == null ? 0 : count;
                                }
                                Collections.sort(people, (a, b) -> a.name.compareToIgnoreCase(b.name));
                                cb.onResult(true, people);
                            })
                            .addOnFailureListener(e -> cb.onResult(false, Collections.emptyList()));
                })
                .addOnFailureListener(e -> cb.onResult(false, Collections.emptyList()));
    }

    public static void markReady(@NonNull Context context, @NonNull String requestId, @NonNull WriteCallback cb) {
        updateStatus(context, requestId, StockRequest.STATUS_PENDING, StockRequest.STATUS_READY, true, cb);
    }

    public static void markRetrieved(@NonNull Context context, @NonNull String requestId, @NonNull WriteCallback cb) {
        updateStatus(context, requestId, StockRequest.STATUS_READY, StockRequest.STATUS_RETRIEVED, false, cb);
    }

    /**
     * Deletes stock_requests/{requestId} only when it is already retrieved.
     * Does not delete the weekly check.
     */
    public static void deleteRetrieved(@NonNull Context context, @NonNull String requestId, @NonNull WriteCallback cb) {
        if (BuildConfig.IS_OFFLINE) {
            cb.onComplete(false, context.getString(R.string.stock_unavailable_offline));
            return;
        }
        if (!SessionManager.isAdmin(context)) {
            cb.onComplete(false, context.getString(R.string.stock_admin_only));
            return;
        }
        String uid = authUid();
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(requestId)) {
            cb.onComplete(false, context.getString(R.string.stock_delete_failed));
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onComplete(false, context.getString(R.string.stock_delete_failed));
            return;
        }
        DocumentReference ref = db.collection(FirestorePaths.STOCK_REQUESTS).document(requestId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (snap == null || !snap.exists()) {
                throw new IllegalStateException("missing");
            }
            String status = snap.getString("status");
            if (!StockRequest.STATUS_RETRIEVED.equals(status)) {
                throw new IllegalStateException("status");
            }
            transaction.delete(ref);
            return null;
        }).addOnSuccessListener(v -> cb.onComplete(true, null))
                .addOnFailureListener(e -> {
                    String message = e != null && e.getMessage() != null && e.getMessage().contains("status")
                            ? context.getString(R.string.stock_only_retrieved_delete)
                            : context.getString(R.string.stock_delete_failed);
                    cb.onComplete(false, message);
                });
    }

    private static void updateStatus(@NonNull Context context,
                                     @NonNull String requestId,
                                     @NonNull String fromStatus,
                                     @NonNull String toStatus,
                                     boolean readyTransition,
                                     @NonNull WriteCallback cb) {
        if (BuildConfig.IS_OFFLINE) {
            cb.onComplete(false, context.getString(R.string.stock_unavailable_offline));
            return;
        }
        if (!SessionManager.isAdmin(context)) {
            cb.onComplete(false, context.getString(R.string.stock_admin_only));
            return;
        }
        String uid = authUid();
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(requestId)) {
            cb.onComplete(false, context.getString(R.string.stock_save_failed));
            return;
        }
        FirebaseFirestore db = firestore();
        if (db == null) {
            cb.onComplete(false, context.getString(R.string.stock_save_failed));
            return;
        }
        String name = displayName(context);
        DocumentReference ref = db.collection(FirestorePaths.STOCK_REQUESTS).document(requestId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (snap == null || !snap.exists()) {
                throw new IllegalStateException("missing");
            }
            String status = snap.getString("status");
            if (!fromStatus.equals(status)) {
                throw new IllegalStateException("status");
            }
            Map<String, Object> patch = new HashMap<>();
            patch.put("status", toStatus);
            if (readyTransition) {
                patch.put("approvedAt", FieldValue.serverTimestamp());
                patch.put("approvedByUid", uid);
                patch.put("approvedByName", name);
            } else {
                patch.put("retrievedAt", FieldValue.serverTimestamp());
                patch.put("retrievedByUid", uid);
                patch.put("retrievedByName", name);
            }
            transaction.update(ref, patch);
            String requester = snap.getString("requesterName");
            if (requester == null || requester.trim().isEmpty()) requester = "Staff";
            Object rawItems = snap.get("items");
            int count = rawItems instanceof java.util.List ? ((java.util.List<?>) rawItems).size() : 0;
            return requester.trim() + " - " + count + (count == 1 ? " item" : " items");
        }).addOnSuccessListener(summary -> {
            cb.onComplete(true, null);
            if (summary != null) {
                if (readyTransition) {
                    AuditLogRepository.stock("stock_ready", "Stock Ready", requestId, summary);
                } else {
                    AuditLogRepository.stock("stock_retrieved", "Stock Retrieved", requestId, summary);
                }
            }
        })
                .addOnFailureListener(e -> {
                    int message = readyTransition
                            ? R.string.stock_only_pending_ready
                            : R.string.stock_only_ready_retrieved;
                    if (e == null || e.getMessage() == null || !e.getMessage().contains("status")) {
                        message = R.string.stock_save_failed;
                    }
                    cb.onComplete(false, context.getString(message));
                });
    }

    @NonNull
    public static String displayName(@NonNull Context context) {
        String name = SessionManager.getName(context);
        if (!TextUtils.isEmpty(name)) {
            String trimmed = name.trim();
            if (!looksLikeUid(trimmed)) {
                return limit(trimmed, 120);
            }
        }
        String email = SessionManager.getEmail(context);
        if (!TextUtils.isEmpty(email)) {
            int at = email.indexOf('@');
            String local = at > 0 ? email.substring(0, at).trim() : email.trim();
            if (!local.isEmpty() && !looksLikeUid(local)) return limit(local, 120);
        }
        return "User";
    }

    @NonNull
    public static String authUid() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && !TextUtils.isEmpty(user.getUid())) return user.getUid();
        } catch (Exception ignored) {
        }
        return "";
    }

    @Nullable
    private static StockUserSummary summaryFromUser(@Nullable DocumentSnapshot ds) {
        if (ds == null || !ds.exists()) return null;
        String docId = ds.getId() != null ? ds.getId().trim() : "";
        if (docId.isEmpty() || docId.matches("\\d{3}")) return null;
        Object active = ds.get("active");
        if (active == null) active = ds.get("Active");
        if (active instanceof Boolean && !((Boolean) active)) return null;

        String role = firstString(ds, "role", "Role");
        String roleNorm = SessionManager.normalizeRole(role);
        if (!"admin".equals(roleNorm) && !"super_admin".equals(roleNorm) && !"tech".equals(roleNorm)) {
            return null;
        }
        String contractKey = firstString(ds, "contractKey", "ContractKey", "contract_key");
        if (TextUtils.isEmpty(contractKey)) return null;
        String name = firstString(ds, "name", "Name");
        if (TextUtils.isEmpty(name) || looksLikeUid(name)) return null;
        return new StockUserSummary(docId, name, 0);
    }

    @NonNull
    private static List<Map<String, Object>> itemMaps(@NonNull List<StockRequestItem> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StockRequestItem item : items) {
            Map<String, Object> map = new HashMap<>();
            map.put("productName", item.getProductName());
            map.put("quantity", item.getQuantity());
            map.put("productType", item.getProductType());
            out.add(map);
        }
        return out;
    }

    private static void sortNewestFirst(@NonNull List<StockRequest> requests) {
        Collections.sort(requests, (a, b) -> Long.compare(b.createdAtMillis, a.createdAtMillis));
    }

    @Nullable
    private static FirebaseFirestore firestore() {
        try {
            return FirebaseHelper.getFirestore();
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static String firstString(DocumentSnapshot ds, String... keys) {
        for (String key : keys) {
            try {
                Object raw = ds.get(key);
                if (raw == null) continue;
                String value = String.valueOf(raw).trim();
                if (!value.isEmpty()) return value;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static boolean looksLikeUid(@NonNull String value) {
        String v = value.trim();
        return v.length() >= 20 && v.matches("[A-Za-z0-9]+");
    }

    @NonNull
    private static String limit(@NonNull String value, int max) {
        String trimmed = value.trim();
        if (trimmed.length() <= max) return trimmed;
        return trimmed.substring(0, max);
    }
}
