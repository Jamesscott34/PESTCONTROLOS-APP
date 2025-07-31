package com.grpc.grpc;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Locale;

/**
 * Displays in-app notification history so users can see what notifications they received
 * when they log in.
 */
public class NotificationsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userName;
    private LinearLayout notificationsContainer;
    private TextView emptyText;
    private Button deleteAllButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            userName = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_NAME", "User");
        }
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        notificationsContainer = findViewById(R.id.notificationsContainer);
        emptyText = findViewById(R.id.emptyText);
        deleteAllButton = findViewById(R.id.deleteAllButton);

        if (deleteAllButton != null) {
            deleteAllButton.setOnClickListener(v -> confirmDeleteAll());
        }

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });

        loadNotifications();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Notifications")
                .setMessage("Delete all notifications?")
                .setPositiveButton("Yes", (d, w) -> deleteAllNotifications())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteAllNotifications() {
        String userKey = userName.trim().toLowerCase();
        db.collection("notifications")
                .document(userKey)
                .collection("items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Toast.makeText(this, "No notifications to delete.", Toast.LENGTH_SHORT).show();
                        loadNotifications();
                        return;
                    }
                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Notifications deleted.", Toast.LENGTH_SHORT).show();
                                loadNotifications();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadNotifications() {
        String userKey = userName.trim().toLowerCase();
        db.collection("notifications")
                .document(userKey)
                .collection("items")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshot -> {
                    notificationsContainer.removeAllViews();
                    if (snapshot.isEmpty()) {
                        emptyText.setVisibility(android.view.View.VISIBLE);
                        if (deleteAllButton != null) deleteAllButton.setEnabled(false);
                        return;
                    }
                    emptyText.setVisibility(android.view.View.GONE);
                    if (deleteAllButton != null) deleteAllButton.setEnabled(true);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

                    // Mark all unread items as read (so home screen badge clears after viewing)
                    WriteBatch batch = db.batch();
                    boolean hasUnreadToUpdate = false;

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String title = doc.getString("title");
                        String body = doc.getString("body");
                        String type = doc.getString("type");
                        Object dataObj = doc.get("data");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : null;
                        Object ts = doc.get("timestamp");
                        String timeStr = ts instanceof com.google.firebase.Timestamp
                                ? sdf.format(((com.google.firebase.Timestamp) ts).toDate())
                                : "Recently";

                        Boolean read = doc.getBoolean("read");
                        if (read == null || !read) {
                            hasUnreadToUpdate = true;
                            batch.update(doc.getReference(), "read", true);
                        }

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(24, 16, 24, 16);
                        card.setBackgroundResource(R.drawable.surface_frame);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0, 0, 0, 12);
                        card.setLayoutParams(params);

                        TextView titleView = new TextView(this);
                        titleView.setText(title != null ? title : "Notification");
                        titleView.setTextSize(16);
                        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
                        card.addView(titleView);

                        TextView bodyView = new TextView(this);
                        bodyView.setText(body != null ? body : "");
                        bodyView.setTextSize(14);
                        bodyView.setPadding(0, 4, 0, 0);
                        card.addView(bodyView);

                        TextView timeView = new TextView(this);
                        timeView.setText(timeStr);
                        timeView.setTextSize(12);
                        timeView.setPadding(0, 4, 0, 0);
                        timeView.setTextColor(MaterialColors.getColor(timeView, com.google.android.material.R.attr.colorOnSurface));
                        card.addView(timeView);

                        // Tap to open where the notification "lives" in the app (messages / work view / contracts / jobs)
                        card.setOnClickListener(v -> openDestinationForNotification(type, data));

                        // Long-press: actions (open location, delete)
                        card.setOnLongClickListener(v -> {
                            showNotificationActions(doc, type, data);
                            return true;
                        });

                        notificationsContainer.addView(card);
                    }

                    if (hasUnreadToUpdate) {
                        batch.commit();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("NotificationsActivity", "Error loading notifications", e);
                    emptyText.setText("Could not load notifications.");
                    emptyText.setVisibility(android.view.View.VISIBLE);
                    if (deleteAllButton != null) deleteAllButton.setEnabled(false);
                });
    }

    private void confirmDeleteOne(QueryDocumentSnapshot doc) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Notification")
                .setMessage("Delete this notification?")
                .setPositiveButton("Delete", (d, w) -> doc.getReference()
                        .delete()
                        .addOnSuccessListener(v -> loadNotifications())
                        .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNotificationActions(QueryDocumentSnapshot doc, String type, Map<String, Object> data) {
        String[] options = new String[] {"Open location", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle("Notification")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        openLocationForNotification(type, data);
                    } else if (which == 1) {
                        confirmDeleteOne(doc);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openDestinationForNotification(String type, Map<String, Object> data) {
        // Default to current user if missing
        String currentUser = userName != null ? userName : "";

        // Conversation messages: open the chat directly
        if ("conversation_message".equals(type) && data != null) {
            String convId = asString(data.get("convId"));
            if (!TextUtils.isEmpty(convId)) {
                String displayName = getConversationDisplayName(convId, currentUser);
                Intent intent = new Intent(this, MessagingActivity.class);
                intent.putExtra("USER_NAME", currentUser);
                intent.putExtra("CONVERSATION_ID", convId);
                intent.putExtra("CONVERSATION_NAME", displayName);
                startActivity(intent);
                return;
            }
            // Fallback
            Intent intent = new Intent(this, MessagingConversationsActivity.class);
            intent.putExtra("USER_NAME", currentUser);
            startActivity(intent);
            return;
        }

        // WorkView updates: open WorkView for the target user (keeps correct user)
        if ("workview_update".equals(type) && data != null) {
            String targetUser = asString(data.get("targetUser"));
            if (TextUtils.isEmpty(targetUser)) targetUser = currentUser;
            Intent intent = new Intent(this, WorkViewActivity.class);
            intent.putExtra("USER_NAME", targetUser);
            startActivity(intent);
            return;
        }

        // Contract updates/assignments: open contract view for that user
        if ("contract_update".equals(type) && data != null) {
            String contractUser = asString(data.get("userName"));
            if (TextUtils.isEmpty(contractUser)) contractUser = currentUser;
            Intent intent = new Intent(this, ViewContractActivity.class);
            intent.putExtra("USER_NAME", contractUser);
            startActivity(intent);
            return;
        }

        // JobWork: open jobs screen for the assigned tech (or current user)
        if ("jobwork".equals(type) && data != null) {
            String assigned = asString(data.get("assignedTech"));
            if (TextUtils.isEmpty(assigned)) assigned = currentUser;
            Intent intent = new Intent(this, JobsActivity.class);
            intent.putExtra("USER_NAME", assigned);
            startActivity(intent);
            return;
        }

        // Lead updates: open Leads screen for the recipient user
        if ("lead_update".equals(type) && data != null) {
            String targetUser = asString(data.get("targetUser"));
            if (TextUtils.isEmpty(targetUser)) targetUser = currentUser;
            Intent intent = new Intent(this, ViewLeadsActivity.class);
            intent.putExtra("USER_NAME", targetUser);
            startActivity(intent);
            return;
        }

        // Management tasks
        if ("management".equals(type)) {
            Intent intent = new Intent(this, ViewManagmentJobActivity.class);
            intent.putExtra("USER_NAME", currentUser);
            startActivity(intent);
            return;
        }

        // Fallback: open Notifications list only
        Toast.makeText(this, "No screen linked to this notification.", Toast.LENGTH_SHORT).show();
    }

    private String getConversationDisplayName(String convId, String currentUser) {
        if ("group".equalsIgnoreCase(convId)) return "Group";
        String[] parts = convId.split("_");
        if (parts.length >= 2) {
            String a = parts[0];
            String b = parts[1];
            if (!TextUtils.isEmpty(currentUser) && a.equalsIgnoreCase(currentUser)) return capitalize(b);
            if (!TextUtils.isEmpty(currentUser) && b.equalsIgnoreCase(currentUser)) return capitalize(a);
            return capitalize(a) + " & " + capitalize(b);
        }
        return "Chat";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void openLocationForNotification(String type, Map<String, Object> data) {
        // 1) Direct address in data
        String direct = extractAddress(data);
        if (!TextUtils.isEmpty(direct)) {
            openMaps(direct);
            return;
        }

        // 2) Try resolve by notification type + IDs
        if ("jobwork".equals(type) && data != null) {
            String jobId = asString(data.get("jobId"));
            if (!TextUtils.isEmpty(jobId)) {
                db.collection("JobWork").document(jobId).get()
                        .addOnSuccessListener(ds -> {
                            String addr = ds.getString("Address");
                            if (TextUtils.isEmpty(addr)) addr = ds.getString("address");
                            if (!TextUtils.isEmpty(addr)) openMaps(addr);
                            else Toast.makeText(this, "No address found for this job.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Could not load job address.", Toast.LENGTH_SHORT).show());
                return;
            }
        }

        if ("contract_update".equals(type) && data != null) {
            String contractId = asString(data.get("contractId"));
            String contractUser = asString(data.get("userName"));
            if (TextUtils.isEmpty(contractUser)) contractUser = userName;
            if (!TextUtils.isEmpty(contractId) && !TextUtils.isEmpty(contractUser)) {
                db.collection(contractUser + " Contracts").document(contractId).get()
                        .addOnSuccessListener(ds -> {
                            String addr = ds.getString("address");
                            if (!TextUtils.isEmpty(addr)) openMaps(addr);
                            else Toast.makeText(this, "No address found for this contract.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Could not load contract address.", Toast.LENGTH_SHORT).show());
                return;
            }
        }

        if ("workview_update".equals(type) && data != null) {
            String eventId = asString(data.get("eventId"));
            String targetUser = asString(data.get("targetUser"));
            if (!TextUtils.isEmpty(eventId) && !TextUtils.isEmpty(targetUser)) {
                String collection = targetUser.trim().toLowerCase() + "_workview";
                db.collection(collection).document(eventId).get()
                        .addOnSuccessListener(ds -> {
                            String addr = ds.getString("address");
                            if (!TextUtils.isEmpty(addr)) openMaps(addr);
                            else Toast.makeText(this, "No address found for this work view event.", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> Toast.makeText(this, "Could not load event address.", Toast.LENGTH_SHORT).show());
                return;
            }
        }

        Toast.makeText(this, "No location found for this notification.", Toast.LENGTH_SHORT).show();
    }

    private String extractAddress(Map<String, Object> data) {
        if (data == null) return null;
        String[] keys = new String[] {"eventAddress", "address", "Address", "event_address"};
        for (String k : keys) {
            String v = asString(data.get(k));
            if (!TextUtils.isEmpty(v) && !"N/A".equalsIgnoreCase(v.trim())) return v.trim();
        }
        return null;
    }

    private String asString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private void openMaps(String address) {
        try {
            // Record this as the user's last "map opened" location
            try {
                FirebaseFirestore.getInstance()
                        .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                        .document(LocationSharing.userKey(userName))
                        .set(new java.util.HashMap<String, Object>() {{
                            put("userName", userName);
                            put("lastMapQuery", address);
                            put("lastMapClientTimestampMs", System.currentTimeMillis());
                            put("lastMapAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                            put("source", "map_open");
                        }}, com.google.firebase.firestore.SetOptions.merge());
            } catch (Exception ignored) {}

            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            // Prefer Google Maps, but allow fallback
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        } catch (Exception e) {
            try {
                Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open maps.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
