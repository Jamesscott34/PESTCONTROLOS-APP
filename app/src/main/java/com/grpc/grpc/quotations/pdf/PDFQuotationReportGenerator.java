package com.grpc.grpc.quotations.pdf;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.data.ReportDatabaseHelper;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
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
 * PDFQuotationReportGenerator.java
 *
 * This class generates a professionally formatted PDF quotation report for pest control services.
 * The report includes company details, customer information, itemized pricing, VAT calculations,
 * and payment instructions. The generated PDF is saved locally and recorded in the database.
 *
 * Features:
 * - Generates a structured PDF with company branding and customer details
 * - Includes an itemized table with automatic VAT (23%) calculations
 * - Calculates the first quarter payment for contract-based services
 * - Saves the quotation details in the local database for future reference
 * - Applies a watermark and structured formatting to the report
 * - Ensures compliance with professional pest control service quotations
 *
 * Author: GRPC
 */

public class PDFQuotationReportGenerator {

    public static File generateQuotationReport(
            String quoteNumber, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, Context context) {
        return generateQuotationReport(
                quoteNumber,
                null,
                null,
                address,
                quoteDescription,
                descriptions,
                lineTotals,
                userEmail,
                mobileNumber,
                null,
                true,
                context
        );
    }

    /**
     * Generates a quotation report PDF with optional password protection. Always uses compression.
     * @param ownerPassword If non-null and non-empty, PDF is encrypted with this as owner password (editing restricted).
     */
    public static File generateQuotationReport(
            String quoteNumber, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, String ownerPassword, Context context) {
        return generateQuotationReport(
                quoteNumber,
                null,
                null,
                address,
                quoteDescription,
                descriptions,
                lineTotals,
                userEmail,
                mobileNumber,
                ownerPassword,
                true,
                context
        );
    }

    /**
     * New overload: include Customer/Company details (name + address) separately from premises address.
     *
     * @param companyName Customer/company name (optional)
     * @param companyAddress Customer/company address (optional)
     * @param premisesAddress Service/premises address (optional)
     * @param saveToDb If true, inserts quote into local DB. Set false if caller already saved.
     */
    public static File generateQuotationReport(
            String quoteNumber,
            String companyName,
            String companyAddress,
            String premisesAddress,
            String quoteDescription,
            List<String> descriptions,
            List<Double> lineTotals,
            String userEmail,
            String mobileNumber,
            String ownerPassword,
            boolean saveToDb,
            Context context) {

        File quotesFolder = new File(context.getExternalFilesDir(null), TenantBranding.quotesFolderName(context));

        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        String pdfFileName = generateUniquePdfFileName(context);
        File pdfFile = new File(quotesFolder, pdfFileName);

        WriterProperties writerProperties = new WriterProperties();
        writerProperties.setFullCompressionMode(true);
        if (ownerPassword != null && !ownerPassword.isEmpty()) {
            writerProperties.setStandardEncryption(
                    null,
                    ownerPassword.getBytes(),
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
            );
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties);
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
            leftCell.add(new Paragraph("\n" + TenantBranding.companyName(context)).setBold().setFontSize(16));
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(14));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(14));
            leftCell.add(new Paragraph("Website: " + TenantBranding.companyWebsiteShort(context)).setFontSize(14));
            headerTable.addCell(leftCell);

            // Right Section: Date, Quote Validity, and Customer Info
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            String currentDate = sdf.format(new Date());
            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + currentDate).setFontSize(14).setBold());
            rightCell.add(new Paragraph("Quote Valid for 30 Days").setFontSize(12).setItalic());
            rightCell.add(new Paragraph("\nCustomer:").setBold());
            if (companyName != null && !companyName.trim().isEmpty()) {
                rightCell.add(new Paragraph(companyName.trim()).setFontSize(14));
            }
            if (companyAddress != null && !companyAddress.trim().isEmpty()) {
                rightCell.add(new Paragraph(companyAddress.trim()).setFontSize(14));
            }
            if (premisesAddress != null && !premisesAddress.trim().isEmpty()) {
                rightCell.add(new Paragraph("\nPremises Address:").setBold());
                rightCell.add(new Paragraph(premisesAddress.trim()).setFontSize(14));
            }
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
                table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(lineTotal))));
                table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(vatAmount))));
                table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(total))));

                grandTotal += total;
            }

            document.add(table);

            // ✅ Additional Section for Payment Summary at the Bottom
            document.add(new Paragraph("\n\nPayment Summary:").setFontSize(16).setBold().setUnderline());
            document.add(new Paragraph("First Quarter Payment: " + CurrencyFormatter.formatEuro(firstQuarterPayment)).setFontSize(14).setBold());
            document.add(new Paragraph("Additional Materials Total: " + CurrencyFormatter.formatEuro(additionalLineItemsTotal)).setFontSize(14));
            document.add(new Paragraph("Total Payment Due: " + CurrencyFormatter.formatEuro(firstQuarterPayment + additionalLineItemsTotal))
                    .setFontSize(14).setBold());

            // ✅ Save the quote data to the database before closing the document (optional; callers may already save)
            if (saveToDb) {
                ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(context);
                dbHelper.insertQuote(
                        quoteNumber,
                        currentDate,
                        premisesAddress != null ? premisesAddress : "",
                        quoteDescription,
                        grandTotal,
                        userEmail,
                        mobileNumber,
                        true);
            }

            // ✅ Close the document after successful data saving
            document.close();
            byte[] stampPw = (ownerPassword != null && !ownerPassword.isEmpty())
                    ? ownerPassword.getBytes() : null;
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), stampPw);

            // ✅ Notify user
            Toast.makeText(context, context.getString(R.string.quote_saved_toast), Toast.LENGTH_SHORT).show();

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
    static String generateUniquePdfFileName(Context context) {
        int randomNum = 1000 + new Random().nextInt(9000); // Random 4-digit number
        String prefix = context != null ? TenantBranding.filenamePrefix(context) : "GRPC";
        return prefix + "_Quote_" + randomNum + ".pdf";
    }
}
