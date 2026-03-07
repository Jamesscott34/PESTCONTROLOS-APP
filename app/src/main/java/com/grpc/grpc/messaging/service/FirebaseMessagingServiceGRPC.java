package com.grpc.grpc.messaging.service;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.contracts.ui.ContractsActivity;
import com.grpc.grpc.jobs.ui.JobsActivity;
import com.grpc.grpc.login.LoginActivity;
import com.grpc.grpc.main.MainActivity;
import com.grpc.grpc.messaging.ui.MessagingConversationsActivity;
import com.grpc.grpc.workview.ui.WorkViewActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessagingServiceGRPC extends FirebaseMessagingService {

    private static final String PREFS_NAME = "GRPC";

    /** Returns the logged-in user name, or null if not logged in. */
    private String getLoggedInUser() {
        String user = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("USER_NAME", null);
        if (user == null || user.trim().isEmpty() || "User".equalsIgnoreCase(user.trim())) {
            return null;
        }
        return user.trim();
    }

    /** Creates intent for target activity with USER_NAME, or LoginActivity if not logged in. */
    private Intent createNotificationIntent(Class<?> targetActivity, String userNameFromData) {
        String user = getLoggedInUser();
        if (user == null) {
            Intent login = new Intent(this, LoginActivity.class);
            login.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return login;
        }
        Intent intent = new Intent(this, targetActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("USER_NAME", userNameFromData != null ? userNameFromData : user);
        return intent;
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String type = remoteMessage.getData() != null ? remoteMessage.getData().get("type") : null;
        Log.d("GRPC-FCM", "Push received. type=" + type + " data=" + remoteMessage.getData());

        if (type == null || type.trim().isEmpty()) {
            // Fallback: generic notification using title/body if present.
            if (remoteMessage.getNotification() != null) {
                String title = remoteMessage.getNotification().getTitle();
                String body = remoteMessage.getNotification().getBody();
                if (title == null || title.trim().isEmpty()) title = "Notification";
                if (body == null) body = "";
                showNotification(title, body);
            }
            return;
        }

        switch (type) {
            case "work_event":
                showWorkEventNotification(remoteMessage);
                break;
            case "message":
                showMessageNotification(remoteMessage);
                break;
            case "job_assignment":
                showJobWorkNotification(remoteMessage);
                break;
            case "management_task":
                showManagementJobNotification(remoteMessage);
                break;
            case "workview_update":
                showWorkViewUpdateNotification(remoteMessage);
                break;
            case "conversation_message":
                showConversationMessageNotification(remoteMessage);
                break;
            case "contract_update":
                showContractUpdateNotification(remoteMessage);
                break;
            default:
                // Any other type (including potential Behinds list alerts) use a generic notification.
                if (remoteMessage.getNotification() != null) {
                    String title = remoteMessage.getNotification().getTitle();
                    String body = remoteMessage.getNotification().getBody();
                    if (title == null || title.trim().isEmpty()) title = "Notification";
                    if (body == null) body = "";
                    showNotification(title, body);
                }
                break;
        }
    }

    private void showNotification(String title, String message) {
        String channelId = "grpc_channel";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.bk)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showWorkEventNotification(RemoteMessage remoteMessage) {
        String channelId = "work_reminders";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Work Event Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for work event reminders");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "Work Event Reminder";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "You have a work event coming up";

        String eventAddress = remoteMessage.getData().get("eventAddress");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body + "\n📍 " + (eventAddress != null ? eventAddress : "No address")))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        String eventUser = remoteMessage.getData().get("targetUser");
        Intent intent = createNotificationIntent(WorkViewActivity.class, eventUser);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.content.Intent mapIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        if (eventAddress != null && !eventAddress.isEmpty() && !eventAddress.equals("N/A")) {
            android.net.Uri gmmIntentUri = android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(eventAddress));
            mapIntent.setData(gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
        }
        android.app.PendingIntent mapPendingIntent = android.app.PendingIntent.getActivity(this, 1, mapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);
        builder.addAction(android.R.drawable.ic_dialog_map, "Route", mapPendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showMessageNotification(RemoteMessage remoteMessage) {
        String channelId = "messages";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Instant Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for instant messages");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "New Message";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "You have a new message";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        Intent intent = createNotificationIntent(MessagingConversationsActivity.class, null);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showJobWorkNotification(RemoteMessage remoteMessage) {
        String channelId = "jobs";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Job Assignments",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for job assignments");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "New Job Assignment";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "You have a new job assignment";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        String assignedTech = remoteMessage.getData().get("assignedTech");
        Intent intent = createNotificationIntent(JobsActivity.class, assignedTech);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showManagementJobNotification(RemoteMessage remoteMessage) {
        String channelId = "management";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Management Tasks",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for management tasks");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "New Management Task";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "You have a new management task";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        String assignedManager = remoteMessage.getData().get("assignedManager");
        Intent intent = createNotificationIntent(MainActivity.class, assignedManager);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showWorkViewUpdateNotification(RemoteMessage remoteMessage) {
        String channelId = "work_reminders";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Work View Updates",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications when your work view is updated");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "Work View Updated";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "Your work schedule has been updated";

        String eventAddress = remoteMessage.getData().get("eventAddress");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body + (eventAddress != null && !eventAddress.isEmpty() ? "\n📍 " + eventAddress : "")))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        String targetUser = remoteMessage.getData().get("targetUser");
        Intent intent = createNotificationIntent(WorkViewActivity.class, targetUser);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showConversationMessageNotification(RemoteMessage remoteMessage) {
        String channelId = "messages";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Chat messages");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "New Message";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "You have a new message";

        String convId = remoteMessage.getData().get("convId");
        String displayName = "group".equals(convId) ? "Group" : convId;
        if (convId != null && convId.contains("_") && !"group".equals(convId)) {
            String[] parts = convId.split("_");
            displayName = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1) + " / "
                    + parts[1].substring(0, 1).toUpperCase() + parts[1].substring(1);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        Intent intent = createNotificationIntent(MessagingConversationsActivity.class, null);
        intent.putExtra("CONVERSATION_ID", convId);
        intent.putExtra("CONVERSATION_NAME", displayName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void showContractUpdateNotification(RemoteMessage remoteMessage) {
        String channelId = "contracts";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Contract Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for contract updates");
            manager.createNotificationChannel(channel);
        }

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "Contract Updated";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "A contract has been updated";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        String contractUser = remoteMessage.getData().get("userName");
        Intent intent = createNotificationIntent(ContractsActivity.class, contractUser);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
