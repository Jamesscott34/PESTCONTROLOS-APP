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
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfReader;

import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * ReportViewActivity displays a list of saved PDF reports and allows users to interact with them.
 */
public class ERAViewActivity extends AppCompatActivity {

    // UI Components
    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button returnButton;

    // Adapter and data structures for managing reports
    private ReportAdapter adapter;
    private List<File> reportFiles;
    private List<File> allReportFiles;

    private String userName;

    /**
     * Initializes the activity and sets up the RecyclerView, search bar, and return button.
     *
     * @param savedInstanceState The saved state of the activity, used for restoring data.
     */
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_era_viewer);



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
        returnButton.setOnClickListener(view -> navigateBackToMainActivity());
    }

    private void navigateBackToMainActivity() {
        Intent intent = new Intent(ERAViewActivity.this, MainActivity.class);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();
    }

    /**
     * Loads all PDF reports from the designated report storage folder.
     */
    private void loadReports() {
        reportFiles = new ArrayList<>();
        allReportFiles = new ArrayList<>();

        // Access the reports folder in external storage
        File reportsFolder = new File(getExternalFilesDir(null), "EnvironmentalRiskAssessments");
        if (reportsFolder.exists()) {
            // Filter for PDF files only
            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files != null) {
                allReportFiles.addAll(Arrays.asList(files));
                reportFiles.addAll(Arrays.asList(files));
            }
        }

        // Initialize the adapter with click listeners
        adapter = new ReportAdapter(this, reportFiles, new ReportAdapter.OnReportClickListener() {
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
     * Displays options when a report is single-clicked (View or Edit).
     *
     * @param file The selected report file.
     */
    private void showSinglePressOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an Option")
                .setItems(new CharSequence[]{"View"}, (dialog, which) -> {
                    if (which == 0) {
                        viewPDF(file);
                    }
                })
                .show();
    }

    /**
     * Displays options when a report is long-clicked (Share, Delete, or Rename).
     *
     * @param file The selected report file.
     */
    private void showLongPressOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select an Option")
                .setItems(new CharSequence[]{"Share", "Delete", "Rename"}, (dialog, which) -> {
                    if (which == 0) {
                        shareReport(file);
                    } else if (which == 1) {
                        deleteReport(file);
                    } else if (which == 2) {
                        renameReport(file);
                    }
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
