package com.grpc.grpc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessagingServiceGRPC extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getTitle()
                : "GRPC";

        String body = remoteMessage.getNotification() != null
                ? remoteMessage.getNotification().getBody()
                : "You have a new message.";

        Log.d("GRPC-FCM", "Push received: " + title + " - " + body);

        // Check notification type and handle accordingly
        String notificationType = remoteMessage.getData().get("type");
        if ("work_event_reminder".equals(notificationType)) {
            showWorkEventNotification(remoteMessage);
        } else if ("message".equals(notificationType)) {
            // Check if this message is from the current user
            String sender = remoteMessage.getData().get("sender");
            if (sender != null && !isCurrentUser(sender)) {
                showMessageNotification(remoteMessage);
            }
        } else if ("jobwork".equals(notificationType)) {
            showJobWorkNotification(remoteMessage);
        } else if ("management".equals(notificationType)) {
            showManagementJobNotification(remoteMessage);
        } else if ("contract_update".equals(notificationType)) {
            showContractUpdateNotification(remoteMessage);
        } else {
            showNotification(title, body);
        }
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

        // Create intent for opening the app
        android.content.Intent intent = new android.content.Intent(this, WorkViewActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        // Create intent for opening the messaging activity
        android.content.Intent intent = new android.content.Intent(this, MessagingActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        // Create intent for opening the jobs activity
        android.content.Intent intent = new android.content.Intent(this, JobsActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        // Create intent for opening the main activity
        android.content.Intent intent = new android.content.Intent(this, MainActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        // Create intent for opening the contracts activity
        android.content.Intent intent = new android.content.Intent(this, ContractsActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
