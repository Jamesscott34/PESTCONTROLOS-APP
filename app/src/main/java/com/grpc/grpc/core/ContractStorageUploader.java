package com.grpc.grpc.core;

import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;

/**
 * Uploads locally generated PDFs into the contract-linked storage folder and
 * syncs the matching metadata document.
 */
public final class ContractStorageUploader {

    private ContractStorageUploader() {
    }

    public static boolean shouldAutoUpload(String contractId) {
        return ContractReportSync.hasContractId(contractId)
                && FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    public static void uploadContractReport(
            File pdfFile,
            String contractId,
            String reportType,
            String companyName,
            ContractReportSync.Callback onSuccess,
            ContractReportSync.ErrorCallback onFailure
    ) {
        if (pdfFile == null || !pdfFile.exists()) {
            if (onFailure != null) {
                onFailure.onError(new IllegalStateException("PDF file not found"));
            }
            return;
        }

        if (!shouldAutoUpload(contractId)) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        String folderPath = ContractReportSync.buildContractStorageFolder(contractId);
        String storagePath = folderPath + "/" + pdfFile.getName();
        StorageReference fileRef = FirebaseStorage.getInstance().getReference().child(storagePath);

        UploadTask uploadTask = fileRef.putFile(Uri.fromFile(pdfFile));
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            StorageMetricsHelper.recordUpload();
            ContractReportSync.syncMetadata(
                    contractId,
                    storagePath,
                    pdfFile.getName(),
                    reportType,
                    companyName,
                    onSuccess,
                    onFailure
            );
        }).addOnFailureListener(e -> {
            if (onFailure != null) {
                onFailure.onError(e);
            }
        });
    }
}
