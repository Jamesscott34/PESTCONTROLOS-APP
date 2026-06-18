package com.grpc.grpc.core;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves Firebase Storage contract folder segments ({@code contracts/{contractDocId}})
 * to human-readable company names from Firestore {@code contracts} documents.
 * Storage paths remain ID-based; only labels change in the UI.
 */
public final class ContractStorageDisplayHelper {

    private ContractStorageDisplayHelper() {}

    public interface NamesCallback {
        void onResult(Map<String, String> idToCompanyName);
    }

    @Nullable
    public static String companyNameFromSnapshot(@Nullable DocumentSnapshot snap) {
        if (snap == null || !snap.exists()) return null;
        String[] keys = {"name", "Name", "companyName", "Company", "CustomerName"};
        for (String k : keys) {
            Object v = snap.get(k);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"N/A".equalsIgnoreCase(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Loads display labels for contract document ids (parallel reads).
     */
    public static void loadContractFolderLabels(
            FirebaseFirestore db,
            List<String> contractIds,
            NamesCallback cb
    ) {
        if (db == null || contractIds == null || contractIds.isEmpty()) {
            cb.onResult(Collections.emptyMap());
            return;
        }
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String id : contractIds) {
            if (id == null || id.trim().isEmpty()) continue;
            tasks.add(db.collection(FirestorePaths.CONTRACTS).document(id.trim()).get());
        }
        if (tasks.isEmpty()) {
            cb.onResult(Collections.emptyMap());
            return;
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
            Map<String, String> out = new HashMap<>();
            for (Task<DocumentSnapshot> task : tasks) {
                if (!task.isSuccessful()) continue;
                DocumentSnapshot doc = task.getResult();
                if (doc == null) continue;
                String label = companyNameFromSnapshot(doc);
                if (label != null) {
                    out.put(doc.getId(), label);
                }
            }
            cb.onResult(out);
        });
    }
}
