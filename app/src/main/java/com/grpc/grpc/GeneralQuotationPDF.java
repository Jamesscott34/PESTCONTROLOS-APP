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
 * GeneralQuotationPDF.java
 *
 * This class generates a professional PDF quotation for pest control services.
 * The generated quotation includes customer details, line items, VAT calculations,
 * and payment instructions. The PDF is saved locally and stored in the database.
 *
 * Features:
 * - Generates a structured PDF with customer and quotation details
 * - Calculates VAT (13.5%) and total payment values
 * - Saves the PDF file to a dedicated folder
 * - Stores the quotation details in the local database
 * - Displays success or error messages to the user
 *
 * Author: James Scott
 */


public class GeneralQuotationPDF {

    public static File generateQuotation(
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, Context context) {

        File quotesFolder = new File(context.getExternalFilesDir(null), "GRPEST_QUOTES");
        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Generate a random 4-digit number for the quote number
        int randomNum = 1000 + new Random().nextInt(9000);
        String quoteNumber = "GRPC-" + randomNum;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String pdfFileName = "GRPC_General-Quote_" + randomNum + ".pdf";
        File pdfFile = new File(quotesFolder, pdfFileName);

        double grandTotal = 0;

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // Apply watermark (if needed)
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Add company logo and header
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200);

            // Header table
            float[] headerWidths = {1, 1};
            Table headerTable = new Table(headerWidths).setWidth(UnitValue.createPercentValue(100));
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER);
            leftCell.add(logo);
            leftCell.add(new Paragraph("\nGood Riddance Pest Control").setBold().setFontSize(16));
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(14));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(14));
            leftCell.add(new Paragraph("Website: grpestcontrol.ie").setFontSize(14));
            headerTable.addCell(leftCell);

            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + currentDate).setFontSize(14).setBold());
            rightCell.add(new Paragraph("Quote Number: " + quoteNumber).setFontSize(14).setBold());
            rightCell.add(new Paragraph("\nCustomer Address:").setBold());
            rightCell.add(new Paragraph(address).setFontSize(14));
            headerTable.addCell(rightCell);

            document.add(headerTable);

            // Add quote description
            document.add(new Paragraph("\nQuote Breakdown:").setFontSize(16).setBold().setUnderline());
            document.add(new Paragraph(quoteDescription).setFontSize(14));

            // Add line items
            float[] columnWidths = {300f, 100f, 100f, 100f};
            Table table = new Table(columnWidths).setWidth(UnitValue.createPercentValue(100));

            // Table headers
            table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Line Total (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("VAT (13.5%) (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total (€)").setBold()));

            for (int i = 0; i < descriptions.size(); i++) {
                double lineTotal = lineTotals.get(i);
                double vatAmount = lineTotal * 0.135;
                double total = lineTotal + vatAmount;

                table.addCell(new Cell().add(new Paragraph(descriptions.get(i))));
                table.addCell(new Cell().add(new Paragraph(String.format("€%.2f", lineTotal))));
                table.addCell(new Cell().add(new Paragraph(String.format("€%.2f", vatAmount))));
                table.addCell(new Cell().add(new Paragraph(String.format("€%.2f", total))));

                grandTotal += total;
            }

            document.add(table);

            // Payment Summary
            document.add(new Paragraph("\nPayment Summary:").setFontSize(18).setBold().setUnderline());
            document.add(new Paragraph("Total Payment: €" + String.format("%.2f", grandTotal)).setFontSize(16).setBold());

            document.add(new Paragraph("Note: Payment to be made on initial visit.").setFontSize(12).setItalic());

            document.close();

            // Save to database
            ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(context);
            dbHelper.insertBirdQuote(
                    quoteNumber,
                    currentDate,
                    address,
                    quoteDescription,
                    grandTotal,
                    userEmail,
                    mobileNumber
            );

            Toast.makeText(context, "Quotation PDF Generated and Saved Successfully!", Toast.LENGTH_SHORT).show();

            if (context instanceof AppCompatActivity) {
                ((AppCompatActivity) context).finish();
            }

            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error Creating Bird Quotation PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }
}
