package com.grpc.grpc.core;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

/**
 * Updates storage_metadata/summary in Firestore for upload/download metrics.
 * Only admin users can write (Firestore rules); for tech uploads a Cloud Function is needed to count.
 */
public final class StorageMetricsHelper {

    private static final String COLLECTION = "storage_metadata";
    private static final String DOC_SUMMARY = "summary";

    private StorageMetricsHelper() {}

    /** Call after a successful report/file upload. Increments uploadsToday, uploadsThisWeek, uploadsThisMonth. */
    public static void recordUpload() {
        try {
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("uploadsToday", FieldValue.increment(1));
            updates.put("uploadsThisWeek", FieldValue.increment(1));
            updates.put("uploadsThisMonth", FieldValue.increment(1));
            FirebaseFirestore.getInstance()
                    .collection(COLLECTION)
                    .document(DOC_SUMMARY)
                    .set(updates, SetOptions.merge());
        } catch (Exception ignored) {
            // Rules may deny if not admin; Cloud Function can count all uploads
        }
    }

    /** Call when a user views/downloads a file from Storage. Increments downloadsToday, etc. */
    public static void recordDownload() {
        try {
            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("downloadsToday", FieldValue.increment(1));
            updates.put("downloadsThisWeek", FieldValue.increment(1));
            updates.put("downloadsThisMonth", FieldValue.increment(1));
            FirebaseFirestore.getInstance()
                    .collection(COLLECTION)
                    .document(DOC_SUMMARY)
                    .set(updates, SetOptions.merge());
        } catch (Exception ignored) {
        }
    }
}
