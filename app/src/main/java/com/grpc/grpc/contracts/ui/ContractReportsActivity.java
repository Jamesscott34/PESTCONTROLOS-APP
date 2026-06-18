package com.grpc.grpc.contracts.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.search.ui.SearchResultsAdapter;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/**
 * ContractReportsActivity.java
 *
 * This activity searches for reports related to a specific contract across all Firebase Storage folders
 * and displays them in a popup dialog. Users can view, download, or share the reports.
 *
 * Features:
 * - Searches across all Reports25 folders and subfolders
 * - Displays matching reports in a popup dialog
 * - Allows viewing, downloading, and sharing reports
 * - Provides user-friendly interface for report management
 *
 * Author: GRPC
 */

public class ContractReportsActivity extends AppCompatActivity {

    private static final String REPORTS_SUBCOLLECTION = "reports";
    private static final String EXTRA_OPEN_CONTRACT_FOLDER_ONLY = "OPEN_CONTRACT_FOLDER_ONLY";

    private static final String EXTRA_REPORTS_FOLDER = "REPORTS_FOLDER";
    private static final String ROOT_FILES_LABEL = "(Root files)";

    private String contractId;
    private String contractName;
    private String userName;
    private String reportsFolder; // e.g. Reports26
    private int reportYear; // e.g. 2026
    private boolean openContractFolderOnly;
    private boolean browseContractByYearFolders;
    private final List<String> foundReports = new ArrayList<>();
    private final Map<String, String> reportDisplayNames = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contract_reports);

        // Get contract name and username from intent
        contractId = getIntent().getStringExtra("CONTRACT_ID");
        contractName = getIntent().getStringExtra("CONTRACT_NAME");
        userName = getIntent().getStringExtra("USER_NAME");
        reportsFolder = getIntent().getStringExtra(EXTRA_REPORTS_FOLDER);
        reportYear = getIntent().getIntExtra("REPORT_YEAR", 0);
        openContractFolderOnly = getIntent().getBooleanExtra(EXTRA_OPEN_CONTRACT_FOLDER_ONLY, false);
        browseContractByYearFolders = openContractFolderOnly
                || (ContractReportSync.useContractReportsOnly()
                && ContractReportSync.hasContractId(contractId)
                && getIntent().getStringExtra(EXTRA_REPORTS_FOLDER) == null);

        if (!browseContractByYearFolders && (reportsFolder == null || reportsFolder.trim().isEmpty())) {
            // Fallback to current year folder if not provided
            int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            reportsFolder = "Reports" + String.format(java.util.Locale.getDefault(), "%02d", y % 100);
            reportYear = y;
        }

        if (contractName == null || contractName.isEmpty()) {
            Toast.makeText(this, "Error: Contract name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (browseContractByYearFolders) {
            showContractYearFolders();
        } else {
            searchContractReports();
        }
    }

    private void showContractYearFolders() {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("Contract Reports")
                .setMessage("Loading folders for: " + contractName)
                .setCancelable(false)
                .show();

        String contractFolder = ContractReportSync.buildContractStorageFolder(contractId);
        ContractStoragePathHelper.listContractYearFolders(contractId, yearFolders -> runOnUiThread(() ->
                ContractStoragePathHelper.listFilesInFolder(contractFolder, (subfolders, rootFiles) -> runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    List<SearchResultsAdapter.ListItem> folderItems = new ArrayList<>();
                    if (!rootFiles.isEmpty()) {
                        folderItems.add(new SearchResultsAdapter.ListItem(ROOT_FILES_LABEL, ROOT_FILES_LABEL));
                    }
                    if (yearFolders != null) {
                        for (String year : yearFolders) {
                            folderItems.add(new SearchResultsAdapter.ListItem(year, year));
                        }
                    }
                    if (folderItems.isEmpty()) {
                        Toast.makeText(this, "No contract report folders found for: " + contractName, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    showFolderPickerDialog(folderItems, contractFolder);
                }))));
    }

    private void showFolderPickerDialog(List<SearchResultsAdapter.ListItem> folderItems, String contractFolder) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        RecyclerView resultsRecyclerView = dialogView.findViewById(R.id.resultsRecyclerView);
        resultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<SearchResultsAdapter.ListItem> visibleFolders = new ArrayList<>(folderItems);
        SearchResultsAdapter adapter = SearchResultsAdapter.forLabeledItems(visibleFolders, selected -> {
            String folderPath = ROOT_FILES_LABEL.equals(selected)
                    ? contractFolder
                    : contractFolder + "/" + selected;
            String folderLabel = ROOT_FILES_LABEL.equals(selected) ? "Root files" : selected;
            openContractFolderFiles(folderPath, folderLabel);
        }, true);
        resultsRecyclerView.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                List<SearchResultsAdapter.ListItem> filtered = new ArrayList<>();
                for (SearchResultsAdapter.ListItem item : folderItems) {
                    if (query.isEmpty() || item.displayLabel.toLowerCase(Locale.ROOT).contains(query)) {
                        filtered.add(item);
                    }
                }
                adapter.updateListItems(filtered);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select folder — " + contractName)
                .setView(dialogView)
                .setNegativeButton("Cancel", (d, w) -> finish())
                .create();
        dialog.setOnCancelListener(d -> finish());
        dialog.show();
    }

    private void openContractFolderFiles(String folderPath, String folderLabel) {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle(folderLabel)
                .setMessage("Loading reports...")
                .setCancelable(false)
                .show();

        LinkedHashMap<String, String> reports = new LinkedHashMap<>();
        final int[] pending = {2};
        Runnable finish = () -> {
            pending[0]--;
            if (pending[0] > 0) {
                return;
            }
            loadingDialog.dismiss();
            if (reports.isEmpty()) {
                Toast.makeText(this, "No reports found in " + folderLabel, Toast.LENGTH_SHORT).show();
                return;
            }
            showFileListDialog(reports, folderLabel);
        };

        ContractStoragePathHelper.listFilesInFolder(folderPath, (ignored, files) -> runOnUiThread(() -> {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String name = entry.getValue();
                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    reports.put(entry.getKey(), name);
                }
            }
            finish.run();
        }));

        String folderPrefix = folderPath.endsWith("/") ? folderPath : folderPath + "/";
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACT_REPORTS)
                .document(contractId)
                .collection(REPORTS_SUBCOLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> runOnUiThread(() -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        String storagePath = doc.getString("storagePath");
                        if (storagePath == null || storagePath.trim().isEmpty()) {
                            continue;
                        }
                        String trimmedPath = storagePath.trim();
                        if (!trimmedPath.equals(folderPath) && !trimmedPath.startsWith(folderPrefix)) {
                            continue;
                        }
                        String fileName = doc.getString("fileName");
                        String displayName = (fileName != null && !fileName.trim().isEmpty())
                                ? fileName.trim()
                                : fileNameFromStoragePath(trimmedPath);
                        reports.put(trimmedPath, displayName);
                    }
                    finish.run();
                }))
                .addOnFailureListener(e -> runOnUiThread(finish));
    }

    private void showFileListDialog(Map<String, String> reports, String folderLabel) {
        List<SearchResultsAdapter.ListItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : reports.entrySet()) {
            items.add(new SearchResultsAdapter.ListItem(entry.getValue(), entry.getKey()));
        }
        Collections.sort(items, (a, b) -> a.displayLabel.compareToIgnoreCase(b.displayLabel));

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        RecyclerView resultsRecyclerView = dialogView.findViewById(R.id.resultsRecyclerView);
        resultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<SearchResultsAdapter.ListItem> visibleItems = new ArrayList<>(items);
        SearchResultsAdapter adapter = SearchResultsAdapter.forLabeledItems(visibleItems, storagePath -> {
            String fileName = reports.get(storagePath);
            if (fileName == null) {
                fileName = fileNameFromStoragePath(storagePath);
            }
            int slash = storagePath.lastIndexOf('/');
            String folderPath = slash >= 0 ? storagePath.substring(0, slash) : storagePath;
            showReportOptions(folderPath, fileName);
        }, false);
        resultsRecyclerView.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                List<SearchResultsAdapter.ListItem> filtered = new ArrayList<>();
                for (SearchResultsAdapter.ListItem item : items) {
                    if (query.isEmpty() || item.displayLabel.toLowerCase(Locale.ROOT).contains(query)) {
                        filtered.add(item);
                    }
                }
                adapter.updateListItems(filtered);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        new AlertDialog.Builder(this)
                .setTitle(folderLabel + " — " + contractName)
                .setView(dialogView)
                .setNegativeButton("Back", null)
                .show();
    }

    private static String fileNameFromStoragePath(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            return "report.pdf";
        }
        int slash = storagePath.lastIndexOf('/');
        return slash >= 0 ? storagePath.substring(slash + 1) : storagePath;
    }

    /**
     * Searches for reports related to the contract across all ReportsXX folders and subfolders.
     * Uses normalized, flexible contract name matching (ignores spaces, underscores, and case).
     */
    private void searchContractReports() {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("Searching Reports")
                .setMessage(buildLoadingMessage())
                .setCancelable(false)
                .show();

        foundReports.clear();
        LinkedHashSet<String> uniqueReports = new LinkedHashSet<>();
        final int[] pendingSources = {0};
        Runnable finishSource = () -> {
            pendingSources[0]--;
            if (pendingSources[0] <= 0) {
                LinkedHashMap<String, String> metadataNames = new LinkedHashMap<>(reportDisplayNames);
                foundReports.clear();
                reportDisplayNames.clear();
                for (String path : uniqueReports) {
                    foundReports.add(path);
                    String name = metadataNames.get(path);
                    reportDisplayNames.put(path, name != null ? name : fileNameFromStoragePath(path));
                }
                loadingDialog.dismiss();
                showSearchResults();
            }
        };

        boolean includeLegacyReports = "grpc".equalsIgnoreCase(BuildConfig.FLAVOR);
        boolean includeContractReports = ContractReportSync.hasContractId(contractId);

        if (includeLegacyReports && !openContractFolderOnly) {
            pendingSources[0]++;
            searchLegacyReports(uniqueReports, finishSource);
        }

        if (includeContractReports) {
            pendingSources[0]++;
            searchContractStorage(uniqueReports, finishSource);

            pendingSources[0]++;
            searchContractReportMetadata(uniqueReports, finishSource);
        }

        if (pendingSources[0] == 0) {
            loadingDialog.dismiss();
            showSearchResults();
        }
    }

    private String buildLoadingMessage() {
        if (openContractFolderOnly) {
            return "Searching contracts folder for: " + contractName;
        }
        if ("grpc".equalsIgnoreCase(BuildConfig.FLAVOR) && reportsFolder != null && !reportsFolder.trim().isEmpty()) {
            return "Searching " + reportsFolder + " and linked contract reports for: " + contractName;
        }
        return "Searching linked contract reports for: " + contractName;
    }

    private void searchLegacyReports(LinkedHashSet<String> uniqueReports, Runnable finishSource) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        StorageReference reportsRoot = storageRef.child(reportsFolder);
        String normalizedContractName = normalizeName(contractName);

        reportsRoot.listAll().addOnSuccessListener(listResult -> {
            for (StorageReference file : listResult.getItems()) {
                addLegacyMatch(uniqueReports, normalizedContractName, reportsFolder, file.getName());
            }

            List<StorageReference> monthFolders = listResult.getPrefixes();
            if (monthFolders == null || monthFolders.isEmpty()) {
                finishSource.run();
                return;
            }

            final int[] pendingFolders = {monthFolders.size()};
            for (StorageReference monthFolder : monthFolders) {
                monthFolder.listAll().addOnSuccessListener(monthResult -> {
                    for (StorageReference file : monthResult.getItems()) {
                        addLegacyMatch(uniqueReports, normalizedContractName, reportsFolder + "/" + monthFolder.getName(), file.getName());
                    }
                    pendingFolders[0]--;
                    if (pendingFolders[0] <= 0) {
                        finishSource.run();
                    }
                }).addOnFailureListener(e -> {
                    pendingFolders[0]--;
                    if (pendingFolders[0] <= 0) {
                        finishSource.run();
                    }
                });
            }
        }).addOnFailureListener(e -> finishSource.run());
    }

    private void addLegacyMatch(LinkedHashSet<String> uniqueReports, String normalizedContractName, String folderPath, String fileName) {
        int dot = fileName.lastIndexOf(".");
        String base = (dot > 0) ? fileName.substring(0, dot) : fileName;
        String normalizedFileName = normalizeName(base);
        if (normalizedFileName.contains(normalizedContractName)) {
            uniqueReports.add(folderPath + "/" + fileName);
        }
    }

    private void searchContractStorage(LinkedHashSet<String> uniqueReports, Runnable finishSource) {
        String contractFolder = ContractReportSync.buildContractStorageFolder(contractId);
        ContractStoragePathHelper.listFilesInFolder(contractFolder, (subfolders, rootFiles) -> {
            for (Map.Entry<String, String> entry : rootFiles.entrySet()) {
                uniqueReports.add(entry.getKey());
            }
            if (subfolders == null || subfolders.isEmpty()) {
                finishSource.run();
                return;
            }
            final int[] pending = {subfolders.size()};
            for (String subfolder : subfolders) {
                String childPath = contractFolder + "/" + subfolder;
                ContractStoragePathHelper.listFilesInFolder(childPath, (ignored, files) -> {
                    for (String storagePath : files.keySet()) {
                        uniqueReports.add(storagePath);
                    }
                    pending[0]--;
                    if (pending[0] <= 0) {
                        finishSource.run();
                    }
                });
            }
        });
    }

    private void searchContractReportMetadata(LinkedHashSet<String> uniqueReports, Runnable finishSource) {
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACT_REPORTS)
                .document(contractId)
                .collection(REPORTS_SUBCOLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        String storagePath = doc.getString("storagePath");
                        if (storagePath != null && !storagePath.trim().isEmpty()) {
                            String trimmedPath = storagePath.trim();
                            uniqueReports.add(trimmedPath);
                            String fileName = doc.getString("fileName");
                            if (fileName != null && !fileName.trim().isEmpty()) {
                                reportDisplayNames.put(trimmedPath, fileName.trim());
                            }
                        }
                    }
                    finishSource.run();
                })
                .addOnFailureListener(e -> finishSource.run());
    }

    /**
     * Normalizes a name by removing spaces, underscores, and converting to lowercase.
     */
    private String normalizeName(String name) {
        return name.replaceAll("[ _]", "").toLowerCase();
    }

    /**
     * Shows search results in a dialog with real-time search functionality.
     */
    private void showSearchResults() {
        if (foundReports.isEmpty()) {
            String message = openContractFolderOnly
                    ? "No contract reports found for: " + contractName
                    : "No reports found for: " + contractName + " in " + reportsFolder;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String yearLabel = openContractFolderOnly ? "Contracts" : ((reportYear > 0) ? String.valueOf(reportYear) : "linked");
        builder.setTitle("Reports (" + yearLabel + ") for: " + contractName + " (" + foundReports.size() + " found)");

        // Create custom layout for search and results
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        RecyclerView resultsRecyclerView = dialogView.findViewById(R.id.resultsRecyclerView);
        
        // Set up RecyclerView
        resultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        List<SearchResultsAdapter.ListItem> listItems = new ArrayList<>();
        for (String path : foundReports) {
            String label = reportDisplayNames.containsKey(path)
                    ? reportDisplayNames.get(path)
                    : fileNameFromStoragePath(path);
            listItems.add(new SearchResultsAdapter.ListItem(label, path));
        }
        SearchResultsAdapter adapter = SearchResultsAdapter.forLabeledItems(listItems, selectedPath -> {
            String fileName = reportDisplayNames.containsKey(selectedPath)
                    ? reportDisplayNames.get(selectedPath)
                    : fileNameFromStoragePath(selectedPath);
            int slash = selectedPath.lastIndexOf('/');
            String folderPath = slash >= 0 ? selectedPath.substring(0, slash) : selectedPath;
            showReportOptions(folderPath, fileName);
        }, false);
        resultsRecyclerView.setAdapter(adapter);
        
        builder.setView(dialogView);

        // Add search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                List<SearchResultsAdapter.ListItem> filteredList = new ArrayList<>();
                for (SearchResultsAdapter.ListItem item : listItems) {
                    if (query.isEmpty()
                            || item.displayLabel.toLowerCase(Locale.ROOT).contains(query)
                            || item.value.toLowerCase(Locale.ROOT).contains(query)) {
                        filteredList.add(item);
                    }
                }
                adapter.updateListItems(filteredList);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Shows options for a selected report (View, Download, Share).
     */
    private void showReportOptions(String folderPath, String fileName) {
        String reportPath = folderPath + "/" + fileName;
        String[] options = {"View", "Download", "Share"};
        
        new AlertDialog.Builder(this)
                .setTitle("Report Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // View
                            viewReport(reportPath);
                            break;
                        case 1: // Download
                            downloadReport(reportPath);
                            break;
                        case 2: // Share
                            shareReport(reportPath);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Views a report using PDF viewer.
     */
    private void viewReport(String reportPath) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference fileRef = storage.getReference().child(reportPath);

        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to open report: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Downloads a report to local storage.
     */
    private void downloadReport(String reportPath) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference fileRef = storage.getReference().child(reportPath);

        // Create local file path
        String fileName = reportPath.substring(reportPath.lastIndexOf("/") + 1);
        java.io.File localFile = new java.io.File(getExternalFilesDir(null), fileName);

        fileRef.getFile(localFile).addOnSuccessListener(taskSnapshot -> {
            Toast.makeText(this, "Report downloaded: " + fileName, Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to download report: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Shares a report using Android share intent.
     */
    private void shareReport(String reportPath) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference fileRef = storage.getReference().child(reportPath);

        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Report: " + contractName);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Report for contract: " + contractName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Report"));
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to share report: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }
} 