package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
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
 * Author: James Scott
 */

public class ContractReportsActivity extends AppCompatActivity {

    private String contractName;
    private String userName;
    private List<String> foundReports = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contract_reports);

        // Get contract name and username from intent
        contractName = getIntent().getStringExtra("CONTRACT_NAME");
        userName = getIntent().getStringExtra("USER_NAME");

        if (contractName == null || contractName.isEmpty()) {
            Toast.makeText(this, "Error: Contract name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Search for reports related to this contract
        searchContractReports();
    }

    /**
     * Searches for reports related to the contract across all ReportsXX folders and subfolders.
     * Uses normalized, flexible contract name matching (ignores spaces, underscores, and case).
     */
    private void searchContractReports() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();

        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("Searching Reports")
                .setMessage("Searching for reports related to: " + contractName)
                .setCancelable(false)
                .show();

        foundReports.clear();
        String normalizedContractName = normalizeName(contractName);

        // List all root folders and filter for ReportsXX
        storageRef.listAll().addOnSuccessListener(listResult -> {
            List<StorageReference> reportRoots = new ArrayList<>();
            for (StorageReference folder : listResult.getPrefixes()) {
                String folderName = folder.getName();
                if (folderName.matches("Reports\\d+")) {
                    reportRoots.add(folder);
                }
            }
            if (reportRoots.isEmpty()) {
                loadingDialog.dismiss();
                showSearchResults();
                return;
            }
            int[] completedRoots = {0};
            int totalRoots = reportRoots.size();
            for (StorageReference reportsFolder : reportRoots) {
                reportsFolder.listAll().addOnSuccessListener(listResult2 -> {
                    // Search in monthly subfolders (January, February, etc.)
                    for (StorageReference monthFolder : listResult2.getPrefixes()) {
                        monthFolder.listAll().addOnSuccessListener(monthResult -> {
                            for (StorageReference file : monthResult.getItems()) {
                                String fileName = file.getName();
                                String normalizedFileName = normalizeName(fileName.substring(0, fileName.lastIndexOf(".")));
                                if (normalizedFileName.contains(normalizedContractName)) {
                                    foundReports.add(reportsFolder.getName() + "/" + monthFolder.getName() + "/" + fileName);
                                }
                            }
                            completedRoots[0]++;
                            if (completedRoots[0] >= totalRoots * listResult2.getPrefixes().size()) {
                                loadingDialog.dismiss();
                                showSearchResults();
                            }
                        });
                    }
                    // Search in files directly in ReportsXX folder
                    for (StorageReference file : listResult2.getItems()) {
                        String fileName = file.getName();
                        String normalizedFileName = normalizeName(fileName.substring(0, fileName.lastIndexOf(".")));
                        if (normalizedFileName.contains(normalizedContractName)) {
                            foundReports.add(reportsFolder.getName() + "/" + fileName);
                        }
                    }
                    completedRoots[0]++;
                    if (completedRoots[0] >= totalRoots * listResult2.getPrefixes().size()) {
                        loadingDialog.dismiss();
                        showSearchResults();
                    }
                }).addOnFailureListener(e -> {
                    completedRoots[0]++;
                    if (completedRoots[0] >= totalRoots) {
                        loadingDialog.dismiss();
                        showSearchResults();
                    }
                });
            }
        }).addOnFailureListener(e -> {
            loadingDialog.dismiss();
            showSearchResults();
        });
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
            Toast.makeText(this, "No reports found for: " + contractName, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reports for: " + contractName + " (" + foundReports.size() + " found)");

        // Create custom layout for search and results
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        RecyclerView resultsRecyclerView = dialogView.findViewById(R.id.resultsRecyclerView);
        
        // Set up RecyclerView
        resultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        SearchResultsAdapter adapter = new SearchResultsAdapter(foundReports, selectedItem -> {
            String[] pathParts = selectedItem.split("/");
            if (pathParts.length >= 2) {
                String folderPath = selectedItem.substring(0, selectedItem.lastIndexOf("/"));
                String fileName = pathParts[pathParts.length - 1];
                showReportOptions(folderPath, fileName);
            }
        });
        resultsRecyclerView.setAdapter(adapter);
        
        builder.setView(dialogView);

        // Add search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                List<String> filteredList = new ArrayList<>();
                for (String report : foundReports) {
                    if (report.toLowerCase().contains(s.toString().toLowerCase())) {
                        filteredList.add(report);
                    }
                }
                adapter.updateResults(filteredList);
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