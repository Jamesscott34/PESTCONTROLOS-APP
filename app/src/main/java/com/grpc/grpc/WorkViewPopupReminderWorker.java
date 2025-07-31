package com.grpc.grpc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkViewPopupReminderWorker
 *
 * Runs at the scheduled reminder time (30 minutes before) and shows a LOCAL
 * heads-up notification (popup) on the device.
 *
 * This is NOT push. It is per-device, per-logged-in user scheduling.
 */
public class WorkViewPopupReminderWorker extends Worker {

    public static final String KEY_USER_NAME = "userName";
    public static final String KEY_COLLECTION = "collection";
    public static final String KEY_EVENT_DOC_ID = "eventDocId";
    public static final String KEY_EXPECTED_DATE = "expectedDate"; // yyyy-MM-dd
    public static final String KEY_EXPECTED_TIME = "expectedTime"; // HH:mm
    public static final String KEY_EVENT_NAME = "eventName";
    public static final String KEY_EVENT_TYPE = "eventType";
    public static final String KEY_EVENT_ADDRESS = "eventAddress";

    private static final String CHANNEL_ID = "workview_reminders";

    public WorkViewPopupReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String userName = safe(getInputData().getString(KEY_USER_NAME));
        String eventDocId = safe(getInputData().getString(KEY_EVENT_DOC_ID));
        String expectedDate = safe(getInputData().getString(KEY_EXPECTED_DATE));
        String expectedTime = safe(getInputData().getString(KEY_EXPECTED_TIME));
        String eventName = safe(getInputData().getString(KEY_EVENT_NAME));
        String eventType = safe(getInputData().getString(KEY_EVENT_TYPE));
        String eventAddress = safe(getInputData().getString(KEY_EVENT_ADDRESS));

        if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(eventDocId)) {
            return Result.success();
        }

        try {
            // OFFLINE-FIRST: do not call Firestore here. We rely on:
            // - the inputs captured at scheduling time, and
            // - the local event cache (best-effort) to suppress stale reminders.
            if (!WorkViewLocalEventStore.shouldShowPopup(
                    getApplicationContext(),
                    userName,
                    eventDocId,
                    expectedDate,
                    expectedTime
            )) {
                return Result.success();
            }

            String currentType = eventType;
            if (!"job".equalsIgnoreCase(currentType) && !"contract".equalsIgnoreCase(currentType)) {
                return Result.success(); // only jobs/contracts get popups
            }

            // Build popup notification
            ensureChannel();

            String title = "⏰ Upcoming " + ("contract".equalsIgnoreCase(currentType) ? "Contract" : "Job");
            String body = (TextUtils.isEmpty(eventName) ? "You have an upcoming event" : eventName)
                    + (TextUtils.isEmpty(expectedTime) ? "" : (" at " + expectedTime))
                    + (TextUtils.isEmpty(eventAddress) ? "" : ("\n" + eventAddress));

            Intent openIntent = new Intent(getApplicationContext(), WorkViewActivity.class);
            openIntent.putExtra("USER_NAME", userName);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pi = PendingIntent.getActivity(
                    getApplicationContext(),
                    stableId(eventDocId),
                    openIntent,
                    (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0) | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder b = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.bk)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(stableId(eventDocId), b.build());
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel existing = nm.getNotificationChannel(CHANNEL_ID);
        if (existing != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Work reminders",
                NotificationManager.IMPORTANCE_HIGH
        );
        ch.setDescription("Popup reminders 30 minutes before scheduled jobs/contracts.");
        nm.createNotificationChannel(ch);
    }

    private static int stableId(String s) {
        return s != null ? s.hashCode() : 0;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

