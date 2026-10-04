package com.grpc.grpc.location.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Previously deleted locations older than 30 minutes.
 * Locations are now kept until the next update, including across days.
 */
public class LastLocationCleanupWorker extends Worker {
    public static final String KEY_USER_NAME = "USER_NAME";

    public LastLocationCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        return Result.success();
    }
}
