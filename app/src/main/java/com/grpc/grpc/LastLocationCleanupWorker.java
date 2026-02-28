package com.grpc.grpc;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Deletes the current user's last location once it's older than 15 minutes.
 * Runs every 15 minutes.
 */
public class LastLocationCleanupWorker extends Worker {
    public static final String KEY_USER_NAME = "USER_NAME";

    private static final long EXPIRE_MS = 30L * 60L * 1000L;

    public LastLocationCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String userName = getInputData().getString(KEY_USER_NAME);
        final String userKey = LocationSharing.userKey(userName);
        if (userKey.isEmpty()) return Result.success();

        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentSnapshot snap = com.google.android.gms.tasks.Tasks.await(
                    db.collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                            .document(userKey)
                            .get(),
                    8, java.util.concurrent.TimeUnit.SECONDS
            );

            if (snap == null || !snap.exists()) return Result.success();
            Long ts = snap.getLong("clientTimestampMs");
            if (ts == null) return Result.success();

            long age = System.currentTimeMillis() - ts;
            if (age >= EXPIRE_MS) {
                db.collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                        .document(userKey)
                        .delete();
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}

