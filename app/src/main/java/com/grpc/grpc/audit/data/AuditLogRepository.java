package com.grpc.grpc.audit.data;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.audit.model.AuditLogEntry;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.GrpcApplication;
import com.grpc.grpc.core.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes one small audit record after a business action has already succeeded.
 * A failed write is logged for debugging and never reported as a failed job, report, or contract.
 */
public final class AuditLogRepository {

    private static final String TAG = "AuditLog";
    private static final int LIST_LIMIT = 400;

    public interface ListCallback {
        void onResult(List<AuditLogEntry> entries, @Nullable Exception error);
    }

    public interface DeleteCallback {
        void onComplete(boolean success);
    }

    private AuditLogRepository() {}

    public static void contractRoutine(String contractId, String contractName) {
        write("contract_routine", "Contract Routine", "contracts", contractId, contractName,
                null, null, null, null, null, null, null, null);
    }

    public static void contractCallOut(String contractId, String contractName) {
        write("contract_call_out", "Contract Call Out", "contracts", contractId, contractName,
                null, null, null, null, null, null, null, null);
    }

    public static void contractAssetsUpdated(String contractId, String contractName) {
        write("assets_updated", "Assets Updated", "contracts", contractId, contractName,
                null, null, null, contractId, null, null, null, null);
    }

    public static void contractUpdated(String contractId, String contractName) {
        write("contract_updated", "Contract Updated", "contracts", contractId, contractName,
                null, null, null, contractId, null, null, null, null);
    }

    public static void contractDeleted(String contractId, String contractName) {
        write("contract_deleted", "Contract Deleted", "contracts", contractId, contractName,
                null, null, null, contractId, null, null, null, null);
    }

    public static void reportUploaded(String fileName, String siteName, String storagePath,
                                      @Nullable String contractId, @Nullable String jobId,
                                      @Nullable String managementJobId, @Nullable String jobRef) {
        write("report_uploaded", "Report Uploaded", "reports", storagePath, siteName,
                fileName, jobId, jobRef, contractId, null, null, managementJobId, null);
    }

    public static void quotationCreated(String fileName, String siteName, String storagePath) {
        write("quotation_created", "Quotation Created", "quotations", storagePath, siteName,
                fileName, null, null, null, null, null, null, storagePath);
    }

    public static void jobCreated(String jobId, String customerName) {
        write("job_created", "Job Created", "job_work", jobId, customerName,
                null, jobId, null, null, null, null, null, "job_created_" + jobId);
    }

    public static void jobVisit(String action, String jobId, String customerName, String visitId) {
        String type;
        String label;
        if ("follow_up".equals(action)) {
            type = "job_follow_up";
            label = "Job Follow Up";
        } else if ("completed".equals(action)) {
            type = "job_completed";
            label = "Job Completed";
        } else if ("reopened".equals(action)) {
            type = "job_reopened";
            label = "Job Reopened";
        } else {
            return;
        }
        write(type, label, "job_work", jobId, customerName,
                null, jobId, null, null, null, null, null, jobId + "_" + visitId + "_" + action);
    }

    public static void managementJobCreated(String jobId, String jobRef, String address) {
        String name = joinRef(jobRef, address);
        write("management_job_created", "Management Job Created", "management", jobId, name,
                null, jobId, jobRef, null, null, null, jobId, "management_created_" + jobId);
    }

    public static void managementVisit(String action, String jobId, String jobRef, String address, String visitId) {
        String type;
        String label;
        if ("follow_up".equals(action)) {
            type = "management_job_follow_up";
            label = "Management Job Follow Up";
        } else if ("completed".equals(action)) {
            type = "management_job_completed";
            label = "Management Job Completed";
        } else if ("reopened".equals(action)) {
            type = "management_job_reopened";
            label = "Management Job Reopened";
        } else {
            return;
        }
        write(type, label, "management", jobId, joinRef(jobRef, address),
                null, jobId, jobRef, null, null, null, jobId, jobId + "_" + visitId + "_" + action);
    }

    public static void invoiceCreated(String invoiceNumber, String customerName, String fileName,
                                      @Nullable String companyId, @Nullable String contractId) {
        String name = joinRef(invoiceNumber, customerName);
        write("invoice_created", "Invoice Created", "billing", invoiceNumber, name,
                fileName, null, null, contractId, companyId, null, null, "invoice_" + invoiceNumber);
    }

    public static void stock(String eventType, String eventLabel, String requestId, String summary) {
        write(eventType, eventLabel, "stock", requestId, summary,
                null, null, null, null, null, null, null, eventType + "_" + requestId);
    }

    public static ListenerRegistration listenNewest(ListCallback callback) {
        if (BuildConfig.IS_OFFLINE || FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onResult(new ArrayList<>(), new Exception("Audit Logs are unavailable."));
            return null;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        return db.collection(FirestorePaths.AUDIT_LOGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(LIST_LIMIT)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        callback.onResult(new ArrayList<>(), error);
                        return;
                    }
                    List<AuditLogEntry> entries = new ArrayList<>();
                    if (snap != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                            entries.add(AuditLogEntry.from(doc));
                        }
                    }
                    callback.onResult(entries, null);
                });
    }

    /** Deletes every audit_logs document. Firestore batches hold at most 500 writes. */
    public static void deleteAll(DeleteCallback callback) {
        if (BuildConfig.IS_OFFLINE || FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onComplete(false);
            return;
        }
        deleteNextPage(FirebaseFirestore.getInstance(), callback);
    }

    private static void deleteNextPage(FirebaseFirestore db, DeleteCallback callback) {
        db.collection(FirestorePaths.AUDIT_LOGS)
                .limit(400)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        callback.onComplete(true);
                        return;
                    }
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> deleteNextPage(db, callback))
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Audit logs were not deleted: " + (e.getMessage() == null ? "" : e.getMessage()));
                                callback.onComplete(false);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Audit logs were not deleted: " + (e.getMessage() == null ? "" : e.getMessage()));
                    callback.onComplete(false);
                });
    }

    private static void write(String eventType, String eventLabel, String module,
                              @Nullable String entityId, @Nullable String entityName, @Nullable String fileName,
                              @Nullable String jobId, @Nullable String jobRef, @Nullable String contractId,
                              @Nullable String companyId, @Nullable String reportId, @Nullable String managementJobId,
                              @Nullable String dedupeKey) {
        try {
            if (BuildConfig.IS_OFFLINE) return;
            Context context = GrpcApplication.appContext();
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (context == null || user == null || TextUtils.isEmpty(user.getUid())) return;

            SessionManager.Session session = SessionManager.getCached(context);
            String actorName = session != null && !TextUtils.isEmpty(session.name) ? session.name.trim() : "";
            if (actorName.isEmpty() || looksLikeUid(actorName)) actorName = "Staff";
            String role = session != null && session.roleNorm != null ? session.roleNorm : "";
            if (role.isEmpty()) role = "unknown";

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference ref = dedupeKey == null || dedupeKey.trim().isEmpty()
                    ? db.collection(FirestorePaths.AUDIT_LOGS).document()
                    : db.collection(FirestorePaths.AUDIT_LOGS).document(documentId(eventType, dedupeKey));

            Map<String, Object> data = new HashMap<>();
            data.put("auditId", ref.getId());
            data.put("actorUid", user.getUid());
            data.put("actorName", limit(actorName, 120));
            data.put("actorRole", limit(role, 40));
            data.put("eventType", limit(eventType, 40));
            data.put("eventLabel", limit(eventLabel, 80));
            data.put("module", limit(module, 40));
            data.put("createdAt", FieldValue.serverTimestamp());
            putText(data, "entityId", entityId, 300);
            putText(data, "entityName", entityName, 200);
            putText(data, "fileName", fileName, 180);
            putText(data, "jobId", jobId, 128);
            putText(data, "jobRef", jobRef, 40);
            putText(data, "contractId", contractId, 128);
            putText(data, "companyId", companyId, 128);
            putText(data, "reportId", reportId, 300);
            putText(data, "managementJobId", managementJobId, 128);

            ref.set(data).addOnFailureListener(e ->
                    Log.w(TAG, "Audit entry was not saved: " + (e.getMessage() == null ? eventType : e.getMessage())));
        } catch (Exception e) {
            Log.w(TAG, "Audit entry was not saved: " + e.getMessage());
        }
    }

    private static void putText(Map<String, Object> data, String key, @Nullable String value, int max) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        data.put(key, limit(trimmed, max));
    }

    private static String joinRef(@Nullable String ref, @Nullable String detail) {
        String left = ref == null ? "" : ref.trim();
        String right = detail == null ? "" : detail.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + " - " + right;
    }

    private static String documentId(String eventType, String dedupeKey) {
        String raw = eventType + "_" + dedupeKey.trim().toLowerCase(Locale.UK);
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                safe.append(c);
            } else {
                safe.append('_');
            }
        }
        String id = safe.toString().replaceAll("_+", "_");
        if (id.length() <= 140) return id;
        return eventType + "_" + Integer.toHexString(dedupeKey.hashCode()) + "_"
                + id.substring(id.length() - 48);
    }

    private static String limit(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, max);
    }

    private static boolean looksLikeUid(String value) {
        return value.length() >= 20 && value.matches("[A-Za-z0-9]+");
    }
}
