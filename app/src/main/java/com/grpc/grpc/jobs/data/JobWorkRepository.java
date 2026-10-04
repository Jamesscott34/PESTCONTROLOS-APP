package com.grpc.grpc.jobs.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.grpc.grpc.audit.data.AuditLogRepository;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes the live Service Job collection {@code jobwork}.
 * Follow up, complete, and reopen share one transaction so a retry cannot count a visit twice.
 */
public final class JobWorkRepository {

    public static final String COLLECTION = "jobwork";

    private final FirebaseFirestore db;

    public JobWorkRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public JobWorkRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public interface JobsCallback {
        void onResult(List<JobRecord> jobs, @Nullable Exception error);
    }

    public interface JobCallback {
        void onResult(@Nullable JobRecord job, @Nullable Exception error);
    }

    public interface CreatedCallback {
        void onSuccess(String jobId);

        void onError(Exception error);
    }

    public interface DoneCallback {
        void onSuccess();

        void onError(Exception error);
    }

    public interface VisitsCallback {
        void onResult(List<VisitRecord> visits, @Nullable Exception error);
    }

    public interface ReportsCallback {
        void onResult(List<ReportLink> reports, @Nullable Exception error);
    }

    public static final class NewJob {
        public String assignedTechKey = "";
        public String assignedTech = "";
        public String customerName = "";
        public String customerEmail = "";
        public String customerContact = "";
        public String issueDetails = "";
        public String address = "";
        public String createdBy = "";
    }

    public static final class JobRecord {
        public final String id;
        public final Map<String, Object> data;

        public JobRecord(String id, Map<String, Object> data) {
            this.id = id == null ? "" : id;
            this.data = data == null ? new HashMap<>() : data;
        }

        public static JobRecord from(DocumentSnapshot doc) {
            Map<String, Object> data = doc.getData();
            return new JobRecord(doc.getId(), data == null ? new HashMap<>() : new HashMap<>(data));
        }

        public String text(String key) {
            Object value = data.get(key);
            return value == null ? "" : value.toString();
        }

        public long visitCount() {
            return number(data.get("visitCount"));
        }

        public String normalizedStatus() {
            String raw = text("Status").trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            if ("completed".equals(raw)) return "completed";
            if ("follow_up".equals(raw) || "followup".equals(raw)) return "follow_up";
            return "active";
        }

        public boolean legacyWithoutStatus() {
            return text("Status").trim().isEmpty();
        }

        public String statusLabel() {
            String status = normalizedStatus();
            if ("follow_up".equals(status)) return "FOLLOW UP REQUIRED";
            if ("completed".equals(status)) return "COMPLETED";
            return "ACTIVE";
        }

        public int displayYear() {
            long year = number(data.get("year"));
            if (year > 1900) return (int) year;
            long created = millis(data.get("CreatedAt"));
            if (created <= 0) return 0;
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(created);
            return calendar.get(Calendar.YEAR);
        }

        public boolean assignedTo(String contractKey) {
            if (contractKey == null || contractKey.trim().isEmpty()) return false;
            String key = contractKey.trim().toLowerCase(Locale.ROOT);
            String storedKey = text("AssignedTechKey").trim().toLowerCase(Locale.ROOT);
            if (!storedKey.isEmpty()) return key.equals(storedKey);
            return key.equals(text("AssignedTech").trim().toLowerCase(Locale.ROOT));
        }
    }

    public static final class VisitRecord {
        public final String id;
        public final String action;
        public final String techName;
        public final String storagePath;
        public final String fileName;
        public final String notes;
        public final Object when;

        public VisitRecord(String id, String action, String techName, String storagePath,
                           String fileName, String notes, Object when) {
            this.id = id == null ? "" : id;
            this.action = action == null ? "" : action;
            this.techName = techName == null ? "" : techName;
            this.storagePath = storagePath == null ? "" : storagePath;
            this.fileName = fileName == null ? "" : fileName;
            this.notes = notes == null ? "" : notes;
            this.when = when;
        }

        public String actionLabel() {
            if ("completed".equals(action)) return "COMPLETED";
            if ("reopened".equals(action)) return "REOPENED";
            if ("follow_up".equals(action)) return "FOLLOW UP";
            return action.isEmpty() ? "VISIT" : action;
        }
    }

    public static final class ReportLink {
        public final String id;
        public final String storagePath;
        public final String fileName;
        public final String visitId;
        public final Object createdAt;
        public final long reportYear;

        public ReportLink(String id, String storagePath, String fileName, String visitId,
                          Object createdAt, long reportYear) {
            this.id = id == null ? "" : id;
            this.storagePath = storagePath == null ? "" : storagePath;
            this.fileName = fileName == null ? "" : fileName;
            this.visitId = visitId == null ? "" : visitId;
            this.createdAt = createdAt;
            this.reportYear = reportYear;
        }
    }

    public static String formatWhen(Object value) {
        long millis = millis(value);
        if (millis <= 0) return "";
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(millis));
    }

    public static boolean isAlreadyComplete(Exception error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        return message.contains("ALREADY_COMPLETED") || message.toLowerCase(Locale.ROOT).contains("already complete");
    }

    public String newVisitId() {
        return db.collection(COLLECTION).document().getId();
    }

    /**
     * Admin and super admin listen to every service job.
     * A technician listens only to their AssignedTechKey, plus legacy jobs whose key is blank
     * and whose AssignedTech display matches that key.
     */
    @Nullable
    public ListenerRegistration listen(Context context, JobsCallback callback) {
        if (context != null && SessionManager.isAdmin(context)) {
            return db.collection(COLLECTION).addSnapshotListener((snapshots, error) ->
                    deliver(snapshots, error, callback));
        }
        String key = context == null ? "" : SessionManager.getContractKey(context);
        if (key == null || key.trim().isEmpty()) {
            callback.onResult(Collections.emptyList(), new Exception("Technician identity is missing."));
            return null;
        }
        String techKey = key.trim().toLowerCase(Locale.ROOT);
        String display = StaffDirectory.capitalizeContractKey(techKey);
        Map<String, JobRecord> keyed = new LinkedHashMap<>();
        Map<String, JobRecord> legacy = new LinkedHashMap<>();
        Query primary = db.collection(COLLECTION).whereEqualTo("AssignedTechKey", techKey);
        Query older = db.collection(COLLECTION)
                .whereEqualTo("AssignedTechKey", "")
                .whereEqualTo("AssignedTech", display);
        ListenerRegistration first = primary.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                callback.onResult(Collections.emptyList(), error);
                return;
            }
            keyed.clear();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    JobRecord job = JobRecord.from(doc);
                    keyed.put(job.id, job);
                }
            }
            publish(keyed, legacy, callback);
        });
        ListenerRegistration second = older.addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                callback.onResult(Collections.emptyList(), error);
                return;
            }
            legacy.clear();
            if (snapshots != null) {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    JobRecord job = JobRecord.from(doc);
                    legacy.put(job.id, job);
                }
            }
            publish(keyed, legacy, callback);
        });
        return () -> {
            first.remove();
            second.remove();
        };
    }

    public void getJob(String jobId, JobCallback callback) {
        if (jobId == null || jobId.trim().isEmpty()) {
            callback.onResult(null, new Exception("Missing job."));
            return;
        }
        db.collection(COLLECTION).document(jobId.trim()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onResult(null, new Exception("Job not found."));
                        return;
                    }
                    callback.onResult(JobRecord.from(doc), null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    public void createJob(NewJob request, CreatedCallback callback) {
        if (request == null) {
            callback.onError(new Exception("Missing job."));
            return;
        }
        String techKey = request.assignedTechKey == null ? "" : request.assignedTechKey.trim().toLowerCase(Locale.ROOT);
        if (techKey.isEmpty()) {
            callback.onError(new Exception("Assigned technician is missing."));
            return;
        }
        String display = request.assignedTech == null || request.assignedTech.trim().isEmpty()
                ? StaffDirectory.capitalizeContractKey(techKey)
                : request.assignedTech.trim();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Map<String, Object> job = new HashMap<>();
        job.put("AssignedTech", display);
        job.put("AssignedTechKey", techKey);
        job.put("CustomerName", safe(request.customerName));
        job.put("CustomerEmail", safe(request.customerEmail).isEmpty() ? "N/A" : request.customerEmail.trim());
        job.put("CustomerContact", safe(request.customerContact));
        job.put("IssueDetails", safe(request.issueDetails));
        job.put("Address", safe(request.address));
        job.put("CreatedBy", safe(request.createdBy));
        job.put("createdByUid", user == null ? "" : user.getUid());
        job.put("CreatedAt", new Date());
        job.put("JobType", "Service");
        job.put("year", Calendar.getInstance().get(Calendar.YEAR));
        job.put("Status", "active");
        job.put("Accepted", true);
        job.put("visitCount", 0L);
        db.collection(COLLECTION).add(job)
                .addOnSuccessListener(ref -> {
                    callback.onSuccess(ref.getId());
                    AuditLogRepository.jobCreated(ref.getId(), safe(request.customerName));
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Follow up, complete, or reopen. The same visit id is reused on retry so the counter moves once.
     * A report id is stored on the visit when the caller has one. Uploading a report does not call this.
     */
    public void applyVisit(String jobId, String visitId, String action, String notes,
                           String actingName, String actingKey,
                           @Nullable String reportId, @Nullable String storagePath, @Nullable String fileName,
                           DoneCallback callback) {
        if (jobId == null || visitId == null || action == null) {
            callback.onError(new Exception("Missing visit."));
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError(new Exception("Sign in required."));
            return;
        }
        DocumentReference jobRef = db.collection(COLLECTION).document(jobId);
        DocumentReference visitRef = jobRef.collection("visits").document(visitId);
        String linkedReportId = reportId == null ? "" : reportId.trim();
        DocumentReference reportRef = linkedReportId.isEmpty() ? null : jobRef.collection("reports").document(linkedReportId);
        db.runTransaction(transaction -> {
            DocumentSnapshot job = transaction.get(jobRef);
            if (!job.exists()) {
                throw new FirebaseFirestoreException("JOB_MISSING", FirebaseFirestoreException.Code.NOT_FOUND);
            }
            DocumentSnapshot existing = transaction.get(visitRef);
            DocumentSnapshot report = reportRef == null ? null : transaction.get(reportRef);
            if (existing.exists()) {
                return new VisitResult(false, "");
            }
            String customerName = textOr(job.getString("CustomerName"), "");
            long count = number(job.get("visitCount"));
            String rawStatus = job.getString("Status");
            String status = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            boolean completed = "completed".equals(status);
            String jobKey = textOr(job.getString("AssignedTechKey"), "").toLowerCase(Locale.ROOT);
            String techKey = !jobKey.isEmpty() ? jobKey : textOr(actingKey, "").toLowerCase(Locale.ROOT);
            String uid = user.getUid();
            String techName = textOr(actingName, textOr(job.getString("AssignedTech"), techKey));

            if ("reopened".equals(action)) {
                if (!completed) {
                    throw new FirebaseFirestoreException("NOT_COMPLETED", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                Map<String, Object> visit = baseVisit(visitId, "reopened", uid, techKey, techName, count);
                if (job.get("completedAt") != null) visit.put("previousCompletedAt", job.get("completedAt"));
                visit.put("previousCompletedByUid", textOr(job.getString("completedByUid"), ""));
                visit.put("previousCompletedByName", textOr(job.getString("completedByName"), ""));
                visit.put("previousCompletionNotes", textOr(job.getString("completionNotes"), ""));
                transaction.set(visitRef, visit);
                Map<String, Object> updates = new HashMap<>();
                updates.put("Status", "follow_up");
                updates.put("lastVisitId", visitId);
                updates.put("lastVisitAt", FieldValue.serverTimestamp());
                updates.put("completedAt", FieldValue.delete());
                updates.put("completedByUid", FieldValue.delete());
                updates.put("completedByName", FieldValue.delete());
                updates.put("completionNotes", FieldValue.delete());
                transaction.update(jobRef, updates);
                return new VisitResult(true, customerName);
            }

            if (completed) {
                throw new FirebaseFirestoreException("ALREADY_COMPLETED", FirebaseFirestoreException.Code.FAILED_PRECONDITION);
            }
            long next = count + 1;
            Map<String, Object> visit = baseVisit(visitId, action, uid, techKey, techName, next);
            if (report != null && report.exists()) {
                visit.put("reportId", report.getId());
                String path = textOr(storagePath, textOr(report.getString("storagePath"), ""));
                String name = textOr(fileName, textOr(report.getString("fileName"), ""));
                visit.put("storagePath", path);
                visit.put("fileName", name);
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("visitCount", next);
            updates.put("lastVisitId", visitId);
            updates.put("lastVisitAt", FieldValue.serverTimestamp());
            if ("completed".equals(action)) {
                String note = notes == null ? "" : notes.trim();
                visit.put("completionNotes", note);
                updates.put("Status", "completed");
                updates.put("completedAt", FieldValue.serverTimestamp());
                updates.put("completedByUid", uid);
                updates.put("completedByName", techName);
                updates.put("completionNotes", note);
            } else {
                updates.put("Status", "follow_up");
            }
            transaction.set(visitRef, visit);
            transaction.update(jobRef, updates);
            if (report != null && report.exists()) {
                String currentVisit = report.getString("visitId");
                if (currentVisit == null || currentVisit.trim().isEmpty()) {
                    transaction.update(reportRef, "visitId", visitId);
                }
            }
            return new VisitResult(true, customerName);
        }).addOnSuccessListener(result -> {
            callback.onSuccess();
            if (result != null && result.created) {
                AuditLogRepository.jobVisit(action, jobId, result.entityName, visitId);
            }
        }).addOnFailureListener(e -> callback.onError(friendly(e)));
    }

    public void complete(String jobId, String visitId, String notes, String actingName, String actingKey,
                         @Nullable String reportId, @Nullable String storagePath, @Nullable String fileName,
                         DoneCallback callback) {
        applyVisit(jobId, visitId, "completed", notes, actingName, actingKey, reportId, storagePath, fileName, callback);
    }

    public void reassign(String jobId, String contractKey, DoneCallback callback) {
        if (contractKey == null || contractKey.trim().isEmpty()) {
            callback.onError(new Exception("Choose a technician."));
            return;
        }
        String key = contractKey.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> updates = new HashMap<>();
        updates.put("AssignedTechKey", key);
        updates.put("AssignedTech", StaffDirectory.capitalizeContractKey(key));
        db.collection(COLLECTION).document(jobId).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    /** Writes the address only. It does not complete the job or backfill legacy status. */
    public void saveAddress(String jobId, String address, DoneCallback callback) {
        if (jobId == null || jobId.trim().isEmpty()) {
            callback.onError(new Exception("Missing job."));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("Address", address == null ? "" : address.trim());
        db.collection(COLLECTION).document(jobId.trim()).update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void saveFollowUpDate(String jobId, String dateTime, DoneCallback callback) {
        db.collection(COLLECTION).document(jobId)
                .update("FollowUpDate", dateTime == null ? "" : dateTime)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    /**
     * Deletes the job document only, matching the existing Job Work delete.
     * Visits, report metadata, and JobWorkReports files are not removed.
     */
    public void deleteJob(String jobId, DoneCallback callback) {
        if (jobId == null || jobId.trim().isEmpty()) {
            callback.onError(new Exception("Missing job."));
            return;
        }
        db.collection(COLLECTION).document(jobId.trim()).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void saveEmail(String jobId, String email, DoneCallback callback) {
        db.collection(COLLECTION).document(jobId)
                .update("CustomerEmail", email == null ? "" : email.trim())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void listVisits(String jobId, VisitsCallback callback) {
        db.collection(COLLECTION).document(jobId).collection("visits").get()
                .addOnSuccessListener(snap -> {
                    List<VisitRecord> visits = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Object when = doc.get("visitDate");
                        if (when == null) when = doc.get("createdAt");
                        String notes = doc.getString("completionNotes");
                        if (notes == null || notes.isEmpty()) notes = doc.getString("previousCompletionNotes");
                        visits.add(new VisitRecord(
                                doc.getId(),
                                textOr(doc.getString("action"), ""),
                                textOr(doc.getString("techName"), ""),
                                textOr(doc.getString("storagePath"), ""),
                                textOr(doc.getString("fileName"), ""),
                                notes == null ? "" : notes,
                                when
                        ));
                    }
                    visits.sort((a, b) -> Long.compare(millis(b.when), millis(a.when)));
                    callback.onResult(visits, null);
                })
                .addOnFailureListener(e -> callback.onResult(Collections.emptyList(), e));
    }

    public void listReports(String jobId, ReportsCallback callback) {
        db.collection(COLLECTION).document(jobId).collection("reports").get()
                .addOnSuccessListener(snap -> {
                    List<ReportLink> reports = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        reports.add(new ReportLink(
                                doc.getId(),
                                textOr(doc.getString("storagePath"), ""),
                                textOr(doc.getString("fileName"), ""),
                                textOr(doc.getString("visitId"), ""),
                                doc.get("createdAt"),
                                number(doc.get("reportYear"))
                        ));
                    }
                    reports.sort((a, b) -> Long.compare(millis(b.createdAt), millis(a.createdAt)));
                    callback.onResult(reports, null);
                })
                .addOnFailureListener(e -> callback.onResult(Collections.emptyList(), e));
    }

    public void linkReport(String jobId, String storagePath, String fileName, int reportYear,
                           String createdByName, DoneCallback callback) {
        if (jobId == null || jobId.trim().isEmpty() || storagePath == null || storagePath.trim().isEmpty()) {
            if (callback != null) callback.onError(new Exception("Missing report link."));
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        DocumentReference reportRef = db.collection(COLLECTION).document(jobId.trim()).collection("reports").document();
        Map<String, Object> report = new HashMap<>();
        report.put("reportId", reportRef.getId());
        report.put("jobId", jobId.trim());
        report.put("storagePath", storagePath.trim());
        report.put("fileName", fileName == null ? "" : fileName.trim());
        report.put("createdAt", FieldValue.serverTimestamp());
        report.put("reportYear", reportYear);
        report.put("createdByUid", user == null ? "" : user.getUid());
        report.put("createdByName", createdByName == null ? "" : createdByName.trim());
        report.put("visitId", "");
        reportRef.set(report)
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onError(e);
                });
    }

    private static void deliver(com.google.firebase.firestore.QuerySnapshot snapshots, Exception error,
                                JobsCallback callback) {
        if (error != null) {
            callback.onResult(Collections.emptyList(), error);
            return;
        }
        List<JobRecord> jobs = new ArrayList<>();
        if (snapshots != null) {
            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                jobs.add(JobRecord.from(doc));
            }
        }
        jobs.sort((a, b) -> Long.compare(millis(b.data.get("CreatedAt")), millis(a.data.get("CreatedAt"))));
        callback.onResult(jobs, null);
    }

    private static void publish(Map<String, JobRecord> keyed, Map<String, JobRecord> legacy, JobsCallback callback) {
        Map<String, JobRecord> merged = new LinkedHashMap<>();
        merged.putAll(legacy);
        merged.putAll(keyed);
        List<JobRecord> jobs = new ArrayList<>(merged.values());
        jobs.sort((a, b) -> Long.compare(millis(b.data.get("CreatedAt")), millis(a.data.get("CreatedAt"))));
        callback.onResult(jobs, null);
    }

    private static Map<String, Object> baseVisit(String visitId, String action, String uid,
                                                 String techKey, String techName, long sequence) {
        Map<String, Object> visit = new HashMap<>();
        visit.put("visitId", visitId);
        visit.put("action", action);
        visit.put("sequence", sequence);
        visit.put("visitDate", FieldValue.serverTimestamp());
        visit.put("createdAt", FieldValue.serverTimestamp());
        visit.put("techUid", uid == null ? "" : uid);
        visit.put("techKey", techKey == null ? "" : techKey);
        visit.put("techName", techName == null ? "" : techName);
        visit.put("reportId", "");
        visit.put("storagePath", "");
        visit.put("fileName", "");
        return visit;
    }

    private static Exception friendly(Exception error) {
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("ALREADY_COMPLETED")) return new Exception("This job is already complete.");
        if (message.contains("NOT_COMPLETED")) return new Exception("Only a completed job can be reopened.");
        if (message.contains("JOB_MISSING")) return new Exception("Service job not found.");
        return error == null ? new Exception("Could not save the service job.") : error;
    }

    private static long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        return 0L;
    }

    private static long millis(Object value) {
        if (value instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) value).toDate().getTime();
        }
        if (value instanceof Date) return ((Date) value).getTime();
        return 0L;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? (fallback == null ? "" : fallback) : value.trim();
    }

    private static final class VisitResult {
        final boolean created;
        final String entityName;

        VisitResult(boolean created, String entityName) {
            this.created = created;
            this.entityName = entityName == null ? "" : entityName;
        }
    }
}
