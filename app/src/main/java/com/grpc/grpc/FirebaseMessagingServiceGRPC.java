package com.grpc.grpc;

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

        // In-app only notifications: ignore all push notifications (no status-bar / system notifications).
        // Keeping this service avoids manifest/class reference issues while ensuring no outside-app alerts.
        Log.d("GRPC-FCM", "Push received but ignored (in-app only). type=" + remoteMessage.getData().get("type"));
    }

    private void showNotification(String title, String message) {
        String channelId = "grpc_channel";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "GRPC Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.bk) // ✅ This must exist in res/drawable
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

        // Get event data
        String eventName = remoteMessage.getData().get("eventName");
        String eventAddress = remoteMessage.getData().get("eventAddress");
        String eventTime = remoteMessage.getData().get("eventTime");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(body + "\n📍 " + (eventAddress != null ? eventAddress : "No address")))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // Create intent - LoginActivity if not signed in, WorkViewActivity with user if signed in
        String eventUser = remoteMessage.getData().get("targetUser");
        Intent intent = createNotificationIntent(WorkViewActivity.class, eventUser);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Create intent for opening maps with route
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

    private boolean isCurrentUser(String sender) {
        // Get current user from SharedPreferences or Firebase Auth
        // For now, we'll use a simple approach - you can enhance this
        return false; // This will be enhanced based on your user management
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

        // Create intent - LoginActivity if not signed in, MessagingActivity with user if signed in
        Intent intent = createNotificationIntent(MessagingActivity.class, null);
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

        // Create intent - LoginActivity if not signed in, JobsActivity with user if signed in
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

        // Create intent - LoginActivity if not signed in, MainActivity with user if signed in
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

        // Create intent - LoginActivity if not signed in, WorkViewActivity with user if signed in
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

        // Create intent - LoginActivity if not signed in, MessagingActivity with conv if signed in
        Intent intent = createNotificationIntent(MessagingActivity.class, null);
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

        // Create intent - LoginActivity if not signed in, ContractsActivity with user if signed in
        String contractUser = remoteMessage.getData().get("userName");
        Intent intent = createNotificationIntent(ContractsActivity.class, contractUser);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
