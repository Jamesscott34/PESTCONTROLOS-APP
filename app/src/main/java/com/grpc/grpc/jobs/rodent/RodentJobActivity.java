package com.grpc.grpc.jobs.rodent;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;

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
 * RodentJobActivity.java
 *
 * This activity automatically generates and saves a structured PDF report for rodent control jobs.
 * It follows a structured 3-point approach: Monitoring & Baiting, Proofing Assessment, and Ongoing Monitoring.
 * The generated report is saved locally with a standardized filename.
 *
 * Features:
 * - Extracts intent data for report generation
 * - Pre-fills site inspection, recommendations, and follow-up details
 * - Generates and saves a structured PDF report
 * - Includes technician details and contact information
 * - Applies a watermark and footer for branding
 * - Supports the Rodent Riddance Program for structured pest control interventions
 *
 * Author: GRPC
 */


public class RodentJobActivity extends AppCompatActivity {

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
        routineType = intent.getStringExtra("ROUTINE_TYPE");

        // Ensure values are not null
        companyName = (companyName != null) ? companyName : "N/A";
        address = (address != null) ? address : "N/A";

        // Default Routine Type
        routineType = "Rodent Riddance";
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
        return "As part of our Rodent Riddance Program, an initial setup has been carried out at the premises "
                + "to address and control rodent activity effectively. This program follows a structured 3-point approach:\n\n"
                + "1️⃣ **Monitoring & Baiting** – A thorough site inspection was conducted, and bait stations have been strategically placed.\n"
                + "2️⃣ **Proofing Assessment** – The site was assessed for potential entry points, and recommendations for proofing will be given.\n"
                + "3️⃣ **Ongoing Monitoring** – An adequate amount of baits has been utilized, with further evaluation scheduled for follow-up.";
    }

    /**
     * Generates recommendations based on the visit type.
     */
    private String generateRecommendation() {
        return "No specific recommendations were noted at this time. Recommendations may follow on the follow-up visit.";
    }

    /**
     * Determines follow-up actions based on the visit type.
     */
    private String generateFollowUp() {
        return "A follow-up visit is scheduled within the next 5 - 7 working days.";
    }

    /**
     * Auto-fills preparation details.
     */
    private String generatePreparation() {
        return "As part of the Rodent Riddance Program, Vertox block bait has been strategically placed in designated bait stations.\n"
                + "Clients have been advised to seal food storage areas and ensure proper waste disposal.\n"
                + "The placement of baits will be monitored in subsequent visits to assess effectiveness.";
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
        String folderName = TenantBranding.reportsFolderName(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new File(context.getExternalFilesDir(null), folderName);
        } else {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), folderName);
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
