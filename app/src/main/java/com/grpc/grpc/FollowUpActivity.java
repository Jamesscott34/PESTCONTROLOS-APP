package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.AreaBreakType;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FollowUpActivity.java
 *
 * This activity allows users to add follow-up details to an existing pest control report.
 * Users can input follow-up information, select images, and append them to a PDF report.
 * The updated report includes structured details, images, and watermarks for professional formatting.
 *
 * Features:
 * - Input follow-up details and technician observations
 * - Select multiple images to attach to the report
 * - Append new data and images to an existing PDF report
 * - Apply a watermark and structured formatting to the updated report
 * - Save the updated report and replace the original file
 *
 * Author: GRPC
 */

public class FollowUpActivity extends AppCompatActivity {

    private EditText dateInput, customerfollowupInput,siteInspectionInput, recommendationsInput,
            followUpInput,prepInput, techInput;

    private Button saveButton, backButton, selectImageButton;

    private List<Uri> selectedImageUris = new ArrayList<>();

    /**
     * Initializes the activity, sets up UI elements, retrieves the selected PDF file path,
     * and handles user interactions for follow-up report creation.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_follow_up);

        // Initialize input fields
        dateInput = findViewById(R.id.dateInput);
        customerfollowupInput = findViewById(R.id.customerfollowupInput);
        siteInspectionInput = findViewById(R.id.siteInspectionInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        followUpInput = findViewById(R.id.followUpInput);
        prepInput = findViewById(R.id.prepInput);
        techInput = findViewById(R.id.techInput);

        // Initialize buttons
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        selectImageButton = findViewById(R.id.selectImageButton);

        // Set current date
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        dateInput.setText(sdf.format(new Date()));

        // List to store selected image URIs
        List<Uri> selectedImageUris = new ArrayList<>();

        // Retrieve the selected PDF file path
        Intent intent = getIntent();
        String pdfFilePath = intent.getStringExtra("selected_pdf");

        // Set up button actions
        selectImageButton.setOnClickListener(view -> openImageSelector(selectedImageUris));
        saveButton.setOnClickListener(view -> {
            if (pdfFilePath != null) {
                saveFollowUpToPDF(pdfFilePath, selectedImageUris);
            } else {
                Toast.makeText(this, "No PDF file selected!", Toast.LENGTH_SHORT).show();
            }
        });
        backButton.setOnClickListener(view -> finish());
    }

    /**
     * Opens the system image selector to allow users to pick multiple images for the report.
     *
     * @param selectedImageUris List of selected image URIs to store user-selected images.
     */
    private void openImageSelector(List<Uri> selectedImageUris) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, 1);
    }

    /**
     * Handles the result from the image selector activity.
     * Stores selected images and displays a toast message with the count.
     *
     * @param requestCode The request code to identify the activity result.
     * @param resultCode  The result code indicating success or failure.
     * @param data        The intent data containing selected image URIs.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) { // Multiple images selected
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) { // Single image selected
                selectedImageUris.add(data.getData());
            }
            Toast.makeText(this, selectedImageUris.size() + " images selected!", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Appends follow-up details and selected images to the existing PDF report.
     * Updates the PDF with structured data, images, and watermarks.
     *
     * @param pdfFilePath        The file path of the selected PDF report.
     * @param selectedImageUris  List of image URIs to be attached to the PDF.
     */
    private void saveFollowUpToPDF(String pdfFilePath, List<Uri> selectedImageUris) {
        try {
            // Prepare follow-up details from the input fields
            String followUpDetails =
                    "Date: " + dateInput.getText().toString() +
                            "\nFollow-Up Visit: " + customerfollowupInput.getText().toString() +
                            "\nSite Inspection: " + siteInspectionInput.getText().toString() +
                            "\nRecommendations: " + recommendationsInput.getText().toString() +
                            "\nPreparation: " + prepInput.getText().toString() +
                            "\nFollow-Up Instructions: " + followUpInput.getText().toString() +
                            "\nTechnician: " + techInput.getText().toString();

            // Load the existing PDF
            File file = new File(pdfFilePath);
            File updatedFile = new File(file.getParent(), "Updated_" + file.getName());

            PdfDocument pdfDoc = new PdfDocument(new PdfReader(file), new PdfWriter(updatedFile));
            Document document = new Document(pdfDoc);

            // Add a new page to the PDF
            PdfPage newPage = pdfDoc.addNewPage();
            document.add(new AreaBreak(AreaBreakType.LAST_PAGE)); // Break to the new page

            // Add watermark and logo
            int watermarkResourceId = getResources().getIdentifier("bk", "drawable", getPackageName());
            ImageData watermarkData = ImageDataFactory.create(getResources().openRawResource(watermarkResourceId).readAllBytes());
            Image watermark = new Image(watermarkData)
                    .scaleToFit(500, 500)
                    .setFixedPosition(newPage.getPageSize().getWidth() / 4, newPage.getPageSize().getHeight() / 4)
                    .setOpacity(0.1f);
            document.add(watermark);

            int logoResourceId = getResources().getIdentifier("logo", "drawable", getPackageName());
            ImageData logoData = ImageDataFactory.create(getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData)
                    .scaleToFit(200, 200)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(logo);

            // Add title to the report
            Paragraph title = new Paragraph("Good Riddance Pest Control Follow-Up Report")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold()
                    .setFontColor(ColorConstants.BLUE);
            document.add(title);
            document.add(new Paragraph("\n"));

            // Add follow-up details dynamically
            document.add(new Paragraph("Follow-Up")
                    .setFontSize(18)
                    .setBold()
                    .setUnderline()
                    .setTextAlignment(TextAlignment.LEFT));

            // Add follow-up details line by line
            for (String line : followUpDetails.split("\\n")) {
                String[] splitDetail = line.split(":", 2);
                if (splitDetail.length == 2) {
                    document.add(new Paragraph(splitDetail[0].trim())
                            .setFontSize(16)
                            .setBold()
                            .setUnderline());
                    document.add(new Paragraph(splitDetail[1].trim())
                            .setFontSize(14));
                } else {
                    document.add(new Paragraph(line.trim())
                            .setFontSize(14));
                }
            }

            if (selectedImageUris != null && !selectedImageUris.isEmpty()) {
                document.add(new Paragraph("\nAttached Images:").setFontSize(16).setBold());

                for (int i = 0; i < selectedImageUris.size(); i++) {
                    Uri uri = selectedImageUris.get(i);
                    try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                        if (inputStream != null) {
                            // Create ImageData from the URI
                            ImageData imageData = ImageDataFactory.create(inputStream.readAllBytes());
                            Image image = new Image(imageData)
                                    .scaleToFit(400, 400) // Scale image to fit within page bounds
                                    .setHorizontalAlignment(HorizontalAlignment.CENTER);

                            // Add caption for the image
                            document.add(new Paragraph("Tech Field Image " + (i + 1))
                                    .setFontSize(16)
                                    .setBold()
                                    .setTextAlignment(TextAlignment.CENTER));

                            // Add a new page if there isn't enough space for the image
                            if (document.getRenderer().getCurrentArea().getBBox().getHeight() < 450) {
                                addWatermarkToPage(newPage);
                                document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                            }

                            // Add the image to the document
                            document.add(image);
                        }
                    } catch (IOException e) {
                        Toast.makeText(this, "Error loading image: " + uri.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            // Add footer to the last page
            document.add(new Paragraph("Good Riddance Pest Control - www.grpestcontrol.ie")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            // Close the Document and PdfDocument
            document.close();
            pdfDoc.close();

            // Replace the original file with the updated one
            if (file.delete() && updatedFile.renameTo(file)) {
                Toast.makeText(this, "Follow-up details and images saved successfully to PDF!", Toast.LENGTH_SHORT).show();
                finish(); // Return to the previous activity
            } else {
                Toast.makeText(this, "Failed to update the original PDF file.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error editing PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Adds a watermark image to a specific page in the PDF.
     *
     * @param page The PdfPage where the watermark will be added.
     */
    private void addWatermarkToPage(PdfPage page) {
        try {
            Rectangle pageSize = page.getPageSize();
            PdfCanvas canvas = new PdfCanvas(page);

            // Add watermark
            int watermarkResourceId = getResources().getIdentifier("bk", "drawable", getPackageName());
            ImageData watermarkData = ImageDataFactory.create(getResources().openRawResource(watermarkResourceId).readAllBytes());
            Image watermark = new Image(watermarkData)
                    .scaleToFit(500, 500)
                    .setFixedPosition((pageSize.getWidth() - 500) / 2, (pageSize.getHeight() - 500) / 2)
                    .setOpacity(0.1f);

            new Canvas(canvas, page.getDocument(), pageSize).add(watermark);
        } catch (Exception e) {
            Toast.makeText(this, "Error adding watermark: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }







}

