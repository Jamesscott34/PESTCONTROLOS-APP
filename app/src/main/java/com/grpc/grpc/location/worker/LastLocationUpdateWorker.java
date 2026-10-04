package com.grpc.grpc.location.worker;

import com.grpc.grpc.location.LocationSharing;

import android.app.NotificationManager;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes best-effort current/last-known location to Firestore {@code last_locations}/{authUid}.
 * Runs approximately every 15 minutes at any time of day, without a status notification.
 */
public class LastLocationUpdateWorker extends Worker {
    public static final String KEY_USER_NAME = "USER_NAME";
    /** When true, upload even outside 08:00–17:30 (e.g. login after 17:30). */
    public static final String KEY_FORCE_UPLOAD = "FORCE_UPLOAD";

    private static final String TAG = LocationSharing.TAG;
    private static final String CHANNEL_ID = "location_tracking";

    public LastLocationUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String userName = getInputData().getString(KEY_USER_NAME);
        final String userKey = LocationSharing.userKey(userName);
        if (userKey.isEmpty()) {
            Log.w(TAG, "Location update skipped: empty user key");
            return Result.success();
        }

        // Only upload under the currently authenticated UID (shared-device safety).
        FirebaseUser authUser = null;
        try {
            authUser = FirebaseAuth.getInstance().getCurrentUser();
        } catch (Exception ignored) {}
        if (authUser == null || authUser.getUid() == null || authUser.getUid().trim().isEmpty()) {
            Log.i(TAG, "Location update skipped: not authenticated");
            return Result.success();
        }
        final String authUid = authUser.getUid();
        if (!authUid.equals(userKey)) {
            Log.w(TAG, "Location update skipped: scheduled key does not match signed-in UID");
            return Result.success();
        }

        dismissLocationNotification();

        if (!LocationSharing.hasLocationPermission(getApplicationContext())) {
            Log.w(TAG, "Location permission unavailable — tracking unavailable this cycle");
            return Result.success();
        }

        try {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(getApplicationContext());

            Location loc = null;
            try {
                loc = Tasks.await(client.getLastLocation(), 8, TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.w(TAG, "getLastLocation failed", e);
            }

            if (loc == null) {
                try {
                    com.google.android.gms.location.CurrentLocationRequest request =
                            new com.google.android.gms.location.CurrentLocationRequest.Builder()
                                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                                    .setMaxUpdateAgeMillis(7L * 24L * 60L * 60L * 1000L)
                                    .setDurationMillis(10_000L)
                                    .build();
                    loc = Tasks.await(client.getCurrentLocation(request, null), 15, TimeUnit.SECONDS);
                } catch (Exception e) {
                    Log.w(TAG, "getCurrentLocation failed", e);
                }
            }

            if (loc == null) {
                Log.w(TAG, "Location unavailable this cycle");
                return Result.success();
            }

            Log.i(TAG, "Location obtained (accuracy="
                    + (loc.hasAccuracy() ? loc.getAccuracy() : -1) + ")");

            long now = System.currentTimeMillis();
            Map<String, Object> update = new HashMap<>();
            update.put("userName", authUid);
            update.put("lat", loc.getLatitude());
            update.put("lng", loc.getLongitude());
            update.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : null);
            update.put("provider", loc.getProvider());
            update.put("clientTimestampMs", now);
            update.put("updatedAt", FieldValue.serverTimestamp());
            update.put("source", "gps_last_known");
            update.put("stale", false);

            Log.i(TAG, "Firebase location upload started for " + LocationSharing.redact(authUid));
            Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                            .document(authUid)
                            .set(update, SetOptions.merge()),
                    20, TimeUnit.SECONDS
            );
            Log.i(TAG, "Firebase location upload successful");

            try {
                JSONObject json = new JSONObject();
                json.put("userKey", authUid);
                json.put("lat", loc.getLatitude());
                json.put("lng", loc.getLongitude());
                json.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : JSONObject.NULL);
                json.put("clientTimestampMs", now);
                json.put("source", "gps_last_known");
                json.put("stale", false);
                LocationSharing.cacheLastLocation(getApplicationContext(), authUid, json.toString());
            } catch (Exception ignored) {}

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Firebase location upload failed", e);
            return Result.retry();
        }
    }

    /** Removes the old "Sharing location" notification and its channel. */
    private void dismissLocationNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm =
                    (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.deleteNotificationChannel(CHANNEL_ID);
        } catch (Exception ignored) {}
    }
}
