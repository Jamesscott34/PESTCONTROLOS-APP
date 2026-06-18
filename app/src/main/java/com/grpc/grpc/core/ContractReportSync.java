package com.grpc.grpc.core;

import android.util.Log;

import com.grpc.grpc.BuildConfig;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes contract-linked report metadata so reports can be browsed by contract
 * without depending on legacy Storage folder scanning.
 */
public final class ContractReportSync {
    private static final String TAG = "ContractReportSync";
    private static final String REPORTS_SUBCOLLECTION = "reports";
    private static final String CONTRACT_STORAGE_ROOT = "contracts";

    private ContractReportSync() {
    }

    public interface Callback {
        void run();
    }

    public interface ErrorCallback {
        void onError(Exception error);
    }

    public static boolean useContractReportsOnly() {
        return !BuildConfig.IS_OFFLINE && !"grpc".equalsIgnoreCase(BuildConfig.FLAVOR);
    }

    public static boolean hasContractId(String contractId) {
        return contractId != null && !contractId.trim().isEmpty();
    }

    /**
     * Canonical storage path segment: {@code contracts/{contractDocumentId}}.
     * Keep the Firestore document id here so paths stay stable; use
     * {@link ContractStorageDisplayHelper} in UI to show company name.
     */
    public static String buildContractStorageFolder(String contractId) {
        return CONTRACT_STORAGE_ROOT + "/" + contractId.trim();
    }

    public static void syncMetadata(
            String contractId,
            String storagePath,
            String fileName,
            String reportType,
            String companyName,
            Callback onSuccess,
            ErrorCallback onFailure
    ) {
        if (!hasContractId(contractId)) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        String trimmedContractId = contractId.trim();
        String trimmedStoragePath = storagePath != null ? storagePath.trim() : "";
        String trimmedFileName = fileName != null ? fileName.trim() : "";
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        Map<String, Object> data = new HashMap<>();
        data.put("contractId", trimmedContractId);
        data.put("fileName", trimmedFileName);
        data.put("storagePath", trimmedStoragePath);
        data.put("reportType", reportType != null ? reportType : "report");
        data.put("companyName", companyName != null ? companyName.trim() : "");
        data.put("uploadedBy", uid);
        data.put("storageMode", trimmedStoragePath.startsWith(CONTRACT_STORAGE_ROOT + "/")
                ? "contracts"
                : "legacy");
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACT_REPORTS)
                .document(trimmedContractId)
                .collection(REPORTS_SUBCOLLECTION)
                .document(buildReportId(trimmedStoragePath, trimmedFileName))
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to sync contract report metadata", e);
                    if (onFailure != null) {
                        onFailure.onError(e);
                    }
                });
    }

    private static String buildReportId(String storagePath, String fileName) {
        String base = storagePath != null && !storagePath.isEmpty() ? storagePath : fileName;
        if (base == null || base.trim().isEmpty()) {
            base = String.valueOf(System.currentTimeMillis());
        }
        return base.trim().replace("/", "_");
    }
}
