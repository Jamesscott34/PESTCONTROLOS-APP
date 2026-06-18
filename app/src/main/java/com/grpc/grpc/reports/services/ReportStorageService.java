package com.grpc.grpc.reports.services;

import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.core.ContractReportSync;
import com.grpc.grpc.core.ContractStorageUploader;
import com.grpc.grpc.core.StorageMetricsHelper;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

/**
 * Shared upload path for generated PDFs that need Android/CRM visibility.
 */
public final class ReportStorageService {
    private ReportStorageService() {}

    public static void uploadGeneratedPdf(
            File pdfFile,
            String contractId,
            String reportType,
            String companyName,
            ContractReportSync.Callback onSuccess,
            ContractReportSync.ErrorCallback onFailure
    ) {
        if (pdfFile == null || !pdfFile.exists()) {
            if (onFailure != null) onFailure.onError(new IllegalStateException("PDF file not found"));
            return;
        }

        if (contractId != null && !contractId.trim().isEmpty()) {
            ContractStorageUploader.uploadContractReport(
                    pdfFile,
                    contractId,
                    reportType,
                    companyName,
                    onSuccess,
                    onFailure
            );
            return;
        }

        if (BuildConfig.IS_OFFLINE || FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (onSuccess != null) onSuccess.run();
            return;
        }

        String folder = currentReportsFolder();
        String storagePath = folder + "/" + pdfFile.getName();
        StorageReference fileRef = FirebaseStorage.getInstance().getReference().child(storagePath);
        fileRef.putFile(Uri.fromFile(pdfFile))
                .addOnSuccessListener(snapshot -> {
                    StorageMetricsHelper.recordUpload();
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    if (onFailure != null) onFailure.onError(e);
                });
    }

    private static String currentReportsFolder() {
        int year = Calendar.getInstance(Locale.UK).get(Calendar.YEAR) % 100;
        return "Reports" + String.format(Locale.UK, "%02d", year);
    }
}
