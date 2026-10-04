package com.grpc.grpc.stock.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.messaging.NotificationUtils;
import com.grpc.grpc.stock.data.StockRepository;
import com.grpc.grpc.stock.model.StockRequest;
import com.grpc.grpc.stock.model.StockRequestItem;
import com.grpc.grpc.stock.util.StockDates;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-only request detail. Admins mark ready, mark retrieved, and delete retrieved requests.
 */
public class StockRequestDetailActivity extends AppCompatActivity {

    public static final String EXTRA_REQUEST_ID = "STOCK_REQUEST_ID";

    private TextView contentView;
    private Button readyButton;
    private Button retrievedButton;
    private Button deleteButton;
    private String requestId = "";
    @Nullable
    private StockRequest request;
    private boolean admin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.stock_unavailable_offline, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        if (requestId == null || requestId.trim().isEmpty()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_stock_request_detail);
        contentView = findViewById(R.id.stockDetailContent);
        readyButton = findViewById(R.id.buttonMarkReady);
        retrievedButton = findViewById(R.id.buttonMarkRetrieved);
        deleteButton = findViewById(R.id.buttonDeleteRequest);
        readyButton.setOnClickListener(v -> markReady());
        retrievedButton.setOnClickListener(v -> markRetrieved());
        deleteButton.setOnClickListener(v -> confirmDelete());

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            admin = session != null && session.isAdmin;
            load();
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BuildConfig.IS_OFFLINE && requestId != null && !requestId.isEmpty() && contentView != null) {
            load();
        }
    }

    private void load() {
        StockRepository.loadRequest(requestId, (loaded, loadedRequest) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (!loaded || loadedRequest == null) {
                request = null;
                hideActions();
                Toast.makeText(this, R.string.stock_detail_missing, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            String self = StockRepository.authUid();
            if (!admin && !loadedRequest.requesterUid.equals(self)) {
                hideActions();
                Toast.makeText(this, R.string.stock_detail_missing, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            request = loadedRequest;
            contentView.setText(buildContent(loadedRequest));
            readyButton.setVisibility(admin && loadedRequest.isPending() ? View.VISIBLE : View.GONE);
            retrievedButton.setVisibility(admin && loadedRequest.isReady() ? View.VISIBLE : View.GONE);
            deleteButton.setVisibility(admin && loadedRequest.isRetrieved() ? View.VISIBLE : View.GONE);
        }));
    }

    private void hideActions() {
        if (readyButton != null) readyButton.setVisibility(View.GONE);
        if (retrievedButton != null) retrievedButton.setVisibility(View.GONE);
        if (deleteButton != null) deleteButton.setVisibility(View.GONE);
    }

    private String buildContent(StockRequest current) {
        StringBuilder body = new StringBuilder();
        body.append(getString(R.string.stock_detail_requested_by, current.displayName())).append("\n\n");
        String requestedOn = StockDates.format(current.createdAtMillis);
        body.append(getString(R.string.stock_detail_requested_on, requestedOn.isEmpty() ? "—" : requestedOn)).append("\n\n");
        body.append(getString(R.string.stock_detail_status, StockDates.statusLabel(this, current.status))).append("\n\n");
        body.append(getString(R.string.stock_detail_items)).append("\n");
        if (current.items.isEmpty()) {
            body.append("—\n");
        } else {
            for (StockRequestItem item : current.items) {
                body.append(item.getProductName()).append("\n");
                body.append(getString(R.string.stock_detail_qty, item.getQuantity())).append("\n\n");
            }
        }
        if (!current.notes.isEmpty()) {
            body.append("\n").append(getString(R.string.stock_detail_notes, current.notes)).append("\n");
        }
        if (current.approvedAtMillis > 0L || !current.approvedByName.isEmpty()) {
            String approvedName = current.approvedByName.isEmpty() ? "—" : current.approvedByName;
            String approvedOn = StockDates.format(current.approvedAtMillis);
            body.append("\n").append(getString(R.string.stock_detail_approved,
                    approvedOn.isEmpty() ? "—" : approvedOn, approvedName));
        }
        if (current.retrievedAtMillis > 0L || !current.retrievedByName.isEmpty()) {
            String retrievedName = current.retrievedByName.isEmpty() ? "—" : current.retrievedByName;
            String retrievedOn = StockDates.format(current.retrievedAtMillis);
            body.append("\n\n").append(getString(R.string.stock_detail_retrieved,
                    retrievedOn.isEmpty() ? "—" : retrievedOn, retrievedName));
        }
        return body.toString().trim();
    }

    private void markReady() {
        if (!admin || request == null || !request.isPending()) return;
        readyButton.setEnabled(false);
        String notifyUid = request.requesterUid;
        String id = request.id;
        StockRepository.markReady(this, id, (success, message) -> runOnUiThread(() -> {
            readyButton.setEnabled(true);
            if (!success) {
                Toast.makeText(this, message != null ? message : getString(R.string.stock_save_failed), Toast.LENGTH_LONG).show();
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("requestId", id);
            NotificationUtils.writeInAppNotification(
                    notifyUid,
                    "stock_ready_" + id,
                    getString(R.string.stock_ready_notification_title),
                    getString(R.string.stock_ready_notification_body),
                    "stock_ready",
                    data);
            Toast.makeText(this, R.string.stock_marked_ready, Toast.LENGTH_SHORT).show();
            load();
        }));
    }

    private void markRetrieved() {
        if (!admin || request == null || !request.isReady()) return;
        retrievedButton.setEnabled(false);
        StockRepository.markRetrieved(this, request.id, (success, message) -> runOnUiThread(() -> {
            retrievedButton.setEnabled(true);
            if (!success) {
                Toast.makeText(this, message != null ? message : getString(R.string.stock_save_failed), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.stock_marked_retrieved, Toast.LENGTH_SHORT).show();
            load();
        }));
    }

    private void confirmDelete() {
        if (!admin || request == null || !request.isRetrieved()) return;
        String name = request.displayName();
        String date = StockDates.format(request.retrievedAtMillis);
        if (date.isEmpty()) date = "—";
        new AlertDialog.Builder(this)
                .setTitle(R.string.stock_delete_title)
                .setMessage(getString(R.string.stock_delete_message, name, date))
                .setNegativeButton(R.string.stock_cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.stock_delete_confirm, (dialog, which) -> deleteRetrieved())
                .show();
    }

    private void deleteRetrieved() {
        if (!admin || request == null || !request.isRetrieved()) return;
        deleteButton.setEnabled(false);
        StockRepository.deleteRetrieved(this, request.id, (success, message) -> runOnUiThread(() -> {
            deleteButton.setEnabled(true);
            if (!success) {
                Toast.makeText(this, message != null ? message : getString(R.string.stock_delete_failed), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.stock_deleted, Toast.LENGTH_SHORT).show();
            finish();
        }));
    }
}
