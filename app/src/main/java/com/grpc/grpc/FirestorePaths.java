package com.grpc.grpc;

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
}

