package com.grpc.grpc.search.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.contracts.ui.ViewContractActivity;
import com.grpc.grpc.jobs.ui.ViewJobActivity;
import com.grpc.grpc.leads.ui.ViewLeadsActivity;
import com.grpc.grpc.workview.ui.WorkViewActivity;
import com.grpc.grpc.reports.ui.CloudStorageBrowserActivity;
import com.grpc.grpc.core.*;

import android.content.Intent;
import android.net.Uri;
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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.io.File;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

/**
 * Global Search entry point:
 * Role-gated (canSearch = true): unified search across contracts, reports (local), commission (leads), work view.
 * Kept low-risk: taps open the existing module screens, optionally with the correct contract owner selected.
 */
public class SearchActivity extends AppCompatActivity {

    public static final String EXTRA_SEARCH_QUERY = "SEARCH_QUERY";

    private static final String PREFS = "GRPC_SEARCH";
    private static final String KEY_RECENTS = "recent_queries";
    private static final int MAX_RECENTS = 10;
    private static final int MAX_RESULTS_PER_SECTION = 10;
    /** Cap for Firebase Storage file hits (search spans contracts, ReportsYY, and other root folders). */
    private static final int MAX_REMOTE_STORAGE_RESULTS = 60;
    /** Max storage prefix depth under each report/year root (folder + file discovery). */
    private static final int STORAGE_TREE_DEPTH_REPORT_ROOT = 5;
    /** Max prefix depth under each {@code contracts/{id}/} path. */
    private static final int STORAGE_TREE_DEPTH_CONTRACT_BRANCH = 3;

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

        // No recent searches shown; search runs as you type
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
            if (statusText != null) {
                statusText.setText("Type to search contracts, reports, commission, work view.");
                statusText.setVisibility(View.VISIBLE);
            }
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
                openContractFromSearch(q, item.owner, item.contractDocumentId);
                break;
            case LEAD:
                openModule(ViewLeadsActivity.class, q, null);
                break;
            case LOCAL_REPORT:
                openLocalReportResult(item);
                break;
            case REMOTE_REPORT:
                openRemoteReportResult(item);
                break;
            case STORAGE_FOLDER:
                openStorageFolder(item.reportPath);
                break;
            case STORED_REPORTS:
                openStoredReports();
                break;
            case WORKVIEW:
                openWorkView(item.owner);
                break;
        }
    }

    private void openWorkView(String targetUser) {
        Intent intent = new Intent(this, WorkViewActivity.class);
        intent.putExtra("USER_NAME", TextUtils.isEmpty(targetUser) ? userName : targetUser);
        startActivity(intent);
    }

    private void openStoredReports() {
        Intent intent = new Intent(this, CloudStorageBrowserActivity.class);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_STORED_REPORTS);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
        startActivity(intent);
    }

    /** Drill into a Storage prefix from search (folder rows). */
    private void openStorageFolder(String folderPath) {
        if (TextUtils.isEmpty(folderPath)) return;
        Intent intent = new Intent(this, CloudStorageBrowserActivity.class);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_STORED_REPORTS);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_OPEN_FOLDER_PATH, folderPath.trim());
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
        startActivity(intent);
    }

    private void openContractFromSearch(String query, String contractOwner, String contractDocumentId) {
        Intent intent = new Intent(this, ViewContractActivity.class);
        intent.putExtra("USER_NAME", userName);
        if (!TextUtils.isEmpty(query)) intent.putExtra(EXTRA_SEARCH_QUERY, query);
        if (!TextUtils.isEmpty(contractOwner)) intent.putExtra(ViewContractActivity.EXTRA_TECHNICIAN_OVERRIDE, contractOwner);
        if (!TextUtils.isEmpty(contractDocumentId)) intent.putExtra(ViewContractActivity.EXTRA_OPEN_CONTRACT_ID, contractDocumentId);
        startActivity(intent);
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

    private void openLocalReportResult(GlobalSearchItem item) {
        if (item == null || TextUtils.isEmpty(item.localFilePath)) return;
        File file = new File(item.localFilePath);
        if (!file.exists()) {
            android.widget.Toast.makeText(this, "Local report not found.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        viewLocalPdf(file);
    }

    private void openRemoteReportResult(GlobalSearchItem item) {
        if (item == null || TextUtils.isEmpty(item.reportPath)) return;
        boolean isSuperAdmin = SessionManager.isSuperAdmin(this);
        CharSequence[] options = isSuperAdmin
                ? new CharSequence[]{"View", "Download", "Delete"}
                : new CharSequence[]{"View", "Download"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(item.title != null ? item.title : "Report")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        viewRemoteReport(item.reportPath);
                    } else if (which == 1) {
                        downloadRemoteReport(item.reportPath);
                    } else if (which == 2 && isSuperAdmin) {
                        deleteRemoteReport(item.reportPath);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void viewLocalPdf(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Failed to open report.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void viewRemoteReport(String reportPath) {
        FirebaseStorage.getInstance().getReference().child(reportPath)
                .getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/pdf");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> android.widget.Toast.makeText(this, "Failed to open report: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
    }

    private void downloadRemoteReport(String reportPath) {
        String fileName = reportPath.substring(reportPath.lastIndexOf('/') + 1);
        File reportsFolder = new File(getExternalFilesDir(null), TenantBranding.reportsFolderName(this));
        if (!reportsFolder.exists()) reportsFolder.mkdirs();
        File localFile = new File(reportsFolder, fileName);
        FirebaseStorage.getInstance().getReference().child(reportPath)
                .getFile(localFile)
                .addOnSuccessListener(taskSnapshot -> android.widget.Toast.makeText(this, "Saved to local reports: " + fileName, android.widget.Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> android.widget.Toast.makeText(this, "Failed to download report: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
    }

    private void deleteRemoteReport(String reportPath) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Report")
                .setMessage("Delete this stored report?")
                .setPositiveButton("Delete", (dialog, which) -> FirebaseStorage.getInstance().getReference().child(reportPath)
                        .delete()
                        .addOnSuccessListener(v -> {
                            cleanupContractReportMetadata(reportPath);
                            android.widget.Toast.makeText(this, "Stored report deleted.", android.widget.Toast.LENGTH_SHORT).show();
                            renderForQuery(getQuery());
                        })
                        .addOnFailureListener(e -> android.widget.Toast.makeText(this, "Delete failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cleanupContractReportMetadata(String reportPath) {
        if (TextUtils.isEmpty(reportPath) || !reportPath.startsWith("contracts/")) return;
        String[] parts = reportPath.split("/");
        if (parts.length < 3) return;
        String contractId = parts[1];
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACT_REPORTS)
                .document(contractId)
                .collection("reports")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String storagePath = doc.getString("storagePath");
                        if (reportPath.equals(storagePath)) {
                            doc.getReference().delete();
                        }
                    }
                });
    }

    private List<GlobalSearchItem> searchLocalReports(String q) {
        List<GlobalSearchItem> results = new ArrayList<>();
        try {
            File reportsFolder = new File(getExternalFilesDir(null), TenantBranding.reportsFolderName(this));
            if (!reportsFolder.exists()) return results;
            File[] files = reportsFolder.listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".pdf"));
            if (files == null) return results;
            String needle = q.toLowerCase();
            for (File f : files) {
                if (f == null) continue;
                String name = f.getName() != null ? f.getName() : "";
                if (name.toLowerCase().contains(needle)) {
                    results.add(GlobalSearchItem.resultLocalReport(name, "Local report file", f.getAbsolutePath()));
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
        private final List<GlobalSearchItem> remoteReports = new ArrayList<>();
        private final List<GlobalSearchItem> workviewEvents = new ArrayList<>();
        private final Set<String> remoteReportPaths = new LinkedHashSet<>();
        private final Set<String> remoteFolderPaths = new LinkedHashSet<>();

        private int pending = 0;

        SearchAccumulator(String query, int gen) {
            this.query = query;
            this.needle = query.toLowerCase();
            this.gen = gen;
        }

        void run() {
            // Jobs (same visibility rules as ViewJobActivity)
            pending++;
            SessionManager.ensureLoaded(SearchActivity.this, null);
            Query jq;
            if (SessionManager.seesAllJobs(SearchActivity.this)) {
                jq = db.collection(FirestorePaths.JOBWORK);
            } else {
                String ck = SessionManager.getContractKey(SearchActivity.this);
                if (ck == null || ck.trim().isEmpty()) {
                    ck = userName != null ? userName.trim() : "";
                }
                String techDisplay = StaffDirectory.capitalizeContractKey(ck.trim().toLowerCase(Locale.getDefault()));
                jq = db.collection(FirestorePaths.JOBWORK).whereEqualTo("AssignedTech", techDisplay);
            }
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

            pending++;
            searchContractsForCurrentRole();

            // Leads (Commission)
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

            // Work View events (current user's calendar)
            String wvUser = userName != null ? userName.trim() : "";
            if (!wvUser.isEmpty()) {
                pending++;
                String wvCollection = wvUser.toLowerCase(java.util.Locale.getDefault()) + "_workview";
                db.collection(wvCollection).get().addOnCompleteListener(t -> {
                    if (t.isSuccessful() && t.getResult() != null) {
                        for (QueryDocumentSnapshot ds : t.getResult()) {
                            if (ds == null) continue;
                            String eventName = asLower(ds.getString("eventName"));
                            String address = asLower(ds.getString("address"));
                            String issue = asLower(ds.getString("issue"));
                            String notes = asLower(ds.getString("notes"));
                            String date = ds.getString("date");
                            String time = ds.getString("time");
                            if (matchesAny(eventName, address, issue, notes)) {
                                String title = safeOne(ds.getString("eventName"), "Work View event");
                                String sub = "Work View • " + safeOne(date, "?") + " " + safeOne(time, "");
                                workviewEvents.add(GlobalSearchItem.result(GlobalSearchKind.WORKVIEW, title, sub, wvUser));
                                if (workviewEvents.size() >= MAX_RESULTS_PER_SECTION) break;
                            }
                        }
                    }
                    doneOne();
                });
            }

            pending++;
            searchFirebaseStorageRoots(() -> doneOne());
        }

        private void searchContractsForCurrentRole() {
            SessionManager.Session session = SessionManager.getCached(SearchActivity.this);
            boolean adminSearch = session != null && session.isAdmin;
            Query contractQuery;
            if (adminSearch) {
                contractQuery = db.collection(FirestorePaths.CONTRACTS);
            } else {
                String contractKey = session != null ? session.contractKey : SessionManager.getContractKey(SearchActivity.this);
                if (TextUtils.isEmpty(contractKey) && userName != null) {
                    contractKey = userName.trim();
                }
                if (TextUtils.isEmpty(contractKey)) {
                    doneOne();
                    return;
                }
                String assignedTechLower = contractKey.trim().toLowerCase(Locale.getDefault());
                contractQuery = db.collection(FirestorePaths.CONTRACTS).whereEqualTo("assignedTech", assignedTechLower);
            }

            contractQuery.get().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult() != null) {
                    for (QueryDocumentSnapshot ds : t.getResult()) {
                        if (ds == null) continue;
                        String name = asLower(ds.getString("name"));
                        String address = asLower(ds.getString("address"));
                        String contact = asLower(ds.getString("contact"));
                        String email = asLower(ds.getString("email"));
                        if (matchesAny(name, address, contact, email)) {
                            String title = safeOne(ds.getString("name"), "Contract");
                            String ownerKey = ds.getString("assignedTech");
                            if (TextUtils.isEmpty(ownerKey)) ownerKey = adminSearch ? "Unassigned" : SessionManager.getContractKey(SearchActivity.this);
                            String sub = "Contracts • " + safeOne(ownerKey, "Unassigned") + " • " + safeOne(ds.getString("address"), "No address");
                            String docId = ds.getId();
                            contracts.add(GlobalSearchItem.resultContract(title, sub, ownerKey, docId != null ? docId : ""));
                            if (docId != null && !docId.trim().isEmpty()) {
                                enqueueContractFolderSearch(docId.trim(), title);
                            }
                            if (contracts.size() >= MAX_RESULTS_PER_SECTION) break;
                        }
                    }
                }
                doneOne();
            });
        }

        private void enqueueContractFolderSearch(String contractId, String contractTitle) {
            if (TextUtils.isEmpty(contractId) || remoteReports.size() >= MAX_REMOTE_STORAGE_RESULTS) return;
            pending++;
            FirebaseStorage.getInstance()
                    .getReference()
                    .child("contracts/" + contractId)
                    .listAll()
                    .addOnSuccessListener(listResult -> {
                        for (StorageReference item : listResult.getItems()) {
                            if (remoteReports.size() >= MAX_REMOTE_STORAGE_RESULTS) break;
                            addRemoteReport("Firebase · contracts • " + safeOne(contractTitle, "Contract"),
                                    "contracts/" + contractId + "/" + item.getName(), item.getName());
                        }
                        doneOne();
                    })
                    .addOnFailureListener(e -> doneOne());
        }

        /**
         * Lists all bucket root folders when rules allow; always searches {@code contracts/...} for filename
         * matches (so queries like "bank" find {@code the_bank_date.pdf} without a Firestore contract hit).
         * Falls back to probing ReportsYY-style folders if root list is denied.
         */
        private void searchFirebaseStorageRoots(Runnable onComplete) {
            StorageFolderHelper.discoverAllRootFolders(
                    folderNames -> {
                        List<String> roots = folderNames != null ? folderNames : new ArrayList<>();
                        int nonContractRoots = 0;
                        for (String n : roots) {
                            if (n == null || n.trim().isEmpty()) continue;
                            if ("contracts".equalsIgnoreCase(n.trim())) continue;
                            nonContractRoots++;
                        }
                        final int total = 1 + nonContractRoots;
                        final int[] pending = {total};
                        Runnable childDone = () -> {
                            pending[0]--;
                            if (pending[0] <= 0) onComplete.run();
                        };
                        searchContractsStorageForNeedle(childDone);
                        for (String folderName : roots) {
                            if (folderName == null || folderName.trim().isEmpty()) continue;
                            if ("contracts".equalsIgnoreCase(folderName.trim())) continue;
                            StorageReference ref = FirebaseStorage.getInstance().getReference().child(folderName.trim());
                            searchStorageSubtree(ref, folderName.trim(), STORAGE_TREE_DEPTH_REPORT_ROOT, childDone);
                        }
                    },
                    () -> {
                        final int[] pending = {2};
                        Runnable childDone = () -> {
                            pending[0]--;
                            if (pending[0] <= 0) onComplete.run();
                        };
                        searchReportFoldersFallback(childDone);
                        searchContractsStorageForNeedle(childDone);
                    });
        }

        private void searchReportFoldersFallback(Runnable onComplete) {
            StorageFolderHelper.discoverReportFolders(folderNames -> {
                if (folderNames == null || folderNames.isEmpty()) {
                    onComplete.run();
                    return;
                }
                final int[] pendingFolders = {folderNames.size()};
                for (String folderName : folderNames) {
                    StorageReference reportRoot = FirebaseStorage.getInstance().getReference().child(folderName);
                    searchStorageSubtree(reportRoot, folderName, STORAGE_TREE_DEPTH_REPORT_ROOT, () -> {
                        pendingFolders[0]--;
                        if (pendingFolders[0] <= 0) onComplete.run();
                    });
                }
            });
        }

        /**
         * {@code contracts/{id}/…}: matching folder names, nested prefixes, and files (underscore/space tolerant).
         */
        private void searchContractsStorageForNeedle(Runnable onComplete) {
            SessionManager.Session session = SessionManager.getCached(SearchActivity.this);
            if (session == null || !session.isAdmin) {
                onComplete.run();
                return;
            }
            FirebaseStorage.getInstance().getReference().child("contracts").listAll()
                    .addOnSuccessListener(listResult -> {
                        List<StorageReference> idFolders = listResult.getPrefixes();
                        if (idFolders == null || idFolders.isEmpty()) {
                            onComplete.run();
                            return;
                        }
                        final int[] pm = {idFolders.size()};
                        for (StorageReference idFolder : idFolders) {
                            String seg = idFolder.getName() != null ? idFolder.getName() : "";
                            String base = "contracts/" + seg;
                            if (textMatchesStorageNeedle(seg)) {
                                addRemoteFolder(seg, base);
                            }
                            enumerateStorageBranch(idFolder, base, 0, STORAGE_TREE_DEPTH_CONTRACT_BRANCH, () -> {
                                pm[0]--;
                                if (pm[0] <= 0) onComplete.run();
                            });
                        }
                    })
                    .addOnFailureListener(e -> onComplete.run());
        }

        /** List {@code ref} and descend into subfolders up to {@code maxDepth} (exclusive: depth 0 lists this level only). */
        private void searchStorageSubtree(StorageReference ref, String pathSoFar, int maxDepth, Runnable onComplete) {
            enumerateStorageBranch(ref, pathSoFar, 0, maxDepth, onComplete);
        }

        /**
         * @param depth current depth from {@code ref} root of this subtree (0 = list this ref's files and child prefixes)
         * @param maxDepth max child levels to recurse (e.g. 5 allows root + 4 levels of subfolders)
         */
        private void enumerateStorageBranch(StorageReference ref, String pathSoFar, int depth, int maxDepth, Runnable onComplete) {
            if (remoteReports.size() >= MAX_REMOTE_STORAGE_RESULTS) {
                onComplete.run();
                return;
            }
            ref.listAll()
                    .addOnSuccessListener(listResult -> {
                        for (StorageReference f : listResult.getItems()) {
                            tryAddRemoteFirebaseFileAtPath(pathSoFar, f.getName());
                        }
                        List<StorageReference> prefs = listResult.getPrefixes();
                        if (prefs == null || prefs.isEmpty() || depth >= maxDepth) {
                            onComplete.run();
                            return;
                        }
                        final int[] pending = {prefs.size()};
                        Runnable childDone = () -> {
                            pending[0]--;
                            if (pending[0] <= 0) onComplete.run();
                        };
                        for (StorageReference pref : prefs) {
                            String seg = pref.getName() != null ? pref.getName() : "";
                            String childPath = pathSoFar.isEmpty() ? seg : pathSoFar + "/" + seg;
                            if (textMatchesStorageNeedle(seg)) {
                                addRemoteFolder(seg, childPath);
                            }
                            enumerateStorageBranch(pref, childPath, depth + 1, maxDepth, childDone);
                        }
                    })
                    .addOnFailureListener(e -> onComplete.run());
        }

        /** Folder or file segment / name vs query (handles {@code the bank} vs {@code the_bank}). */
        private boolean textMatchesStorageNeedle(String text) {
            if (TextUtils.isEmpty(text) || TextUtils.isEmpty(needle)) return false;
            String t = text.toLowerCase(Locale.getDefault());
            if (t.contains(needle)) return true;
            String tn = t.replace('_', ' ').replaceAll("\\s+", " ").trim();
            String nn = needle.replace('_', ' ').replaceAll("\\s+", " ").trim();
            return tn.contains(nn);
        }

        private void tryAddRemoteFirebaseFileAtPath(String pathSoFar, String fileName) {
            if (remoteReports.size() >= MAX_REMOTE_STORAGE_RESULTS) return;
            if (fileName == null || !textMatchesStorageNeedle(fileName)) return;
            String path = pathSoFar + "/" + fileName;
            String subtitle = "File · " + pathSoFar.replace("/", " · ");
            addRemoteReport(subtitle, path, fileName);
        }

        private void addRemoteFolder(String segment, String fullPath) {
            if (TextUtils.isEmpty(fullPath) || remoteReports.size() >= MAX_REMOTE_STORAGE_RESULTS) return;
            if (!remoteFolderPaths.add(fullPath)) return;
            int slash = fullPath.lastIndexOf('/');
            String parent = slash > 0 ? fullPath.substring(0, slash) : "";
            String subtitle = parent.isEmpty()
                    ? "Firebase Storage"
                    : "Subfolder of: " + parent.replace("/", " · ");
            remoteReports.add(GlobalSearchItem.resultStorageFolder(
                    "Folder · " + segment,
                    subtitle,
                    fullPath));
        }

        private void addRemoteReport(String sourceLabel, String storagePath, String fileName) {
            if (TextUtils.isEmpty(storagePath) || TextUtils.isEmpty(fileName)) return;
            if (remoteReports.size() >= MAX_REMOTE_STORAGE_RESULTS) return;
            if (!remoteReportPaths.add(storagePath)) return;
            remoteReports.add(GlobalSearchItem.resultRemoteReport(fileName, sourceLabel, storagePath));
        }

        /** One search section per top-level bucket folder (e.g. contracts, Reports25). */
        private void appendFirebaseStorageByRootFolder(List<GlobalSearchItem> merged) {
            if (remoteReports.isEmpty()) return;
            Map<String, List<GlobalSearchItem>> byRoot = new LinkedHashMap<>();
            for (GlobalSearchItem item : remoteReports) {
                if (item == null || TextUtils.isEmpty(item.reportPath)) continue;
                String root = storageRootSegment(item.reportPath);
                if (root.isEmpty()) root = "Storage";
                byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(item);
            }
            for (Map.Entry<String, List<GlobalSearchItem>> e : byRoot.entrySet()) {
                appendSection(merged, "Firebase · " + e.getKey(), e.getValue());
            }
        }

        private static String storageRootSegment(String path) {
            if (path == null || path.isEmpty()) return "";
            int slash = path.indexOf('/');
            return slash > 0 ? path.substring(0, slash) : path;
        }

        private void doneOne() {
            pending--;
            if (pending > 0) return;
            if (isStale(gen)) return;

            List<GlobalSearchItem> merged = new ArrayList<>();
            appendSection(merged, "Jobs", jobs);
            appendSection(merged, "Contracts", contracts);
            List<GlobalSearchItem> contractOwnerWorkView = new ArrayList<>();
            Set<String> workViewOwnerSeen = new LinkedHashSet<>();
            for (GlobalSearchItem item : contracts) {
                if (item == null || item.kind != GlobalSearchKind.CONTRACT) continue;
                if (item.owner == null || item.owner.trim().isEmpty()) continue;
                String nk = item.owner.trim().toLowerCase(Locale.getDefault());
                if (!workViewOwnerSeen.add(nk)) continue;
                String label = StaffDirectory.capitalizeContractKey(nk);
                contractOwnerWorkView.add(GlobalSearchItem.result(GlobalSearchKind.WORKVIEW,
                        "Work View — " + label,
                        "Calendar for this technician (" + nk + "_workview)",
                        item.owner.trim()));
            }
            appendSection(merged, "Work View (contract owner)", contractOwnerWorkView);
            appendSection(merged, "Commission (Leads)", leads);
            appendSection(merged, "Local Reports", reports);
            appendFirebaseStorageByRootFolder(merged);
            List<GlobalSearchItem> storedReportsLink = new ArrayList<>();
            storedReportsLink.add(GlobalSearchItem.result(GlobalSearchKind.STORED_REPORTS,
                    "Browse Firebase Storage",
                    "Open the browser to explore all folders (reports, contracts, …)",
                    null));
            appendSection(merged, "Browse storage", storedReportsLink);
            appendSection(merged, "Work View", workviewEvents);

            adapter.setItems(merged);
            if (statusText != null) {
                int total = jobs.size() + contracts.size() + contractOwnerWorkView.size() + leads.size() + reports.size() + remoteReports.size() + workviewEvents.size() + 1;
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

