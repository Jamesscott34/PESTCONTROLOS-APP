/**
 * ReportViewActivity.java
 *
 * This activity allows the user to view, search, share, and delete reports stored as PDF files.
 * It provides a RecyclerView to display the list of reports and offers single-click and long-click
 * options for interacting with the reports.
 *
 * Key Features:
 * - List all stored PDF reports.
 * - Search reports by name using a search bar.
 * - View, share, and delete reports.
 * - Provides user-friendly alerts for actions like deletion and sharing.
 */

package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.view.ActionMode;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * QuotationViewActivity.java
 *
 * This activity allows users to view, search, share, rename, and delete stored quotation reports in PDF format.
 * The quotations are displayed in a RecyclerView, enabling easy interaction with single and long press options.
 *
 * Features:
 * - Displays a list of stored PDF quotations
 * - Supports searching quotations by name using a search bar
 * - Enables viewing quotations with a PDF viewer
 * - Allows users to share, rename, and delete quotations
 * - Ensures user-friendly alerts for report interactions
 *
 * Author: GRPC
 */

public class QuotationViewActivity extends AppCompatActivity {

    // UI Components
    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button returnButton;

    // Adapter and data structures for managing reports
    private ReportAdapter adapter;
    private List<File> reportFiles;
    private List<File> allReportFiles;

    private String userName;
    private ActionMode actionMode;
    /**
     * Initializes the activity and sets up the RecyclerView, search bar, and return button.
     *
     * @param savedInstanceState The saved state of the activity, used for restoring data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_viewer);

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI components
        recyclerView = findViewById(R.id.report_recycler_view);
        searchBar = findViewById(R.id.search_bar);
        returnButton = findViewById(R.id.buttonreturn);

        // Set up RecyclerView with a LinearLayoutManager
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Load reports and display them in the RecyclerView
        loadReports();

        // Filter reports based on search bar input
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterReports(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Return to the main activity when the return button is clicked
        returnButton.setOnClickListener(view -> {
            Intent intent = new Intent(QuotationViewActivity.this, MainActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass the userName to the main activity
            startActivity(intent); // Start the main activity
            finish(); // Close the current activity
        });
    }

    /**
     * Loads all PDF reports from the designated report storage folder.
     */
    private void loadReports() {
        reportFiles = new ArrayList<>();
        allReportFiles = new ArrayList<>();

        // Access the reports folder in external storage
        File reportsFolder = new File(getExternalFilesDir(null), "GRPEST_QUOTES");
        if (reportsFolder.exists()) {
            // Filter for PDF files only
            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files != null) {
                allReportFiles.addAll(Arrays.asList(files));
                reportFiles.addAll(Arrays.asList(files));
            }
        }

        // Initialize the adapter with click listeners (multi-select + single actions)
        adapter = new ReportAdapter(this, reportFiles, new ReportAdapter.OnReportClickListener() {
            @Override
            public void onReportClick(File file) {
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelected(file);
                    syncActionMode();
                } else {
                    showSinglePressOptions(file);
                }
            }

            @Override
            public void onReportLongClick(File file) {
                if (!adapter.isSelectionMode()) {
                    adapter.setSelectionMode(true);
                    adapter.toggleSelected(file);
                    startSelectionActionMode();
                } else {
                    adapter.toggleSelected(file);
                }
                syncActionMode();
            }
        });

        // Bind the adapter to the RecyclerView
        recyclerView.setAdapter(adapter);
    }

    private void startSelectionActionMode() {
        if (actionMode != null) return;
        actionMode = startSupportActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.menu_report_multiselect, menu);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.action_select_all) {
                    adapter.selectAllVisible();
                    syncActionMode();
                    return true;
                }
                if (id == R.id.action_share) {
                    shareQuotations(adapter.getSelectedFiles());
                    mode.finish();
                    return true;
                }
                if (id == R.id.action_delete) {
                    confirmDeleteMultiple(adapter.getSelectedFiles());
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                actionMode = null;
                adapter.setSelectionMode(false);
                adapter.clearSelection();
            }
        });
        syncActionMode();
    }

    private void syncActionMode() {
        if (actionMode == null) return;
        int count = adapter.getSelectedCount();
        if (count <= 0) {
            actionMode.finish();
            return;
        }
        actionMode.setTitle(count + " selected");
    }

    private void shareQuotations(List<File> files) {
        if (files == null || files.isEmpty()) return;
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : files) {
                if (f == null) continue;
                Uri fileUri = FileProvider.getUriForFile(this, "com.grpc.grpc.fileprovider", f);
                uris.add(fileUri);
            }
            if (uris.isEmpty()) return;
            Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.setType("application/pdf");
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            android.content.ClipData clipData = null;
            for (Uri u : uris) {
                if (u == null) continue;
                if (clipData == null) clipData = android.content.ClipData.newRawUri("quotations", u);
                else clipData.addItem(new android.content.ClipData.Item(u));
            }
            if (clipData != null) shareIntent.setClipData(clipData);
            startActivity(Intent.createChooser(shareIntent, "Share Quotations"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the selected quotations.", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteMultiple(List<File> files) {
        if (files == null || files.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete " + files.size() + " selected quotation(s)?")
                .setPositiveButton("Delete", (d, w) -> deleteMultiple(files))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMultiple(List<File> files) {
        int deleted = 0;
        int failed = 0;
        for (File f : files) {
            if (f != null && f.delete()) deleted++;
            else failed++;
        }
        Toast.makeText(this, "Deleted: " + deleted + (failed > 0 ? " (failed: " + failed + ")" : ""), Toast.LENGTH_SHORT).show();
        loadReports();
        if (actionMode != null) actionMode.finish();
    }

    /**
     * Displays options when a report is single-clicked (View or Edit).
     *
     * @param file The selected report file.
     */
    private void showSinglePressOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an Option")
                .setItems(new CharSequence[]{"View", "Share", "Delete", "Rename"}, (dialog, which) -> {
                    if (which == 0) viewPDF(file);
                    else if (which == 1) shareReport(file);
                    else if (which == 2) deleteReport(file);
                    else if (which == 3) renameReport(file);
                })
                .show();
    }

    /**
     * Filters the displayed reports based on the user's search query.
     *
     * @param query The text entered in the search bar.
     */
    private void filterReports(String query) {
        reportFiles.clear();
        for (File report : allReportFiles) {
            if (report.getName().toLowerCase().startsWith(query.toLowerCase())) {
                reportFiles.add(report);
            }
        }
        adapter.notifyDataSetChanged();  // Update the adapter with filtered results
    }

    /**
     * Shares the selected report using an Intent.
     *
     * @param file The report file to be shared.
     */
    private void shareReport(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Report"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the report.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Deletes the selected report and refreshes the report list.
     *
     * @param file The report file to be deleted.
     */
    private void deleteReport(File file) {
        if (file.delete()) {
            Toast.makeText(this, "Report deleted successfully!", Toast.LENGTH_SHORT).show();
            loadReports();  // Refresh the list after deletion
        } else {
            Toast.makeText(this, "Failed to delete report.", Toast.LENGTH_SHORT).show();
        }
    }



    /**
     * Launches an intent to view the selected PDF file using a PDF viewer app.
     *
     * @param file The report file to be viewed.
     */
    private void viewPDF(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    file
            );

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(viewIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No application found to view this PDF.", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Renames the selected report and refreshes the report list.
     *
     * @param file The report file to be renamed.
     */
    private void renameReport(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Report");

        // Create an EditText for the user to input the new name
        final EditText input = new EditText(this);
        input.setText(file.getName().replace(".pdf", ""));
        builder.setView(input);

        // Add "Rename" and "Cancel" buttons
        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !newName.equals(file.getName())) {
                File newFile = new File(file.getParent(), newName + ".pdf");
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "Report renamed successfully!", Toast.LENGTH_SHORT).show();
                    loadReports(); // Refresh the list after renaming
                } else {
                    Toast.makeText(this, "Failed to rename report.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Invalid name or name unchanged.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
