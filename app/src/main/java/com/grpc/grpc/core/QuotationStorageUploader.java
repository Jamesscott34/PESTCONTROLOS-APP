package com.grpc.grpc.core;

import android.net.Uri;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Uploads quotation PDFs into Firebase Storage root folder: quotations/
 * and auto-renames on collision to avoid overwrite.
 */
public final class QuotationStorageUploader {

    public interface Callback {
        void run();
    }

    public interface ErrorCallback {
        void onError(Exception e);
    }

    private static final String ROOT_FOLDER = "quotations";

    private QuotationStorageUploader() {
    }

    public static void uploadQuotationPdf(
            File pdfFile,
            Callback onSuccess,
            ErrorCallback onFailure
    ) {
        if (pdfFile == null || !pdfFile.exists()) {
            if (onFailure != null) onFailure.onError(new IllegalStateException("Quotation PDF not found"));
            return;
        }

        StorageReference storageRoot = FirebaseStorage.getInstance().getReference();
        StorageReference quotationsFolderRef = storageRoot.child(ROOT_FOLDER);
        String originalName = pdfFile.getName();

        quotationsFolderRef.listAll().addOnSuccessListener(listResult -> {
            Set<String> existingNames = new HashSet<>();
            for (StorageReference item : listResult.getItems()) {
                if (item != null && item.getName() != null) {
                    existingNames.add(item.getName());
                }
            }

            String uniqueName = originalName;
            if (existingNames.contains(uniqueName)) {
                int dot = originalName.lastIndexOf('.');
                String base = dot > 0 ? originalName.substring(0, dot) : originalName;
                String ext = dot > 0 ? originalName.substring(dot) : "";
                uniqueName = base + "_" + System.currentTimeMillis() + ext;
            }

            String storagePath = ROOT_FOLDER + "/" + uniqueName;
            storageRoot.child(storagePath).putFile(Uri.fromFile(pdfFile))
                    .addOnSuccessListener(taskSnapshot -> {
                        StorageMetricsHelper.recordUpload();
                        if (onSuccess != null) onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        if (onFailure != null) onFailure.onError(e);
                    });
        }).addOnFailureListener(e -> {
            // Fallback: attempt direct upload with original name.
            String storagePath = ROOT_FOLDER + "/" + originalName;
            storageRoot.child(storagePath).putFile(Uri.fromFile(pdfFile))
                    .addOnSuccessListener(taskSnapshot -> {
                        StorageMetricsHelper.recordUpload();
                        if (onSuccess != null) onSuccess.run();
                    })
                    .addOnFailureListener(err -> {
                        if (onFailure != null) onFailure.onError(err);
                    });
        });
    }
}
