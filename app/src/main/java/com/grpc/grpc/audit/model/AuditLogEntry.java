package com.grpc.grpc.audit.model;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One completed piece of work. Display uses names and filenames, not Firebase ids.
 */
public class AuditLogEntry {

    public final String id;
    public final String actorUid;
    public final String actorName;
    public final String actorRole;
    public final String eventType;
    public final String eventLabel;
    public final String module;
    public final String entityId;
    public final String entityName;
    public final String fileName;
    public final String jobId;
    public final String jobRef;
    public final String contractId;
    public final String companyId;
    public final String reportId;
    public final String managementJobId;
    @Nullable
    public final Date createdAt;

    public AuditLogEntry(String id, String actorUid, String actorName, String actorRole,
                         String eventType, String eventLabel, String module,
                         String entityId, String entityName, String fileName,
                         String jobId, String jobRef, String contractId, String companyId,
                         String reportId, String managementJobId, @Nullable Date createdAt) {
        this.id = id == null ? "" : id;
        this.actorUid = actorUid == null ? "" : actorUid;
        this.actorName = actorName == null ? "" : actorName;
        this.actorRole = actorRole == null ? "" : actorRole;
        this.eventType = eventType == null ? "" : eventType;
        this.eventLabel = eventLabel == null ? "" : eventLabel;
        this.module = module == null ? "" : module;
        this.entityId = entityId == null ? "" : entityId;
        this.entityName = entityName == null ? "" : entityName;
        this.fileName = fileName == null ? "" : fileName;
        this.jobId = jobId == null ? "" : jobId;
        this.jobRef = jobRef == null ? "" : jobRef;
        this.contractId = contractId == null ? "" : contractId;
        this.companyId = companyId == null ? "" : companyId;
        this.reportId = reportId == null ? "" : reportId;
        this.managementJobId = managementJobId == null ? "" : managementJobId;
        this.createdAt = createdAt;
    }

    public static AuditLogEntry from(DocumentSnapshot doc) {
        Date when = null;
        Object raw = doc.get("createdAt");
        if (raw instanceof Timestamp) {
            when = ((Timestamp) raw).toDate();
        } else if (raw instanceof Date) {
            when = (Date) raw;
        }
        return new AuditLogEntry(
                doc.getId(),
                text(doc, "actorUid"),
                text(doc, "actorName"),
                text(doc, "actorRole"),
                text(doc, "eventType"),
                text(doc, "eventLabel"),
                text(doc, "module"),
                text(doc, "entityId"),
                text(doc, "entityName"),
                text(doc, "fileName"),
                text(doc, "jobId"),
                text(doc, "jobRef"),
                text(doc, "contractId"),
                text(doc, "companyId"),
                text(doc, "reportId"),
                text(doc, "managementJobId"),
                when
        );
    }

    /** What the list should show under the action. Filename wins when the work produced a file. */
    public String displayDetail() {
        if (!fileName.isEmpty()) return fileName;
        if (!entityName.isEmpty()) return entityName;
        if (!jobRef.isEmpty()) return jobRef;
        return "";
    }

    public String displayName() {
        return actorName.isEmpty() ? "Staff" : actorName;
    }

    public String displayAction() {
        if (!eventLabel.isEmpty()) return eventLabel.toUpperCase(Locale.UK);
        return eventType.replace('_', ' ').toUpperCase(Locale.UK);
    }

    public String displayDate() {
        if (createdAt == null) return "";
        return format("dd/MM/yyyy");
    }

    public String displayTime() {
        if (createdAt == null) return "";
        return format("HH:mm");
    }

    private String format(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.UK);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Dublin"));
        return format.format(createdAt);
    }

    private static String text(DocumentSnapshot doc, String key) {
        String value = doc.getString(key);
        return value == null ? "" : value.trim();
    }
}
