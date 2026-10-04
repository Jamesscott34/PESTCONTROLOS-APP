package com.grpc.grpc.stock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.stock.data.StockRepository;
import com.grpc.grpc.stock.model.StockWeeklyCheck;
import com.grpc.grpc.stock.util.StockDates;
import com.grpc.grpc.stock.util.StockWeekHelper;

/**
 * Stock entry. Technicians see their own weekly check. Admins open the user list.
 */
public class StockActivity extends AppCompatActivity {

    private TextView nameView;
    private TextView weekView;
    private Button nothingNeededButton;
    private String uid = "";
    private boolean openedUserList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (blockIfUnavailable()) return;
        setContentView(R.layout.activity_stock);
        nameView = findViewById(R.id.stockUserName);
        weekView = findViewById(R.id.stockWeekStatus);
        nothingNeededButton = findViewById(R.id.buttonNothingNeeded);
        Button requestButton = findViewById(R.id.buttonRequestStock);
        Button myRequestsButton = findViewById(R.id.buttonMyRequests);

        requestButton.setOnClickListener(v -> startActivity(new Intent(this, StockRequestActivity.class)));
        myRequestsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyStockRequestsActivity.class);
            intent.putExtra(MyStockRequestsActivity.EXTRA_SCOPE, MyStockRequestsActivity.SCOPE_SELF);
            startActivity(intent);
        });
        nothingNeededButton.setOnClickListener(v -> saveNothingNeeded());

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (isFinishing() || openedUserList) return;
            if (session == null || (!session.isTech && !session.isAdmin)) {
                Toast.makeText(this, R.string.stock_not_signed_in, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (session.isAdmin && !session.isTech) {
                openedUserList = true;
                startActivity(new Intent(this, StockUsersActivity.class));
                finish();
                return;
            }
            uid = StockRepository.authUid();
            nameView.setText(StockRepository.displayName(this));
            loadWeek();
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BuildConfig.IS_OFFLINE && uid != null && !uid.isEmpty()) {
            nameView.setText(StockRepository.displayName(this));
            loadWeek();
        }
    }

    private void loadWeek() {
        String weekKey = StockWeekHelper.currentWeekKey();
        StockRepository.loadWeeklyCheck(uid, weekKey, (loaded, check) -> runOnUiThread(() -> showWeek(loaded, check)));
    }

    private void showWeek(boolean loaded, @Nullable StockWeeklyCheck check) {
        if (isFinishing()) return;
        if (!loaded) {
            weekView.setText(R.string.stock_week_load_failed);
            nothingNeededButton.setVisibility(View.GONE);
            return;
        }
        if (check == null) {
            weekView.setText(R.string.stock_week_not_completed);
            nothingNeededButton.setVisibility(View.VISIBLE);
            return;
        }
        String date = StockDates.format(check.createdAtMillis);
        if (date.isEmpty()) date = StockWeekHelper.currentWeekKey();
        if (check.isNotNeeded()) {
            weekView.setText(getString(R.string.stock_week_completed_none, date));
        } else {
            weekView.setText(getString(R.string.stock_week_completed_request, date));
        }
        nothingNeededButton.setVisibility(View.GONE);
    }

    private void saveNothingNeeded() {
        nothingNeededButton.setEnabled(false);
        StockRepository.markNothingNeeded(this, (success, message) -> runOnUiThread(() -> {
            nothingNeededButton.setEnabled(true);
            if (!success) {
                Toast.makeText(this, message != null ? message : getString(R.string.stock_save_failed), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.stock_nothing_saved, Toast.LENGTH_SHORT).show();
            loadWeek();
        }));
    }

    private boolean blockIfUnavailable() {
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.stock_unavailable_offline, Toast.LENGTH_LONG).show();
            finish();
            return true;
        }
        return DemoFirebaseExpiryHelper.finishIfBlocked(this);
    }
}
