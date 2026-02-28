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
        // This is implemented as a "fan-out" write so admins only ever read their own notifications.
        if (shouldFanOutToAdmins(type)) {
            try {
                for (String adminId : StaffDirectory.getCachedAdminStaffIds()) {
                    if (adminId == null) continue;
                    String adminKey = adminId.trim();
                    if (adminKey.isEmpty() || adminKey.equals(userKey)) continue;
                    FirebaseFirestore.getInstance()
                            .collection("notifications")
                            .document(adminKey)
                            .collection("items")
                            .document(safeDocId + "_a" + adminKey)
                            .set(notif);
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Resolve a user-ish string into a stable notification recipient key.
     * Preference order:
     * - StaffID -> StaffID
     * - ContractKey -> StaffID via StaffDirectory cache
     * - Fallback -> lowercased string (legacy)
     */
    public static String resolveNotificationRecipientKey(String recipientUser) {
        if (recipientUser == null) return "";
        String raw = recipientUser.trim();
        if (raw.isEmpty()) return "";

        // StaffID already
        if (raw.matches("\\d{1,3}")) {
            try {
                int n = Integer.parseInt(raw);
                return String.format(Locale.US, "%03d", n);
            } catch (Exception ignored) {
                return raw;
            }
        }

        // Try mapping (accept first token so full names still work)
        String first = raw;
        int sp = raw.indexOf(' ');
        if (sp > 0) first = raw.substring(0, sp);

        // Prefer ContractKey -> StaffID mapping.
        String id = StaffDirectory.getStaffIdForContractKey(first);
        if (id == null) id = StaffDirectory.getUserId(first);
        if (id != null && !id.trim().isEmpty()) return id.trim();

        // Legacy fallback
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
                || "commission".equals(t);
    }
}
