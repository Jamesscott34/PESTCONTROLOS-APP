package com.grpc.grpc.stock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.stock.adapter.StockRequestAdapter;
import com.grpc.grpc.stock.data.StockRepository;
import com.grpc.grpc.stock.model.StockRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Request history. Technicians only see their own rows.
 * Admins can open one person or every request in the current Firebase project.
 */
public class MyStockRequestsActivity extends AppCompatActivity {

    public static final String EXTRA_SCOPE = "STOCK_SCOPE";
    public static final String EXTRA_USER_UID = "STOCK_USER_UID";
    public static final String EXTRA_USER_NAME = "STOCK_USER_NAME";
    public static final String EXTRA_FILTER = "STOCK_FILTER";

    public static final String SCOPE_SELF = "self";
    public static final String SCOPE_USER = "user";
    public static final String SCOPE_ALL = "all";

    private static final String FILTER_ALL = "all";

    private final List<StockRequest> allRows = new ArrayList<>();
    private final List<StockRequest> visibleRows = new ArrayList<>();
    private StockRequestAdapter adapter;
    private TextView emptyView;
    private TextView titleView;
    private String scope = SCOPE_SELF;
    private String userUid = "";
    private String userName = "";
    private String filter = FILTER_ALL;
    private boolean showRequester;
    private int loadGeneration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (blockIfUnavailable()) return;
        setContentView(R.layout.activity_my_stock_requests);
        titleView = findViewById(R.id.stockHistoryTitle);
        emptyView = findViewById(R.id.stockHistoryEmpty);
        ListView listView = findViewById(R.id.stockHistoryList);
        adapter = new StockRequestAdapter(this, visibleRows, false);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visibleRows.size()) return;
            Intent intent = new Intent(this, StockRequestDetailActivity.class);
            intent.putExtra(StockRequestDetailActivity.EXTRA_REQUEST_ID, visibleRows.get(position).id);
            startActivity(intent);
        });

        findViewById(R.id.buttonFilterPending).setOnClickListener(v -> applyFilter(StockRequest.STATUS_PENDING));
        findViewById(R.id.buttonFilterReady).setOnClickListener(v -> applyFilter(StockRequest.STATUS_READY));
        findViewById(R.id.buttonFilterRetrieved).setOnClickListener(v -> applyFilter(StockRequest.STATUS_RETRIEVED));
        findViewById(R.id.buttonFilterAll).setOnClickListener(v -> applyFilter(FILTER_ALL));

        String requestedScope = getIntent().getStringExtra(EXTRA_SCOPE);
        if (requestedScope != null) scope = requestedScope;
        userUid = getIntent().getStringExtra(EXTRA_USER_UID);
        userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        String requestedFilter = getIntent().getStringExtra(EXTRA_FILTER);
        if (requestedFilter != null && !requestedFilter.isEmpty()) filter = requestedFilter;

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            boolean admin = session != null && session.isAdmin;
            if (SCOPE_ALL.equals(scope) || SCOPE_USER.equals(scope)) {
                if (!admin) {
                    scope = SCOPE_SELF;
                }
            }
            if (SCOPE_SELF.equals(scope)) {
                userUid = StockRepository.authUid();
                userName = StockRepository.displayName(this);
            }
            showRequester = SCOPE_ALL.equals(scope);
            adapter = new StockRequestAdapter(this, visibleRows, showRequester);
            listView.setAdapter(adapter);
            titleView.setText(titleForScope());
            highlightFilter();
            load();
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BuildConfig.IS_OFFLINE && userUid != null && (SCOPE_ALL.equals(scope) || !userUid.isEmpty())) {
            load();
        }
    }

    private void load() {
        int generation = ++loadGeneration;
        if (SCOPE_ALL.equals(scope)) {
            if (FILTER_ALL.equals(filter)) {
                loadAllStatuses(generation);
            } else {
                StockRepository.loadRequestsByStatus(filter, (loaded, requests) -> {
                    if (generation != loadGeneration) return;
                    showLoaded(loaded, requests, true);
                });
            }
            return;
        }
        StockRepository.loadRequestsForUser(userUid, (loaded, requests) -> {
            if (generation != loadGeneration) return;
            showLoaded(loaded, requests, false);
        });
    }

    private void loadAllStatuses(int generation) {
        List<StockRequest> merged = new ArrayList<>();
        int[] pending = {3};
        boolean[] failed = {false};
        StockRepository.RequestListCallback part = (loaded, requests) -> runOnUiThread(() -> {
            if (generation != loadGeneration) return;
            if (!loaded) failed[0] = true;
            else merged.addAll(requests);
            pending[0]--;
            if (pending[0] > 0) return;
            merged.sort((a, b) -> Long.compare(b.createdAtMillis, a.createdAtMillis));
            showLoaded(!failed[0], merged, true);
        });
        StockRepository.loadRequestsByStatus(StockRequest.STATUS_PENDING, part);
        StockRepository.loadRequestsByStatus(StockRequest.STATUS_READY, part);
        StockRepository.loadRequestsByStatus(StockRequest.STATUS_RETRIEVED, part);
    }

    private void showLoaded(boolean loaded, @NonNull List<StockRequest> requests, boolean alreadyFiltered) {
        if (isFinishing()) return;
        allRows.clear();
        allRows.addAll(requests);
        visibleRows.clear();
        for (StockRequest request : allRows) {
            if (alreadyFiltered || FILTER_ALL.equals(filter) || filter.equals(request.status)) {
                visibleRows.add(request);
            }
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(visibleRows.isEmpty() ? View.VISIBLE : View.GONE);
        if (!loaded) emptyView.setText(R.string.stock_requests_load_failed);
        else emptyView.setText(R.string.stock_no_requests);
    }

    private void applyFilter(@NonNull String next) {
        filter = next;
        highlightFilter();
        if (SCOPE_ALL.equals(scope)) {
            load();
        } else {
            showLoaded(true, new ArrayList<>(allRows), false);
        }
    }

    private void highlightFilter() {
        setSelected(R.id.buttonFilterPending, StockRequest.STATUS_PENDING.equals(filter));
        setSelected(R.id.buttonFilterReady, StockRequest.STATUS_READY.equals(filter));
        setSelected(R.id.buttonFilterRetrieved, StockRequest.STATUS_RETRIEVED.equals(filter));
        setSelected(R.id.buttonFilterAll, FILTER_ALL.equals(filter));
    }

    private void setSelected(int id, boolean selected) {
        Button button = findViewById(id);
        if (button != null) button.setAlpha(selected ? 1f : 0.55f);
    }

    @NonNull
    private String titleForScope() {
        if (SCOPE_USER.equals(scope) && userName != null && !userName.trim().isEmpty()) {
            return userName.trim();
        }
        if (SCOPE_ALL.equals(scope)) return getString(R.string.stock_history_title);
        if (userName != null && !userName.trim().isEmpty()) return userName.trim();
        return getString(R.string.stock_my_requests);
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
