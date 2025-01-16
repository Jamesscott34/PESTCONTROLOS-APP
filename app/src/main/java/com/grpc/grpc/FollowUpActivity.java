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
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.AreaBreakType;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FollowUpActivity extends AppCompatActivity {

    private EditText dateInput, customerfollowupInput,siteInspectionInput, recommendationsInput,
            followUpInput,prepInput, techInput;

    private Button saveButton, backButton, selectImageButton;

    private List<Uri> selectedImageUris = new ArrayList<>();


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
     * Opens the system image selector for choosing images.
     */
    private void openImageSelector(List<Uri> selectedImageUris) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, 1);
    }

    /**
     * Handles the result from the image selector activity.
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
            Document document = new Document(pdfDoc); // Use Document for dynamic layout

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

            // Adding a title to the report
            Paragraph title = new Paragraph("Good Riddance Pest Control Report")
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
            String[] lines = followUpDetails.split("\\n");
            for (String line : lines) {
                String[] splitDetail = line.split(":", 2);
                if (splitDetail.length == 2) {
                    // Add header (key)
                    document.add(new Paragraph(splitDetail[0].trim())
                            .setFontSize(16)
                            .setBold()
                            .setUnderline());

                    // Add user input (value)
                    document.add(new Paragraph(splitDetail[1].trim())
                            .setFontSize(14));
                } else {
                    // Add as plain text if no key-value format is detected
                    document.add(new Paragraph(line.trim())
                            .setFontSize(14));
                }
            }

            // Add selected images dynamically and manage page space
            if (selectedImageUris != null && !selectedImageUris.isEmpty()) {
                document.add(new Paragraph("\nAttached Images:").setFontSize(16).setBold());

                for (Uri imageUri : selectedImageUris) {
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    if (inputStream != null) {
                        ImageData imageData = ImageDataFactory.create(inputStream.readAllBytes());
                        Image image = new Image(imageData)
                                .scaleToFit(newPage.getPageSize().getWidth() - 72, 400)
                                .setHorizontalAlignment(HorizontalAlignment.CENTER);

                        // Check if there's enough space for the image, add a new page if necessary
                        if (document.getRenderer().getCurrentArea().getBBox().getHeight() < 450) {
                            document.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                        }

                        document.add(image);
                        inputStream.close();
                    }
                }
            }

            // Add footer to the last page
            document.add(new Paragraph("Good Riddance Pest Control - www.grpestcontrol.ie")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            // Close the Document and PdfDocument to save changes
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





}

