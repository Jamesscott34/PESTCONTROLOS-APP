package com.grpc.grpc;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * InAppReminderWorker
 *
 * Runs at the scheduled reminder time and writes an IN-APP notification record
 * to Firestore under: notifications/{user}/items
 *
 * This is intentionally NOT an Android system notification.
 */
public class InAppReminderWorker extends Worker {

    public static final String KEY_USER_NAME = "userName";
    public static final String KEY_COLLECTION = "collection";
    public static final String KEY_EVENT_DOC_ID = "eventDocId";
    public static final String KEY_EXPECTED_DATE = "expectedDate"; // yyyy-MM-dd
    public static final String KEY_EXPECTED_TIME = "expectedTime"; // HH:mm
    public static final String KEY_EVENT_NAME = "eventName";
    public static final String KEY_EVENT_TYPE = "eventType";
    public static final String KEY_EVENT_ADDRESS = "eventAddress";

    public InAppReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String userName = safe(getInputData().getString(KEY_USER_NAME));
        String collection = safe(getInputData().getString(KEY_COLLECTION));
        String eventDocId = safe(getInputData().getString(KEY_EVENT_DOC_ID));
        String expectedDate = safe(getInputData().getString(KEY_EXPECTED_DATE));
        String expectedTime = safe(getInputData().getString(KEY_EXPECTED_TIME));
        String eventName = safe(getInputData().getString(KEY_EVENT_NAME));
        String eventType = safe(getInputData().getString(KEY_EVENT_TYPE));
        String eventAddress = safe(getInputData().getString(KEY_EVENT_ADDRESS));

        if (TextUtils.isEmpty(userName) || TextUtils.isEmpty(collection) || TextUtils.isEmpty(eventDocId)) {
            return Result.success();
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        try {
            // 1) Ensure the event still exists and matches the expected schedule
            DocumentReference eventRef = db.collection(collection).document(eventDocId);
            DocumentSnapshot eventSnap = Tasks.await(eventRef.get(), 10, TimeUnit.SECONDS);
            if (eventSnap == null || !eventSnap.exists()) {
                return Result.success(); // deleted
            }

            String status = asString(eventSnap.get("status"));
            if (!"scheduled".equalsIgnoreCase(status)) {
                return Result.success(); // completed/cancelled
            }

            String currentDate = asString(eventSnap.get("date"));
            String currentTime = asString(eventSnap.get("time"));
            if (!TextUtils.isEmpty(expectedDate) && !TextUtils.equals(expectedDate, currentDate)) {
                return Result.success(); // rescheduled
            }
            if (!TextUtils.isEmpty(expectedTime) && !TextUtils.equals(expectedTime, currentTime)) {
                return Result.success(); // rescheduled
            }

            // 2) Dedupe: deterministic notification doc ID
            String dedupeId = ("reminder_" + eventDocId + "_" + expectedDate + "_" + expectedTime)
                    .replaceAll("[^a-zA-Z0-9_\\-]", "_");

            String title = "⏰ Upcoming Work";
            String body = (TextUtils.isEmpty(eventName) ? "You have an upcoming event" : eventName)
                    + (TextUtils.isEmpty(expectedTime) ? "" : (" at " + expectedTime));

            Map<String, Object> data = new HashMap<>();
            data.put("eventId", eventDocId);      // workview document id
            data.put("targetUser", userName);     // keep correct user context
            data.put("eventName", eventName);
            data.put("eventType", eventType);
            data.put("eventDate", expectedDate);
            data.put("eventTime", expectedTime);
            data.put("eventAddress", eventAddress);
            data.put("type", "workview_update");  // reuse in-app deep links

            // Deterministic docId makes this idempotent; no need to pre-read.
            NotificationUtils.writeInAppNotification(userName, dedupeId, title, body, "workview_update", data);
            return Result.success();
        } catch (Exception e) {
            // transient: retry
            return Result.retry();
        }
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

