package com.grpc.grpc.location.worker;

import com.grpc.grpc.core.*;
import com.grpc.grpc.location.LocationSharing;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes best-effort last-known location to Firestore.
 * Runs every 30 minutes.
 */
public class LastLocationUpdateWorker extends Worker {
    public static final String KEY_USER_NAME = "USER_NAME";

    public LastLocationUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String userName = getInputData().getString(KEY_USER_NAME);
        final String userKey = LocationSharing.userKey(userName);
        if (userKey.isEmpty()) return Result.success();

        // If location permission is not granted, do nothing (best effort).
        if (!LocationSharing.hasLocationPermission(getApplicationContext())) {
            return Result.success();
        }

        try {
            FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(getApplicationContext());

            // lastLocation is fast + battery friendly; may be null.
            Location loc = Tasks.await(client.getLastLocation(), 8, TimeUnit.SECONDS);
            if (loc == null) {
                try {
                    com.google.android.gms.location.CurrentLocationRequest request =
                            new com.google.android.gms.location.CurrentLocationRequest.Builder()
                                    .setPriority(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                                    .setMaxUpdateAgeMillis(5 * 60 * 1000L)
                                    .setDurationMillis(8000L)
                                    .build();
                    loc = Tasks.await(client.getCurrentLocation(request, null), 12, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            if (loc == null) return Result.success(); // No fix available; try again next cycle.

            long now = System.currentTimeMillis();
            Map<String, Object> update = new HashMap<>();
            update.put("userName", userName != null ? userName : userKey);
            update.put("lat", loc.getLatitude());
            update.put("lng", loc.getLongitude());
            update.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : null);
            update.put("provider", loc.getProvider());
            update.put("clientTimestampMs", now);
            update.put("updatedAt", FieldValue.serverTimestamp());
            update.put("source", "gps_last_known");

            FirebaseFirestore.getInstance()
                    .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                    .document(userKey)
                    .set(update, com.google.firebase.firestore.SetOptions.merge());

            // Cache (helps when offline)
            try {
                JSONObject json = new JSONObject();
                json.put("userKey", userKey);
                json.put("lat", loc.getLatitude());
                json.put("lng", loc.getLongitude());
                json.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : JSONObject.NULL);
                json.put("clientTimestampMs", now);
                json.put("source", "gps_last_known");
                LocationSharing.cacheLastLocation(getApplicationContext(), userKey, json.toString());
            } catch (Exception ignored) {}

            return Result.success();
        } catch (Exception e) {
            // Try again later
            return Result.retry();
        }
    }
}

