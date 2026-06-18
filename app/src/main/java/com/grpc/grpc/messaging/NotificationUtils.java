package com.grpc.grpc.messaging;

import com.grpc.grpc.core.*;

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
     * notifications/{staffId}/items/{docId}
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
        String userKey = resolveNotificationRecipientKey(recipientUser);
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

        // Admins should receive notifications for key business events across the app.
        // Fan-out uses resolved keys so the same user is not written twice (creator vs admin).
        if (shouldFanOutToAdmins(type)) {
            try {
                for (String adminId : StaffDirectory.getCachedAdminStaffIds()) {
                    if (adminId == null) continue;
                    String resolvedAdminKey = resolveNotificationRecipientKey(adminId.trim());
                    if (resolvedAdminKey.isEmpty() || resolvedAdminKey.equals(userKey)) continue;
                    FirebaseFirestore.getInstance()
                            .collection("notifications")
                            .document(resolvedAdminKey)
                            .collection("items")
                            .document(safeDocId + "_a" + resolvedAdminKey)
                            .set(notif);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Resolve a user-ish string into notification recipient key (authUid).
     * - If already looks like Firebase UID (long alphanumeric), return as-is.
     * - Else ContractKey/name -> authUid via StaffDirectory cache.
     */
    public static String resolveNotificationRecipientKey(String recipientUser) {
        if (recipientUser == null) return "";
        String raw = recipientUser.trim();
        if (raw.isEmpty()) return "";

        // Already authUid (Firebase UIDs are long and alphanumeric)
        if (raw.length() >= 15 && !raw.matches("\\d{1,3}")) return raw;

        String first = raw;
        int sp = raw.indexOf(' ');
        if (sp > 0) first = raw.substring(0, sp);

        String id = StaffDirectory.getStaffIdForContractKey(first);
        if (id == null) id = StaffDirectory.getUserId(first);
        if (id != null && !id.trim().isEmpty()) return id.trim();

        return raw.toLowerCase(Locale.getDefault());
    }

    private static boolean shouldFanOutToAdmins(String type) {
        if (type == null) return false;
        String t = type.trim().toLowerCase(Locale.getDefault());
        return "jobwork".equals(t)
                || "management".equals(t)
                || "contract_update".equals(t)
                || "lead_update".equals(t)
                || "commission_update".equals(t)
                || "commission".equals(t)
                || "workview_update".equals(t)
                || "daily_pdf".equals(t);
    }
}
