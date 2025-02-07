package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ServiceAgreementGenerator {

    @SuppressLint("DefaultLocale")
    public static String generateServiceAgreement(Context context,
                                                  String name, String address, String email, String phone, String vat,
                                                  String technicianName, String grpcOffice, double price,
                                                  int visits) {

        File serviceAgreementsFolder = new File(context.getExternalFilesDir(null), "ServiceAgreements");
        if (!serviceAgreementsFolder.exists() && !serviceAgreementsFolder.mkdirs()) {
            Toast.makeText(context, "Error creating Service Agreements folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        String pdfFileName = generatePdfFileName(name);
        File pdfFile = new File(serviceAgreementsFolder, pdfFileName);
        String pdfPath = pdfFile.getAbsolutePath();

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            try {
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                ImageData imageData = ImageDataFactory.create(stream.toByteArray());
                Image logo = new Image(imageData).scaleToFit(200, 200);

                // Create a table for header with two columns (Logo & Address)
                Table headerTable = new Table(new float[]{1, 2}); // Adjust column sizes (logo smaller, text larger)
                headerTable.setWidth(UnitValue.createPercentValue(100)); // Ensure full width

// Left Cell: Logo
                Cell logoCell = new Cell()
                        .add(logo)
                        .setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.LEFT); // Ensure logo is left-aligned

// Right Cell: Company Name & Address
                Cell textCell = new Cell()
                        .add(new Paragraph("GRPC").setBold().setFontSize(14).setTextAlignment(TextAlignment.RIGHT))
                        .add(new Paragraph("35 Limekiln Green").setFontSize(12).setTextAlignment(TextAlignment.RIGHT))
                        .add(new Paragraph("Walkinstown").setFontSize(12).setTextAlignment(TextAlignment.RIGHT))
                        .setBorder(Border.NO_BORDER);

// Add cells to table
                headerTable.addCell(logoCell);
                headerTable.addCell(textCell);

// Add table to document
                document.add(headerTable);

            } catch (Exception e) {
                Log.e("PDFGenerator", "Error adding logo", e);
            }


            document.add(new Paragraph("GRPC SERVICE AGREEMENT").setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER));


        Table mainTable = new Table(2);

            // Section A: Customer Information - Full Width Title
            Paragraph customerInfoTitle = new Paragraph("Section A: Customer Information")
                    .setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(5);

            document.add(customerInfoTitle); // Add the title separately

// Customer Information - Details Table
            Table customerTable = new Table(2);
            customerTable.setWidth(UnitValue.createPercentValue(100)); // Set full width

            customerTable.addCell(new Cell().add(new Paragraph("Name:")).setBold());
            customerTable.addCell(new Cell().add(new Paragraph(name)));

            customerTable.addCell(new Cell().add(new Paragraph("Address:")).setBold());
            customerTable.addCell(new Cell().add(new Paragraph(address)));

            customerTable.addCell(new Cell().add(new Paragraph("Email:")).setBold());
            customerTable.addCell(new Cell().add(new Paragraph(email)));

            customerTable.addCell(new Cell().add(new Paragraph("Phone:")).setBold());
            customerTable.addCell(new Cell().add(new Paragraph(phone)));

            customerTable.addCell(new Cell().add(new Paragraph("VAT:")).setBold());
            customerTable.addCell(new Cell().add(new Paragraph(vat)));

            customerTable.addCell(new Cell().add(new Paragraph("Customer Signature:")).setBold());
            customerTable.addCell(new Cell().add(new Paragraph(" ")));

            document.add(customerTable); // Add customer details table

// Spacer before Section B
            document.add(new Paragraph("\n"));

// Section B: Service Declaration - Full Width Title
            Paragraph serviceDeclarationTitle = new Paragraph("Section B: Service Declaration")
                    .setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(5);

            document.add(serviceDeclarationTitle); // Add the title separately

// Service Declaration - Full Width Paragraph
            document.add(new Paragraph(
                    "Good Riddance Pest Control (GRPC) is dedicated to delivering comprehensive, reliable, and high-standard pest management solutions "
                            + "tailored to the specific needs of the client. Our qualified and certified technicians will conduct " + visits + " scheduled service visits per year, "
                            + "ensuring proactive prevention, early detection, and swift corrective actions to maintain a pest-free environment.\n\n"

                            + "GRPC’s approach is based on the principles of Integrated Pest Management (IPM), prioritizing environmentally responsible, science-backed, and industry-compliant "
                            + "pest control strategies. We utilize a combination of preventive measures, advanced treatment techniques, and thorough site assessments to safeguard "
                            + "your premises against pest infestations.\n\n"

                            + "Scope of Service Includes:\n"
                            + "✔️ Regular monitoring and inspections tailored to the facility's risk profile.\n"
                            + "✔️ Implementation of proactive preventive measures to mitigate infestation risks.\n"
                            + "✔️ Application of safe, targeted, and approved treatments to eliminate pest activity.\n"
                            + "✔️ Provision of detailed service reports and regulatory documentation to ensure full compliance with HACCP, BRC, and other industry standards.\n"
            ));

            // Technician Signature at the Bottom
            document.add(new Paragraph("Technician Signature: " + technicianName).setBold().setBackgroundColor(ColorConstants.LIGHT_GRAY));


            // Add another spacer before next section
            document.add(new Paragraph("\n"));
            document.add(new Paragraph("\n"));

            // Section C: Maintenance Information - Full Width Title
            Paragraph maintenanceTitle = new Paragraph("Section C: Maintenance Information")
                    .setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(5);

            document.add(maintenanceTitle); // Add the title separately

// Maintenance Information - Full Width Table
            Table serviceTable = new Table(2);
            serviceTable.setWidth(UnitValue.createPercentValue(100)); // Ensure full width

            serviceTable.addCell(new Cell().add(new Paragraph("Technician:")).setBold());
            serviceTable.addCell(new Cell().add(new Paragraph(technicianName)));

            serviceTable.addCell(new Cell().add(new Paragraph("Total Cost (" + vat + "% VAT):")).setBold());
            serviceTable.addCell(new Cell().add(new Paragraph("€" + String.format("%.2f", price))));

            serviceTable.addCell(new Cell().add(new Paragraph("Price Per Quarter (" + vat + "% VAT):")).setBold());
            double pricePerQuarter = price / 4;
            serviceTable.addCell(new Cell().add(new Paragraph("€" + String.format("%.2f", pricePerQuarter))));

            serviceTable.addCell(new Cell().add(new Paragraph("Visits:")).setBold());
            serviceTable.addCell(new Cell().add(new Paragraph(visits + " per year")));

            // Add the table to the document
            document.add(serviceTable);



            // Technician Signature - Full Width
            document.add(new Paragraph("Technician Signature: " + technicianName).setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setBold()
                    .setTextAlignment(TextAlignment.LEFT));
            // Spacer before Technician Signature
            document.add(new Paragraph("\n"));

            document.add(new Paragraph("\n Section D:GRPC Service Commitment – " + visits + " Scheduled Inspections Per Year").setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setBold().setFontSize(14));

            Table visitTable = new Table(2);
            visitTable.addCell(new Cell().add(new Paragraph("Service Category").setBold()).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            visitTable.addCell(new Cell().add(new Paragraph("Scope of Services").setBold()).setBackgroundColor(ColorConstants.LIGHT_GRAY));

            visitTable.addCell("External Pest Prevention & Control:");
            visitTable.addCell("GRPC will conduct proactive and routine inspections of the designated external areas to prevent pest activity. "
                    + "This includes treatment and monitoring in accordance with industry best practices. "
                    + "The contract covers the maintenance of ___ external units on site, ensuring compliance with health and safety regulations."
                    +"Additional Externals can be Acquired at a cost and can be maintained are charged an additional €30 + VAT@23% per quarter per unit. ");

            visitTable.addCell("Internal Rodent & Pest Monitoring:");
            visitTable.addCell("GRPC will implement and maintain a comprehensive rodent monitoring system within the premises. "
                    + "This includes strategic placement of rodent monitoring stations and adjustment of control measures based on findings during each visit. "
                    + "Our approach follows a risk-based assessment methodology to minimize infestation risks effectively.");

            visitTable.addCell("Fly Control & Airborne Pest Management:");
            visitTable.addCell("To ensure compliance with food safety and hygiene regulations, ___ standard electronic fly control units will be serviced ___ . "
                    +"times per Year, with A bulb change as Required "
                    + "Additional fly units can be provided at a cost and serviced at a rate of €40 + VAT@23% per quarter per unit.");

            visitTable.addCell("Insect Activity Surveillance & Treatment:");
            visitTable.addCell("GRPC will conduct **detailed insect activity assessments** during each scheduled visit, identifying potential breeding grounds. "
                    + "Preventive treatments and corrective actions will be implemented as necessary. "
                    + "Our technicians will provide site-specific recommendations to mitigate risk and ensure long-term insect control.");

            visitTable.addCell("Sanitation & Structural Recommendations:");
            visitTable.addCell("As part of our commitment to integrated pest management (IPM), GRPC will provide tailored recommendations on sanitation and structural improvements. "
                    + "These recommendations aim to reduce conditions conducive to pest infestations and enhance overall pest prevention measures.");

            visitTable.addCell("Regulatory Compliance & Documentation:");
            visitTable.addCell("All inspections, treatments, and preventive actions will be documented in accordance with HACCP, BRC, and relevant food safety regulations. "
                    + "GRPC will provide detailed service reports and compliance documentation to support regulatory requirements and audits.");

            document.add(visitTable);


            // Convert VAT string to a double safely
            double vatValue;
            try {
                vatValue = Double.parseDouble(vat);
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid VAT format!", Toast.LENGTH_SHORT).show();
                return null;
            }

            // Add another spacer before next section
            document.add(new Paragraph("\n"));


            // Section E: Declaration Title (Full Width, Centered, Light Gray Background)
            Paragraph declarationTitle = new Paragraph("Section E: Declaration")
                    .setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(5);

            document.add(declarationTitle); // Add title separately

// Declaration Content Table (Single Column for Better Readability)
            Table declarationTable = new Table(1);
            declarationTable.setWidth(UnitValue.createPercentValue(100)); // Ensure full width

            Cell declarationCell = new Cell()
                    .add(new Paragraph("The Customer agrees to fulfill all contractual obligations under this agreement, including the payment of service fees. " +
                            "The total service fee for the agreed services is €" + String.format("%.2f", price)  +
                            "(including" + vatValue + "% VAT) for " + visits + " scheduled visits per year. " +
                            "The Customer agrees to remit payment in four equal installments, each amounting t €" +
                            String.format("%.2f", pricePerQuarter) + "(including" + vatValue + "% VAT), payable quarterly.\n\n" +

                            "Each quarter, an email notification will be sent regarding the payment, and the invoice must be paid within 24 hours of receipt,Or a Card Payment can be taken on the day Via Technician or over the Phone. \n\n" +

                            "Please print a copy of this Service Agreement, sign it, and return it to GRPC via Email/Post. Alternatively, you may use DocuSign to digitally sign the agreement " +
                            "and send it back electronically.")) // Professional language with formatting
                    .setTextAlignment(TextAlignment.LEFT) // Align text to left for better readability
                    .setPadding(10) // Add padding for readability
                    .setBorder(Border.NO_BORDER); // Optional: Remove border for a clean layout

            declarationTable.addCell(declarationCell);
            document.add(declarationTable); // Add Declaration Table to Document
            // Add the table to the document

            // Add another spacer before next section
            document.add(new Paragraph("\n"));

            // Section F: Customer Authorization (Full Width, Centered, Light Gray Background)
            Paragraph customerAuthTitle = new Paragraph("Section F: Customer Authorization")
                    .setBold().setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(5);

            document.add(customerAuthTitle); // Add the title separately

// Customer Information Table - Full Width
            Table customerInfoTable = new Table(2);
            customerInfoTable.setWidth(UnitValue.createPercentValue(100)); // Ensure full width

            customerInfoTable.addCell(new Cell().add(new Paragraph("Customer Name:")).setBold());
            customerInfoTable.addCell(new Cell().add(new Paragraph(name)));

            customerInfoTable.addCell(new Cell().add(new Paragraph("Address:")).setBold());
            customerInfoTable.addCell(new Cell().add(new Paragraph(address)));

            customerInfoTable.addCell(new Cell().add(new Paragraph("Phone Number:")).setBold());
            customerInfoTable.addCell(new Cell().add(new Paragraph(phone)));

// Add Customer Info Table to Document
            document.add(customerInfoTable);

// Spacer Before Signature Section
            document.add(new Paragraph("\n"));

// Signature Table - Full Width
            Table signatureTable = new Table(2);
            signatureTable.setWidth(UnitValue.createPercentValue(100)); // Ensure full width

            signatureTable.addCell(new Cell().add(new Paragraph("Customer Name: (PRINT NAME)")).setBold());
            signatureTable.addCell(new Cell().add(new Paragraph(" "))); // Empty space for manual entry

            signatureTable.addCell(new Cell().add(new Paragraph("Customer Signature: (SIGNATURE)")).setBold());
            signatureTable.addCell(new Cell().add(new Paragraph(" "))); // Blank line for signature

            signatureTable.addCell(new Cell().add(new Paragraph("Technician Signature:")).setBold());
            signatureTable.addCell(new Cell().add(new Paragraph(technicianName))); // Technician (Username)

            signatureTable.addCell(new Cell().add(new Paragraph("Date:")).setBold());
            signatureTable.addCell(new Cell().add(new Paragraph(
                    new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()))));

// Add Signature Table to Document
            document.add(signatureTable);

            document.close();
            return pdfPath;


        } catch (IOException e) {
            Log.e("PDFGenerator", "Error creating PDF", e);
            return null;
        }
    }



/**
     * Generates a unique PDF filename based on the customer's name and current timestamp.
     * @param customerName - The name of the customer.
     * @return A properly formatted filename.
     */
    private static String generatePdfFileName(String customerName) {
        // Remove special characters from name
        String sanitizedCustomerName = customerName.replaceAll("[^a-zA-Z0-9]", "_");

        // Generate timestamp for uniqueness
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

        // Format filename
        return "Service_Agreement_" + sanitizedCustomerName + ".pdf";
    }
}
