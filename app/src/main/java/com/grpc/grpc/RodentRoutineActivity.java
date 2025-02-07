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
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
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

public class RodentRoutineActivity extends AppCompatActivity {

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
            routineType = "Routine";
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
            case "No Activity":
                return "As part of the scheduled pest management program, a comprehensive routine inspection of the site was conducted."
                        + "During the assessment, all designated monitoring stations were thoroughly examined, and no evidence of rodent activity was detected at this time. There were no visible signs of pest ingress, contamination, or structural vulnerabilities that could facilitate future infestations."
                        + "All bait stations, traps, and monitoring devices were found to be intact, undisturbed, and fully operational. Environmental factors, such as hygiene levels and waste management practices, were reviewed and determined to be in compliance with pest control best practices."
                        + "Given the absence of rodent activity, no further intervention is required at this stage. However, the site will continue to be monitored as per the routine pest control schedule to ensure ongoing protection and early detection of any potential pest concerns.";


            default:
                return "A scheduled routine pest control service was carried out as part of the ongoing maintenance program for this site."
                        + "All designated monitoring stations, bait stations, and traps were inspected to ensure they remain operational and strategically positioned for optimal effectiveness. No evidence of new or increased rodent activity was observed at this time."
                        + "The surrounding environment, including waste management procedures, structural integrity, and general hygiene conditions, was also reviewed. No immediate risk factors contributing to pest attraction or infestation were identified."
                        + "Pest control measures currently in place continue to function as intended, providing a proactive defense against potential rodent intrusion. The site will remain under routine monitoring, with any necessary adjustments to the pest control strategy made in response to future developments.";
        }
    }



    /**
     * Generates professional recommendations based on the visit type.
     * This provides site-specific guidance for continued pest management and prevention.
     */
    private String getRecommendation() {
        switch (routineType) {
            case "No Activity":
                return "No specific recommendations were noted at this time."
                        + "The site remains well-maintained, with no signs of rodent activity observed. It is advised to continue with regular cleaning and general maintenance practices to ensure the environment remains inhospitable to pests.\n"
                        + "Routine monitoring will continue as part of the scheduled pest management program, with adjustments made if necessary based on future findings.";



            default:
                return "Standard recommendations apply based on routine pest control assessments.\n\n"
                        + "All monitoring stations were checked and remain in good condition. No immediate issues were identified, and existing preventive measures continue to be effective.\n"
                        + "Regular monitoring and good hygiene practices are advised to maintain a pest-free environment. Any adjustments to the pest control strategy will be determined during subsequent visits.";
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
            case "No Activity":
                return "No follow-up is required at this time."
                        + "The site remains free of pest activity, and all monitoring stations were found to be in good condition. "
                        + "Routine inspections will continue as per the agreed pest control schedule to ensure ongoing protection.";






            default:
                return "Routine follow-up will be conducted as per the contract."
                        + "All monitoring stations remain in place, and no immediate concerns were noted. "
                        + "Regular inspections will continue to ensure the site remains pest-free, with adjustments made if necessary based on future findings.";
        }
    }


    /**
     * Auto-fills preparation details based on the visit type.
     * This section ensures appropriate safety measures and site readiness for pest control interventions.
     */
    private String getPreparation() {
        switch (routineType) {
            case "No Activity":
                return "No preparatory actions were required during this visit."
                        + "The site was found to be well-maintained, with no evidence of pest activity. All monitoring stations remain in place and undisturbed. "
                        + "Standard hygiene and housekeeping practices should continue to prevent any potential infestations.";



            default:
                return "Routine pest control measures have been implemented as per standard preparation guidelines."
                        + "Bait stations have been inspected, and environmental conditions assessed. No immediate concerns were noted, and the site remains under continuous monitoring for any necessary adjustments.";
        }
    }



    /**
     * Sets the technician's name and contact based on the username.
     */
    private void setTechnicianDetails() {
        if ("James".equalsIgnoreCase(userName)) {
            techName = "James Scott";
            techContact = "0879000271";
        } else if ("Ian".equalsIgnoreCase(userName)) {
            techName = "Ian Winston";
            techContact = "0879134971";
        } else {
            techName = "Unknown Technician";
            techContact = "N/A";
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void generateAndSaveReport() {
        Context context = RodentRoutineActivity.this;

        // Ensure external storage is available
        File pdfFolder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pdfFolder = new File(context.getExternalFilesDir(null), "GRPEST REPORTS");
        } else {
            pdfFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "GRPEST REPORTS");
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
            Paragraph title = new Paragraph("Good Riddance Pest Control Report")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(ColorConstants.BLUE);
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


        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
        }

        finish();
    }


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
