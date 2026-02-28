package com.grpc.grpc;

import android.annotation.SuppressLint;
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
 * ServiceAgreementViewActivity.java
 *
 * This activity allows users to view, search, share, rename, and delete stored service agreements in PDF format.
 * The agreements are displayed in a RecyclerView, providing user-friendly interaction with single-click and long-click options.
 *
 * Features:
 * - Displays a list of stored service agreement PDFs
 * - Allows searching agreements by name using a search bar
 * - Supports viewing agreements using a PDF viewer
 * - Enables sharing, renaming, and deleting agreements
 * - Ensures user-friendly alerts and UI interaction
 *
 * Author: GRPC
 */

public class ServiceAgreementViewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button returnButton;

    private ReportAdapter adapter;
    private List<File> agreementFiles;
    private List<File> allAgreementFiles;

    private String userName;
    private ActionMode actionMode;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_agreements_view);

        // Retrieve the username from the intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        recyclerView = findViewById(R.id.service_agreement_recycler_view);
        searchBar = findViewById(R.id.search_bar);
        returnButton = findViewById(R.id.buttonReturn);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadServiceAgreements();

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterServiceAgreements(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        returnButton.setOnClickListener(view -> {
            Intent intent = new Intent(ServiceAgreementViewActivity.this, MainActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Loads all PDF service agreements from the "ServiceAgreements" folder.
     */
    private void loadServiceAgreements() {
        agreementFiles = new ArrayList<>();
        allAgreementFiles = new ArrayList<>();

        // Corrected path: Match with ServiceAgreementGenerator.java
        File agreementsFolder = new File(getExternalFilesDir(null), "ServiceAgreements");
        if (agreementsFolder.exists()) {
            File[] files = agreementsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
            if (files != null) {
                allAgreementFiles.addAll(Arrays.asList(files));
                agreementFiles.addAll(Arrays.asList(files));
            }
        } else {
            Toast.makeText(this, "No Service Agreements found", Toast.LENGTH_SHORT).show();
        }

        adapter = new ReportAdapter(this, agreementFiles, new ReportAdapter.OnReportClickListener() {
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
                    shareServiceAgreements(adapter.getSelectedFiles());
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

    private void shareServiceAgreements(List<File> files) {
        if (files == null || files.isEmpty()) return;
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (File f : files) {
                if (f == null) continue;
                Uri fileUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", f);
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
                if (clipData == null) clipData = android.content.ClipData.newRawUri("agreements", u);
                else clipData.addItem(new android.content.ClipData.Item(u));
            }
            if (clipData != null) shareIntent.setClipData(clipData);
            startActivity(Intent.createChooser(shareIntent, "Share Service Agreements"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the selected agreements.", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteMultiple(List<File> files) {
        if (files == null || files.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Delete " + files.size() + " selected agreement(s)?")
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
        loadServiceAgreements();
        if (actionMode != null) actionMode.finish();
    }

    /**
     * Filters the displayed service agreements based on the search query.
     *
     * @param query The text entered in the search bar.
     */
    private void filterServiceAgreements(String query) {
        agreementFiles.clear();
        for (File agreement : allAgreementFiles) {
            if (agreement.getName().toLowerCase().contains(query.toLowerCase())) {
                agreementFiles.add(agreement);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showSinglePressOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an Option")
                .setItems(new CharSequence[]{"View", "Share", "Delete", "Rename"}, (dialog, which) -> {
                    if (which == 0) viewPDF(file);
                    else if (which == 1) shareServiceAgreement(file);
                    else if (which == 2) deleteServiceAgreement(file);
                    else if (which == 3) renameServiceAgreement(file);
                })
                .show();
    }

    /**
     * Shares the selected service agreement using an Intent.
     *
     * @param file The service agreement file to be shared.
     */
    private void shareServiceAgreement(File file) {
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

            startActivity(Intent.createChooser(shareIntent, "Share Service Agreement"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the agreement.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Deletes the selected service agreement and refreshes the list.
     *
     * @param file The service agreement file to be deleted.
     */
    private void deleteServiceAgreement(File file) {
        if (file.delete()) {
            Toast.makeText(this, "Service agreement deleted successfully!", Toast.LENGTH_SHORT).show();
            loadServiceAgreements(); // Refresh list
        } else {
            Toast.makeText(this, "Failed to delete service agreement.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Launches an intent to view the selected PDF file using a PDF viewer app.
     *
     * @param file The service agreement file to be viewed.
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
     * Renames the selected service agreement and refreshes the list.
     *
     * @param file The service agreement file to be renamed.
     */
    private void renameServiceAgreement(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Service Agreement");

        final EditText input = new EditText(this);
        input.setText(file.getName().replace(".pdf", ""));
        builder.setView(input);

        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                File newFile = new File(file.getParent(), newName + ".pdf");
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "Service agreement renamed successfully!", Toast.LENGTH_SHORT).show();
                    loadServiceAgreements();
                } else {
                    Toast.makeText(this, "Failed to rename service agreement.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
}
