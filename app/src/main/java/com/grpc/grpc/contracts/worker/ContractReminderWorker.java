package com.grpc.grpc.contracts.worker;

import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.messaging.NotificationUtils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs every 12 hours. For the current user, finds contract reminders that are due
 * (lastNotifiedAt is null or older than 12h), sends an in-app notification, and updates lastNotifiedAt.
 */
public class ContractReminderWorker extends Worker {

    private static final long REMINDER_INTERVAL_MS = TimeUnit.HOURS.toMillis(12);

    public ContractReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();
        long nowMs = System.currentTimeMillis();

        try {
            QuerySnapshot snap = Tasks.await(db.collection(FirestorePaths.CONTRACT_REMINDERS)
                    .whereEqualTo("userId", uid)
                    .get());
            if (snap == null) return Result.success();
            for (QueryDocumentSnapshot doc : snap) {
                Timestamp lastTs = doc.getTimestamp("lastNotifiedAt");
                long lastMs = lastTs != null ? lastTs.toDate().getTime() : 0L;
                if (nowMs - lastMs < REMINDER_INTERVAL_MS) continue;

                String contractId = doc.getString("contractId");
                String contractName = doc.getString("contractName");
                if (contractId == null) continue;

                String title = "Reminder: " + (contractName != null && !contractName.isEmpty() ? contractName : "Contract");
                String body = "You asked to be reminded about this contract every 12 hours. Open Contracts to view.";
                Map<String, Object> data = new HashMap<>();
                data.put("contractId", contractId);
                data.put("type", "contract_reminder");

                String notifDocId = "contract_reminder_" + contractId + "_" + nowMs;
                NotificationUtils.writeInAppNotification(uid, notifDocId, title, body, "contract_reminder", data);

                Tasks.await(doc.getReference().update("lastNotifiedAt", Timestamp.now()));
            }
        } catch (Exception e) {
            return Result.retry();
        }
        return Result.success();
    }
}
