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
public class ReportViewActivity extends AppCompatActivity {

    // UI Components
    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button returnButton;

    // Adapter and data structures for managing reports
    private ReportAdapter adapter;
    private List<File> reportFiles;
    private List<File> allReportFiles;

    /**
     * Initializes the activity and sets up the RecyclerView, search bar, and return button.
     *
     * @param savedInstanceState The saved state of the activity, used for restoring data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_viewer);

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
        returnButton.setOnClickListener(view -> finish());
    }

    /**
     * Loads all PDF reports from the designated report storage folder.
     */
    private void loadReports() {
        reportFiles = new ArrayList<>();
        allReportFiles = new ArrayList<>();

        // Access the reports folder in external storage
        File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
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
                .setItems(new CharSequence[]{"View", "Edit"}, (dialog, which) -> {
                    if (which == 0) {
                        viewPDF(file);
                    } else if (which == 1) {
                        showEditOptions(file);
                    }
                })
                .show();
    }

    /**
     * Displays options when "Edit" is selected (Follow-Up or Rewrite).
     *
     * @param file The selected report file.
     */
    private void showEditOptions(File file) {
       Intent intent = new Intent(this,FollowUpActivity.class);
       intent.putExtra("selected_pdf", file.getAbsolutePath());
       startActivity(intent);
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
     * Edits the selected PDF by adding a follow-up section.
     *
     * @param file The report file to be edited.
     */
    private void editPDF(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Follow-Up Details");

        // Create an EditText for user input
        final EditText input = new EditText(this);
        input.setHint("Enter follow-up details");
        builder.setView(input);

        // Add "Save" and "Cancel" buttons
        builder.setPositiveButton("Save", (dialog, which) -> {
            String followUpDetails = input.getText().toString().trim();
            if (!followUpDetails.isEmpty()) {
                addFollowUpToPDF(file, followUpDetails);
            } else {
                Toast.makeText(this, "Details cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
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


    /**
     * Adds follow-up details to an existing PDF file on a new page with a logo, title, watermark, and footer.
     *
     * @param file The PDF file to be updated.
     * @param followUpDetails The follow-up details entered by the user.
     */
    private void addFollowUpToPDF(File file, String followUpDetails) {
        try {
            // Destination file to save the updated PDF
            File updatedFile = new File(file.getParent(), "Updated_" + file.getName());

            // Create a PdfDocument from the existing file
            PdfDocument pdfDoc = new PdfDocument(new PdfReader(file), new PdfWriter(updatedFile));

            // Create a new page and bind it to the document
            PdfPage newPage = pdfDoc.addNewPage();
            Rectangle pageSize = newPage.getPageSize();

            // Create a separate Document object for the new page
            Document document = new Document(pdfDoc, new com.itextpdf.kernel.geom.PageSize(pageSize));
            document.setMargins(36, 36, 36, 36); // Set margins for the new page

            // Add a watermark image to the new page
            int watermarkResourceId = getResources().getIdentifier("bk", "drawable", getPackageName());
            ImageData watermarkData = ImageDataFactory.create(getResources().openRawResource(watermarkResourceId).readAllBytes());
            Image watermark = new Image(watermarkData)
                    .scaleToFit(500, 500)
                    .setFixedPosition(pageSize.getWidth() / 4, pageSize.getHeight() / 4)
                    .setOpacity(0.1f);
            document.add(watermark);

            // Add a logo image at the top of the new page
            int logoResourceId = getResources().getIdentifier("logo", "drawable", getPackageName());
            ImageData logoData = ImageDataFactory.create(getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData)
                    .scaleToFit(200, 200)
                    .setFixedPosition(pageSize.getWidth() / 2 - 100, pageSize.getHeight() - 150); // Centered at top
            document.add(logo);

            // Add a title below the logo
            document.add(new Paragraph("Good Riddance Pest Control Report")
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(com.itextpdf.kernel.colors.ColorConstants.BLUE)
                    .setTextAlignment(TextAlignment.CENTER));

            // Add the follow-up header
            document.add(new Paragraph("Follow-Up Visit")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.LEFT));

            // Add the date
            document.add(new Paragraph("Date: " + new SimpleDateFormat("dd-MM-yyyy").format(new Date()))
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.LEFT));

            // Handle follow-up details (support multi-line content with manual line breaks)
            String[] lines = followUpDetails.split("\n"); // Split text by newline
            for (String line : lines) {
                document.add(new Paragraph(line)
                        .setFontSize(14)
                        .setTextAlignment(TextAlignment.LEFT));
            }

            // Add footer to the new page
            document.add(new Paragraph("Good Riddance Pest Control - www.grpestcontrol.ie")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            // Close the document to save changes
            document.close();

            // Replace the original file with the updated one
            if (file.delete() && updatedFile.renameTo(file)) {
                Toast.makeText(this, "Follow-up details added successfully on a new page!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to update the original file.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error editing PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Placeholder method for rewriting the selected PDF by replacing its content.
     *
     * @param file The PDF file to be rewritten.
     */
    private void rewritePDF(File file) {
        // Display a toast message indicating this feature is not yet implemented
        Toast.makeText(this, "Rewrite functionality is not implemented yet.", Toast.LENGTH_SHORT).show();

        // Alternatively, add a TODO comment if planning to implement later
        // TODO: Implement the functionality to rewrite the PDF with new content
    }
}
