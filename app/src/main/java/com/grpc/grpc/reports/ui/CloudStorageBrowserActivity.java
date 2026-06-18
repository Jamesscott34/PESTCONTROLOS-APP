package com.grpc.grpc.reports.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.ContractStorageDisplayHelper;
import com.grpc.grpc.core.ContractStoragePathHelper;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StorageFolderHelper;
import com.grpc.grpc.core.StorageMetricsHelper;
import com.grpc.grpc.core.TenantBranding;

import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Drill-down browser for Firebase Storage: {@code contracts/{contractId}/…},
 * annual report roots ({@code Reports26}-style), and stored-reports bucket root (all top-level folders).
 */
public class CloudStorageBrowserActivity extends AppCompatActivity {

    private static final String LOCAL_DOWNLOAD_SUBDIR = "CloudDownloads";

    public static final String EXTRA_ENTRY_MODE = "CLOUD_ENTRY_MODE";
    public static final int MODE_CONTRACTS = 1;
    public static final int MODE_REPORTS = 2;
    /** Same drill-down UI as report folders; bucket root via {@link StorageFolderHelper#discoverAllRootFolders}. */
    public static final int MODE_STORED_REPORTS = 3;
    /** Browse a specific Firebase Storage root folder (e.g. "management jobs", "JobWorkReports"). */
    public static final int MODE_FIXED_ROOT = 4;

    public static final String EXTRA_USER_NAME = "USER_NAME";
    public static final String EXTRA_FIXED_ROOT_PATH = "FIXED_ROOT_PATH";
    public static final String EXTRA_FIXED_ROOT_TITLE = "FIXED_ROOT_TITLE";
    public static final String EXTRA_FIXED_ROOT_HINT = "FIXED_ROOT_HINT";

    /** Opens this folder path directly (e.g. from Search). Same extra as {@link StoredReportsActivity#EXTRA_OPEN_FOLDER_PATH}. */
    public static final String EXTRA_OPEN_FOLDER_PATH = "OPEN_FOLDER_PATH";

    /** When true, tapping a {@code .pdf} file finishes with {@link #EXTRA_RESULT_STORAGE_PATH} instead of opening externally. */
    public static final String EXTRA_RETURN_STORAGE_PATH_ON_PICK = "RETURN_STORAGE_PATH_ON_PICK";

    public static final String EXTRA_RESULT_STORAGE_PATH = "RESULT_STORAGE_PATH";

    /**
     * When {@link #EXTRA_ENTRY_MODE} is {@link #MODE_STORED_REPORTS}: if true, list every top-level folder
     * (companies, invoices, …). If false (default), hide those paths for normal “Stored Reports” entry points.
     * Set only from {@link com.grpc.grpc.admin.ui.AdminDashboardActivity}.
     */
    public static final String EXTRA_SHOW_ALL_TOP_LEVEL_STORAGE_FOLDERS = "SHOW_ALL_TOP_LEVEL_STORAGE_FOLDERS";

    private TextView pathLabel;
    private TextView screenHint;
    @Nullable
    private EditText contractSearchBar;
    private CloudStorageEntryAdapter adapter;
    @Nullable
    private Button buttonCloudNewFolder;
    @Nullable
    private Button buttonCloudUpload;

    private ActivityResultLauncher<Intent> uploadFileLauncher;

    private int entryMode = MODE_CONTRACTS;
    /**
     * Current storage path under the bucket, or null when {@link #MODE_REPORTS} and picking a report root folder.
     */
    @Nullable
    private String currentPath;
    private String userName;
    private String fixedRootPath;
    private String fixedRootTitle;
    private String fixedRootHint;
    /** Resolved bucket name for {@link #MODE_FIXED_ROOT} (handles grpc {@code managment jobs} typo). */
    private String resolvedFixedRootPath = "";
    /** Unfiltered bucket root when opened from admin dashboard ({@link #EXTRA_SHOW_ALL_TOP_LEVEL_STORAGE_FOLDERS}). */
    private boolean showAllTopLevelStorageFolders = false;

    private interface FixedRootCallback {
        void onResolved(String resolvedPath);
    }

    /**
     * {@code parent} = listed from bucket {@code Reports/}, only ReportsXX names shown.
     * {@code probe} = fell back to root-level ReportsXX paths (legacy layout).
     */
    private String reportsRootListingMode = "";

    private static final String REPORTS_SEGMENT = "Reports";
    /** Matches {@code Reports} + digits (e.g. Reports26, Reports27). */
    private static final Pattern REPORTS_NUMERIC_ROOT =
            Pattern.compile("(?i)^Reports\\d+$");

    /** Firestore contract doc id → company name for {@code contracts/} UI labels (paths stay id-based). */
    private final Map<String, String> contractIdToDisplayName = new HashMap<>();

    /** Full list for client-side search filtering in contract browser mode. */
    private final List<CloudStorageEntryAdapter.Entry> currentFullEntries = new ArrayList<>();

    private static final int REPORT_SEARCH_MIN_CHARS = 2;
    private static final int REPORT_SEARCH_MAX_RESULTS = 200;
    private static final long REPORT_SEARCH_DEBOUNCE_MS = 400L;

    private boolean reportSearchActive = false;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingReportSearch;
    @Nullable
    private ActionMode cloudActionMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uploadFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    uploadUriIntoCurrentFolder(uri);
                });
        setContentView(R.layout.activity_cloud_storage_browser);

        userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        entryMode = getIntent().getIntExtra(EXTRA_ENTRY_MODE, MODE_CONTRACTS);
        showAllTopLevelStorageFolders = getIntent().getBooleanExtra(EXTRA_SHOW_ALL_TOP_LEVEL_STORAGE_FOLDERS, false);
        fixedRootPath = getIntent().getStringExtra(EXTRA_FIXED_ROOT_PATH);
        fixedRootTitle = getIntent().getStringExtra(EXTRA_FIXED_ROOT_TITLE);
        fixedRootHint = getIntent().getStringExtra(EXTRA_FIXED_ROOT_HINT);

        if (isBlockedUser()) {
            Toast.makeText(this, R.string.cloud_storage_unavailable_offline, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) {
            return;
        }

        SessionManager.ensureLoaded(this, s -> runOnUiThread(() -> {
            updateNewFolderButtonVisibility();
            refreshCloudActionModeMenu();
        }));

        pathLabel = findViewById(R.id.cloudStoragePathLabel);
        screenHint = findViewById(R.id.cloudStorageScreenHint);
        contractSearchBar = findViewById(R.id.cloudStorageSearchBar);
        if (contractSearchBar != null) {
            contractSearchBar.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyStorageSearchFilter();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
        RecyclerView recyclerView = findViewById(R.id.cloudStorageRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CloudStorageEntryAdapter(new CloudStorageEntryAdapter.Listener() {
            @Override
            public void onEntryClick(CloudStorageEntryAdapter.Entry entry) {
                if (adapter.isSelectionMode()) {
                    syncCloudActionMode();
                    return;
                }
                CloudStorageBrowserActivity.this.onEntryClick(entry);
            }

            @Override
            public void onEntryLongClick(CloudStorageEntryAdapter.Entry entry) {
                if (entry.folder || !supportsCloudMultiSelect()) {
                    return;
                }
                String path = entry.resolveStoragePath(currentPath);
                if (path == null && adapter != null) {
                    path = entry.resolveStoragePath(adapter.getListCurrentPath());
                }
                if (path == null) {
                    return;
                }
                if (!adapter.isSelectionMode()) {
                    startCloudSelectionActionMode();
                }
                adapter.selectAllVisibleFilePaths(null);
                syncCloudActionMode();
            }
        });
        recyclerView.setAdapter(adapter);

        buttonCloudNewFolder = findViewById(R.id.buttonCloudNewFolder);
        if (buttonCloudNewFolder != null) {
            buttonCloudNewFolder.setOnClickListener(v -> showCreateFolderDialog());
        }
        buttonCloudUpload = findViewById(R.id.buttonCloudUpload);
        if (buttonCloudUpload != null) {
            buttonCloudUpload.setOnClickListener(v -> startPickFileForUpload());
        }

        Button back = findViewById(R.id.buttonCloudStorageBack);
        back.setOnClickListener(v -> navigateUp());

        applyScreenHint();

        if (entryMode == MODE_CONTRACTS) {
            setTitle(R.string.cloud_storage_title_contracts);
            currentPath = "contracts";
            updateContractSearchVisibility();
            loadStoragePath(currentPath);
        } else if (entryMode == MODE_FIXED_ROOT) {
            if (fixedRootPath == null || fixedRootPath.trim().isEmpty()) {
                Toast.makeText(this, R.string.cloud_storage_empty_folder, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (fixedRootTitle != null && !fixedRootTitle.trim().isEmpty()) {
                setTitle(fixedRootTitle.trim());
            } else {
                setTitle(fixedRootPath.trim());
            }
            resolveFixedRootPath(fixedRootPath.trim(), resolved -> {
                resolvedFixedRootPath = resolved;
                String openPath = getIntent().getStringExtra(EXTRA_OPEN_FOLDER_PATH);
                if (openPath != null && !openPath.trim().isEmpty()) {
                    currentPath = openPath.trim();
                } else {
                    currentPath = resolved;
                }
                loadStoragePath(currentPath);
            });
        } else if (entryMode == MODE_STORED_REPORTS) {
            setTitle(showAllTopLevelStorageFolders
                    ? R.string.admin_dashboard_full_storage_title
                    : R.string.view_reports_stored_reports);
            String openPath = getIntent().getStringExtra(EXTRA_OPEN_FOLDER_PATH);
            if (openPath != null && !openPath.trim().isEmpty()) {
                currentPath = openPath.trim();
                loadStoragePath(currentPath);
            } else {
                currentPath = null;
                loadStoredReportRoots();
            }
        } else {
            setTitle(R.string.cloud_storage_title_reports);
            currentPath = null;
            reportsRootListingMode = "";
            updateStorageSearchVisibility();
            loadReportRoots();
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingReportSearch != null) {
            searchHandler.removeCallbacks(pendingReportSearch);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionManager.ensureLoaded(this, s -> runOnUiThread(() -> {
            updateNewFolderButtonVisibility();
            refreshCloudActionModeMenu();
        }));
    }

    /**
     * Parent path in the bucket where a new folder should be created ({@code child/.keep} is uploaded).
     * {@code ""} means bucket root. {@code null} if folder creation is not available at this level.
     */
    @Nullable
    private String getParentPathForNewFolder() {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_STORAGE_PATH_ON_PICK, false)) {
            return null;
        }
        if (entryMode == MODE_CONTRACTS) {
            if (currentPath == null || "contracts".equals(currentPath)) {
                return null;
            }
            if (currentPath.startsWith("contracts/")) {
                return currentPath.trim();
            }
            return null;
        }
        if (entryMode == MODE_STORED_REPORTS) {
            return currentPath != null ? currentPath : "";
        }
        if (entryMode == MODE_FIXED_ROOT) {
            if (currentPath != null && !currentPath.trim().isEmpty()) {
                return currentPath.trim();
            }
            if (fixedRootPath != null && !fixedRootPath.trim().isEmpty()) {
                return fixedRootPath.trim();
            }
            return null;
        }
        if (entryMode == MODE_REPORTS) {
            if (currentPath != null && !currentPath.trim().isEmpty()) {
                return currentPath.trim();
            }
            if (!"parent".equals(reportsRootListingMode) && !"probe".equals(reportsRootListingMode)) {
                return null;
            }
            return "parent".equals(reportsRootListingMode) ? REPORTS_SEGMENT : "";
        }
        return null;
    }

    private void updateNewFolderButtonVisibility() {
        if (buttonCloudNewFolder != null) {
            boolean show = !isBlockedUser()
                    && !DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)
                    && getParentPathForNewFolder() != null
                    && (SessionManager.isAdmin(this)
                    || (entryMode == MODE_CONTRACTS && canUploadToCurrentFolder()));
            buttonCloudNewFolder.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        updateUploadButtonVisibility();
    }

    private void updateUploadButtonVisibility() {
        if (buttonCloudUpload == null) return;
        boolean show = canUploadToCurrentFolder();
        buttonCloudUpload.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private boolean canUploadToCurrentFolder() {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_STORAGE_PATH_ON_PICK, false)) {
            return false;
        }
        if (isBlockedUser() || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
            return false;
        }
        return currentPath != null && !currentPath.trim().isEmpty();
    }

    private void startPickFileForUpload() {
        if (!canUploadToCurrentFolder()) {
            Toast.makeText(this, R.string.cloud_storage_upload_need_folder, Toast.LENGTH_SHORT).show();
            return;
        }
        if (uploadFileLauncher == null) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        uploadFileLauncher.launch(Intent.createChooser(intent, getString(R.string.cloud_storage_upload_file_button)));
    }

    private String queryOpenableDisplayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            name = "upload_" + System.currentTimeMillis();
        }
        name = name.trim();
        if (!isValidStorageFileName(name)) {
            String cleaned = name.replaceAll("[\\\\/:*?\"<>|]+", "_");
            if (!isValidStorageFileName(cleaned)) {
                cleaned = "upload_" + System.currentTimeMillis();
            }
            name = cleaned;
        }
        if (".keep".equalsIgnoreCase(name)) {
            name = "upload_" + System.currentTimeMillis();
        }
        return name;
    }

    private void uploadUriIntoCurrentFolder(Uri uri) {
        String path = currentPath != null ? currentPath.trim() : "";
        if (path.isEmpty()) return;
        String displayName = queryOpenableDisplayName(uri);
        String storagePath = path + "/" + displayName;
        Toast.makeText(this, R.string.cloud_storage_upload_working, Toast.LENGTH_SHORT).show();
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(storagePath);
        ref.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> {
                    StorageMetricsHelper.recordUpload();
                    Toast.makeText(this, R.string.cloud_storage_upload_done, Toast.LENGTH_SHORT).show();
                    loadStoragePath(currentPath);
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Toast.makeText(this, getString(R.string.cloud_storage_upload_failed, msg), Toast.LENGTH_LONG).show();
                });
    }

    private static boolean isValidNewFolderName(@Nullable String name) {
        if (name == null) return false;
        String s = name.trim();
        if (s.isEmpty() || s.length() > 200) return false;
        if (s.contains("/") || s.contains("\\")) return false;
        if (".".equals(s) || "..".equals(s)) return false;
        if (".keep".equalsIgnoreCase(s)) return false;
        return true;
    }

    private void showCreateFolderDialog() {
        String parent = getParentPathForNewFolder();
        if (parent == null) return;

        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(pad, pad, pad, 0);
        final EditText input = new EditText(this);
        input.setHint(R.string.cloud_storage_new_folder_hint);
        frame.addView(input);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_storage_new_folder_title)
                .setView(frame)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String folderName = input.getText() != null ? input.getText().toString().trim() : "";
            if (!isValidNewFolderName(folderName)) {
                Toast.makeText(this, R.string.cloud_storage_new_folder_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            dlg.dismiss();
            createCloudFolder(parent, folderName);
        }));
        dlg.show();
    }

    private void createCloudFolder(String parentPath, String folderName) {
        String keepKey = parentPath.isEmpty()
                ? folderName + "/.keep"
                : parentPath + "/" + folderName + "/.keep";
        Toast.makeText(this, R.string.cloud_storage_creating_folder, Toast.LENGTH_SHORT).show();
        FirebaseStorage.getInstance().getReference().child(keepKey).putBytes(new byte[0])
                .addOnSuccessListener(taskSnapshot -> {
                    StorageMetricsHelper.recordUpload();
                    Toast.makeText(this, R.string.cloud_storage_new_folder_created, Toast.LENGTH_SHORT).show();
                    if (currentPath == null) {
                        if (entryMode == MODE_STORED_REPORTS) {
                            loadStoredReportRoots();
                        } else if (entryMode == MODE_REPORTS) {
                            loadReportRoots();
                        }
                    } else {
                        loadStoragePath(currentPath);
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Toast.makeText(this, getString(R.string.cloud_storage_new_folder_failed, msg), Toast.LENGTH_LONG).show();
                });
    }

    private void applyScreenHint() {
        if (screenHint == null) return;
        if (entryMode == MODE_CONTRACTS) {
            screenHint.setText(R.string.cloud_storage_screen_hint);
        } else if (entryMode == MODE_FIXED_ROOT) {
            if (fixedRootHint != null && !fixedRootHint.trim().isEmpty()) {
                screenHint.setText(fixedRootHint.trim());
            } else {
                screenHint.setText(R.string.cloud_storage_screen_hint);
            }
        } else if (entryMode == MODE_STORED_REPORTS) {
            screenHint.setText(R.string.stored_reports_screen_subtitle);
        } else {
            screenHint.setText(R.string.cloud_storage_pick_report_folder);
        }
    }

    private boolean isBlockedUser() {
        return BuildConfig.IS_OFFLINE
                || "Offline".equals(userName)
                || "Offline User".equals(userName);
    }

    @Override
    public void onBackPressed() {
        if (!navigateUp()) {
            super.onBackPressed();
        }
    }

    /**
     * @return true if navigation was handled (do not call super).
     */
    private boolean navigateUp() {
        if (entryMode == MODE_REPORTS && currentPath == null) {
            reportsRootListingMode = "";
            finish();
            return true;
        }
        if (entryMode == MODE_STORED_REPORTS && currentPath == null) {
            finish();
            return true;
        }
        if (entryMode == MODE_CONTRACTS && "contracts".equals(currentPath)) {
            finish();
            return true;
        }
        if (entryMode == MODE_FIXED_ROOT && fixedRootPath != null && fixedRootPath.equals(currentPath)) {
            finish();
            return true;
        }
        if (currentPath == null) {
            finish();
            return true;
        }

        int slash = currentPath.lastIndexOf('/');
        if (slash <= 0) {
            if (entryMode == MODE_REPORTS) {
                currentPath = null;
                loadReportRoots();
                return true;
            }
            if (entryMode == MODE_STORED_REPORTS) {
                currentPath = null;
                loadStoredReportRoots();
                return true;
            }
            if (entryMode == MODE_FIXED_ROOT) {
                finish();
                return true;
            }
            finish();
            return true;
        }

        currentPath = currentPath.substring(0, slash);
        if (entryMode == MODE_FIXED_ROOT && fixedRootPath != null && !fixedRootPath.isEmpty()) {
            if (!currentPath.startsWith(fixedRootPath)) {
                currentPath = fixedRootPath;
            }
        }
        if (entryMode == MODE_REPORTS && currentPath.isEmpty()) {
            currentPath = null;
            loadReportRoots();
            return true;
        }
        if (entryMode == MODE_REPORTS && REPORTS_SEGMENT.equalsIgnoreCase(currentPath) && "parent".equals(reportsRootListingMode)) {
            currentPath = null;
            loadReportRoots();
            return true;
        }
        loadStoragePath(currentPath);
        return true;
    }

    private void clearStorageSearchBar() {
        if (contractSearchBar == null) {
            return;
        }
        if (entryMode == MODE_CONTRACTS || entryMode == MODE_REPORTS) {
            contractSearchBar.setText("");
        }
    }

    private void clearContractSearchBar() {
        clearStorageSearchBar();
    }

    private void bindEntryList(List<CloudStorageEntryAdapter.Entry> entries) {
        currentFullEntries.clear();
        if (entries != null) {
            currentFullEntries.addAll(entries);
        }
        if (adapter != null) {
            adapter.setListCurrentPath(currentPath);
        }
        applyStorageSearchFilter();
    }

    private void updateStorageSearchVisibility() {
        if (contractSearchBar == null) {
            return;
        }
        boolean show = entryMode == MODE_CONTRACTS || entryMode == MODE_REPORTS;
        contractSearchBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            contractSearchBar.setHint(entryMode == MODE_REPORTS
                    ? R.string.cloud_storage_reports_search_hint
                    : R.string.cloud_storage_contract_search_hint);
        } else {
            contractSearchBar.setText("");
        }
    }

    private void updateContractSearchVisibility() {
        updateStorageSearchVisibility();
    }

    private void applyStorageSearchFilter() {
        if (adapter == null) {
            return;
        }
        if (entryMode == MODE_REPORTS) {
            applyReportSearch();
            return;
        }
        applyContractSearchFilter();
    }

    private void applyReportSearch() {
        if (contractSearchBar == null) {
            return;
        }
        String query = contractSearchBar.getText() != null
                ? contractSearchBar.getText().toString().trim()
                : "";
        if (query.length() < REPORT_SEARCH_MIN_CHARS) {
            if (reportSearchActive) {
                reportSearchActive = false;
                if (currentPath == null) {
                    loadReportRoots();
                } else {
                    loadStoragePath(currentPath);
                }
            }
            return;
        }
        if (pendingReportSearch != null) {
            searchHandler.removeCallbacks(pendingReportSearch);
        }
        pendingReportSearch = () -> runReportGlobalSearch(query);
        searchHandler.postDelayed(pendingReportSearch, REPORT_SEARCH_DEBOUNCE_MS);
    }

    private void runReportGlobalSearch(String query) {
        reportSearchActive = true;
        if (pathLabel != null) {
            pathLabel.setText(getString(R.string.cloud_storage_searching_reports));
        }
        resolveReportSearchRoots(roots -> ContractStoragePathHelper.searchFilesByName(
                roots,
                query,
                REPORT_SEARCH_MAX_RESULTS,
                hits -> runOnUiThread(() -> {
                    if (hits == null || hits.isEmpty()) {
                        Toast.makeText(this, R.string.cloud_storage_search_no_report_files, Toast.LENGTH_SHORT).show();
                        adapter.setItems(Collections.emptyList());
                        return;
                    }
                    List<CloudStorageEntryAdapter.Entry> rows = new ArrayList<>();
                    for (ContractStoragePathHelper.FileSearchHit hit : hits) {
                        rows.add(new CloudStorageEntryAdapter.Entry(
                                hit.fileName,
                                false,
                                getString(R.string.cloud_storage_search_result_folder, hit.folderLabel),
                                hit.storagePath
                        ));
                    }
                    adapter.setItems(rows);
                    adapter.setListCurrentPath(currentPath);
                })
        ));
    }

    private interface ReportRootsCallback {
        void onRoots(List<String> roots);
    }

    private void resolveReportSearchRoots(ReportRootsCallback callback) {
        FirebaseStorage.getInstance().getReference().child(REPORTS_SEGMENT).listAll()
                .addOnSuccessListener(listResult -> {
                    List<String> roots = new ArrayList<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        String name = prefix.getName();
                        if (isReportsYearFolderName(name)) {
                            roots.add(REPORTS_SEGMENT + "/" + name);
                        }
                    }
                    if (!roots.isEmpty()) {
                        callback.onRoots(roots);
                        return;
                    }
                    StorageFolderHelper.discoverReportFolders(folders -> {
                        List<String> probed = new ArrayList<>();
                        if (folders != null) {
                            for (String name : folders) {
                                if (isReportsYearFolderName(name)) {
                                    probed.add(name);
                                }
                            }
                        }
                        callback.onRoots(probed);
                    });
                })
                .addOnFailureListener(e -> StorageFolderHelper.discoverReportFolders(folders -> {
                    List<String> probed = new ArrayList<>();
                    if (folders != null) {
                        for (String name : folders) {
                            if (isReportsYearFolderName(name)) {
                                probed.add(name);
                            }
                        }
                    }
                    callback.onRoots(probed);
                }));
    }

    private void applyContractSearchFilter() {
        if (adapter == null || contractSearchBar == null) {
            return;
        }
        String query = contractSearchBar.getText() != null
                ? contractSearchBar.getText().toString().trim().toLowerCase(Locale.ROOT)
                : "";
        if (query.isEmpty()) {
            adapter.setListCurrentPath(currentPath);
            adapter.setItems(currentFullEntries);
            return;
        }
        List<CloudStorageEntryAdapter.Entry> filtered = new ArrayList<>();
        for (CloudStorageEntryAdapter.Entry entry : currentFullEntries) {
            String name = entry.name != null ? entry.name : "";
            String title = entry.displayTitle != null ? entry.displayTitle : "";
            if (ContractStoragePathHelper.fileNameMatchesSearch(name, query)
                    || ContractStoragePathHelper.fileNameMatchesSearch(title, query)) {
                filtered.add(entry);
            }
        }
        adapter.setListCurrentPath(currentPath);
        adapter.setItems(filtered);
    }

    private void loadReportRoots() {
        reportsRootListingMode = "";
        pathLabel.setText(getString(R.string.cloud_storage_reports_filtered_root));

        FirebaseStorage.getInstance().getReference().child(REPORTS_SEGMENT).listAll()
                .addOnSuccessListener(listResult -> {
                    List<CloudStorageEntryAdapter.Entry> rows = new ArrayList<>();
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        String name = prefix.getName();
                        if (isReportsYearFolderName(name)) {
                            rows.add(new CloudStorageEntryAdapter.Entry(name, true));
                        }
                    }
                    Collections.sort(rows, (a, b) -> b.name.compareToIgnoreCase(a.name));
                    runOnUiThread(() -> {
                        if (rows.isEmpty()) {
                            loadReportRootsFromProbe();
                            return;
                        }
                        reportsRootListingMode = "parent";
                        pathLabel.setText(getString(R.string.cloud_storage_reports_filtered_root));
                        adapter.setItems(rows);
                        adapter.setListCurrentPath(currentPath);
                        updateNewFolderButtonVisibility();
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(this::loadReportRootsFromProbe));
    }

    private void loadReportRootsFromProbe() {
        reportsRootListingMode = "probe";
        pathLabel.setText(getString(R.string.cloud_storage_pick_report_folder));
        StorageFolderHelper.discoverAllRootFolders(
                names -> runOnUiThread(() -> bindProbedReportRootRows(names)),
                () -> StorageFolderHelper.discoverReportFolders(folders -> runOnUiThread(() -> bindProbedReportRootRows(folders))));
    }

    private void bindProbedReportRootRows(List<String> names) {
        List<CloudStorageEntryAdapter.Entry> rows = new ArrayList<>();
        if (names != null) {
            for (String name : names) {
                if (isReportsYearFolderName(name)) {
                    rows.add(new CloudStorageEntryAdapter.Entry(name, true));
                }
            }
        }
        Collections.sort(rows, (a, b) -> b.name.compareToIgnoreCase(a.name));
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.cloud_storage_no_report_folders, Toast.LENGTH_SHORT).show();
        }
        adapter.setItems(rows);
        adapter.setListCurrentPath(currentPath);
        updateNewFolderButtonVisibility();
    }

    private static boolean isReportsYearFolderName(@Nullable String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return REPORTS_NUMERIC_ROOT.matcher(name.trim()).matches();
    }

    private void loadStoredReportRoots() {
        StorageFolderHelper.discoverAllRootFolders(
                folders -> runOnUiThread(() -> bindStoredRootFolderList(folders, true)),
                () -> StorageFolderHelper.discoverReportFolders(folders -> runOnUiThread(() -> {
                    Toast.makeText(this, R.string.stored_reports_root_list_denied_fallback, Toast.LENGTH_LONG).show();
                    bindStoredRootFolderList(folders, false);
                })));
    }

    private void bindStoredRootFolderList(List<String> folders, boolean toastIfEmpty) {
        List<CloudStorageEntryAdapter.Entry> rows = new ArrayList<>();
        for (String name : folders) {
            if (name != null && !name.isEmpty()) {
                if (shouldHideFromStoredRoot(name)) {
                    continue;
                }
                rows.add(new CloudStorageEntryAdapter.Entry(name, true));
            }
        }
        Collections.sort(rows, (a, b) -> a.name.compareToIgnoreCase(b.name));
        if (rows.isEmpty() && toastIfEmpty) {
            Toast.makeText(this, R.string.stored_reports_no_root_folders, Toast.LENGTH_LONG).show();
        }
        adapter.setItems(rows);
        adapter.setListCurrentPath(currentPath);
        updatePathLabel(null);
        updateNewFolderButtonVisibility();
    }

    /**
     * Stored Reports should not duplicate folders that already have dedicated entry points in View Reports.
     */
    private boolean shouldHideFromStoredRoot(String folderName) {
        if (showAllTopLevelStorageFolders) {
            return false;
        }
        if (folderName == null) return false;
        String n = folderName.trim();
        if (n.isEmpty()) return false;
        if ("contracts".equalsIgnoreCase(n)) return true;
        if ("quotations".equalsIgnoreCase(n)) return true;
        if ("jobworkreports".equalsIgnoreCase(n)) return true;
        if ("management jobs".equalsIgnoreCase(n)) return true;
        if ("managment jobs".equalsIgnoreCase(n)) return true;
        if ("grpc pricing".equalsIgnoreCase(n)) return true;
        if ("demo pricing".equalsIgnoreCase(n)) return true;
        if ("invoices".equalsIgnoreCase(n)) return true;
        if ("companies".equalsIgnoreCase(n)) return true;
        return false;
    }

    private void loadStoragePath(String path) {
        if (path == null) {
            if (entryMode == MODE_REPORTS) {
                loadReportRoots();
            } else if (entryMode == MODE_STORED_REPORTS) {
                loadStoredReportRoots();
            }
            return;
        }
        updatePathLabel(path);
        ensureContractDisplayNameForCurrentPath(path);

        StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);
        ref.listAll()
                .addOnSuccessListener(listResult -> {
                    List<CloudStorageEntryAdapter.Entry> folders = new ArrayList<>();
                    List<CloudStorageEntryAdapter.Entry> files = new ArrayList<>();

                    for (StorageReference prefix : listResult.getPrefixes()) {
                        String name = prefix.getName();
                        if (name != null && !name.isEmpty()) {
                            folders.add(new CloudStorageEntryAdapter.Entry(name, true));
                        }
                    }
                    for (StorageReference item : listResult.getItems()) {
                        String name = item.getName();
                        if (name == null || name.isEmpty() || ".keep".equalsIgnoreCase(name)) {
                            continue;
                        }
                        files.add(new CloudStorageEntryAdapter.Entry(name, false, null, path + "/" + name));
                    }

                    Collections.sort(folders, (a, b) -> a.name.compareToIgnoreCase(b.name));
                    Collections.sort(files, (a, b) -> a.name.compareToIgnoreCase(b.name));

                    List<CloudStorageEntryAdapter.Entry> combined = new ArrayList<>(folders.size() + files.size());
                    combined.addAll(folders);
                    combined.addAll(files);

                    runOnUiThread(() -> {
                        if (combined.isEmpty()) {
                            Toast.makeText(this, R.string.cloud_storage_empty_folder, Toast.LENGTH_SHORT).show();
                        }
                        if (entryMode == MODE_CONTRACTS && "contracts".equals(path) && !folders.isEmpty()) {
                            List<String> ids = new ArrayList<>();
                            for (CloudStorageEntryAdapter.Entry e : folders) {
                                ids.add(e.name);
                            }
                            bindEntryList(combined);
                            ContractStorageDisplayHelper.loadContractFolderLabels(
                                    FirebaseFirestore.getInstance(),
                                    ids,
                                    map -> runOnUiThread(() -> {
                                        contractIdToDisplayName.putAll(map);
                                        List<CloudStorageEntryAdapter.Entry> resolved = new ArrayList<>();
                                        for (CloudStorageEntryAdapter.Entry e : folders) {
                                            String d = map.get(e.name);
                                            resolved.add(new CloudStorageEntryAdapter.Entry(
                                                    e.name, true, d != null ? d : ""));
                                        }
                                        Collections.sort(resolved, (a, b) -> a.sortKey().compareTo(b.sortKey()));
                                        List<CloudStorageEntryAdapter.Entry> merged = new ArrayList<>(resolved);
                                        merged.addAll(files);
                                        bindEntryList(merged);
                                        updatePathLabel(path);
                                        updateNewFolderButtonVisibility();
                                    }));
                        } else {
                            bindEntryList(combined);
                            updateNewFolderButtonVisibility();
                        }
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.cloud_storage_list_failed) + e.getMessage(), Toast.LENGTH_LONG).show()
                ));
    }

    /**
     * Resolves common folder-name variants against Firebase root folders.
     * This keeps buttons stable even when legacy/misspelled folder names exist in bucket root.
     */
    private void resolveFixedRootPath(String requestedPath, FixedRootCallback callback) {
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            callback.onResolved("");
            return;
        }
        final String requested = requestedPath.trim();
        FirebaseStorage.getInstance().getReference().listAll()
                .addOnSuccessListener(listResult -> {
                    List<String> rootNames = new ArrayList<>();
                    for (StorageReference p : listResult.getPrefixes()) {
                        if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                            rootNames.add(p.getName().trim());
                        }
                    }
                    // Exact match first.
                    for (String root : rootNames) {
                        if (root.equals(requested)) {
                            callback.onResolved(root);
                            return;
                        }
                    }
                    // Case-insensitive match.
                    for (String root : rootNames) {
                        if (root.equalsIgnoreCase(requested)) {
                            callback.onResolved(root);
                            return;
                        }
                    }
                    // Known alias pair for management jobs typo.
                    if ("management jobs".equalsIgnoreCase(requested)) {
                        for (String root : rootNames) {
                            if ("managment jobs".equalsIgnoreCase(root)) {
                                callback.onResolved(root);
                                return;
                            }
                        }
                    } else if ("managment jobs".equalsIgnoreCase(requested)) {
                        for (String root : rootNames) {
                            if ("management jobs".equalsIgnoreCase(root)) {
                                callback.onResolved(root);
                                return;
                            }
                        }
                    }
                    // Fall back to requested value.
                    callback.onResolved(requested);
                })
                .addOnFailureListener(e -> callback.onResolved(requested));
    }

    private void updatePathLabel(@Nullable String path) {
        if (pathLabel == null) return;
        if (entryMode == MODE_STORED_REPORTS && (path == null || path.isEmpty())) {
            pathLabel.setText(R.string.stored_reports_path_bucket_root);
            return;
        }
        pathLabel.setText(formatContractsPathLabel(path != null ? path : ""));
    }

    /**
     * Breadcrumb for the path line: under {@code contracts/…}, the first folder after {@code contracts}
     * is replaced with the company name when known.
     */
    private String formatContractsPathLabel(@Nullable String path) {
        if (path == null || path.isEmpty()) return "";
        if (entryMode != MODE_CONTRACTS || !path.startsWith("contracts")) {
            return path;
        }
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String seg = parts[i];
            if (seg.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" / ");
            if (i == 1 && parts.length > 1 && "contracts".equals(parts[0])) {
                String friendly = contractIdToDisplayName.get(seg);
                sb.append(friendly != null ? friendly : seg);
            } else {
                sb.append(seg);
            }
        }
        return sb.length() > 0 ? sb.toString() : path;
    }

    /** Loads company name for the contract id in {@code contracts/{id}/…} so the path breadcrumb can show it. */
    private void ensureContractDisplayNameForCurrentPath(String path) {
        if (entryMode != MODE_CONTRACTS || path == null || !path.startsWith("contracts/")) {
            return;
        }
        String rest = path.substring("contracts/".length());
        if (rest.isEmpty()) return;
        int slash = rest.indexOf('/');
        String cid = slash < 0 ? rest : rest.substring(0, slash);
        if (cid.isEmpty() || contractIdToDisplayName.containsKey(cid)) {
            return;
        }
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACTS)
                .document(cid)
                .get()
                .addOnSuccessListener(snap -> {
                    String nm = ContractStorageDisplayHelper.companyNameFromSnapshot(snap);
                    if (nm != null) {
                        contractIdToDisplayName.put(cid, nm);
                        runOnUiThread(() -> updatePathLabel(currentPath));
                    }
                });
    }

    private void onEntryClick(CloudStorageEntryAdapter.Entry entry) {
        if (entry.folder) {
            clearStorageSearchBar();
            reportSearchActive = false;
            if (currentPath == null) {
                if (entryMode == MODE_REPORTS && "parent".equals(reportsRootListingMode)) {
                    currentPath = REPORTS_SEGMENT + "/" + entry.name;
                } else {
                    currentPath = entry.name;
                }
            } else {
                currentPath = currentPath + "/" + entry.name;
            }
            loadStoragePath(currentPath);
            return;
        }

        if (currentPath == null && entry.storagePath == null) {
            return;
        }
        String fullPath = entry.resolveStoragePath(currentPath);
        if (fullPath == null) {
            return;
        }
        String displayName = entry.name;
        if (getIntent().getBooleanExtra(EXTRA_RETURN_STORAGE_PATH_ON_PICK, false)) {
            if (entry.name == null || !entry.name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                Toast.makeText(this, R.string.pdf_editor_cloud_pick_pdf_only, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_STORAGE_PATH, fullPath);
            setResult(RESULT_OK, data);
            finish();
            return;
        }
        showFileActionsDialog(fullPath, displayName);
    }

    private void showFileActionsDialog(String storagePath, String displayName) {
        boolean isAdminOrSuper = SessionManager.isAdmin(this) || SessionManager.isSuperAdmin(this);
        boolean isTech = SessionManager.isTech(this);
        List<String> items = new ArrayList<>();
        if (isAdminOrSuper) {
            items.add(getString(R.string.cloud_storage_action_view));
            items.add(getString(R.string.cloud_storage_action_rename));
            items.add(getString(R.string.cloud_storage_action_download));
            items.add(getString(R.string.cloud_storage_action_delete));
            items.add(getString(R.string.cloud_storage_action_share));
        } else if (isTech) {
            items.add(getString(R.string.cloud_storage_action_share));
            items.add(getString(R.string.cloud_storage_action_view));
            items.add(getString(R.string.cloud_storage_action_download));
        } else {
            items.add(getString(R.string.cloud_storage_action_view));
            items.add(getString(R.string.cloud_storage_action_download));
            items.add(getString(R.string.cloud_storage_action_share));
        }

        new AlertDialog.Builder(this)
                .setTitle(displayName)
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    String action = items.get(which);
                    if (action.equals(getString(R.string.cloud_storage_action_view))) {
                        openRemoteFile(storagePath, displayName);
                    } else if (action.equals(getString(R.string.cloud_storage_action_download))) {
                        downloadRemoteFile(storagePath, displayName);
                    } else if (action.equals(getString(R.string.cloud_storage_action_share))) {
                        shareRemoteFile(storagePath, displayName);
                    } else if (action.equals(getString(R.string.cloud_storage_action_rename))) {
                        showRenameFileDialog(storagePath, displayName);
                    } else if (action.equals(getString(R.string.cloud_storage_action_delete))) {
                        confirmDeleteRemoteFile(storagePath, displayName);
                    }
                })
                .show();
    }

    private void shareRemoteFile(String storagePath, String displayName) {
        FirebaseStorage.getInstance()
                .getReference()
                .child(storagePath)
                .getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    StorageMetricsHelper.recordDownload();
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_SUBJECT, displayName);
                    intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.cloud_storage_share_message,
                            displayName, uri.toString()));
                    try {
                        startActivity(Intent.createChooser(intent, getString(R.string.cloud_storage_share_chooser)));
                    } catch (Exception e) {
                        Toast.makeText(this, getString(R.string.cloud_storage_share_failed, ""), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Toast.makeText(this, getString(R.string.cloud_storage_share_failed, msg), Toast.LENGTH_LONG).show();
                });
    }

    private File resolveLocalDownloadFile(String displayName) {
        String fileNameOnly = displayName == null ? "" : displayName.trim();
        int slash = Math.max(fileNameOnly.lastIndexOf('/'), fileNameOnly.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < fileNameOnly.length()) {
            fileNameOnly = fileNameOnly.substring(slash + 1);
        }
        if (fileNameOnly.isEmpty()) {
            fileNameOnly = "downloaded_file.pdf";
        }

        File root = getExternalFilesDir(null);
        if (root == null) {
            root = getFilesDir();
        }
        File dir = new File(root, TenantBranding.reportsFolderName(this));
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, getString(R.string.cloud_storage_download_failed, "Could not create download folder."),
                    Toast.LENGTH_LONG).show();
            return null;
        }
        File out = new File(dir, fileNameOnly);
        if (out.exists()) {
            String base = fileNameOnly;
            String ext = "";
            int dot = fileNameOnly.lastIndexOf('.');
            if (dot > 0) {
                base = fileNameOnly.substring(0, dot);
                ext = fileNameOnly.substring(dot);
            }
            out = new File(dir, base + "_" + System.currentTimeMillis() + ext);
        }
        return out;
    }

    private void downloadRemoteFile(String storagePath, String displayName) {
        File outFile = resolveLocalDownloadFile(displayName);
        if (outFile == null) return;

        Toast.makeText(this, R.string.pdf_editor_downloading, Toast.LENGTH_SHORT).show();
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(storagePath);
        ref.getFile(outFile)
                .addOnSuccessListener(taskSnapshot -> {
                    StorageMetricsHelper.recordDownload();
                    Toast.makeText(this, getString(R.string.cloud_storage_download_saved, outFile.getAbsolutePath()),
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Toast.makeText(this, getString(R.string.cloud_storage_download_failed, msg), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmDeleteRemoteFile(String storagePath, String displayName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_storage_delete_confirm_title)
                .setMessage(getString(R.string.cloud_storage_delete_confirm_message, displayName))
                .setPositiveButton(R.string.cloud_storage_action_delete, (d, w) -> deleteRemoteFile(storagePath))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteRemoteFile(String storagePath) {
        FirebaseStorage.getInstance()
                .getReference()
                .child(storagePath)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.cloud_storage_action_delete, Toast.LENGTH_SHORT).show();
                    if (currentPath != null) {
                        loadStoragePath(currentPath);
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Toast.makeText(this, getString(R.string.cloud_storage_delete_failed, msg), Toast.LENGTH_LONG).show();
                });
    }

    private static boolean isValidStorageFileName(String name) {
        if (name == null) return false;
        String s = name.trim();
        return !s.isEmpty() && !s.contains("/") && !s.contains("\\") && !"..".equals(s);
    }

    private void showRenameFileDialog(String storagePath, String displayName) {
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(pad, pad, pad, 0);
        final EditText input = new EditText(this);
        input.setHint(R.string.cloud_storage_rename_hint);
        input.setText(displayName);
        input.setSelectAllOnFocus(true);
        frame.addView(input);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_storage_rename_title)
                .setView(frame)
                .setPositiveButton(R.string.cloud_storage_rename_apply, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newName = input.getText() != null ? input.getText().toString().trim() : "";
            if (!isValidStorageFileName(newName)) {
                Toast.makeText(this, R.string.cloud_storage_rename_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (newName.equals(displayName)) {
                Toast.makeText(this, R.string.cloud_storage_rename_same, Toast.LENGTH_SHORT).show();
                return;
            }
            dlg.dismiss();
            performRenameRemoteFile(storagePath, displayName, newName);
        }));
        dlg.show();
    }

    private void performRenameRemoteFile(String oldFullPath, String oldDisplayName, String newDisplayName) {
        int slash = oldFullPath.lastIndexOf('/');
        if (slash < 0) {
            Toast.makeText(this, R.string.cloud_storage_rename_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String parent = oldFullPath.substring(0, slash);
        String newFullPath = parent + "/" + newDisplayName;

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference oldRef = storage.getReference().child(oldFullPath);
        StorageReference newRef = storage.getReference().child(newFullPath);

        File temp;
        try {
            temp = File.createTempFile("cloud_rename_", oldDisplayName.replace("/", "_"), getCacheDir());
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cloud_storage_rename_failed, ""), Toast.LENGTH_LONG).show();
            return;
        }

        oldRef.getFile(temp)
                .addOnFailureListener(e -> {
                    if (!temp.delete()) { /* ignore */ }
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Toast.makeText(this, getString(R.string.cloud_storage_rename_failed, msg), Toast.LENGTH_LONG).show();
                })
                .addOnSuccessListener(ts -> newRef.putFile(Uri.fromFile(temp))
                        .addOnFailureListener(e -> {
                            if (!temp.delete()) { /* ignore */ }
                            String msg = e.getMessage() != null ? e.getMessage() : "";
                            Toast.makeText(this, getString(R.string.cloud_storage_rename_failed, msg), Toast.LENGTH_LONG).show();
                        })
                        .addOnSuccessListener(st -> oldRef.delete()
                                .addOnCompleteListener(t2 -> {
                                    if (!temp.delete()) { /* ignore */ }
                                    if (!t2.isSuccessful()) {
                                        String msg = t2.getException() != null && t2.getException().getMessage() != null
                                                ? t2.getException().getMessage() : "";
                                        Toast.makeText(this, getString(R.string.cloud_storage_delete_failed, msg),
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(this, R.string.cloud_storage_rename_done, Toast.LENGTH_SHORT).show();
                                    }
                                    if (currentPath != null) {
                                        loadStoragePath(currentPath);
                                    }
                                })));
    }

    private void openRemoteFile(String storagePath, String displayName) {
        FirebaseStorage.getInstance()
                .getReference()
                .child(storagePath)
                .getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    StorageMetricsHelper.recordDownload();
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, guessMimeType(displayName));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.cloud_storage_no_viewer, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, getString(R.string.cloud_storage_open_failed) + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private static String guessMimeType(String fileName) {
        if (fileName == null) return "*/*";
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "*/*";
    }

    private boolean supportsCloudMultiSelect() {
        if (getIntent().getBooleanExtra(EXTRA_RETURN_STORAGE_PATH_ON_PICK, false)) {
            return false;
        }
        if (isBlockedUser()) {
            return false;
        }
        return entryMode == MODE_CONTRACTS
                || entryMode == MODE_REPORTS
                || entryMode == MODE_STORED_REPORTS
                || entryMode == MODE_FIXED_ROOT;
    }

    @Nullable
    private String contractRootFromPath(@Nullable String path) {
        if (path == null || !path.startsWith("contracts/")) {
            return null;
        }
        String rest = path.substring("contracts/".length());
        if (rest.isEmpty()) {
            return null;
        }
        int slash = rest.indexOf('/');
        String contractId = slash < 0 ? rest : rest.substring(0, slash);
        if (contractId.isEmpty()) {
            return null;
        }
        return "contracts/" + contractId;
    }

    @Nullable
    private String reportsBrowseRootFromPath(@Nullable String path) {
        if (path == null || path.trim().isEmpty()) {
            return REPORTS_SEGMENT;
        }
        String normalized = path.trim();
        if (normalized.startsWith(REPORTS_SEGMENT + "/")) {
            return REPORTS_SEGMENT;
        }
        int slash = normalized.indexOf('/');
        String first = slash < 0 ? normalized : normalized.substring(0, slash);
        if (isReportsYearFolderName(first)) {
            return first;
        }
        if (isReportsYearFolderName(normalized)) {
            return normalized;
        }
        return REPORTS_SEGMENT;
    }

    private String resolveMoveBrowseRoot() {
        if (entryMode == MODE_CONTRACTS) {
            String root = contractRootFromPath(currentPath);
            if (root == null) {
                List<String> selected = adapter.getSelectedStoragePaths();
                if (!selected.isEmpty()) {
                    root = contractRootFromPath(selected.get(0));
                }
            }
            return root != null ? root : "contracts";
        }
        if (entryMode == MODE_REPORTS) {
            if (currentPath != null && !currentPath.trim().isEmpty()) {
                return reportsBrowseRootFromPath(currentPath);
            }
            List<String> selected = adapter.getSelectedStoragePaths();
            if (!selected.isEmpty()) {
                return reportsBrowseRootFromPath(selected.get(0));
            }
            return REPORTS_SEGMENT;
        }
        if (entryMode == MODE_STORED_REPORTS) {
            if (currentPath != null && !currentPath.trim().isEmpty()) {
                return currentPath.trim();
            }
            List<String> selected = adapter.getSelectedStoragePaths();
            if (!selected.isEmpty()) {
                String path = selected.get(0);
                int slash = path.lastIndexOf('/');
                return slash > 0 ? path.substring(0, slash) : "";
            }
            return "";
        }
        if (entryMode == MODE_FIXED_ROOT) {
            if (!resolvedFixedRootPath.isEmpty()) {
                return resolvedFixedRootPath;
            }
            if (fixedRootPath != null && !fixedRootPath.trim().isEmpty()) {
                return fixedRootPath.trim();
            }
            return currentPath != null ? currentPath.trim() : "";
        }
        return currentPath != null ? currentPath.trim() : "";
    }

    private void startCloudSelectionActionMode() {
        if (cloudActionMode != null || !supportsCloudMultiSelect()) {
            return;
        }
        adapter.setSelectionMode(true);
        cloudActionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_cloud_storage_multiselect, menu);
                MenuItem moveItem = menu.findItem(R.id.action_move);
                if (moveItem != null) {
                    moveItem.setVisible(SessionManager.canMove(CloudStorageBrowserActivity.this));
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                MenuItem moveItem = menu.findItem(R.id.action_move);
                if (moveItem != null) {
                    moveItem.setVisible(SessionManager.canMove(CloudStorageBrowserActivity.this));
                }
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_select_all) {
                    adapter.selectAllVisibleFilePaths(null);
                    syncCloudActionMode();
                    return true;
                }
                if (id == R.id.action_share) {
                    shareSelectedCloudFiles();
                    mode.finish();
                    return true;
                }
                if (id == R.id.action_download) {
                    downloadSelectedCloudFiles();
                    mode.finish();
                    return true;
                }
                if (id == R.id.action_move) {
                    if (!SessionManager.canMove(CloudStorageBrowserActivity.this)) {
                        Toast.makeText(CloudStorageBrowserActivity.this,
                                R.string.cloud_storage_move_not_allowed, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (adapter.getSelectedCount() <= 0) {
                        Toast.makeText(CloudStorageBrowserActivity.this,
                                R.string.cloud_storage_select_files, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    showMoveDestinationPicker();
                    return true;
                }
                if (id == R.id.action_delete) {
                    if (!SessionManager.isAdmin(CloudStorageBrowserActivity.this)
                            && !SessionManager.isSuperAdmin(CloudStorageBrowserActivity.this)) {
                        Toast.makeText(CloudStorageBrowserActivity.this,
                                R.string.cloud_storage_admin_only_delete, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    confirmDeleteSelectedCloudFiles();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                cloudActionMode = null;
                adapter.setSelectionMode(false);
                adapter.clearSelection();
            }
        });
    }

    private void refreshCloudActionModeMenu() {
        if (cloudActionMode != null) {
            cloudActionMode.invalidate();
        }
    }

    private void syncCloudActionMode() {
        if (cloudActionMode == null) {
            return;
        }
        int count = adapter.getSelectedCount();
        if (count <= 0) {
            cloudActionMode.setTitle(getString(R.string.cloud_storage_select_files));
            return;
        }
        cloudActionMode.setTitle(getString(R.string.cloud_storage_selected_count, count));
    }

    private void downloadSelectedCloudFiles() {
        List<String> paths = adapter.getSelectedStoragePaths();
        for (String path : paths) {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            downloadRemoteFile(path, fileName);
        }
    }

    private void shareSelectedCloudFiles() {
        List<String> paths = adapter.getSelectedStoragePaths();
        if (paths.isEmpty()) {
            return;
        }
        if (paths.size() == 1) {
            String path = paths.get(0);
            shareRemoteFile(path, path.substring(path.lastIndexOf('/') + 1));
            return;
        }
        Toast.makeText(this, R.string.cloud_storage_share_working, Toast.LENGTH_SHORT).show();
        final int[] pending = {paths.size()};
        final StringBuilder body = new StringBuilder();
        final List<String> names = new ArrayList<>();
        for (String path : paths) {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            names.add(fileName);
            FirebaseStorage.getInstance().getReference().child(path).getDownloadUrl()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            StorageMetricsHelper.recordDownload();
                            synchronized (body) {
                                body.append(fileName).append(": ").append(task.getResult().toString()).append("\n");
                            }
                        }
                        pending[0]--;
                        if (pending[0] <= 0) {
                            runOnUiThread(() -> launchMultiShareIntent(names, body.toString().trim()));
                        }
                    });
        }
    }

    private void launchMultiShareIntent(List<String> names, String linksBody) {
        if (linksBody.isEmpty()) {
            Toast.makeText(this, getString(R.string.cloud_storage_share_failed, ""), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        String subject = names.size() == 1 ? names.get(0) : getString(R.string.cloud_storage_share_multiple_subject, names.size());
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, linksBody);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.cloud_storage_share_chooser)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cloud_storage_share_failed, ""), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteSelectedCloudFiles() {
        List<String> paths = adapter.getSelectedStoragePaths();
        if (paths.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_storage_delete_confirm_title)
                .setMessage(getString(R.string.cloud_storage_delete_confirm_message, paths.size() + " file(s)"))
                .setPositiveButton(R.string.cloud_storage_action_delete, (d, w) -> deleteSelectedCloudFiles(paths))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteSelectedCloudFiles(List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        final int[] pending = {paths.size()};
        final int[] deleted = {0};
        for (String path : paths) {
            FirebaseStorage.getInstance().getReference().child(path).delete()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            deleted[0]++;
                        }
                        pending[0]--;
                        if (pending[0] <= 0) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, getString(R.string.cloud_storage_delete_done), Toast.LENGTH_SHORT).show();
                                if (cloudActionMode != null) {
                                    cloudActionMode.finish();
                                }
                                if (currentPath != null) {
                                    loadStoragePath(currentPath);
                                }
                            });
                        }
                    });
        }
    }

    private void showMoveDestinationPicker() {
        String root = resolveMoveBrowseRoot();
        if (root == null) {
            Toast.makeText(this, R.string.cloud_storage_move_select_folder, Toast.LENGTH_SHORT).show();
            return;
        }
        browseMoveDestination(root, root);
    }

    private void browseMoveDestination(String rootPath, String currentFolderPath) {
        FirebaseStorage.getInstance().getReference().child(currentFolderPath).listAll()
                .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                    List<String> options = new ArrayList<>();
                    List<String> optionPaths = new ArrayList<>();
                    if (!currentFolderPath.equals(rootPath)) {
                        options.add("..");
                        int slash = currentFolderPath.lastIndexOf('/');
                        optionPaths.add(slash > 0 ? currentFolderPath.substring(0, slash) : rootPath);
                    }
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        String name = prefix.getName();
                        if (name != null && !name.isEmpty()) {
                            options.add("[Folder] " + name);
                            optionPaths.add(currentFolderPath + "/" + name);
                        }
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.cloud_storage_move_select_folder) + "\n" + currentFolderPath)
                            .setPositiveButton(R.string.cloud_storage_move_here, (d, w) ->
                                    confirmMoveSelectedFilesTo(currentFolderPath))
                            .setNegativeButton(android.R.string.cancel, null);
                    if (options.isEmpty()) {
                        builder.setMessage(getString(R.string.cloud_storage_move_here_hint));
                    } else {
                        builder.setItems(options.toArray(new String[0]), (dialog, which) ->
                                browseMoveDestination(rootPath, optionPaths.get(which)));
                    }
                    builder.show();
                }))
                .addOnFailureListener(e -> runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.cloud_storage_list_failed) + e.getMessage(),
                                Toast.LENGTH_LONG).show()));
    }

    private void confirmMoveSelectedFilesTo(String destinationFolder) {
        List<String> paths = adapter.getSelectedStoragePaths();
        if (paths.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.cloud_storage_move_confirm, paths.size(), destinationFolder))
                .setPositiveButton(R.string.cloud_storage_move_files, (d, w) -> moveSelectedFilesTo(destinationFolder, paths))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void moveSelectedFilesTo(String destinationFolder, List<String> sourcePaths) {
        if (sourcePaths.isEmpty()) {
            return;
        }
        final int[] pending = {sourcePaths.size()};
        final int[] moved = {0};
        for (String sourcePath : sourcePaths) {
            String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
            String destPath = destinationFolder + "/" + fileName;
            if (sourcePath.equals(destPath)) {
                pending[0]--;
                continue;
            }
            StorageReference sourceRef = FirebaseStorage.getInstance().getReference().child(sourcePath);
            StorageReference destRef = FirebaseStorage.getInstance().getReference().child(destPath);
            File temp = new File(getCacheDir(), "cloud_move_" + System.currentTimeMillis() + "_" + fileName);
            sourceRef.getFile(temp)
                    .addOnSuccessListener(task -> destRef.putFile(android.net.Uri.fromFile(temp))
                            .addOnSuccessListener(uploaded -> sourceRef.delete()
                                    .addOnCompleteListener(deleteTask -> {
                                        //noinspection ResultOfMethodCallIgnored
                                        temp.delete();
                                        if (deleteTask.isSuccessful()) {
                                            moved[0]++;
                                        }
                                        pending[0]--;
                                        int remaining = pending[0];
                                        runOnUiThread(() -> {
                                            if (cloudActionMode != null && remaining > 0) {
                                                cloudActionMode.setTitle("Moving… " + remaining + " file" +