package com.grpc.grpc.stock.model;

/**
 * A person shown on the admin stock user list.
 * {@link #uid} is kept for queries. {@link #name} is the only label shown in the UI.
 */
public class StockUserSummary {

    public final String uid;
    public final String name;
    public int pendingCount;

    public StockUserSummary(String uid, String name, int pendingCount) {
        this.uid = uid != null ? uid : "";
        this.name = name != null && !name.trim().isEmpty() ? name.trim() : "Unnamed";
        this.pendingCount = Math.max(0, pendingCount);
    }
}
