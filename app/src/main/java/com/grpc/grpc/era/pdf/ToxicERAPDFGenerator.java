package com.grpc.grpc.era.pdf;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;



/**
 * ToxicERAPDFGenerator.java
 *
 * This class generates a professionally formatted Toxic Environmental Risk Assessment (ERA) PDF report.
 * The document includes company details, environmental considerations, risk mitigation strategies,
 * and technician signatures. The generated PDF is saved locally with a structured layout.
 *
 * Features:
 * - Generates a structured PDF with company branding and customer details
 * - Includes environmental risk factors and mitigation measures
 * - Supports technician signature capture for validation
 * - Saves the PDF file to a designated folder
 * - Applies a watermark and structured formatting to the document
 * - Ensures compliance with responsible pest control practices
 *
 * Author: GRPC
 */


public class ToxicERAPDFGenerator {

    @SuppressLint("DefaultLocale")
    public static String generateToxicEnvironmentalRiskAssessment(Context context, String companyName, String address, String email, Bitmap signature) {
        return generateToxicEnvironmentalRiskAssessment(context, companyName, address, email, signature, null);
    }

    public static String generateToxicEnvironmentalRiskAssessment(
            Context context,
            String companyName,
            String address,
            String email,
            Bitmap signature,
            File outputDirectory
    ) {

        File assessmentsFolder = outputDirectory != null
                ? outputDirectory
                : new File(context.getExternalFilesDir(null), "EnvironmentalRiskAssessments");
        if (!assessmentsFolder.exists() && !assessmentsFolder.mkdirs()) {
            Toast.makeText(context, "Error creating folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        String pdfFileName = generatePdfFileName(companyName);
        File pdfFile = new File(assessmentsFolder, pdfFileName);
        String pdfPath = pdfFile.getAbsolutePath();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // Add Watermark or Footer if needed
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Adding a logo image at the top of the report
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo); // Ensure it's centered


// Add Title
            // Adding a title to the report
            Paragraph title = new Paragraph(TenantBranding.eraTitle(context))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(ColorConstants.BLACK);
            document.add(title);
            document.add(new Paragraph("\n"));

// Add Company and Customer Details in a Table with Left and Right Alignment
            String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

// Create a table with two columns (left for company, right for customer)
            Table detailsTable = new Table(UnitValue.createPercentArray(2)).useAllAvailableWidth();

// Left Column - Company Details
            String userName = SessionManager.getName(context);
            String userTitle = SessionManager.getTitle(context);
            String userEmail = SessionManager.getEmail(context);
            Cell companyDetails = new Cell()
                    .add(new Paragraph("Company Name: " + TenantBranding.companyName(context)).setBold())
                    .add(new Paragraph("Name: " + (userName != null ? userName : "")))
                    .add(new Paragraph("Title: " + (userTitle != null ? userTitle : "")))
                    .add(new Paragraph("Email: " + (userEmail != null ? userEmail : "")))
                    .add(new Paragraph("Date: " + date))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.LEFT);

            detailsTable.addCell(companyDetails);

// Right Column - Customer Details
            Cell customerDetails = new Cell()
                    .add(new Paragraph("Customer Name: \n" + companyName).setBold())
                    .add(new Paragraph("Customer Address:\n " + address))
                    .add(new Paragraph("Customer Email:\n " + email))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.RIGHT);

            detailsTable.addCell(customerDetails);

// Add the Table to the Document
            document.add(detailsTable);

// Add Spacing
            document.add(new Paragraph("\n"));


            // Sections
            addSection(document, "1. Applicable Areas and Baits Used",
                    "Applicable Areas: Dublin, Kildare, Meath, Wicklow\nRodenticides Used: Cholecalciferol, Difenacoum");

            addSection(document, "1. Purpose",
                    "This environmental risk assessment provides an overview of the potential risks associated with rodenticide use and outlines best practices " +
                            "to minimize harm to non-target species and the surrounding environment. While an environmental risk assessment is not a CRRU requirement " +
                            "set by the Department of Agriculture, " + TenantBranding.companyName(context) + " is committed to responsible pest management.");

            addSection(document, "2. Environmental Considerations",
                    "Rodenticides are an essential tool for controlling rat populations, particularly in external environments. However, they must be used carefully " +
                            "to prevent unintended impacts on wildlife, water sources, and biodiversity.");

            addSection(document, "2.1 Non-Target Species at Risk",
                    "The following species are commonly found in Dublin, Kildare, Meath, and Wicklow and may be at risk from rodenticide use:\n" +
                            "- Birds of prey: Barn owls, kestrels, and buzzards, which may feed on poisoned rodents.\n" +
                            "- Scavenger birds: Crows, magpies, and ravens, which may ingest bait or poisoned rodents.\n" +
                            "- Mammals: Foxes, badgers, hedgehogs, and domestic pets, which may consume bait or secondary-poisoned rodents.\n" +
                            "- Small rodents: Field mice and voles, which may inadvertently consume bait.");

            addSection(document, "2.2 Water Contamination Risks",
                    "Rodenticides must not enter watercourses, as contamination could impact aquatic ecosystems. Areas of concern include rivers, canals, lakes, and drainage systems.");

            addSection(document, "2.3 Secondary Poisoning Risks",
                    "Predators and scavengers that consume poisoned rodents can be affected, particularly by second-generation anticoagulants like difenacoum. " +
                            "Cholecalciferol poses a lower secondary poisoning risk but must still be managed carefully.");

            addSection(document, "3. Risk Mitigation Measures",
                    "Effective risk mitigation is essential to ensure the safe and responsible use of rodenticides "
                            +"while minimizing risks to non-target species, the environment, and human health. " +
                            "This section outlines key measures to enhance the safety and effectiveness of pest control practices");

            addSection(document, "3.1 Secure and Targeted Baiting",
                    "- Use tamper-resistant bait stations in all external locations to prevent access by non-target species.\n" +
                            "- Position bait stations strategically, away from open spaces frequented by wildlife.\n" +
                            "- Use bait blocks securely fixed within stations to reduce the risk of bait being removed or scattered.");

            addSection(document, "3.2 Rodenticide Selection",
                    "- Difenacoum (0.005%) is used where a second-generation anticoagulant is necessary, as it poses a lower risk to non-target species compared to stronger alternatives.\n" +
                            "- Cholecalciferol is preferred in locations where secondary poisoning risk must be minimized, as it does not bioaccumulate in predators.");

            addSection(document, "3.3 Carcass Removal and Site Monitoring",
                    "- Conduct regular inspections to remove dead rodents promptly.\n" +
                            "- Dispose of carcasses responsibly, following Department of Agriculture guidelines.");

            addSection(document, "3.4 Water Protection Measures",
                    "- Keep bait stations at least 10 meters away from water sources.\n" +
                            "- Ensure no bait is exposed to prevent runoff during heavy rain.");

            addSection(document, "3.5 Integrated Pest Management (IPM) Approach",
                    "- Prioritize proofing and habitat management before relying on rodenticides.\n" +
                            "- Encourage proper waste storage to reduce rodent attractants.\n" +
                            "- Use trapping methods where feasible, particularly in sensitive environmental areas.");

            addSection(document, "4. Conclusion",
                    TenantBranding.companyName(context) + " is committed to responsible rodenticide use, ensuring effective pest control while minimizing environmental impact. " +
                            "By following secure baiting practices, selecting appropriate rodenticides, and implementing IPM strategies, we help protect non-target species and reduce ecological risks.\n\n" +
                            "This assessment serves as a general guideline and should be adapted to specific site conditions as needed.");

            addSection(document, "5. Technician Signature", "___________________________________________");



            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), null);
            return pdfPath;

        } catch (IOException e) {
            Log.e("PDFGenerator", "Error creating PDF", e);
            return null;
        }
    }

    private static void addSection(Document document, String title, String content) {
        document.add(new Paragraph("\n" + title).setBold().setFontSize(14).setBackgroundColor(ColorConstants.LIGHT_GRAY));
        document.add(new Paragraph(content).setFontSize(12));
    }

    private static String generatePdfFileName(String companyName) {
        // Get the current date in ddMM format
        String datePart = new SimpleDateFormat("ddMM", Locale.getDefault()).format(new Date());

        // Format company name (remove spaces and special characters)
        String formattedCompanyName = companyName.replaceAll("[^a-zA-Z0-9]", "");

        // Construct the file name
        return formattedCompanyName + "_ERA_" + datePart + ".pdf";
    }
}
