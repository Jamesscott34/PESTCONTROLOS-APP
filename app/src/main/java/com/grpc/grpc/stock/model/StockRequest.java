package com.grpc.grpc.stock.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A stock request stored at stock_requests/{requestId}.
 * Status values are pending, ready, and retrieved.
 */
public class StockRequest {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_RETRIEVED = "retrieved";

    public final String id;
    public final String requesterUid;
    public final String requesterName;
    public final String weekKey;
    public final String status;
    public final long createdAtMillis;
    public final String notes;
    public final List<StockRequestItem> items;
    public final long approvedAtMillis;
    public final String approvedByUid;
    public final String approvedByName;
    public final long retrievedAtMillis;
    public final String retrievedByUid;
    public final String retrievedByName;

    public StockRequest(String id,
                        String requesterUid,
                        String requesterName,
                        String weekKey,
                        String status,
                        long createdAtMillis,
                        String notes,
                        List<StockRequestItem> items,
                        long approvedAtMillis,
                        String approvedByUid,
                        String approvedByName,
                        long retrievedAtMillis,
                        String retrievedByUid,
                        String retrievedByName) {
        this.id = id != null ? id : "";
        this.requesterUid = requesterUid != null ? requesterUid : "";
        this.requesterName = requesterName != null ? requesterName : "";
        this.weekKey = weekKey != null ? weekKey : "";
        this.status = status != null ? status : "";
        this.createdAtMillis = createdAtMillis;
        this.notes = notes != null ? notes : "";
        this.items = items != null ? items : new ArrayList<>();
        this.approvedAtMillis = approvedAtMillis;
        this.approvedByUid = approvedByUid != null ? approvedByUid : "";
        this.approvedByName = approvedByName != null ? approvedByName : "";
        this.retrievedAtMillis = retrievedAtMillis;
        this.retrievedByUid = retrievedByUid != null ? retrievedByUid : "";
        this.retrievedByName = retrievedByName != null ? retrievedByName : "";
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isReady() {
        return STATUS_READY.equals(status);
    }

    public boolean isRetrieved() {
        return STATUS_RETRIEVED.equals(status);
    }

    @NonNull
    public String displayName() {
        if (!requesterName.trim().isEmpty()) return requesterName.trim();
        return "Unnamed";
    }

    @NonNull
    private static String quantityText(@Nullable Object raw) {
        if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            if (value == Math.rint(value)) return String.valueOf(((Number) raw).longValue());
            return String.valueOf(raw);
        }
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    @Nullable
    public static StockRequest fromSnapshot(@Nullable DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        List<StockRequestItem> items = new ArrayList<>();
        Object rawItems = doc.get("items");
        if (rawItems instanceof List) {
            for (Object entry : (List<?>) rawItems) {
                if (!(entry instanceof Map)) continue;
                Map<?, ?> map = (Map<?, ?>) entry;
                String name = map.get("productName") != null ? String.valueOf(map.get("productName")).trim() : "";
                String quantity = quantityText(map.get("quantity"));
                String type = map.get("productType") != null ? String.valueOf(map.get("productType")) : "custom";
                items.add(new StockRequestItem(name, quantity, type));
            }
        }
        String notes = doc.getString("notes");
        return new StockRequest(
                doc.getId(),
                doc.getString("requesterUid"),
                doc.getString("requesterName"),
                doc.getString("weekKey"),
                doc.getString("status"),
                millis(doc, "createdAt"),
                notes != null ? notes : "",
                items,
                millis(doc, "approvedAt"),
                doc.getString("approvedByUid"),
                doc.getString("approvedByName"),
                millis(doc, "retrievedAt"),
                doc.getString("retrievedByUid"),
                doc.getString("retrievedByName")
        );
    }

    private static long millis(DocumentSnapshot doc, String field) {
        try {
            Timestamp ts = doc.getTimestamp(field);
            if (ts != null) return ts.toDate().getTime();
        } catch (Exception ignored) {
        }
        return 0L;
    }
}
