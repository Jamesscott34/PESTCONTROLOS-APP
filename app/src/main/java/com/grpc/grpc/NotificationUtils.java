package com.grpc.grpc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NotificationUtils {

    private static final String CHANNEL_ID = "grpc_notifications";

    public static void showNotification(Context context, String title, String message) {
        // In-app only notifications: system notifications are disabled.
        // Keep method for backward compatibility if older code still calls it.
    }

    /**
     * Write an in-app notification record to Firestore:
     * notifications/{user}/items/{docId}
     *
     * This is NOT an Android system notification.
     */
    public static void writeInAppNotification(String recipientUser,
                                              String docId,
                                              String title,
                                              String body,
                                              String type,
                                              Map<String, Object> data) {
        if (recipientUser == null) return;
        String userKey = recipientUser.trim().toLowerCase(Locale.getDefault());
        if (userKey.isEmpty()) return;

        String safeDocId = (docId == null || docId.trim().isEmpty())
                ? ("notif_" + System.currentTimeMillis())
                : docId.trim();

        Map<String, Object> notif = new HashMap<>();
        notif.put("title", title != null ? title : "Notification");
        notif.put("body", body != null ? body : "");
        notif.put("type", type != null ? type : "general");
        notif.put("data", data != null ? data : new HashMap<>());
        notif.put("timestamp", FieldValue.serverTimestamp());
        notif.put("read", false);

        FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(userKey)
                .collection("items")
                .document(safeDocId)
                .set(notif);
    }
}
