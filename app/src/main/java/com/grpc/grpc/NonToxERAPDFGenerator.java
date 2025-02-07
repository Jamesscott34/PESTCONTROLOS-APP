package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.Toast;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NonToxERAPDFGenerator {

    @SuppressLint("DefaultLocale")
    public static String generateEnvironmentalRiskAssessment(Context context, String companyName, String address, String email, Bitmap signature) {

        File assessmentsFolder = new File(context.getExternalFilesDir(null), "EnvironmentalRiskAssessments");
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

            // Add Centered Logo
            Bitmap logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Image logo = new Image(com.itextpdf.io.image.ImageDataFactory.create(stream.toByteArray()))
                    .setWidth(150)
                    .setHeight(150)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER); // Ensure it's centered

            document.add(logo);

// Add Title
            document.add(new Paragraph("GRPC Environmental Risk Assessment")
                    .setBold()
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

// Add Company and Customer Details in a Table with Left and Right Alignment
            String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

// Create a table with two columns (left for company, right for customer)
            Table detailsTable = new Table(UnitValue.createPercentArray(2)).useAllAvailableWidth();

// Left Column - Company Details
            Cell companyDetails = new Cell()
                    .add(new Paragraph("Company Name:\n Good Riddance Pest Control").setBold())
                    .add(new Paragraph("Address: 35 Limekiln Green,\n Walkinstown,Dublin 12,\n D12V6Y2\n"))
                    .add(new Paragraph("Date:\n " + date))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFontSize(14);

            detailsTable.addCell(companyDetails);

// Right Column - Customer Details
            Cell customerDetails = new Cell()
                    .add(new Paragraph("Customer Name:\n " + companyName).setBold())
                    .add(new Paragraph("Customer Address:\n " + address))
                    .add(new Paragraph("Customer Email:\n " + email))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.RIGHT);

            detailsTable.addCell(customerDetails);

// Add the Table to the Document
            document.add(detailsTable);

// Add Spacing
            document.add(new Paragraph("\n"));


// Add Sections
            addSection(document, "1. Applicable Areas and Baits Used",
                    "Applicable Areas: Dublin, Kildare, Meath, Wicklow\nRodenticides Used: Cholecalciferol, Difenacoum");

            addSection(document, "2. Purpose",
                    "This environmental risk assessment outlines the considerations and best practices for the use of non-toxic bait in rodent monitoring. " +
                            "While non-toxic bait poses no direct poisoning risk, it must still be managed responsibly to prevent unintended environmental impact " +
                            "and ensure effective pest monitoring.");

            addSection(document, "3. Environmental Considerations",
                    "Non-toxic bait is primarily used for:\n" +
                            "- Monitoring rodent activity in commercial, industrial, and sensitive locations.\n" +
                            "- Determining bait uptake levels before introducing toxic rodenticides if necessary.\n" +
                            "- Situations where chemical control is restricted, such as food production sites and areas with high wildlife activity.");

            addSection(document, "2.1 Non-Target Species Considerations",
                    "While non-toxic bait does not contain rodenticide, it can still attract non-target wildlife, including:\n" +
                            "- Birds: Crows, pigeons, and magpies may be drawn to accessible grain-based baits.\n" +
                            "- Mammals: Foxes, badgers, hedgehogs, and domestic pets may consume bait if not properly secured.\n" +
                            "- Insects: Stored-product pests may infest grain-based non-toxic baits if not monitored and replaced regularly.");

            addSection(document, "2.2 Environmental Impact Risks",
                    "- Bait spillage: Loose bait can scatter, leading to unintended feeding by wildlife or contamination of sensitive areas.\n" +
                            "- Food source encouragement: Inconsistent bait removal may provide an additional food source for rodents rather than controlling their population.\n" +
                            "- Waterway concerns: Grain-based bait should not be placed near open water sources, as it can contribute to contamination and attract pests.");

            addSection(document, "3. Risk Mitigation Measures", "");

            addSection(document, "3.1 Secure Bait Placement",
                    "- Use tamper-resistant bait stations to protect bait from non-target species.\n" +
                            "- Position bait in discreet locations where rodents are most active while reducing visibility to wildlife.\n" +
                            "- Secure bait within the station to prevent removal and dispersal.");

            addSection(document, "3.2 Regular Monitoring and Replacement",
                    "- Check bait stations frequently to assess rodent activity and replace spoiled or moldy bait.\n" +
                            "- Remove uneaten bait if monitoring is complete or no rodent activity is detected.\n" +
                            "- Ensure grain-based bait does not attract insects, replacing it if signs of infestation appear.");

            addSection(document, "3.3 Water Protection Measures",
                    "- Avoid placing bait within 10 meters of open water to prevent contamination and pest attraction.\n" +
                            "- Use waterproof bait stations in outdoor locations to prevent spoilage and mold growth.");

            addSection(document, "3.4 Integrated Pest Management (IPM) Approach",
                    "- Use non-toxic bait as a first step before considering rodenticide application.\n" +
                            "- Implement proofing measures such as sealing entry points and reducing food sources.\n" +
                            "- Advise clients on proper waste management to prevent rodent attraction.");

            addSection(document, "4. Conclusion",
                    "Good Riddance Pest Control uses non-toxic bait as a key tool in rodent monitoring, ensuring environmentally responsible pest management. " +
                            "While non-toxic bait does not pose a poisoning risk, proper placement, monitoring, and disposal are essential to prevent unintended impacts on wildlife and the environment.\n\n" +
                            "This assessment provides a general guideline and should be adapted based on site-specific conditions.");

            addSection(document, "5. Technician Signature", "\n___________________________________________");



            document.close();
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
