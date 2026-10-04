package com.grpc.grpc.stock.model;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * One technician's response for an ISO week.
 * Kept after a related stock request is deleted so the week stays complete.
 */
public class StockWeeklyCheck {

    public static final String RESPONSE_REQUESTED = "requested";
    public static final String RESPONSE_NOT_NEEDED = "not_needed";

    public final String id;
    public final String userUid;
    public final String userName;
    public final String weekKey;
    public final String response;
    public final long createdAtMillis;
    public final String requestId;

    public StockWeeklyCheck(String id,
                            String userUid,
                            String userName,
                            String weekKey,
                            String response,
                            long createdAtMillis,
                            String requestId) {
        this.id = id != null ? id : "";
        this.userUid = userUid != null ? userUid : "";
        this.userName = userName != null ? userName : "";
        this.weekKey = weekKey != null ? weekKey : "";
        this.response = response != null ? response : "";
        this.createdAtMillis = createdAtMillis;
        this.requestId = requestId != null ? requestId : "";
    }

    public boolean isRequested() {
        return RESPONSE_REQUESTED.equals(response);
    }

    public boolean isNotNeeded() {
        return RESPONSE_NOT_NEEDED.equals(response);
    }

    @Nullable
    public static StockWeeklyCheck fromSnapshot(@Nullable DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        long created = 0L;
        try {
            Timestamp ts = doc.getTimestamp("createdAt");
            if (ts != null) created = ts.toDate().getTime();
        } catch (Exception ignored) {
        }
        String requestId = doc.getString("requestId");
        return new StockWeeklyCheck(
                doc.getId(),
                doc.getString("userUid"),
                doc.getString("userName"),
                doc.getString("weekKey"),
                doc.getString("response"),
                created,
                requestId
        );
    }
}
