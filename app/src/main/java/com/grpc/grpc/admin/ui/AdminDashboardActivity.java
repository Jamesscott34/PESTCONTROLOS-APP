package com.grpc.grpc.admin.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.*;

import android.content.Intent;
import android.os.Bundle;

import com.grpc.grpc.billing.ui.InvoiceListActivity;
import com.grpc.grpc.reports.ui.CloudStorageBrowserActivity;
import com.grpc.grpc.bugreport.ui.BugReportFeatureRequestSubmitActivity;
import com.grpc.grpc.bugreport.ui.BugReportFeatureRequestViewActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView contractsTotalTextView;
    private TextView contractsSummaryListTextView;
    private ListView usersListView;
    private View storageSummaryBlock;
    private View reportsStorageBlock;
    private View authSummaryBlock;
    private TextView storageSummaryTextView;
    private TextView reportsStorageTextView;
    private TextView authSummaryTextView;
    private Button createReportsFolderButton;
    private View reportsFolderBlock;
    private View bugReportFeatureRequestBlock;
    private Button submitBugReportFeatureRequestButton;
    private Button viewBugReportFeatureRequestButton;
    private View invoiceBillingBlock;
    private Button buttonAdminInvoices;
    private View fullStorageBrowserBlock;
    private Button buttonAdminFullStorageBrowser;

    private final List<UserItem> userItems = new ArrayList<>();
    private ArrayAdapter<String> usersAdapter;

    @Nullable
    private String currentCompanyKey = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        contractsTotalTextView = findViewById(R.id.contractsTotalTextView);
        contractsSummaryListTextView = findViewById(R.id.contractsSummaryListTextView);
        usersListView = findViewById(R.id.usersListView);
        storageSummaryBlock = findViewById(R.id.storageSummaryBlock);
        storageSummaryTextView = findViewById(R.id.storageSummaryTextView);
        reportsStorageBlock = findViewById(R.id.reportsStorageBlock);
        reportsStorageTextView = findViewById(R.id.reportsStorageTextView);
        authSummaryBlock = findViewById(R.id.authSummaryBlock);
        authSummaryTextView = findViewById(R.id.authSummaryTextView);
        createReportsFolderButton = findViewById(R.id.createReportsFolderButton);
        reportsFolderBlock = findViewById(R.id.reportsFolderBlock);
        bugReportFeatureRequestBlock = findViewById(R.id.bugReportFeatureRequestBlock);
        submitBugReportFeatureRequestButton = findViewById(R.id.submitBugReportFeatureRequestButton);
        viewBugReportFeatureRequestButton = findViewById(R.id.viewBugReportFeatureRequestButton);
        invoiceBillingBlock = findViewById(R.id.invoiceBillingBlock);
        buttonAdminInvoices = findViewById(R.id.buttonAdminInvoices);
        fullStorageBrowserBlock = findViewById(R.id.fullStorageBrowserBlock);
        buttonAdminFullStorageBrowser = findViewById(R.id.buttonAdminFullStorageBrowser);

        // RBAC: only admin / super_admin may view this screen.
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.isAdmin) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Admin dashboard is available to admin users only.",
                        Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            // Show bug report block only when user has bugReport = true (super_admin/admin true, tech false)
            if (bugReportFeatureRequestBlock != null) {
                bugReportFeatureRequestBlock.setVisibility(session.canBugReport ? View.VISIBLE : View.GONE);
            }

            // Cache company key for filtering (if needed later).
            String key = session.contractKey != null ? session.contractKey.trim() : "";
            if (key.isEmpty()) {
                key = session.staffId != null ? session.staffId.trim() : "";
            }
            currentCompanyKey = key.isEmpty() ? null : key.toLowerCase(Locale.getDefault());

            // Upload/download metrics: hidden for all admin dashboard users.
            if (storageSummaryBlock != null) storageSummaryBlock.setVisibility(View.GONE);
            if (reportsStorageBlock != null) reportsStorageBlock.setVisibility(View.GONE);
            if (authSummaryBlock != null) authSummaryBlock.setVisibility(View.GONE);
            if (reportsFolderBlock != null) {
                boolean showReportsFolder = session.isSuperAdmin
                        && !BuildConfig.IS_OFFLINE
                        && !(BuildConfig.IS_DEMO && DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this));
                reportsFolderBlock.setVisibility(showReportsFolder ? View.VISIBLE : View.GONE);
            }
            if (createReportsFolderButton != null) {
                createReportsFolderButton.setOnClickListener(v -> showCreateReportsFolderFlow());
            }

            // Billing is available to admin/super_admin, or users explicitly granted canInvoice.
            boolean invoicesAvailable = (session.isAdmin || session.canInvoice)
                    && !BuildConfig.IS_OFFLINE
                    && !(BuildConfig.IS_DEMO && DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this));
            if (invoiceBillingBlock != null) {
                invoiceBillingBlock.setVisibility(invoicesAvailable ? View.VISIBLE : View.GONE);
            }
            if (buttonAdminInvoices != null) {
                buttonAdminInvoices.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminDashboardActivity.this, InvoiceListActivity.class);
                    intent.putExtra(InvoiceListActivity.EXTRA_CAN_CREATE, session.canInvoice);
                    startActivity(intent);
                });
            }

            boolean cloudStorageBrowserOk = session.isAdmin
                    && !BuildConfig.IS_OFFLINE
                    && !(BuildConfig.IS_DEMO && DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this));
            if (fullStorageBrowserBlock != null) {
                fullStorageBrowserBlock.setVisibility(cloudStorageBrowserOk ? View.VISIBLE : View.GONE);
            }
            if (buttonAdminFullStorageBrowser != null) {
                if (cloudStorageBrowserOk) {
                    buttonAdminFullStorageBrowser.setOnClickListener(v -> {
                        String un = getIntent().getStringExtra("USER_NAME");
                        if (un == null || un.trim().isEmpty()) {
                            String ck = session.contractKey != null ? session.contractKey.trim() : "";
                            if (!ck.isEmpty()) {
                                un = StaffDirectory.capitalizeContractKey(ck);
                            } else if (session.staffId != null && !session.staffId.trim().isEmpty()) {
                                un = session.staffId.trim();
                            } else {
                                un = "User";
                            }
                        }
                        Intent intent = new Intent(AdminDashboardActivity.this, CloudStorageBrowserActivity.class);
                        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_STORED_REPORTS);
                        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, un);
                        intent.putExtra(CloudStorageBrowserActivity.EXTRA_SHOW_ALL_TOP_LEVEL_STORAGE_FOLDERS, true);
                        startActivity(intent);
                    });
                } else {
                    buttonAdminFullStorageBrowser.setOnClickListener(null);
                }
            }

            if (BuildConfig.IS_OFFLINE || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
                if (contractsTotalTextView != null) {
                    contractsTotalTextView.setText("Contracts: unavailable (offline or demo restricted)");
                }
                return;
            }

            loadContractsSummary();
            loadUsersSummary();

            // Bug report / Feature request: all admins can submit; super_admin can view all and set cost/days.
            if (submitBugReportFeatureRequestButton != null) {
                submitBugReportFeatureRequestButton.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminDashboardActivity.this, BugReportFeatureRequestSubmitActivity.class);
                    intent.putExtra(BugReportFeatureRequestSubmitActivity.EXTRA_USER_NAME, SessionManager.getName(this));
                    startActivity(intent);
                });
            }
            if (viewBugReportFeatureRequestButton != null) {
                viewBugReportFeatureRequestButton.setOnClickListener(v -> {
                    Intent intent = new Intent(AdminDashboardActivity.this, BugReportFeatureRequestViewActivity.class);
                    intent.putExtra(BugReportFeatureRequestViewActivity.EXTRA_USER_NAME, SessionManager.getName(this));
                    startActivity(intent);
                });
            }
        }));
    }

    /**
     * Lets admins / super_admins create a root folder named {@code Reports}{@code <digits>} (e.g. 27 → Reports27).
     * Subfolders are created later in Stored Reports or the full storage browser.
     */
    private void showCreateReportsFolderFlow() {
        if (BuildConfig.IS_OFFLINE || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
            Toast.makeText(this, "Storage unavailable (offline or demo restricted).", Toast.LENGTH_SHORT).show();
            return;
        }

        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(pad, pad, pad, 0);
        final EditText input = new EditText(this);
        input.setHint(R.string.admin_report_folder_number_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        frame.addView(input);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.admin_report_folder_title)
                .setMessage(R.string.admin_report_folder_message)
                .setView(frame)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dlg.setOnShowListener(di -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = input.getText() != null ? input.getText().toString() : "";
            String digits = raw.replaceAll("\\D+", "");
            if (digits.isEmpty()) {
                Toast.makeText(this, R.string.admin_report_folder_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            dlg.dismiss();
            checkAndCreateReportsFolder("Reports" + digits);
        }));
        dlg.show();
    }

    private void checkAndCreateReportsFolder(String folderName) {
        StorageReference root = FirebaseStorage.getInstance().getReference();
        root.listAll()
                .addOnSuccessListener(listResult -> {
                    for (StorageReference prefix : listResult.getPrefixes()) {
                        if (folderName.equals(prefix.getName())) {
                            Toast.makeText(AdminDashboardActivity.this,
                                    getString(R.string.admin_report_folder_exists, folderName),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    StorageReference keepRef = root.child(folderName + "/.keep");
                    byte[] empty = new byte[0];
                    keepRef.putBytes(empty)
                            .addOnSuccessListener(v -> Toast.makeText(AdminDashboardActivity.this,
                                    getString(R.string.admin_report_folder_created, folderName), Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(AdminDashboardActivity.this,
                                    getString(R.string.admin_report_folder_failed,
                                            e != null ? e.getMessage() : "unknown"),
                                    Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(AdminDashboardActivity.this,
                        "Could not list storage: " + (e != null ? e.getMessage() : "unknown"),
                        Toast.LENGTH_LONG).show());
    }

    private void loadContractsSummary() {
        if (contractsTotalTextView == null) return;

        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.CONTRACTS)
                .get()
                .addOnSuccessListener(snap -> {
                    int total = 0;
                    Map<String, Integer> perUser = new HashMap<>();
                    if (snap != null) {
                        for (DocumentSnapshot ds : snap.getDocuments()) {
                            if (ds == null || !ds.exists()) continue;
                            String id = ds.getId() != null ? ds.getId() : "";
                            if ("_schema".equals(id)) continue;
                            total++;

                            Object at = ds.get("assignedTech");
                            String assignedTech = at != null ? String.valueOf(at).trim() : "";
                            if (assignedTech.isEmpty()) {
                                assignedTech = "unassigned";
                            }
                            String keyLower = assignedTech.toLowerCase(Locale.getDefault());
                            Integer prev = perUser.get(keyLower);
                            perUser.put(keyLower, prev != null ? prev + 1 : 1);
                        }
                    }

                    if (contractsSummaryListTextView != null) {
                        if (perUser.isEmpty()) {
                            contractsSummaryListTextView.setText("No contracts found.");
                        } else {
                            List<String> lines = new ArrayList<>();
                            List<String> keys = new ArrayList<>(perUser.keySet());
                            Collections.sort(keys);
                            for (String keyLower : keys) {
                                String label = StaffDirectory.capitalizeContractKey(keyLower);
                                int count = perUser.get(keyLower);
                                lines.add(label + " = " + count);
                            }
                            contractsSummaryListTextView.setText(android.text.TextUtils.join("\n", lines));
                        }
                    }

                    contractsTotalTextView.setText("Total contracts: " + total);
                })
                .addOnFailureListener(e -> {
                    if (contractsSummaryListTextView != null) {
                        contractsSummaryListTextView.setText("Error loading contracts.");
                    }
                    contractsTotalTextView.setText("Total contracts: error");
                });
    }

    private void loadUsersSummary() {
        if (usersListView == null) return;

        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.USERS)
                .get()
                .addOnSuccessListener(snap -> {
                    userItems.clear();
                    List<String> labels = new ArrayList<>();

                    if (snap != null) {
                        for (DocumentSnapshot ds : snap.getDocuments()) {
                            if (ds == null || !ds.exists()) continue;
                            String uid = ds.getId() != null ? ds.getId().trim() : "";

                            String name = safeString(ds.get("name"), ds.get("Name"));
                            String email = safeString(ds.get("email"), ds.get("Email"));
                            String contractKey = safeString(ds.get("contractKey"), ds.get("ContractKey"));
                            String staffId = safeString(ds.get("staffId"), ds.get("StaffID"), ds.get("staffID"));
                            String roleRaw = safeString(ds.get("role"), ds.get("Role"));
                            String roleNorm = SessionManager.normalizeRole(roleRaw);

                            // Numeric IDs are often mirrored staff records. Keep them only when they
                            // actually represent a real account row (name/email/role present).
                            if (uid.matches("\\d{3}")) {
                                boolean hasIdentity = !name.isEmpty() || !email.isEmpty();
                                boolean hasRole = !roleNorm.isEmpty();
                                if (!hasIdentity && !hasRole) {
                                    continue;
                                }
                            }

                            // GRPC app: user summary may include super_admin. All other flavors: never list super_admin.
                            if (!"grpc".equalsIgnoreCase(BuildConfig.FLAVOR) && "super_admin".equals(roleNorm)) {
                                continue;
                            }

                            String displayName = !name.isEmpty() ? name
                                    : (!email.isEmpty() ? email : uid);

                            // lastLogin is set by UserRepository on each login (users/{uid})
                            Timestamp lastLoginTs = null;
                            if (ds.get("lastLogin") instanceof Timestamp) {
                                lastLoginTs = (Timestamp) ds.get("lastLogin");
                            }
                            UserItem item = new UserItem(uid, displayName, roleNorm, contractKey, staffId, lastLoginTs);
                            userItems.add(item);
                            labels.add(buildUserLabel(item));
                        }
                    }

                    usersAdapter = new ArrayAdapter<>(AdminDashboardActivity.this,
                            R.layout.item_simple_card,
                            R.id.cardText,
                            labels);
                    usersListView.setAdapter(usersAdapter);
                    usersListView.setOnItemClickListener(this::onUserClicked);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Failed to load users.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private static long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return v != null ? Long.parseLong(String.valueOf(v)) : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void onUserClicked(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= userItems.size()) return;
        UserItem item = userItems.get(position);
        if (item == null) return;

        String roleNorm = item.role != null ? item.role : "unknown";
        if (!"admin".equals(roleNorm) && !"tech".equals(roleNorm)) {
            Toast.makeText(this,
                    "Only admin/tech roles can be changed here.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = new String[]{"Admin", "Technician"};
        String[] values = new String[]{"admin", "tech"};
        int checked = "admin".equals(roleNorm) ? 0 : 1;

        new AlertDialog.Builder(this)
                .setTitle("Change role for " + item.displayName)
                .setSingleChoiceItems(options, checked, null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    AlertDialog alert = (AlertDialog) dialog;
                    int selected = alert.getListView().getCheckedItemPosition();
                    if (selected < 0 || selected >= values.length) return;
                    String newRole = values[selected];
                    if (newRole.equals(roleNorm)) return;
                    applyRoleChange(item, newRole);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyRoleChange(UserItem item, String newRole) {
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.USERS)
                .document(item.uid)
                .update("role", newRole)
                .addOnSuccessListener(v -> {
                    item.role = newRole;
                    refreshLabels();
                    Toast.makeText(AdminDashboardActivity.this,
                            "Role updated to " + newRole + ".",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(AdminDashboardActivity.this,
                        "Failed to update role.",
                        Toast.LENGTH_SHORT).show());
    }

    private void refreshLabels() {
        if (usersAdapter == null) return;
        List<String> labels = new ArrayList<>();
        for (UserItem item : userItems) {
            labels.add(buildUserLabel(item));
        }
        usersAdapter.clear();
        usersAdapter.addAll(labels);
        usersAdapter.notifyDataSetChanged();
    }

    private static String safeString(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private static String formatLastSignIn(@Nullable Timestamp ts) {
        if (ts == null) return "Never";
        try {
            Date d = ts.toDate();
            return new SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String buildUserLabel(UserItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.displayName);
        if (item.role != null && !item.role.isEmpty()) {
            sb.append(" (").append(item.role).append(")");
        }
        sb.append("\nLast sign-in: ").append(formatLastSignIn(item.lastSignIn));
        List<String> extras = new ArrayList<>();
        if (item.contractKey != null && !item.contractKey.trim().isEmpty()) {
            extras.add("contractKey=" + item.contractKey.trim());
        }
        if (item.staffId != null && !item.staffId.trim().isEmpty()) {
            extras.add("staffId=" + item.staffId.trim());
        }
        if (!extras.isEmpty()) {
            sb.append(" · ");
            sb.append(android.text.TextUtils.join(" | ", extras));
        }
        return sb.toString();
    }

    private void loadReportsStorageSummary() {
        if (reportsStorageTextView == null) return;
        if (BuildConfig.IS_OFFLINE || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
            reportsStorageTextView.setText("Reports (Storage): unavailable");
            return;
        }
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection("storage_metadata").document("reports")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        reportsStorageTextView.setText("Reports (Storage): not tracked.\nPopulate storage_metadata/reports (reportFileCount, reportTotalSizeBytes) via Cloud Functions or admin.");
                        return;
                    }
                    long count = toLong(snapshot.get("reportFileCount"));
                    long sizeBytes = toLong(snapshot.get("reportTotalSizeBytes"));
                    String sizeStr = formatBytes(sizeBytes);
                    reportsStorageTextView.setText("Report files: " + count + "\nTotal size: " + sizeStr);
                })
                .addOnFailureListener(e -> reportsStorageTextView.setText("Reports (Storage): error loading"));
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static final class UserItem {
        final String uid;
        final String displayName;
        String role;
        final String contractKey;
        final String staffId;
        @Nullable final Timestamp lastSignIn;

        UserItem(String uid, String displayName, String role, String contractKey, String staffId, @Nullable Timestamp lastSignIn) {
            this.uid = uid != null ? uid : "";
            this.displayName = displayName != null ? displayName : "";
            this.role = role != null ? role : "";
            this.contractKey = contractKey != null ? contractKey : "";
            this.staffId = staffId != null ? staffId : "";
            this.lastSignIn = lastSignIn;
        }
    }
}

