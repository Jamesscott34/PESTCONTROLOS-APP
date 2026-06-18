package com.grpc.grpc.jobs.rodent;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;

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
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CallOutActivity.java
 *
 * This activity automatically generates and saves a PDF report for pest control callouts and routine visits.
 * It retrieves details such as company name, address, technician information, site inspection results,
 * recommendations, follow-ups, and preparations, then structures them into a professional PDF report.
 *
 * Features:
 * - Retrieves user and company details from intent
 * - Auto-fills visit-related fields based on the visit type
 * - Generates a structured PDF with site inspection details, recommendations, and technician details
 * - Saves the PDF to external storage
 * - Applies a watermark and footer to the report
 *
 * Author: GRPC
 */

public class CallOutActivity extends AppCompatActivity {
    /**
     * Initializes the activity, retrieves intent data, fills in report details,
     * and triggers automatic PDF generation.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */
    private String documentId, userName, companyName, address, routineType;
    private String dateTime, siteInspection, recommendation, followUp, preparation, techName, techContact;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve intent data
        Intent intent = getIntent();
        documentId = intent.getStringExtra("DOCUMENT_ID");
        userName = intent.getStringExtra("USER_NAME");
        companyName = intent.getStringExtra("COMPANY_NAME");
        address = intent.getStringExtra("ADDRESS");
        routineType = intent.getStringExtra("ROUTINE_TYPE");

        // Ensure required fields are not null
        if (companyName == null) companyName = "N/A";
        if (address == null) address = "N/A";

        // ✅ Ensure Visit Type is "Routine" unless it's "Callout"
        if (routineType == null) {
            routineType = "Initial Setup";
        }


        // Auto-fill all fields
        dateTime = getCurrentDateTime();
        siteInspection = getSiteInspection();
        recommendation = getRecommendation();
        followUp = getFollowUp();
        preparation = getPreparation();
        setTechnicianDetails();

        // Automatically generate and save the PDF report
        generateAndSaveReport();
    }

    /**
     * Gets the current date and time in the format "dd-MM-yyyy HH:mm"
     */
    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * Generates a professional site inspection report based on the visit type.
     * This ensures a structured and thorough record of findings, actions taken, and future recommendations.
     */
    private String getSiteInspection() {
        switch (routineType) {
            case "CallOut":
                return "As part of our pest management agreement, an Initial Setup was conducted at the above premises. A comprehensive site inspection was carried out, and monitoring devices have been strategically placed throughout the property to ensure effective pest management."
                        + "During this visit:\n" +
                        "A full assessment of potential pest activity and risk areas was completed.\n" +
                        "Monitoring stations have been strategically placed at key locations for optimal coverage.";



            default:
                return "As part of our pest management agreement, an Initial Setup was conducted at the above premises. A comprehensive site inspection was carried out, and monitoring devices have been strategically placed throughout the property to ensure effective pest management."
                        + "During this visit:\n" +
                        "A full assessment of potential pest activity and risk areas was completed.\n" +
                        "Monitoring stations have been strategically placed at key locations for optimal coverage.";

        }
    }



    /**
     * Generates professional recommendations based on the visit type.
     * This provides site-specific guidance for continued pest management and prevention.
     */
    private String getRecommendation() {
        switch (routineType) {
            case "CallOut":
                return "No specific recommendations were noted at this time.";


            default:
                return "No specific recommendations were noted at this time.";
        }

    }



    /**
     * Auto-fills follow-up details based on the visit type.
     */
    /**
     * Determines follow-up actions based on the visit type.
     * This ensures continuous monitoring and necessary interventions where required.
     */
    private String getFollowUp() {
        switch (routineType) {
            case "CallOut":
                return "A follow-up inspection will be scheduled to assess the effectiveness of the setup and to recommend any necessary adjustments, such as proofing works or additional control measures.";





            default:
                return "A follow-up inspection will be scheduled to assess the effectiveness of the setup and to recommend any necessary adjustments, such as proofing works or additional control measures.";

        }
    }


    /**
     * Auto-fills preparation details based on the visit type.
     * This section ensures appropriate safety measures and site readiness for pest control interventions.
     */
    private String getPreparation() {
        switch (routineType) {

            case "CallOut":
                return "An adequate amount of baits has been utilized to maximize effectiveness, ensuring optimal pest control measures. ";


            default:
                return "An adequate amount of baits has been utilized to maximize effectiveness, ensuring optimal pest control measures.";
        }
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
     * Generates and saves the pest control report as a PDF file.
     * Applies a watermark, includes all report sections, and saves it to external storage.
     */

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void generateAndSaveReport() {
        Context context = CallOutActivity.this;

        // Ensure external storage is available
        File pdfFolder;
        String folderName = TenantBranding.reportsFolderName(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pdfFolder = new File(context.getExternalFilesDir(null), folderName);
        } else {
            pdfFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), folderName);
        }

        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        // Generate a timestamped file name
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String sanitizedReportName = companyName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + currentDate + ".pdf";
        File pdfFile = new File(pdfFolder, sanitizedReportName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile))) {
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            // ✅ Apply watermark and footer from PDFReportGenerator
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Adding Logo
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo);

            // Adding Report Title
            Paragraph title = new Paragraph(TenantBranding.reportTitle(context))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold()
                    .setFontColor(ColorConstants.BLACK);
            document.add(title);
            document.add(new Paragraph("\n"));  // Spacing after title

            // Adding Report Sections
            addReportSection(document, "Company Name", companyName);
            addReportSection(document, "Address", address);
            addReportSection(document, "Date & Time", dateTime);
            addReportSection(document, "Visit Type", routineType);
            addReportSection(document, "Site Inspection", siteInspection);
            addReportSection(document, "Recommendations", recommendation);
            addReportSection(document, "Follow-up Required", followUp);
            addReportSection(document, "Preparation", preparation);
            addReportSection(document, "Technician", techName + " - " + techContact);

            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), null);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    /**
     * Adds a formatted section to the PDF report.
     * Each section includes a heading, a horizontal line, and the relevant content.
     *
     * @param document The PDF document where the section will be added.
     * @param heading  The title of the section.
     * @param content  The content of the section.
     */

    private void addReportSection(Document document, String heading, String content) {
        // Create Heading Paragraph with full-width background
        Paragraph headingParagraph = new Paragraph(heading)
                .setFontSize(14)
                .setBold()
                .setUnderline()
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setMarginTop(10)
                .setMarginBottom(5)
                .setPadding(5)
                .setWidth(UnitValue.createPercentValue(100)); // Full width

        // Add a horizontal line (divider) under the heading
        LineSeparator line = new LineSeparator(new SolidLine())
                .setWidth(UnitValue.createPercentValue(100)) // Full width
                .setMarginBottom(5);

        // Create Content Paragraph
        Paragraph contentParagraph = new Paragraph(content)
                .setFontSize(12)
                .setFontColor(ColorConstants.BLACK)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(10);

        // Add elements to the document
        document.add(headingParagraph);
        document.add(line); // Adds line separator
        document.add(contentParagraph);
    }

}

