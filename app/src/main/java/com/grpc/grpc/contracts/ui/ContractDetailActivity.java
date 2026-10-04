package com.grpc.grpc.contracts.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.ContractReportSync;
import com.grpc.grpc.core.ContractStoragePathHelper;
import com.grpc.grpc.email.ui.EmailComposeActivity;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.core.UserRepository;
import com.grpc.grpc.jobs.rodent.RodentActivityRoutine;
import com.grpc.grpc.jobs.rodent.RodentRoutineActivity;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.maps.ui.MapsPlaceholderActivity;
import com.grpc.grpc.messaging.NotificationUtils;
import com.grpc.grpc.reports.ui.ActionFormActivity;
import com.grpc.grpc.reports.ui.ReportActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Full contract record. List cards in {@link ViewContractActivity} open this screen.
 * Visit writes, counters, report routing, and permission checks are the same operations
 * the contract list used before the actions moved here.
 */
public class ContractDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CONTRACT_ID = "CONTRACT_ID";
    private static final String CONTRACT_REMINDER_WORK_NAME = "contract_reminder_12h";

    private LinearLayout container;
    private FirebaseFirestore db;
    private String userName = "";
    private String contractId = "";
    private Map<String, Object> contract;
    private boolean reminderOn;
    private boolean optionsVisible;
    private int reportFolderLoad;
    private boolean busy;
    private boolean hasPaused;
    private List<UserRepository.AssignableUser> staffOptions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contract_detail);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        FirebaseHelper.initialize();
        FirebaseHelper.ensureAuthentication();
        db = FirebaseHelper.getFirestore();
        container = findViewById(R.id.detailContainer);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.trim().isEmpty()) {
            String key = SessionManager.getContractKey(this);
            userName = key != null ? StaffDirectory.capitalizeContractKey(key.trim()) : "";
        }
        contractId = getIntent().getStringExtra(EXTRA_CONTRACT_ID);
        if (contractId == null || contractId.trim().isEmpty()) {
            Toast.makeText(this, "This contract is no longer available.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        contractId = contractId.trim();

        UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
            staffOptions = users != null ? users : new ArrayList<>();
        }));
        showLoading();
        reload();
    }

    @Override
    protected void onPause() {
        hasPaused = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPaused && contract != null) reload();
    }

    private void showLoading() {
        container.removeAllViews();
        addText("Loading contract...", 16, false);
    }

    private void showUnavailable() {
        container.removeAllViews();
        addText("This contract is no longer available.", 16, false);
        addButton("Back", this::finish);
    }

    private void reload() {
        if (db == null || contractId.isEmpty()) {
            showUnavailable();
            return;
        }
        db.collection(FirestorePaths.CONTRACTS).document(contractId).get()
                .addOnSuccessListener(snapshot -> runOnUiThread(() -> {
                    if (snapshot == null || !snapshot.exists() || snapshot.getData() == null) {
                        showUnavailable();
                        return;
                    }
                    Map<String, Object> data = snapshot.getData();
                    data.put("documentId", snapshot.getId());
                    Object assigned = data.get("assignedTech");
                    data.put("owner", assigned != null ? assigned.toString() : "");
                    contract = data;
                    loadReminderThenRender();
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    Toast.makeText(this, "Could not open this contract.", Toast.LENGTH_LONG).show();
                    if (contract == null) showUnavailable();
                }));
    }

    private void loadReminderThenRender() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || db == null) {
            reminderOn = false;
            render();
            return;
        }
        db.collection(FirestorePaths.CONTRACT_REMINDERS).document(user.getUid() + "_" + contractId)
                .get()
                .addOnCompleteListener(task -> runOnUiThread(() -> {
                    reminderOn = task.isSuccessful() && task.getResult() != null && task.getResult().exists();
                    render();
                }));
    }

    private void render() {
        if (contract == null) {
            showUnavailable();
            return;
        }
        container.removeAllViews();
        String name = text("name");
        String address = text("address");
        String lastVisit = text("lastVisit");
        String nextVisit = ViewContractActivity.calculateNextVisit(contract);
        String status = ViewContractActivity.contractStatusLabel(lastVisit, nextVisit);

        addText(name, 20, true);
        addText("Address\n" + address, 16, false);
        addText("Owner / assigned technician\n" + text("owner"), 16, false);
        addText("Phone\n" + text("contact"), 16, false);
        addText("Email\n" + text("email"), 16, false);
        addText("Notes\n" + notesText(), 16, false);
        addText("Last visit\n" + lastVisit, 16, false);
        addText("Next visit\n" + nextVisit, 16, false);
        addText("Status\n" + status, 16, false);
        addText("Visits per year\n" + text("visits"), 16, false);
        String routines = buildCounterDisplayLines(contract, "Routines", "Routine");
        String callOuts = buildCounterDisplayLines(contract, "Callout", "Callout");
        addText("Routine counts\n" + (routines.isEmpty() ? "No routine visits recorded." : routines), 16, false);
        addText("Call-out counts\n" + (callOuts.isEmpty() ? "No call outs recorded." : callOuts), 16, false);

        LinearLayout optionsPanel = new LinearLayout(this);
        optionsPanel.setOrientation(LinearLayout.VERTICAL);
        optionsPanel.setVisibility(optionsVisible ? View.VISIBLE : View.GONE);
        addButton(optionsPanel, "Mark routine", this::confirmRoutine);
        addButton(optionsPanel, "Mark call-out", this::confirmCallOut);
        addButton(optionsPanel, "Update last visit", this::showUpdateVisitDialog);
        addButton(optionsPanel, "Create report", this::createReport);
        addButton(optionsPanel, "Create action form", this::createActionForm);
        addButton(optionsPanel, "View maps", this::openSiteMaps);
        addButton(optionsPanel, "Assets", this::openAssets);
        addButton(optionsPanel, "Route", () -> openInMaps(address));
        addButton(optionsPanel, "Email", this::emailContract);

        Button optionsButton = new Button(this);
        optionsButton.setText(optionsVisible ? "Hide options" : "Options");
        optionsButton.setMinHeight(dp(48));
        optionsButton.setAllCaps(false);
        optionsButton.setOnClickListener(v -> {
            optionsVisible = !optionsVisible;
            optionsPanel.setVisibility(optionsVisible ? View.VISIBLE : View.GONE);
            optionsButton.setText(optionsVisible ? "Hide options" : "Options");
        });
        container.addView(optionsButton);
        container.addView(optionsPanel);

        SessionManager.ensureLoaded(this, null);
        if (SessionManager.isAdmin(this) || SessionManager.canHardPressContracts(this)) {
            addButton("Edit or delete", () -> showEditOrDeleteDialog(contractId, contract));
        }
        LinearLayout reportFolders = new LinearLayout(this);
        reportFolders.setOrientation(LinearLayout.VERTICAL);
        container.addView(reportFolders);
        loadReportFolders(reportFolders, contractId);
        addButton("Back", this::finish);
    }

    private void emailContract() {
        String email = text("email");
        if (!email.contains("@")) {
            Toast.makeText(this, "This contract has no email address.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, EmailComposeActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra(EmailComposeActivity.EXTRA_CONTRACT_ID, contractId);
        intent.putExtra(EmailComposeActivity.EXTRA_CUSTOMER_NAME, text("name"));
        intent.putExtra(EmailComposeActivity.EXTRA_CUSTOMER_EMAIL, email);
        intent.putExtra(EmailComposeActivity.EXTRA_ADDRESS, text("address"));
        intent.putExtra(EmailComposeActivity.EXTRA_LAST_VISIT, text("lastVisit"));
        startActivity(intent);
    }

    private void loadReportFolders(LinearLayout host, String id) {
        int token = ++reportFolderLoad;
        host.removeAllViews();
        host.addView(folderLabel("Reports"));
        TextView status = folderLabel("Loading folders...");
        host.addView(status);
        if (!ContractReportSync.hasContractId(id)) {
            status.setText("No report folders for this contract.");
            return;
        }
        String contractFolder = ContractReportSync.buildContractStorageFolder(id);
        ContractStoragePathHelper.listFilesInFolder(contractFolder, (subfolders, rootFiles) -> runOnUiThread(() -> {
            if (token != reportFolderLoad || host.getParent() == null) return;
            host.removeAllViews();
            host.addView(folderLabel("Reports"));
            List<String> folders = new ArrayList<>();
            if (subfolders != null) folders.addAll(subfolders);
            folders.sort((a, b) -> {
                boolean yearA = a != null && a.matches("\\d{4}");
                boolean yearB = b != null && b.matches("\\d{4}");
                if (yearA && yearB) return b.compareTo(a);
                if (yearA) return -1;
                if (yearB) return 1;
                String left = a == null ? "" : a;
                String right = b == null ? "" : b;
                return left.compareToIgnoreCase(right);
            });
            boolean hasRootFiles = rootFiles != null && !rootFiles.isEmpty();
            if (folders.isEmpty() && !hasRootFiles) {
                host.addView(folderLabel("No report folders for this contract."));
                return;
            }
            for (String folder : folders) {
                if (folder == null || folder.trim().isEmpty()) continue;
                String label = folder.trim();
                addButton(host, label, () -> openReportFolder(label));
            }
            if (hasRootFiles) {
                addButton(host, "Reports in this folder", () -> openReportFolder(""));
            }
        }));
    }

    private TextView folderLabel(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(0, 10, 0, 0);
        return view;
    }

    private void openReportFolder(String folderName) {
        Intent intent = new Intent(this, ContractReportsActivity.class);
        intent.putExtra("CONTRACT_ID", contractId);
        intent.putExtra("CONTRACT_NAME", text("name"));
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("OPEN_FOLDER_NAME", folderName == null ? "" : folderName);
        startActivity(intent);
    }

    private void confirmRoutine() {
        if (contract == null || busy) return;
        String name = text("name");
        new AlertDialog.Builder(this)
                .setTitle("Routine Confirmation")
                .setMessage("Was a routine done on " + name + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    String currentDate = new java.text.SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                            .format(Calendar.getInstance().getTime());
                    updateVisitDates(text("owner"), contractId, currentDate, text("visits"), name, true);
                    writeVisitHistoryEntry(contractId, "Routine", currentDate);
                    Toast.makeText(this, "Routine marked complete for " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void confirmCallOut() {
        if (contract == null || busy) return;
        String name = text("name");
        new AlertDialog.Builder(this)
                .setTitle("Call Out Confirmation")
                .setMessage("Was a call out done on " + name + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    busy = true;
                    incrementYearlyCounter(contractId, "Callout", "Call out", null, () -> {
                        busy = false;
                        reload();
                    }, name);
                    String calloutDate = new java.text.SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                            .format(Calendar.getInstance().getTime());
                    writeVisitHistoryEntry(contractId, "Callout", calloutDate);
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void createReport() {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("CONTRACT_ID", contractId);
        intent.putExtra("COMPANY_NAME", text("name"));
        intent.putExtra("ADDRESS", text("address"));
        startActivity(intent);
    }

    private void createActionForm() {
        Intent intent = new Intent(this, ActionFormActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("CONTRACT_ID", contractId);
        intent.putExtra("COMPANY_NAME", text("name"));
        intent.putExtra("ADDRESS", text("address"));
        startActivity(intent);
    }

    private void openSiteMaps() {
        Intent mapsIntent = new Intent(this, MapsPlaceholderActivity.class);
        mapsIntent.putExtra("USER_NAME", userName);
        mapsIntent.putExtra("CONTRACT_ID", contractId);
        mapsIntent.putExtra("COMPANY_NAME", text("name"));
        mapsIntent.putExtra("ADDRESS", text("address"));
        startActivity(mapsIntent);
    }

    private void openAssets() {
        Intent assetsIntent = new Intent(this, ContractAssetsActivity.class);
        assetsIntent.putExtra(ContractAssetsActivity.EXTRA_CONTRACT_ID, contractId);
        assetsIntent.putExtra(ContractAssetsActivity.EXTRA_CONTRACT_NAME, text("name"));
        startActivity(assetsIntent);
    }

    private void showRoutineDialog() {
        if (contract == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Routine Type")
                .setItems(new CharSequence[]{"No Activity", "Activity"}, (dialogInterface, which) -> {
                    Intent intent;
                    if (which == 0) {
                        intent = new Intent(this, RodentRoutineActivity.class);
                        intent.putExtra("ROUTINE_TYPE", "No Activity");
                    } else if (which == 1) {
                        intent = new Intent(this, RodentActivityRoutine.class);
                        intent.putExtra("ROUTINE_TYPE", "Activity");
                    } else {
                        return;
                    }
                    intent.putExtra("USER_NAME", userName);
                    intent.putExtra("COMPANY_NAME", text("name"));
                    intent.putExtra("ADDRESS", text("address"));
                    intent.putExtra("DOCUMENT_ID", contractId);
                    startActivity(intent);
                })
                .show();
    }

    private void showUpdateVisitDialog() {
        if (contract == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Last Visit");

        EditText dateInput = new EditText(this);
        dateInput.setHint("Enter Last Visit Date (dd/MM/yy or dd/MM/yyyy)");
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        dateInput.setSingleLine();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        layout.addView(dateInput);

        builder.setView(layout);
        builder.setPositiveButton("Update", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button updateButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (updateButton != null) {
                updateButton.setOnClickListener(v -> {
                    String normalizedDate = normalizeManualVisitDate(dateInput.getText().toString().trim());
                    if (!normalizedDate.isEmpty()) {
                        updateVisitDates(text("owner"), contractId, normalizedDate, text("visits"), text("name"));
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "Invalid date. Use dd/MM/yy or dd/MM/yyyy.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        dialog.show();
    }

    private String normalizeManualVisitDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return "";

        String[] acceptedFormats = {"dd/MM/yy", "dd/MM/yyyy"};
        java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        outputFormat.setLenient(false);

        for (String format : acceptedFormats) {
            try {
                java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(format, Locale.getDefault());
                parser.setLenient(false);
                java.util.Date parsed = parser.parse(rawDate.trim());
                if (parsed != null) return outputFormat.format(parsed);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private void showReportYearPickerAndOpen(String contractName, String id) {
        if (ContractReportSync.useContractReportsOnly() && ContractReportSync.hasContractId(id)) {
            Intent intent = new Intent(this, ContractReportsActivity.class);
            intent.putExtra("CONTRACT_ID", id);
            intent.putExtra("CONTRACT_NAME", contractName);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            return;
        }

        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("View Reports")
                .setMessage("Checking available years…")
                .setCancelable(false)
                .show();

        StorageReference root = FirebaseStorage.getInstance().getReference();
        root.listAll().addOnSuccessListener(result -> {
            loading.dismiss();
            List<YearFolder> yearFolders = new ArrayList<>();
            LinkedHashSet<Integer> seenYears = new LinkedHashSet<>();
            for (StorageReference prefix : result.getPrefixes()) {
                YearFolder yf = YearFolder.tryParse(prefix.getName());
                if (yf != null && seenYears.add(yf.year)) yearFolders.add(yf);
            }
            final List<YearFolder> finalYearFolders;
            if (yearFolders.isEmpty()) {
                finalYearFolders = buildFallbackYears();
            } else {
                Collections.sort(yearFolders, (a, b) -> Integer.compare(b.year, a.year));
                finalYearFolders = yearFolders;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Select report year")
                    .setItems(buildReportPickerLabels(finalYearFolders, id), (d, which) ->
                            openSelectedReportPickerOption(which, finalYearFolders, contractName, id))
                    .setNegativeButton("Cancel", null)
                    .show();
        }).addOnFailureListener(e -> {
            loading.dismiss();
            List<YearFolder> fallback = buildFallbackYears();
            new AlertDialog.Builder(this)
                    .setTitle("Select report year")
                    .setMessage("Could not list Storage folders. Showing default years.")
                    .setItems(buildReportPickerLabels(fallback, id), (d, which) ->
                            openSelectedReportPickerOption(which, fallback, contractName, id))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private String[] buildReportPickerLabels(List<YearFolder> yearFolders, String id) {
        int extra = ContractReportSync.hasContractId(id) ? 1 : 0;
        String[] labels = new String[yearFolders.size() + extra];
        int index = 0;
        if (extra == 1) labels[index++] = "Contracts";
        for (YearFolder folder : yearFolders) labels[index++] = String.valueOf(folder.year);
        return labels;
    }

    private void openSelectedReportPickerOption(int which, List<YearFolder> yearFolders, String contractName, String id) {
        boolean hasContractReports = ContractReportSync.hasContractId(id);
        if (hasContractReports && which == 0) {
            Intent intent = new Intent(this, ContractReportsActivity.class);
            intent.putExtra("CONTRACT_ID", id);
            intent.putExtra("CONTRACT_NAME", contractName);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("OPEN_CONTRACT_FOLDER_ONLY", true);
            startActivity(intent);
            return;
        }
        int yearIndex = hasContractReports ? which - 1 : which;
        if (yearIndex < 0 || yearIndex >= yearFolders.size()) return;
        YearFolder selected = yearFolders.get(yearIndex);
        Intent intent = new Intent(this, ContractReportsActivity.class);
        intent.putExtra("CONTRACT_ID", id);
        intent.putExtra("CONTRACT_NAME", contractName);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("REPORTS_FOLDER", selected.folderName);
        intent.putExtra("REPORT_YEAR", selected.year);
        startActivity(intent);
    }

    private List<YearFolder> buildFallbackYears() {
        int y = Calendar.getInstance().get(Calendar.YEAR);
        List<YearFolder> out = new ArrayList<>();
        out.add(new YearFolder(y, "Reports" + String.format(Locale.getDefault(), "%02d", y % 100)));
        out.add(new YearFolder(y - 1, "Reports" + String.format(Locale.getDefault(), "%02d", (y - 1) % 100)));
        return out;
    }

    private void showVisitsDialog(Map<String, Object> loadedContract, String documentId) {
        if (db == null || documentId == null || documentId.trim().isEmpty()) {
            showVisitsDialogFromData(loadedContract);
            return;
        }
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("Visit History")
                .setMessage("Loading history...")
                .setCancelable(false)
                .show();
        db.collection(FirestorePaths.CONTRACTS)
                .document(documentId)
                .collection("visitHistory")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshot -> {
                    loading.dismiss();
                    if (snapshot == null || snapshot.isEmpty()) {
                        showVisitsDialogFromData(loadedContract);
                        return;
                    }
                    LinearLayout listLayout = new LinearLayout(this);
                    listLayout.setOrientation(LinearLayout.VERTICAL);
                    listLayout.setPadding(32, 16, 32, 16);
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String type = doc.getString("type") != null ? doc.getString("type") : "Visit";
                        String date = doc.getString("date") != null ? doc.getString("date") : "Unknown date";
                        String tech = doc.getString("tech") != null ? doc.getString("tech") : "";
                        String label = "● " + type + "  –  " + date
                                + (tech.isEmpty() ? "" : "  (" + tech + ")");
                        TextView entry = new TextView(this);
                        entry.setText(label);
                        entry.setTextSize(14f);
                        entry.setPadding(0, 10, 0, 10);
                        listLayout.addView(entry);
                        android.view.View divider = new android.view.View(this);
                        divider.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 1));
                        divider.setBackgroundColor(android.graphics.Color.LTGRAY);
                        listLayout.addView(divider);
                    }
                    android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
                    scrollView.addView(listLayout);
                    int maxHeightPx = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
                    scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, maxHeightPx));
                    new AlertDialog.Builder(this)
                            .setTitle("Visit History (" + snapshot.size() + " entries)")
                            .setView(scrollView)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    showVisitsDialogFromData(loadedContract);
                });
    }

    private void showVisitsDialogFromData(Map<String, Object> data) {
        String routineLines = buildCounterDisplayLines(data, "Routines", "Routine");
        String callOutLines = buildCounterDisplayLines(data, "Callout", "Callout");
        StringBuilder message = new StringBuilder();
        message.append("Routine visits:\n");
        message.append(routineLines.isEmpty() ? "No routine visits recorded." : routineLines);
        message.append("\n\nCall outs:\n");
        message.append(callOutLines.isEmpty() ? "No call outs recorded." : callOutLines);
        new AlertDialog.Builder(this)
                .setTitle("View Visits")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void writeVisitHistoryEntry(String documentId, String type, String date) {
        if (db == null || documentId == null || documentId.trim().isEmpty()) return;
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", type);
            entry.put("date", date);
            entry.put("tech", SessionManager.getName(this));
            entry.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
            db.collection(FirestorePaths.CONTRACTS)
                    .document(documentId)
                    .collection("visitHistory")
                    .add(entry)
                    .addOnFailureListener(e -> Log.w("VisitHistory",
                            "Failed to write visit history entry: " + e.getMessage()));
        } catch (Exception e) {
            Log.w("VisitHistory", "Error writing visit history: " + e.getMessage());
        }
    }

    private void updateVisitDates(String owner, String documentId, String lastVisit, String visits, String contractName) {
        updateVisitDates(owner, documentId, lastVisit, visits, contractName, false);
    }

    private void updateVisitDates(String owner, String documentId, String lastVisit, String visits, String contractName, boolean auditRoutine) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", lastVisit);
        Map<String, Object> temp = new HashMap<>();
        temp.put("lastVisit", lastVisit);
        temp.put("visits", visits != null ? visits : "0");
        String nextVisit = ViewContractActivity.calculateNextVisit(temp);
        updates.put("nextVisit", nextVisit);

        try {
            FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
            String authUid = authUser != null ? authUser.getUid() : "null";
            SessionManager.Session session = SessionManager.getCached(this);
            String role = session != null ? session.roleNorm : "unknown";
            String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
            Log.d("ViewContractActivity", "Updating contract visit in collection=" + FirestorePaths.CONTRACTS
                    + " docId=" + documentId
                    + " lastVisit=" + lastVisit
                    + " nextVisit=" + nextVisit
                    + " (ownerKey=" + owner
                    + ", authUid=" + authUid
                    + ", role=" + role
                    + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "") + ")");
        } catch (Exception e) {
            Log.w("ViewContractActivity", "Failed to log contract visit update: " + e.getMessage());
        }

        db.collection(FirestorePaths.CONTRACTS).document(documentId).update(updates).addOnSuccessListener(aVoid -> {
            if (auditRoutine) {
                com.grpc.grpc.audit.data.AuditLogRepository.contractRoutine(documentId, contractName);
            } else {
                com.grpc.grpc.audit.data.AuditLogRepository.contractUpdated(documentId, contractName);
            }
            Toast.makeText(this, "Visit updated successfully.", Toast.LENGTH_SHORT).show();
            try {
                if (!BuildConfig.IS_OFFLINE) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("contractId", documentId);
                    data.put("userName", owner);
                    data.put("contractName", contractName != null ? contractName : "");
                    String docId = "contract_visit_" + documentId + "_" + System.currentTimeMillis();
                    NotificationUtils.writeInAppNotification(
                            owner != null ? owner : SessionManager.getName(this),
                            docId,
                            contractName != null && !contractName.isEmpty() ? contractName : "Contract visit updated",
                            "",
                            "contract_update",
                            data
                    );
                }
            } catch (Exception ignored) { }
            incrementYearlyCounter(documentId, "Routines", "Routine", lastVisit, this::reload);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update visit: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void incrementYearlyCounter(String documentId, String prefix, String label, String visitDate, Runnable onComplete) {
        incrementYearlyCounter(documentId, prefix, label, visitDate, onComplete, null);
    }

    private void incrementYearlyCounter(String documentId, String prefix, String label, String visitDate, Runnable onComplete, String auditContractName) {
        if (db == null || documentId == null || documentId.trim().isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        DocumentReference ref = db.collection(FirestorePaths.CONTRACTS).document(documentId);
        String counterKey = visitDate != null && !visitDate.trim().isEmpty()
                ? buildYearlyCounterKey(prefix, visitDate)
                : buildYearlyCounterKey(prefix);
        String numberPath = counterKey + ".number";
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(ref);
            int current = readCounterNumber(snapshot.get(numberPath));
            transaction.update(ref, numberPath, current + 1);
            return current + 1;
        }).addOnSuccessListener(value -> {
            if ("Callout".equals(prefix) && auditContractName != null) {
                com.grpc.grpc.audit.data.AuditLogRepository.contractCallOut(documentId, auditContractName);
            }
            Toast.makeText(this, label + " counter updated.", Toast.LENGTH_SHORT).show();
            if (onComplete != null) onComplete.run();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update " + label.toLowerCase(Locale.getDefault()) + " counter: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (onComplete != null) onComplete.run();
        });
    }

    private String buildYearlyCounterKey(String prefix) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        return prefix + String.format(Locale.getDefault(), "%02d", year % 100);
    }

    private String buildYearlyCounterKey(String prefix, String visitDate) {
        String suffix = extractTwoDigitYear(visitDate);
        return prefix + (suffix.isEmpty() ? String.format(Locale.getDefault(), "%02d", Calendar.getInstance().get(Calendar.YEAR) % 100) : suffix);
    }

    private String extractTwoDigitYear(String visitDate) {
        if (visitDate == null) return "";
        String trimmed = visitDate.trim();
        if (trimmed.matches("^\\d{2}/\\d{2}/\\d{2}$")) return trimmed.substring(trimmed.length() - 2);
        if (trimmed.matches("^\\d{2}/\\d{2}/\\d{4}$")) return trimmed.substring(trimmed.length() - 2);
        return "";
    }

    private int readCounterNumber(Object raw) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String buildCounterDisplayLines(Map<String, Object> data, String storagePrefix, String displayPrefix) {
        if (data == null || data.isEmpty()) return "";
        TreeMap<Integer, String> newestFirst = new TreeMap<>(Collections.reverseOrder());
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(storagePrefix)) continue;
            String suffix = key.substring(storagePrefix.length());
            if (!suffix.matches("\\d{2}")) continue;
            int count = readCounterNumberFromEntry(entry.getValue());
            if (count <= 0) continue;
            int year = 2000 + Integer.parseInt(suffix);
            newestFirst.put(year, displayPrefix + suffix + " - " + count);
        }
        StringBuilder out = new StringBuilder();
        for (String line : newestFirst.values()) {
            if (out.length() > 0) out.append("\n");
            out.append(line);
        }
        return out.toString();
    }

    private int readCounterNumberFromEntry(Object entryValue) {
        if (entryValue instanceof Map) {
            Object number = ((Map<?, ?>) entryValue).get("number");
            return readCounterNumber(number);
        }
        return readCounterNumber(entryValue);
    }

    private void addContractReminder(String documentId, String contractName, String address, CheckBox checkBox) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || db == null) {
            checkBox.setChecked(false);
            return;
        }
        String docId = user.getUid() + "_" + documentId;
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUid());
        data.put("contractId", documentId);
        data.put("contractName", contractName != null ? contractName : "");
        data.put("contractAddress", address != null ? address : "");
        data.put("lastNotifiedAt", null);
        db.collection(FirestorePaths.CONTRACT_REMINDERS).document(docId).set(data)
                .addOnSuccessListener(aVoid -> {
                    reminderOn = true;
                    scheduleContractReminderWorker();
                    Toast.makeText(this, "You'll get a reminder every 12 hours. Uncheck to stop.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    checkBox.setChecked(false);
                    reminderOn = false;
                    Toast.makeText(this, "Could not set reminder: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
                });
    }

    private void removeContractReminder(String documentId, CheckBox checkBox) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || db == null) return;
        String docId = user.getUid() + "_" + documentId;
        db.collection(FirestorePaths.CONTRACT_REMINDERS).document(docId).delete()
                .addOnSuccessListener(aVoid -> reminderOn = false)
                .addOnFailureListener(e -> {
                    checkBox.setChecked(true);
                    reminderOn = true;
                    Toast.makeText(this, "Could not remove reminder.", Toast.LENGTH_SHORT).show();
                });
    }

    private void scheduleContractReminderWorker() {
        try {
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                    com.grpc.grpc.contracts.worker.ContractReminderWorker.class,
                    12, TimeUnit.HOURS
            ).build();
            WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                    CONTRACT_REMINDER_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work
            );
        } catch (Exception ignored) {}
    }

    private void openInMaps(String address) {
        if (address != null && !address.equals("N/A")) {
            try {
                FirebaseFirestore.getInstance()
                        .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                        .document(LocationSharing.userKey(userName))
                        .set(new HashMap<String, Object>() {{
                            put("userName", userName);
                            put("lastMapQuery", address);
                            put("lastMapClientTimestampMs", System.currentTimeMillis());
                            put("lastMapAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                            put("source", "map_open");
                        }}, com.google.firebase.firestore.SetOptions.merge());
            } catch (Exception ignored) {}

            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "No address available to open in Maps.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditOrDeleteDialog(String documentId, Map<String, Object> current) {
        SessionManager.ensureLoaded(this, null);
        if (!(SessionManager.isAdmin(this) || SessionManager.canHardPressContracts(this))) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("Only an administrator can edit or delete contracts. Contact the office if you need changes.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        EditText nameInput = fieldInput("Name", current.get("name"));
        EditText addressInput = fieldInput("Address", current.get("address"));
        EditText emailInput = fieldInput("Email", current.get("email"));
        EditText contactInput = fieldInput("Contact", current.get("contact"));
        EditText visitsInput = fieldInput("Visits", current.get("visits"));
        EditText notesInput = fieldInput("Notes", current.get("notes"));
        notesInput.setMinLines(3);
        notesInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        layout.addView(nameInput);
        layout.addView(addressInput);
        layout.addView(emailInput);
        layout.addView(contactInput);
        layout.addView(visitsInput);
        layout.addView(notesInput);

        AutoCompleteTextView ownerInput = new AutoCompleteTextView(this);
        ownerInput.setHint("Owner (contractKey)");
        String currentOwner = current.get("owner") != null ? current.get("owner").toString() : "N/A";
        ownerInput.setText(currentOwner);
        ownerInput.setBackgroundResource(R.drawable.edit_text_border);
        ownerInput.setPadding(16, 16, 16, 16);
        List<String> ownerKeys = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (staffOptions != null) {
            for (UserRepository.AssignableUser u : staffOptions) {
                if (u == null || u.contractKey == null) continue;
                String ck = u.contractKey.trim();
                if (ck.isEmpty()) continue;
                if (seen.add(ck.toLowerCase(Locale.getDefault()))) ownerKeys.add(ck);
            }
        }
        if (!ownerKeys.contains(currentOwner) && currentOwner != null && !"N/A".equalsIgnoreCase(currentOwner)) {
            ownerKeys.add(currentOwner);
        }
        Collections.sort(ownerKeys, String::compareToIgnoreCase);
        ArrayAdapter<String> ownerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, ownerKeys);
        ownerInput.setAdapter(ownerAdapter);
        ownerInput.setThreshold(1);
        layout.addView(ownerInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit or Delete Contract")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    String newAddress = addressInput.getText().toString().trim();
                    String newEmail = emailInput.getText().toString().trim();
                    String newContact = contactInput.getText().toString().trim();
                    String newVisits = visitsInput.getText().toString().trim();
                    String newNotes = notesInput.getText().toString().trim();
                    String newOwner = ownerInput.getText().toString().trim();
                    if (newName.isEmpty() || newAddress.isEmpty()) {
                        Toast.makeText(this, "Name and Address are required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String currentOwnerLocal = current.get("owner") != null ? current.get("owner").toString() : "N/A";
                    Map<String, Object> updates = new HashMap<>();
                    if (!newOwner.equalsIgnoreCase(currentOwnerLocal)) {
                        updates.put("assignedTech", newOwner.toLowerCase(Locale.getDefault()));
                    }
                    updates.put("name", newName);
                    updates.put("address", newAddress);
                    updates.put("email", newEmail);
                    updates.put("contact", newContact);
                    updates.put("visits", newVisits);
                    updates.put("notes", newNotes);
                    updateContractFields(currentOwnerLocal, documentId, updates, newName);
                })
                .setNegativeButton("Delete", (dialog, which) -> {
                    String owner = current.get("owner") != null ? current.get("owner").toString() : "Unknown";
                    deleteContract(owner, documentId);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private EditText fieldInput(String hint, Object value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value != null ? value.toString() : "N/A");
        input.setBackgroundResource(R.drawable.edit_text_border);
        input.setPadding(16, 16, 16, 16);
        return input;
    }

    private void updateContractFields(String owner, String documentId, Map<String, Object> updates, String contractName) {
        if (updates == null || updates.isEmpty()) return;
        Object visitsValue = updates.get("visits");
        if (visitsValue != null) {
            try {
                int visits = Integer.parseInt(String.valueOf(visitsValue).trim());
                if (visits < 1 || visits > 99) {
                    Toast.makeText(this, "Visits must be a number between 1 and 99.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for Visits.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        db.collection(FirestorePaths.CONTRACTS).document(documentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> finalUpdates = new HashMap<>(updates);
                    String lastVisit = snapshot != null && snapshot.get("lastVisit") != null
                            ? String.valueOf(snapshot.get("lastVisit"))
                            : "N/A";
                    Object visitsRaw = finalUpdates.containsKey("visits")
                            ? finalUpdates.get("visits")
                            : (snapshot != null ? snapshot.get("visits") : "0");
                    Map<String, Object> temp = new HashMap<>();
                    temp.put("lastVisit", lastVisit);
                    temp.put("visits", visitsRaw != null ? visitsRaw : "0");
                    finalUpdates.put("nextVisit", ViewContractActivity.calculateNextVisit(temp));
                    db.collection(FirestorePaths.CONTRACTS).document(documentId)
                            .update(finalUpdates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Contract updated successfully.", Toast.LENGTH_SHORT).show();
                                try {
                                    if (!BuildConfig.IS_OFFLINE) {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("contractId", documentId);
                                        data.put("userName", owner);
                                        data.put("contractName", contractName != null ? contractName : "");
                                        NotificationUtils.writeInAppNotification(
                                                owner != null ? owner : SessionManager.getName(this),
                                                "contract_update_" + documentId,
                                                contractName != null && !contractName.isEmpty() ? contractName : "Contract updated",
                                                "",
                                                "contract_update",
                                                data
                                        );
                                    }
                                } catch (Exception ignored) { }
                                com.grpc.grpc.audit.data.AuditLogRepository.contractUpdated(documentId, contractName);
                                reload();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to update contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void deleteContract(String owner, String documentId) {
        db.collection(FirestorePaths.CONTRACTS).document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    String contractName = text("name");
                    com.grpc.grpc.audit.data.AuditLogRepository.contractDeleted(documentId, contractName);
                    Toast.makeText(this, "Contract deleted successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private String text(String key) {
        if (contract == null || contract.get(key) == null) return "N/A";
        String value = contract.get(key).toString().trim();
        return value.isEmpty() ? "N/A" : value;
    }

    private String notesText() {
        if (contract == null || contract.get("notes") == null) return "N/A";
        String notes = contract.get("notes").toString().trim();
        return notes.isEmpty() ? "N/A" : notes;
    }

    private void addText(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        view.setPadding(0, 10, 0, 0);
        container.addView(view);
    }

    private void addButton(String label, Runnable action) {
        addButton(container, label, action);
    }

    private void addButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        parent.addView(button);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static class YearFolder {
        final int year;
        final String folderName;

        YearFolder(int year, String folderName) {
            this.year = year;
            this.folderName = folderName;
        }

        static YearFolder tryParse(String folder) {
            if (folder == null || !folder.startsWith("Reports")) return null;
            String suffix = folder.substring("Reports".length());
            if (suffix.isEmpty()) return null;
            try {
                int raw = Integer.parseInt(suffix);
                int year = (suffix.length() <= 2) ? (2000 + raw) : raw;
                if (year < 2000 || year > 2100) return null;
                return new YearFolder(year, folder);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
