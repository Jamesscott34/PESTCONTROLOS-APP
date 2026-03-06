package com.grpc.grpc;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.property.TextAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * RodentRoutineActivity.java
 *
 * This activity automatically generates and saves a structured PDF report for routine rodent control inspections.
 * It retrieves company and technician details, pre-fills the report fields, and applies a professional format.
 * The generated report is saved locally with a standardized filename.
 *
 * Features:
 * - Extracts intent data for report generation
 * - Pre-fills site inspection, recommendations, and follow-up details
 * - Generates and saves a structured PDF report
 * - Includes technician details and contact information
 * - Applies a watermark and footer for branding
 * - Supports routine rodent inspection reports for scheduled pest control maintenance
 *
 * Author: GRPC
 */


public class RodentRoutineActivity extends AppCompatActivity {

    private String documentId, userName, companyName, address, routineType;
    private String dateTime, siteInspection, recommendation, followUp, preparation, techName, techContact;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve and Validate Intent Data
        extractIntentData();

        // Auto-fill all fields
        populateReportFields();

        // Generate & Save Report
        generateAndSaveReport();
    }

    /**
     * Extracts intent data and ensures default values are set.
     */
    private void extractIntentData() {
        Intent intent = getIntent();
        documentId = intent.getStringExtra("DOCUMENT_ID");
        userName = intent.getStringExtra("USER_NAME");
        companyName = intent.getStringExtra("COMPANY_NAME");
        address = intent.getStringExtra("ADDRESS");

        // Ensure values are not null
        companyName = (companyName != null) ? companyName : "N/A";
        address = (address != null) ? address : "N/A";

        // Set Routine Type
        routineType = "Routine";
    }

    /**
     * Populates the report fields with necessary values.
     */
    private void populateReportFields() {
        dateTime = getCurrentDateTime();
        siteInspection = generateSiteInspection();
        recommendation = generateRecommendation();
        followUp = generateFollowUp();
        preparation = generatePreparation();
        setTechnicianDetails();
    }

    /**
     * Returns the current date & time in "dd-MM-yyyy HH:mm" format.
     */
    private String getCurrentDateTime() {
        return new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(new Date());
    }

    /**
     * Generates the site inspection report.
     */
    private String generateSiteInspection() {
        return "A routine inspection of the above site was carried out today. "
                + "All monitors were inspected, and no evidence of rodent activity was detected. During this visit";
    }

    /**
     * Generates recommendations based on the visit type.
     */
    private String generateRecommendation() {
        return "No specific recommendations were noted at this time of visit.";
    }

    /**
     * Determines follow-up actions based on the visit type.
     */
    private String generateFollowUp() {
        return "No follow-up is required at this time.";
    }

    /**
     * Auto-fills preparation details.
     */
    private String generatePreparation() {
        return "No preparatory actions were required during this visit. "
                + "The site was found to be well-maintained, with no evidence of pest activity.";
    }

    /**
     * Sets the technician's name and contact based on the username.
     */
    private void setTechnicianDetails() {
        String userId = StaffDirectory.getUserId(userName);
        techName = StaffDirectory.getTechnicianDisplayLabel(userId);
        if (techName == null || techName.isEmpty()) techName = "Unknown Technician";
        String mobile = StaffDirectory.getMobileForUserId(userId);
        techContact = mobile != null && !mobile.isEmpty() ? mobile : "N/A";
    }

    /**
     * Generates and saves the PDF report.
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void generateAndSaveReport() {
        Context context = this;
        File pdfFolder = getReportDirectory(context);

        if (!pdfFolder.exists()) pdfFolder.mkdirs();

        String fileName = sanitizeFileName(companyName) + "_" + getCurrentDate() + ".pdf";
        File pdfFile = new File(pdfFolder, fileName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile))) {
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            // Add watermark & footer
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Generate PDF content
            addPdfContent(document, context);

            document.close();
        } catch (IOException e) {
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        finish();
    }

    /**
     * Returns the appropriate report directory based on Android version.
     */
    private File getReportDirectory(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new File(context.getExternalFilesDir(null), "GRPEST REPORTS");
        } else {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "GRPEST REPORTS");
        }
    }

    /**
     * Returns the current date in "dd-MM-yyyy" format.
     */
    private String getCurrentDate() {
        return new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
    }

    /**
     * Sanitizes the file name by replacing special characters.
     */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Adds content to the PDF document.
     */
    private void addPdfContent(Document document, Context context) throws IOException {
        // Add Logo
        int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
        ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
        Image logo = new Image(logoData).scaleToFit(150, 150).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
        document.add(logo);

        // Add Report Title
        document.add(new Paragraph(TenantBranding.reportTitle(context))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(16)
                .setBold()
                .setFontColor(ColorConstants.BLUE));

        document.add(new Paragraph("\n"));  // Spacing

        // Add Report Sections
        addReportSection(document, "Company Name", companyName);
        addReportSection(document, "Address", address);
        addReportSection(document, "Date & Time", dateTime);
        addReportSection(document, "Visit Type", routineType);
        addReportSection(document, "Site Inspection", siteInspection);
        addReportSection(document, "Recommendations", recommendation);
        addReportSection(document, "Follow-up Required", followUp);
        addReportSection(document, "Preparation", preparation);
        addReportSection(document, "Technician", techName + " - " + techContact);
    }

    /**
     * Adds a formatted section to the PDF document.
     */
    private void addReportSection(Document document, String heading, String content) {
        document.add(new Paragraph(heading)
                .setFontSize(14)
                .setBold()
                .setUnderline()
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setTextAlignment(TextAlignment.CENTER));

        document.add(new LineSeparator(new SolidLine()).setMarginBottom(5));
        document.add(new Paragraph(content).setFontSize(12).setTextAlignment(TextAlignment.LEFT));
    }
}
