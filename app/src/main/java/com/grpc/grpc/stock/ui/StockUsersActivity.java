package com.grpc.grpc.stock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.stock.adapter.StockUserAdapter;
import com.grpc.grpc.stock.data.StockRepository;
import com.grpc.grpc.stock.model.StockRequest;
import com.grpc.grpc.stock.model.StockUserSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin and super_admin user list. Labels are names, never Firebase UIDs.
 */
public class StockUsersActivity extends AppCompatActivity {

    private final List<StockUserSummary> users = new ArrayList<>();
    private StockUserAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (blockIfUnavailable()) return;
        setContentView(R.layout.activity_stock_users);
        emptyView = findViewById(R.id.stockUsersEmpty);
        ListView listView = findViewById(R.id.stockUsersList);
        Button requestStock = findViewById(R.id.buttonRequestStock);
        Button allPending = findViewById(R.id.buttonAllPending);
        if (requestStock != null) {
            requestStock.setOnClickListener(v -> startActivity(new Intent(this, StockRequestActivity.class)));
        }
        adapter = new StockUserAdapter(this, users);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= users.size()) return;
            StockUserSummary person = users.get(position);
            Intent intent = new Intent(this, MyStockRequestsActivity.class);
            intent.putExtra(MyStockRequestsActivity.EXTRA_SCOPE, MyStockRequestsActivity.SCOPE_USER);
            intent.putExtra(MyStockRequestsActivity.EXTRA_USER_UID, person.uid);
            intent.putExtra(MyStockRequestsActivity.EXTRA_USER_NAME, person.name);
            startActivity(intent);
        });
        allPending.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyStockRequestsActivity.class);
            intent.putExtra(MyStockRequestsActivity.EXTRA_SCOPE, MyStockRequestsActivity.SCOPE_ALL);
            intent.putExtra(MyStockRequestsActivity.EXTRA_FILTER, StockRequest.STATUS_PENDING);
            startActivity(intent);
        });

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.isAdmin) {
                Toast.makeText(this, R.string.stock_admin_only, Toast.LENGTH_LONG).show();
                finish();
            }
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BuildConfig.IS_OFFLINE) loadUsers();
    }

    private void loadUsers() {
        StockRepository.loadUserSummaries(this, (loaded, people) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            users.clear();
            if (loaded) users.addAll(people);
            adapter.notifyDataSetChanged();
            emptyView.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
            if (!loaded) {
                emptyView.setText(R.string.stock_users_load_failed);
            } else if (users.isEmpty()) {
                emptyView.setText(R.string.stock_no_users);
            }
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
