package com.grpc.grpc;

import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.element.Text;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BehindsListPDFGenerator.java
 *
 * This class generates a structured PDF report for overdue contracts (behinds list).
 * It creates a professional document listing all contracts that are past due,
 * including company details, addresses, and next visit dates.
 *
 * Features:
 * - Generates a structured PDF with overdue contract details
 * - Applies a watermark and footer for branding
 * - Saves the report locally in the "BEHINDS LIST" folder
 * - Formats report content with structured headings and separators
 * - Includes company branding and professional formatting
 *
 * Author: James Scott
 */

public class BehindsListPDFGenerator {

    /**
     * Generates a PDF report containing all overdue contracts (behinds list).
     *
     * @param technician The technician name for the report
     * @param overdueContracts List of overdue contract data
     * @param context The Android application context
     * @return The generated PDF file or null if an error occurred
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generateBehindsListPDF(String technician, List<Map<String, Object>> overdueContracts, Context context) {
        // Define the folder for storing behinds list reports
        File pdfFolder = new File(context.getExternalFilesDir(null), "BEHINDS LIST");
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();  // Create the directory if it does not exist
        }

        // Generate a timestamped file name for the PDF report
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String sanitizedTechnician = technician.replaceAll("[^a-zA-Z0-9]", "_");
        String pdfFileName = "BehindsList_" + sanitizedTechnician + "_" + currentDate + ".pdf";
        File pdfFile = new File(pdfFolder, pdfFileName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // Add watermark & footer
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Generate PDF content
            addPdfContent(document, technician, overdueContracts, context);

            document.close();
            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Adds content to the PDF document including header, contract details, and formatting.
     *
     * @param document The PDF document to add content to
     * @param technician The technician name
     * @param overdueContracts List of overdue contracts
     * @param context The Android context
     */
    private static void addPdfContent(Document document, String technician, List<Map<String, Object>> overdueContracts, Context context) {
        try {
            // Add logo at the top center
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            if (logoResourceId != 0) {
                ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
                Image logo = new Image(logoData)
                        .scaleToFit(200, 200)
                        .setFixedPosition(200, 750); // Position at top center
                document.add(logo);
            }

            // Add spacing after logo to prevent overlap with title
            document.add(new Paragraph("\n\n\n\n\n")); // Add multiple line breaks for proper spacing

            // Add title
            Paragraph title = new Paragraph("OVERDUE CONTRACTS REPORT")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20);
            document.add(title);

            // Add separator line after title
            LineSeparator titleSeparator = new LineSeparator(new SolidLine(2f));
            titleSeparator.setMarginTop(10);
            titleSeparator.setMarginBottom(20);
            document.add(titleSeparator);

            // Add subtitle with technician name
            Paragraph subtitle = new Paragraph("Technician: " + technician)
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(subtitle);

            // Add date
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Paragraph date = new Paragraph("Generated on: " + sdf.format(new Date()))
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(30);
            document.add(date);

            // Add separator line
            LineSeparator separator = new LineSeparator(new SolidLine(1f));
            separator.setMarginBottom(20);
            document.add(separator);

            // Add summary
            Paragraph summary = new Paragraph("Total Overdue Contracts: " + overdueContracts.size())
                    .setFontSize(14)
                    .setBold()
                    .setMarginBottom(20);
            document.add(summary);

            // Add each overdue contract - positioned under the logo
            int contractNumber = 1;
            for (Map<String, Object> contract : overdueContracts) {
                String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
                String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
                String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
                String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
                String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
                
                // Calculate next visit date
                String nextVisit = calculateNextVisit(contract);

                // Contract header
                Paragraph contractHeader = new Paragraph(contractNumber + ". " + name)
                        .setFontSize(16)
                        .setBold()
                        .setMarginTop(15)
                        .setMarginBottom(5);
                document.add(contractHeader);

                // Contract details
                Paragraph addressLine = new Paragraph("📍 Address: " + address)
                        .setFontSize(12)
                        .setMarginBottom(3);
                document.add(addressLine);

                Paragraph contactLine = new Paragraph("📞 Contact: " + contact)
                        .setFontSize(12)
                        .setMarginBottom(3);
                document.add(contactLine);

                Paragraph emailLine = new Paragraph("📧 Email: " + email)
                        .setFontSize(12)
                        .setMarginBottom(3);
                document.add(emailLine);

                Paragraph lastVisitLine = new Paragraph("📅 Last Visit: " + lastVisit)
                        .setFontSize(12)
                        .setMarginBottom(3);
                document.add(lastVisitLine);

                Paragraph nextVisitLine = new Paragraph("⏰ Next Visit: " + nextVisit)
                        .setFontSize(12)
                        .setBold()
                        .setMarginBottom(10);
                document.add(nextVisitLine);

                // Add small separator between contracts
                if (contractNumber < overdueContracts.size()) {
                    LineSeparator contractSeparator = new LineSeparator(new SolidLine(0.5f));
                    contractSeparator.setMarginBottom(10);
                    document.add(contractSeparator);
                }

                contractNumber++;
            }

            // Add footer note
            Paragraph footerNote = new Paragraph("This report was automatically generated by Good Riddance Pest Control")
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30);
            document.add(footerNote);

        } catch (Exception e) {
            Toast.makeText(context, "Error adding content to PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Calculates the next visit date based on contract data.
     * This method replicates the logic from ViewContractActivity.
     *
     * @param contract The contract data map
     * @return The calculated next visit date as a string
     */
    private static String calculateNextVisit(Map<String, Object> contract) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat shortYearFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        int visits = contract.get("visits") != null ? Integer.parseInt(contract.get("visits").toString()) : 0;

        if ("N/A".equals(lastVisit) || visits == 0) {
            return "N/A";
        }

        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(dateFormat.parse(lastVisit));

            // Adjust the next visit date based on the number of visits
            switch (visits) {
                case 8:
                    calendar.add(java.util.Calendar.WEEK_OF_YEAR, 6);
                    break;
                case 12:
                    calendar.add(java.util.Calendar.WEEK_OF_YEAR, 4);
                    break;
                case 6:
                    calendar.add(java.util.Calendar.WEEK_OF_YEAR, 8);
                    break;
                case 4:
                    calendar.add(java.util.Calendar.WEEK_OF_YEAR, 12);
                    break;
                default:
                    return "N/A";
            }

            return shortYearFormat.format(calendar.getTime());
        } catch (Exception e) {
            return "Invalid Date";
        }
    }
} 