package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
 * ServiceAgreementViewActivity displays a list of saved Service Agreement PDFs
 * and allows users to interact with them (view, search, share, delete, rename).
 */
public class ServiceAgreementViewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button returnButton;

    private ReportAdapter adapter;
    private List<File> agreementFiles;
    private List<File> allAgreementFiles;

    private String userName;

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
                viewPDF(file);
            }

            @Override
            public void onReportLongClick(File file) {
                showLongPressOptions(file);
            }
        });

        recyclerView.setAdapter(adapter);
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

    /**
     * Displays options when a service agreement is long-clicked (Share, Delete, Rename).
     *
     * @param file The selected service agreement file.
     */
    private void showLongPressOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an Option")
                .setItems(new CharSequence[]{"Share", "Delete", "Rename"}, (dialog, which) -> {
                    if (which == 0) {
                        shareServiceAgreement(file);
                    } else if (which == 1) {
                        deleteServiceAgreement(file);
                    } else if (which == 2) {
                        renameServiceAgreement(file);
                    }
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
                    "com.grpc.grpc.fileprovider",
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
