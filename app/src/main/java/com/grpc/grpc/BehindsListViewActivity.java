package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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
 * BehindsListViewActivity.java
 *
 * This activity allows users to view, search, share, rename, and delete stored behinds list PDF files.
 * The behinds list files are displayed in a RecyclerView, providing user-friendly interaction with single-click and long-click options.
 *
 * Features:
 * - Displays a list of stored PDF behinds list files
 * - Allows searching files by name using a search bar
 * - Supports viewing files using a PDF viewer
 * - Enables sharing, renaming, and deleting files
 * - Ensures user-friendly alerts and UI interaction
 *
 * Author: GRPC
 */

public class BehindsListViewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button backButton;
    private ReportAdapter adapter;
    private List<File> behindsListFiles;
    private List<File> allBehindsListFiles;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_viewer);

        userName = getIntent().getStringExtra("USER_NAME");

        // Initialize views
        recyclerView = findViewById(R.id.report_recycler_view);
        searchBar = findViewById(R.id.search_bar);
        backButton = findViewById(R.id.buttonreturn);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterBehindsListFiles(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Add keyboard handling
        searchBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // When search bar gets focus, ensure it's visible
                searchBar.post(() -> {
                    searchBar.requestFocus();
                });
            }
        });

        // Set up back button
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(BehindsListViewActivity.this, ContractsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });

        loadBehindsListFiles();
    }

    /**
     * Loads all PDF behinds list files from the "BEHINDS LIST" folder.
     */
    private void loadBehindsListFiles() {
        behindsListFiles = new ArrayList<>();
        allBehindsListFiles = new ArrayList<>();

        // Access the behinds list folder in external storage
        File behindsListFolder = new File(getExternalFilesDir(null), "BEHINDS LIST");
        if (behindsListFolder.exists()) {
            // Filter for PDF files only
            File[] files = behindsListFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (files != null) {
                allBehindsListFiles.addAll(Arrays.asList(files));
                behindsListFiles.addAll(Arrays.asList(files));
            }
        } else {
            Toast.makeText(this, "No Behinds List files found", Toast.LENGTH_SHORT).show();
        }

        // Initialize the adapter with click listeners
        adapter = new ReportAdapter(this, behindsListFiles, new ReportAdapter.OnReportClickListener() {
            @Override
            public void onReportClick(File file) {
                showSinglePressOptions(file);
            }

            @Override
            public void onReportLongClick(File file) {
                showLongPressOptions(file);
            }
        });

        // Bind the adapter to the RecyclerView
        recyclerView.setAdapter(adapter);
    }

    /**
     * Extracts technician name from a behinds/due list PDF filename.
     * Examples:
     *  - BehindsList_<TECH>_<DATE>.pdf
     *  - <TECH>_Due_list_<DATE>.pdf
     */
    private String extractTechnicianName(File file) {
        if (file == null) return "Unknown";
        String name = file.getName();

        try {
            if (name.startsWith("BehindsList_")) {
                // BehindsList_<TECH>_<DATE>.pdf
                String remaining = name.substring("BehindsList_".length());
                int underscoreIndex = remaining.indexOf('_');
                if (underscoreIndex > 0) {
                    return remaining.substring(0, underscoreIndex).replace('_', ' ');
                }
            } else if (name.contains("_Due_list_")) {
                // <TECH>_Due_list_<DATE>.pdf
                int idx = name.indexOf("_Due_list_");
                if (idx > 0) {
                    return name.substring(0, idx).replace('_', ' ');
                }
            }
        } catch (Exception ignored) {
        }

        return "Unknown";
    }

    /**
     * Displays options when a file is single-clicked (View).
     *
     * @param file The selected file
     */
    private void showSinglePressOptions(File file) {
        String techName = extractTechnicianName(file);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("View PDF - " + techName)
                .setItems(new CharSequence[]{"View"}, (dialog, which) -> {
                    if (which == 0) {
                        viewPDF(file);
                    }
                })
                .show();
    }

    /**
     * Displays options when a file is long-clicked (Share, Delete, Rename).
     *
     * @param file The selected file
     */
    private void showLongPressOptions(File file) {
        String techName = extractTechnicianName(file);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Options for " + techName)
                .setItems(new CharSequence[]{"Share", "Delete", "Rename"}, (dialog, which) -> {
                    if (which == 0) {
                        shareBehindsListFile(file);
                    } else if (which == 1) {
                        deleteBehindsListFile(file);
                    } else if (which == 2) {
                        renameBehindsListFile(file);
                    }
                })
                .show();
    }

    /**
     * Filters the displayed files based on the search query.
     *
     * @param query The text entered in the search bar
     */
    private void filterBehindsListFiles(String query) {
        behindsListFiles.clear();
        for (File file : allBehindsListFiles) {
            if (file.getName().toLowerCase().contains(query.toLowerCase())) {
                behindsListFiles.add(file);
            }
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Shares the selected file using an Intent.
     *
     * @param file The file to be shared
     */
    private void shareBehindsListFile(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Behinds List"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the file.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Deletes the selected file and refreshes the list.
     *
     * @param file The file to be deleted
     */
    private void deleteBehindsListFile(File file) {
        if (file.delete()) {
            Toast.makeText(this, "File deleted successfully!", Toast.LENGTH_SHORT).show();
            loadBehindsListFiles(); // Refresh list
        } else {
            Toast.makeText(this, "Failed to delete file.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Launches an intent to view the selected PDF file using a PDF viewer app.
     *
     * @param file The file to be viewed
     */
    private void viewPDF(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
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
     * Renames the selected file and refreshes the list.
     *
     * @param file The file to be renamed
     */
    private void renameBehindsListFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename File");

        EditText input = new EditText(this);
        input.setText(file.getName().replace(".pdf", ""));
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                String newFileName = newName + ".pdf";
                File newFile = new File(file.getParent(), newFileName);
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "File renamed successfully!", Toast.LENGTH_SHORT).show();
                    loadBehindsListFiles(); // Refresh list
                } else {
                    Toast.makeText(this, "Failed to rename file.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
} 