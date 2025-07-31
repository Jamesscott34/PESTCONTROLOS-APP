package com.grpc.grpc;

import android.content.Context;
import android.text.TextUtils;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Schedules local popup reminders (heads-up notifications) for WorkView jobs/contracts.
 *
 * IMPORTANT: Scheduling is per-device. Only schedule for the currently logged-in user.
 */
public final class WorkViewPopupReminderScheduler {
    private WorkViewPopupReminderScheduler() {}

    private static final int LOOKAHEAD_DAYS = 7;

    public static void scheduleUpcomingForUser(Context context, String userName) {
        if (context == null) return;
        if (TextUtils.isEmpty(userName)) return;

        String userLower = userName.trim().toLowerCase(Locale.getDefault());
        String collection = userLower + "_workview";

        try {
            // Always schedule from local cache first (offline-friendly).
            for (WorkViewLocalEventStore.CachedEvent ce :
                    WorkViewLocalEventStore.listUpcomingScheduled(context, userName, LOOKAHEAD_DAYS)) {
                try {
                    scheduleForEvent(context, userName, ce.collection, ce.event);
                } catch (Exception ignored) {}
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = sdf.format(new Date());

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, LOOKAHEAD_DAYS);
            String end = sdf.format(cal.getTime());

            FirebaseFirestore.getInstance()
                    .collection(collection)
                    .whereGreaterThanOrEqualTo("date", today)
                    .whereLessThanOrEqualTo("date", end)
                    .whereEqualTo("status", "scheduled")
                    .get()
                    .addOnSuccessListener(snaps -> {
                        for (QueryDocumentSnapshot ds : snaps) {
                            try {
                                WorkEvent ev = ds.toObject(WorkEvent.class);
                                if (ev == null) continue;
                                ev.setId(ds.getId());
                                // Update local cache for offline scheduling/firing
                                WorkViewLocalEventStore.upsert(context, userName, collection, ev);
                                scheduleForEvent(context, userName, collection, ev);
                            } catch (Exception ignored) {}
                        }
                    })
                    .addOnFailureListener(e -> {
                        // best effort; ignore
                    });
        } catch (Exception ignored) {}
    }

    public static void scheduleForEvent(Context context, String currentUserName, String collection, WorkEvent event) {
        if (context == null || event == null) return;
        if (TextUtils.isEmpty(event.getId())) return;
        if (!"scheduled".equalsIgnoreCase(event.getStatus())) return;

        // Only jobs/contracts
        String type = event.getEventType();
        if (!"job".equalsIgnoreCase(type) && !"contract".equalsIgnoreCase(type)) return;

        // Only schedule popups for the device's logged-in user
        if (!TextUtils.isEmpty(event.getUserName())
                && !event.getUserName().equalsIgnoreCase(currentUserName)) {
            return;
        }

        long delayMs = computeDelayMs(event.getDate(), event.getTime(), 30);
        if (delayMs <= 0) return;

        Data input = new Data.Builder()
                .putString(WorkViewPopupReminderWorker.KEY_USER_NAME, currentUserName)
                .putString(WorkViewPopupReminderWorker.KEY_COLLECTION, collection)
                .putString(WorkViewPopupReminderWorker.KEY_EVENT_DOC_ID, event.getId())
                .putString(WorkViewPopupReminderWorker.KEY_EXPECTED_DATE, event.getDate())
                .putString(WorkViewPopupReminderWorker.KEY_EXPECTED_TIME, event.getTime())
                .putString(WorkViewPopupReminderWorker.KEY_EVENT_NAME, event.getEventName())
                .putString(WorkViewPopupReminderWorker.KEY_EVENT_TYPE, event.getEventType())
                .putString(WorkViewPopupReminderWorker.KEY_EVENT_ADDRESS, event.getAddress())
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(WorkViewPopupReminderWorker.class)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(workName(event.getId()), ExistingWorkPolicy.REPLACE, req);

        // Keep local cache aligned with scheduled work (offline reliability).
        try {
            WorkViewLocalEventStore.upsert(context, currentUserName, collection, event);
        } catch (Exception ignored) {}
    }

    public static void cancelForEvent(Context context, String eventId) {
        if (context == null || TextUtils.isEmpty(eventId)) return;
        try {
            WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName(eventId));
        } catch (Exception ignored) {}
    }

    private static String workName(String eventId) {
        return "popup_reminder_" + eventId;
    }

    private static long computeDelayMs(String yyyyMmDd, String hhMm, int minutesBefore) {
        try {
            if (yyyyMmDd == null || hhMm == null) return -1;
            String[] d = yyyyMmDd.split("-");
            String[] t = hhMm.split(":");
            if (d.length != 3 || t.length != 2) return -1;

            int year = Integer.parseInt(d[0]);
            int month = Integer.parseInt(d[1]) - 1;
            int day = Integer.parseInt(d[2]);
            int hour = Integer.parseInt(t[0]);
            int minute = Integer.parseInt(t[1]);

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month);
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            cal.add(Calendar.MINUTE, -minutesBefore);
            return cal.getTimeInMillis() - System.currentTimeMillis();
        } catch (Exception e) {
            return -1;
        }
    }
}

