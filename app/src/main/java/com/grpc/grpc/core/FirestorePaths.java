package com.grpc.grpc.core;

/**
 * Central Firestore collection/document path constants.
 *
 * Using a single source of truth makes it easier to migrate schemas
 * (e.g. from per-tech collections to UID-scoped collections) without
 * scattering string literals across the app.
 */
public final class FirestorePaths {

    private FirestorePaths() {
        // no instances
    }

    /** Users collection: stores profile + permissions keyed by Firebase Auth UID. */
    public static final String USERS = "users";

    /** Contracts collection: contracts/{contractId}. */
    public static final String CONTRACTS = "contracts";

    /** Jobwork collection: jobwork/{jobId}. */
    public static final String JOBWORK = "jobwork";

    /** WorkView events collection: workview/{eventId}. */
    public static final String WORKVIEW = "workview";

    /** Root notifications collection: notifications/{recipientUid}/items/{notifId}. */
    public static final String NOTIFICATIONS = "notifications";

    /** Conversations collection: conversations/{conversationId}. */
    public static final String CONVERSATIONS = "conversations";

    /** Messages subcollection under a conversation: conversations/{id}/messages/{messageId}. */
    public static final String MESSAGES_SUBCOLLECTION = "messages";

    /** Bug reports and feature requests (admin submit; super_admin view and set cost/days). */
    public static final String BUG_REPORT_FEATURE_REQUEST = "bugreport_featurerequest";

    /** Contract reminders: user wants 12h in-app reminder until unchecked. Doc id: userId_contractId. */
    public static final String CONTRACT_REMINDERS = "contract_reminders";

    /** Contract quotations: PDF quotes (4/6/8/12pt) saved to cloud. */
    public static final String CONTRACT_QUOTATIONS = "contract_quotations";

    /** Contract-linked report metadata: contractreports/{contractId}/reports/{reportId}. */
    public static final String CONTRACT_REPORTS = "contractreports";

    /** Platform invoice metadata (PDFs live under Storage companies/{companyId}/invoices/). */
    public static final String INVOICE_LEDGER = "invoice_ledger";

    /** Per-tenant sequence for invoice numbers: invoice_counters/{companyId}. */
    public static final String INVOICE_COUNTERS = "invoice_counters";
}

