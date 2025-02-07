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

public class RodentActivityRoutine extends AppCompatActivity {

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

            case "Activity":
                return "A routine pest control inspection was conducted, with a detailed examination of all monitoring points, bait stations, and surrounding environments."
                        + "During the assessment, evidence of rodent activity was identified in specific areas of the site. Notable indicators included droppings, gnaw marks, and visible disturbances within bait stations, confirming recent rodent presence."
                        + "In response, all affected areas have been replenished with professionally approved toxic bait, strategically placed to maximize control efficacy while ensuring minimal risk to non-target species. Existing bait stations and traps were also inspected for functionality and repositioned where necessary to optimize coverage."
                        + "A follow-up monitoring plan has been put in place to track bait uptake and evaluate the effectiveness of the control measures implemented. The site will remain under close surveillance, and additional corrective actions will be recommended if activity levels persist or increase.";


            default:
                return  "A routine pest control inspection was conducted, with a detailed examination of all monitoring points, bait stations, and surrounding environments."
                        + "During the assessment, evidence of rodent activity was identified in specific areas of the site. Notable indicators included droppings, gnaw marks, and visible disturbances within bait stations, confirming recent rodent presence."
                        + "In response, all affected areas have been replenished with professionally approved toxic bait, strategically placed to maximize control efficacy while ensuring minimal risk to non-target species. Existing bait stations and traps were also inspected for functionality and repositioned where necessary to optimize coverage."
                        + "A follow-up monitoring plan has been put in place to track bait uptake and evaluate the effectiveness of the control measures implemented. The site will remain under close surveillance, and additional corrective actions will be recommended if activity levels persist or increase.";

        }
    }



    /**
     * Generates professional recommendations based on the visit type.
     * This provides site-specific guidance for continued pest management and prevention.
     */
    private String getRecommendation() {
        switch (routineType) {

            case "Activity":
                return "Further recommendations will be provided during the follow-up inspection."
                        + "Rodent activity has been detected, and necessary control measures have been implemented, including replenishment of bait stations and repositioning of traps where required. \n"
                        + "A follow-up visit is advised to assess bait uptake and monitor rodent presence. Additional recommendations may be provided based on the level of activity noted in subsequent inspections.";


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

            case "Activity":
                return "A follow-up visit is scheduled within the next 5 - 7 working days.";





            default:
                return "A follow-up visit is scheduled within the next 5 - 7 working days.";
        }
    }


    /**
     * Auto-fills preparation details based on the visit type.
     * This section ensures appropriate safety measures and site readiness for pest control interventions.
     */
    private String getPreparation() {
        switch (routineType) {

            case "Activity":
                return "As part of the routine pest control service, Vertox block bait has been strategically placed throughout the site in designated bait stations."
                        + "Clients have been advised to ensure that food storage areas remain sealed, waste is properly disposed of, and entry points are secured to reduce the risk of further rodent activity."
                        + "The placement of baits will be monitored in subsequent visits to assess consumption levels and effectiveness.";


            default:
                return "As part of the routine pest control service, Vertox block bait has been strategically placed throughout the site in designated bait stations."
                        + "Clients have been advised to ensure that food storage areas remain sealed, waste is properly disposed of, and entry points are secured to reduce the risk of further rodent activity."
                        + "The placement of baits will be monitored in subsequent visits to assess consumption levels and effectiveness.";


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
        Context context = RodentActivityRoutine.this;

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
