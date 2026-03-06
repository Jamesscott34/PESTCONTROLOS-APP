package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.io.File;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

/**
 * Global Search entry point:
 * Role-gated (admin-only for now): unified results list across Jobs/Contracts/Leads/Reports.
 * Kept low-risk: taps open the existing module screens, optionally with the correct contract owner selected.
 */
public class SearchActivity extends AppCompatActivity {

    public static final String EXTRA_SEARCH_QUERY = "SEARCH_QUERY";

    private static final String PREFS = "GRPC_SEARCH";
    private static final String KEY_RECENTS = "recent_queries";
    private static final int MAX_RECENTS = 10;
    private static final int MAX_RESULTS_PER_SECTION = 10;

    private String userName;
    private EditText searchBar;
    private LinearLayout recentsContainer;
    private TextView recentsLabel;
    private TextView statusText;
    private RecyclerView resultsRecycler;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private int searchGeneration = 0;

    private FirebaseFirestore db;
    private GlobalSearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        if (TextUtils.isEmpty(userName)) {
            userName = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_NAME", "User");
        }

        // Central RBAC
        SessionManager.ensureLoaded(this, null);
        if (TextUtils.isEmpty(userName) || !SessionManager.canSearch(this)) {
            android.widget.Toast.makeText(this, "Access denied.", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();

        searchBar = findViewById(R.id.globalSearchBar);
        recentsContainer = findViewById(R.id.recentSearchesContainer);
        recentsLabel = findViewById(R.id.recentSearchesLabel);
        statusText = findViewById(R.id.searchStatusText);
        resultsRecycler = findViewById(R.id.searchResultsRecycler);
        Button back = findViewById(R.id.searchBackButton);

        String initial = getIntent().getStringExtra(EXTRA_SEARCH_QUERY);
        if (!TextUtils.isEmpty(initial)) {
            searchBar.setText(initial);
            searchBar.setSelection(initial.length());
        }

        adapter = new GlobalSearchAdapter(item -> {
            if (item == null || item.type == GlobalSearchItemType.HEADER) return;
            openFromResult(item);
        });
        resultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        resultsRecycler.setAdapter(adapter);

        renderRecents();
        renderForQuery(getQuery());

        back.setOnClickListener(v -> finish());

        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleSearch();
            }
        });
    }

    private void scheduleSearch() {
        if (pendingSearchRunnable != null) handler.removeCallbacks(pendingSearchRunnable);
        pendingSearchRunnable = () -> renderForQuery(getQuery());
        handler.postDelayed(pendingSearchRunnable, 250);
    }

    private String getQuery() {
        return searchBar != null && searchBar.getText() != null ? searchBar.getText().toString().trim() : "";
    }

    private void renderForQuery(String q) {
        if (TextUtils.isEmpty(q)) {
            if (statusText != null) statusText.setVisibility(View.GONE);
            adapter.setItems(new ArrayList<>());
            return;
        }

        if (q.length() < 2) {
            if (statusText != null) {
                statusText.setText("Type at least 2 characters to search.");
                statusText.setVisibility(View.VISIBLE);
            }
            adapter.setItems(new ArrayList<>());
            return;
        }

        saveRecent(q);
        renderRecents();

        int gen = ++searchGeneration;
        if (statusText != null) {
            statusText.setText("Searching...");
            statusText.setVisibility(View.VISIBLE);
        }

        // Start with local report search immediately (fast)
        List<GlobalSearchItem> reports = searchLocalReports(q);
        List<GlobalSearchItem> merged = new ArrayList<>();
        appendSection(merged, "Reports", reports);
        adapter.setItems(merged);

        // Remote searches
        SearchAccumulator acc = new SearchAccumulator(q, gen);
        acc.run();
    }

    private void renderRecents() {
        List<String> recents = getRecents();
        if (recentsLabel != null) {
            recentsLabel.setVisibility(recents.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        if (recentsContainer == null) return;
        recentsContainer.removeAllViews();

        for (String q : recents) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(q);
            b.setOnClickListener(v -> {
                searchBar.setText(q);
                searchBar.setSelection(q.length());
                renderForQuery(q);
            });
            recentsContainer.addView(b);
        }
    }

    private void saveRecent(String query) {
        if (TextUtils.isEmpty(query)) return;
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(query);
        ordered.addAll(getRecents());

        List<String> trimmed = new ArrayList<>(ordered);
        if (trimmed.size() > MAX_RECENTS) trimmed = trimmed.subList(0, MAX_RECENTS);

        String joined = TextUtils.join("\n", trimmed);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_RECENTS, joined).apply();
    }

    private List<String> getRecents() {
        String joined = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_RECENTS, "");
        if (TextUtils.isEmpty(joined)) return new ArrayList<>();
        String[] parts = joined.split("\n");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) out.add(p);
        }
        return out;
    }

    private void openFromResult(GlobalSearchItem item) {
        String q = getQuery();
        switch (item.kind) {
            case JOB:
                openModule(ViewJobActivity.class, q, null);
                break;
            case CONTRACT:
                openModule(ViewContractActivity.class, q, item.owner);
                break;
            case LEAD:
                openModule(ViewLeadsActivity.class, q, null);
                break;
            case REPORT:
                openModule(ReportViewActivity.class, q, null);
                break;
        }
    }

    private void openModule(Class<?> activity, String query, String contractOwner) {
        Intent intent = new Intent(this, activity);
        intent.putExtra("USER_NAME", userName);
        if (!TextUtils.isEmpty(query)) intent.putExtra(EXTRA_SEARCH_QUERY, query);
        if (activity == ViewContractActivity.class && !TextUtils.isEmpty(contractOwner)) {
            intent.putExtra(ViewContractActivity.EXTRA_TECHNICIAN_OVERRIDE, contractOwner);
        }
        startActivity(intent);
    }

    private List<GlobalSearchItem> searchLocalReports(String q) {
        List<GlobalSearchItem> results = new ArrayList<>();
        try {
            File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
            if (!reportsFolder.exists()) return results;
            File[] files = reportsFolder.listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".pdf"));
            if (files == null) return results;
            String needle = q.toLowerCase();
            for (File f : files) {
                if (f == null) continue;
                String name = f.getName() != null ? f.getName() : "";
                if (name.toLowerCase().contains(needle)) {
                    results.add(GlobalSearchItem.result(GlobalSearchKind.REPORT, name, "Local report file", null));
                    if (results.size() >= MAX_RESULTS_PER_SECTION) break;
                }
            }
        } catch (Exception ignored) {}
        return results;
    }

    private void appendSection(List<GlobalSearchItem> merged, String title, List<GlobalSearchItem> items) {
        if (items == null || items.isEmpty()) return;
        merged.add(GlobalSearchItem.header(title + " (" + items.size() + ")"));
        merged.addAll(items);
    }

    private boolean isStale(int gen) {
        return gen != searchGeneration || isFinishing() || isDestroyed();
    }

    private final class SearchAccumulator {
        private final String query;
        private final String needle;
        private final int gen;

        private final List<GlobalSearchItem> jobs = new ArrayList<>();
        private final List<GlobalSearchItem> contracts = new ArrayList<>();
        private final List<GlobalSearchItem> leads = new ArrayList<>();
        private final List<GlobalSearchItem> reports = searchLocalReports(getQuery());

        private int pending = 0;

        SearchAccumulator(String query, int gen) {
            this.query = query;
            this.needle = query.toLowerCase();
            this.gen = gen;
        }

        void run() {
            // Jobs (same visibility rules as ViewJobActivity)
            pending++;
            Query jq = db.collection(FirestorePaths.JOBWORK).whereEqualTo("AssignedTech", userName);
            jq.get().addOnCompleteListener(t -> {
                if (!t.isSuccessful() || t.getResult() == null) {
                    doneOne();
                    return;
                }
                for (QueryDocumentSnapshot ds : t.getResult()) {
                    if (ds == null) continue;
                    String customer = asLower(ds.getString("CustomerName"));
                    String address = asLower(ds.getString("Address"));
                    String issue = asLower(ds.getString("IssueDetails"));
                    String contact = asLower(ds.getString("CustomerContact"));
                    String email = asLower(ds.getString("CustomerEmail"));
                    if (matchesAny(customer, address, issue, contact, email)) {
                        String title = safeTitle(ds.getString("CustomerName"), ds.getString("IssueDetails"), "Job");
                        String sub = "Jobs • " + safeOne(ds.getString("Address"), "No address");
                        jobs.add(GlobalSearchItem.result(GlobalSearchKind.JOB, title, sub, null));
                        if (jobs.size() >= MAX_RESULTS_PER_SECTION) break;
                    }
                }
                doneOne();
            });

            // Contracts across owners (keyed by ContractKey from users/{StaffID}.ContractKey).
            java.util.List<String> ownerKeys = new java.util.ArrayList<>();
            try {
                for (StaffDirectory.StaffProfile p : StaffDirectory.getCachedStaffProfiles()) {
                    if (p == null) continue;
                    String ck = p.contractKey != null ? p.contractKey.trim() : "";
                    if (!ck.isEmpty()) ownerKeys.add(ck);
                }
            } catch (Exception ignored) {}
            if (ownerKeys.isEmpty() && userName != null && !userName.trim().isEmpty()) {
                ownerKeys.add(userName.trim());
            }
            for (String ownerKey : ownerKeys) {
                pending++;
                String collectionName = StaffDirectory.getContractsCollectionNameFromAnyKey(ownerKey);
                db.collection(collectionName).get().addOnCompleteListener(t -> {
                    if (t.isSuccessful() && t.getResult() != null) {
                        for (QueryDocumentSnapshot ds : t.getResult()) {
                            if (ds == null) continue;
                            String name = asLower(ds.getString("name"));
                            String address = asLower(ds.getString("address"));
                            String contact = asLower(ds.getString("contact"));
                            String email = asLower(ds.getString("email"));
                            if (matchesAny(name, address, contact, email)) {
                                String title = safeOne(ds.getString("name"), "Contract");
                                String sub = "Contracts • " + ownerKey + " • " + safeOne(ds.getString("address"), "No address");
                                contracts.add(GlobalSearchItem.result(GlobalSearchKind.CONTRACT, title, sub, ownerKey));
                                if (contracts.size() >= MAX_RESULTS_PER_SECTION) break;
                            }
                        }
                    }
                    doneOne();
                });
            }

            // Leads
            pending++;
            db.collection("Leads").get().addOnCompleteListener(t -> {
                if (!t.isSuccessful() || t.getResult() == null) {
                    doneOne();
                    return;
                }
                for (QueryDocumentSnapshot ds : t.getResult()) {
                    if (ds == null) continue;
                    String premise = asLower(ds.getString("Premise Name"));
                    String addr = asLower(ds.getString("Premise Address"));
                    String added = asLower(ds.getString("Added By"));
                    String number = asLower(ds.getString("Number"));
                    String email = asLower(ds.getString("Email"));
                    String reason = asLower(ds.getString("Reason"));
                    if (matchesAny(premise, addr, added, number, email, reason)) {
                        String title = safeOne(ds.getString("Premise Name"), "Lead");
                        String sub = "Leads • " + safeOne(ds.getString("Reason"), "Lead") + " • " + safeOne(ds.getString("Premise Address"), "No address");
                        leads.add(GlobalSearchItem.result(GlobalSearchKind.LEAD, title, sub, null));
                        if (leads.size() >= MAX_RESULTS_PER_SECTION) break;
                    }
                }
                doneOne();
            });
        }

        private void doneOne() {
            pending--;
            if (pending > 0) return;
            if (isStale(gen)) return;

            List<GlobalSearchItem> merged = new ArrayList<>();
            appendSection(merged, "Jobs", jobs);
            appendSection(merged, "Contracts", contracts);
            appendSection(merged, "Leads", leads);
            appendSection(merged, "Reports", reports);

            adapter.setItems(merged);
            if (statusText != null) {
                int total = jobs.size() + contracts.size() + leads.size() + reports.size();
                statusText.setText(total == 0 ? "No results." : ("Found " + total + " results"));
                statusText.setVisibility(View.VISIBLE);
            }
        }

        private boolean matchesAny(String... fields) {
            if (TextUtils.isEmpty(needle)) return false;
            for (String f : fields) {
                if (f != null && f.contains(needle)) return true;
            }
            return false;
        }

        private String asLower(String s) {
            return s != null ? s.toLowerCase() : "";
        }

        private String safeOne(String s, String fallback) {
            if (s == null) return fallback;
            String t = s.trim();
            return t.isEmpty() ? fallback : t;
        }

        private String safeTitle(String a, String b, String fallback) {
            String aa = safeOne(a, "");
            String bb = safeOne(b, "");
            if (!aa.isEmpty() && !bb.isEmpty()) return aa + " — " + bb;
            if (!aa.isEmpty()) return aa;
            if (!bb.isEmpty()) return bb;
            return fallback;
        }
    }
}

