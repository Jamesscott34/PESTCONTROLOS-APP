package com.grpc.grpc.contracts.data;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.audit.data.AuditLogRepository;
import com.grpc.grpc.contracts.model.ContractAsset;
import com.grpc.grpc.contracts.util.StandardContractAssets;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the single asset summary for a contract.
 * Path: contracts/{contractId}/assets/summary
 */
public final class ContractAssetsRepository {

    public interface LoadCallback {
        void onResult(boolean exists, List<ContractAsset> items, @Nullable Exception error);
    }

    public interface SaveCallback {
        void onResult(boolean success, @Nullable Exception error);
    }

    public interface AccessCallback {
        void onResult(boolean allowed, @Nullable String contractName, @Nullable Exception error);
    }

    private ContractAssetsRepository() {}

    public static boolean canAccess(Context context, @Nullable String assignedTech) {
        if (SessionManager.isAdmin(context)) return true;
        String key = SessionManager.getContractKey(context);
        if (key == null || assignedTech == null) return false;
        String mine = key.trim();
        String owner = assignedTech.trim();
        return !mine.isEmpty() && mine.equalsIgnoreCase(owner);
    }

    public static void checkAccess(String contractId, Context context, AccessCallback callback) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(contractId)) {
            callback.onResult(false, null, new Exception("unavailable"));
            return;
        }
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACTS)
                .document(contractId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        callback.onResult(false, null, new Exception("missing"));
                        return;
                    }
                    String assigned = doc.getString("assignedTech");
                    String name = doc.getString("name");
                    callback.onResult(canAccess(context, assigned), name, null);
                })
                .addOnFailureListener(e -> callback.onResult(false, null, e));
    }

    public static void load(String contractId, LoadCallback callback) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(contractId)) {
            callback.onResult(false, new ArrayList<>(), new Exception("unavailable"));
            return;
        }
        summaryRef(contractId).get()
                .addOnSuccessListener(doc -> callback.onResult(doc != null && doc.exists(), readItems(doc), null))
                .addOnFailureListener(e -> callback.onResult(false, new ArrayList<>(), e));
    }

    public static void save(Context context, String contractId, String contractName,
                            List<ContractAsset> items, SaveCallback callback) {
        if (BuildConfig.IS_OFFLINE || TextUtils.isEmpty(contractId)) {
            callback.onResult(false, new Exception("unavailable"));
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getUid())) {
            callback.onResult(false, new Exception("signed-out"));
            return;
        }
        String uid = user.getUid();
        String actor = SessionManager.getName(context);
        if (actor == null || actor.trim().isEmpty()) actor = "Staff";
        final String actorName = actor.trim();
        List<Map<String, Object>> payload = toMaps(items);
        DocumentReference ref = summaryRef(contractId);

        ref.get().addOnSuccessListener(snap -> {
            Map<String, Object> data = new HashMap<>();
            data.put("items", payload);
            data.put("updatedAt", FieldValue.serverTimestamp());
            data.put("updatedByUid", uid);
            data.put("updatedByName", actorName);
            if (snap != null && snap.exists() && snap.get("createdAt") != null) {
                data.put("createdAt", snap.get("createdAt"));
                String createdByUid = text(snap, "createdByUid");
                String createdByName = text(snap, "createdByName");
                data.put("createdByUid", createdByUid.isEmpty() ? uid : createdByUid);
                data.put("createdByName", createdByName.isEmpty() ? actorName : createdByName);
            } else {
                data.put("createdAt", FieldValue.serverTimestamp());
                data.put("createdByUid", uid);
                data.put("createdByName", actorName);
            }
            ref.set(data)
                    .addOnSuccessListener(unused -> {
                        AuditLogRepository.contractAssetsUpdated(contractId, contractName);
                        callback.onResult(true, null);
                    })
                    .addOnFailureListener(e -> callback.onResult(false, e));
        }).addOnFailureListener(e -> callback.onResult(false, e));
    }

    private static DocumentReference summaryRef(String contractId) {
        return FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACTS)
                .document(contractId)
                .collection(FirestorePaths.CONTRACT_ASSETS)
                .document(FirestorePaths.CONTRACT_ASSETS_SUMMARY);
    }

    private static List<Map<String, Object>> toMaps(List<ContractAsset> items) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (items == null) return maps;
        for (ContractAsset item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", item.name);
            row.put("quantity", (long) item.quantity);
            row.put("isCustom", item.custom);
            maps.add(row);
        }
        return maps;
    }

    private static List<ContractAsset> readItems(@Nullable DocumentSnapshot doc) {
        List<ContractAsset> items = new ArrayList<>();
        if (doc == null || !doc.exists()) return items;
        Object raw = doc.get("items");
        if (!(raw instanceof List)) return items;
        for (Object entry : (List<?>) raw) {
            if (!(entry instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) entry;
            Object nameObj = map.get("name");
            Object qtyObj = map.get("quantity");
            Object customObj = map.get("isCustom");
            if (!(nameObj instanceof String) || !(qtyObj instanceof Number)) continue;
            String name = StandardContractAssets.cleanName((String) nameObj);
            int quantity = ((Number) qtyObj).intValue();
            if (name.isEmpty() || quantity < 0) continue;
            boolean custom = Boolean.TRUE.equals(customObj);
            String canonical = StandardContractAssets.canonical(name);
            if (canonical != null) {
                items.add(new ContractAsset(canonical, quantity, false));
            } else {
                items.add(new ContractAsset(name, quantity, custom));
            }
        }
        return items;
    }

    private static String text(DocumentSnapshot snap, String key) {
        String value = snap.getString(key);
        return value == null ? "" : value;
    }
}
