package com.grpc.grpc;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * PDF Quotation Generator for GRPEST with corrected layout and payment calculations.
 */
public class PDFQuotationReportGenerator {

    public static File generateQuotationReport(
            String quoteNumber, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, Context context) {

        File quotesFolder = new File(context.getExternalFilesDir(null), "GRPEST_QUOTES");

        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }


        String pdfFileName = generateUniquePdfFileName();
        File pdfFile = new File(quotesFolder, pdfFileName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // ✅ Apply watermark
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // ✅ Adding Company Logo and Header Layout
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200);

            // Header Table
            float[] headerWidths = {1, 1};
            Table headerTable = new Table(headerWidths).setWidth(UnitValue.createPercentValue(100));

            // Left Section: Logo and Company Info
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER);
            leftCell.add(logo);
            leftCell.add(new Paragraph("\nGood Riddance Pest Control").setBold().setFontSize(16));
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(14));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(14));
            leftCell.add(new Paragraph("Website: grpestcontrol.ie").setFontSize(14));
            headerTable.addCell(leftCell);

            // Right Section: Date, Quote Number, and Customer Info
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            String currentDate = sdf.format(new Date());
            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + currentDate).setFontSize(14).setBold());
            rightCell.add(new Paragraph("Quote Valid for 30 Days").setFontSize(12).setItalic());
            rightCell.add(new Paragraph("\nCustomer Address:").setBold());
            rightCell.add(new Paragraph(address).setFontSize(14));
            headerTable.addCell(rightCell);

            document.add(headerTable);

            // ✅ Quote Description Section
            document.add(new Paragraph("\nQuote Description:").setFontSize(16).setBold().setUnderline());
            document.add(new Paragraph(quoteDescription).setFontSize(14));

            // ✅ Line Items Table Setup
            float[] columnWidths = {300f, 100f, 100f, 100f};
            Table table = new Table(columnWidths).setWidth(UnitValue.createPercentValue(100));

            // ✅ Adding Table Headers
            table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Line Total (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("VAT (23%) (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total (€)").setBold()));

            double grandTotal = 0;
            double firstQuarterPayment = 0;
            double additionalLineItemsTotal = 0;

            for (int i = 0; i < descriptions.size(); i++) {
                double lineTotal = lineTotals.get(i);
                double vatAmount = lineTotal * 0.23;
                double total = lineTotal + vatAmount;

                // ✅ Calculate first quarter payment from the first line item after VAT
                if (i == 0) {
                    firstQuarterPayment = total / 4;
                } else {
                    additionalLineItemsTotal += total;
                }

                table.addCell(new Cell().add(new Paragraph(descriptions.get(i))));
                table.addCell(new Cell().add(new Paragraph(String.format("€%.2f", lineTotal))));
                table.addCell(new Cell().add(new Paragraph(String.format("€%.2f", vatAmount))));
                table.addCell(new Cell().add(new Paragraph(String.format("€%.2f", total))));

                grandTotal += total;
            }

            document.add(table);

            // ✅ Additional Section for Payment Summary at the Bottom
            document.add(new Paragraph("\n\nPayment Summary:").setFontSize(16).setBold().setUnderline());
            document.add(new Paragraph("First Quarter Payment: €" + String.format("%.2f", firstQuarterPayment)).setFontSize(14).setBold());
            document.add(new Paragraph("Additional Line Items Total: €" + String.format("%.2f", additionalLineItemsTotal)).setFontSize(14));
            document.add(new Paragraph("Total Payment Due: €" + String.format("%.2f", (firstQuarterPayment + additionalLineItemsTotal)))
                    .setFontSize(14).setBold());

            // ✅ Save the quote data to the database before closing the document
            ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(context);
            dbHelper.insertQuote(
                    quoteNumber,
                    currentDate,
                    address,
                    quoteDescription,
                    grandTotal,
                    userEmail,
                    mobileNumber
            );

            // ✅ Close the document after successful data saving
            document.close();

            // ✅ Notify user
            Toast.makeText(context, "Quotation PDF Created and Saved Successfully!", Toast.LENGTH_SHORT).show();

            // ✅ Return to the previous screen
            if (context instanceof AppCompatActivity) {
                ((AppCompatActivity) context).finish();
            }

            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error Creating PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }

    }
    private static String generateUniquePdfFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String date = sdf.format(new Date());
        int randomNum = 1000 + new Random().nextInt(9000); // Random 4-digit number
        return "GRPEST_Quote_" + date + "-" + randomNum + ".pdf";
    }
}
