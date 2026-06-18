package com.grpc.grpc.quotations.model;

import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.data.ReportDatabaseHelper;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;
import com.grpc.grpc.reports.pdf.ReportPdfSignatureSection;

import android.content.Context;
import android.net.Uri;
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
 * Author: GRPC
 */


public class GeneralQuotationPDF {

    public static final double VAT_RATE_STANDARD = 0.135;
    public static final double VAT_RATE_REDUCED = 0.23;

    /** Footer terms shown on Custom Quotation PDFs only (not catalog/generic-from-sales). */
    private static final String CUSTOM_QUOTE_ADDITIONAL_WORKS_INTRO =
            "Additional works outside the outlined scope will be separately assessed and quoted where required.";
    private static final String CUSTOM_QUOTE_ADDITIONAL_WORKS_DETAIL =
            "Where additional corrective works, drainage repairs, structural remediation, concealed contamination, "
                    + "access-related issues, water ingress repairs or third-party specialist requirements are identified "
                    + "during the progression of the project, a separate quotation can be provided once the affected areas "
                    + "have been inspected, exposed or confirmed. No additional works outside the agreed quotation will "
                    + "proceed without prior approval.";

    public static File generateQuotation(
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, Context context) {
        return generateQuotation("", address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber,
                null, context, null, true, null, VAT_RATE_STANDARD, 0, null);
    }

    public static File generateQuotation(
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String ownerPassword,
            Context context) {
        return generateQuotation("", address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber,
                ownerPassword, context, null, true, null, VAT_RATE_STANDARD, 0, null);
    }

    public static File generateQuotation(
            String customerName,
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish,
            Integer quoteRandomNumOverride) {
        return generateQuotation(customerName, address, quoteDescription, descriptions, lineTotals,
                userEmail, mobileNumber, ownerPassword, context, outputDirectory, persistAndFinish,
                quoteRandomNumOverride, VAT_RATE_STANDARD, 0, null);
    }

    public static File generateQuotation(
            String customerName,
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish,
            Integer quoteRandomNumOverride,
            double vatRate,
            List<Uri> imageUris) {
        return generateQuotation(customerName, address, quoteDescription, descriptions, lineTotals,
                userEmail, mobileNumber, ownerPassword, context, outputDirectory, persistAndFinish,
                quoteRandomNumOverride, vatRate, 0, imageUris, null);
    }

    public static File generateQuotation(
            String customerName,
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish,
            Integer quoteRandomNumOverride,
            double vatRate,
            int depositPercent,
            List<Uri> imageUris) {
        return generateQuotation(customerName, address, quoteDescription, descriptions, lineTotals,
                userEmail, mobileNumber, ownerPassword, context, outputDirectory, persistAndFinish,
                quoteRandomNumOverride, vatRate, depositPercent, imageUris, null);
    }

    public static File generateQuotation(
            String customerName,
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish,
            Integer quoteRandomNumOverride,
            double vatRate,
            int depositPercent,
            List<Uri> imageUris,
            Uri technicianSignatureUri) {

        File quotesFolder = outputDirectory != null
                ? outputDirectory
                : new File(context.getExternalFilesDir(null), TenantBranding.quotesFolderName(context));
        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Use override (preview-confirm consistency) or generate a random quote number.
        int randomNum = quoteRandomNumOverride != null
                ? quoteRandomNumOverride
                : 1000 + new Random().nextInt(9000);
        String quoteNumber = "GRPC-" + randomNum;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String safeCustomerName = sanitizeFilenamePart(customerName);
        String pdfFileName = "GRPC_" + safeCustomerName + "_" + randomNum + ".pdf";
        File pdfFile = new File(quotesFolder, pdfFileName);

        double baseTotalExclVat = 0;
        double vatTotal = 0;
        double grandTotalInclVat = 0;

        WriterProperties writerProperties = new WriterProperties();
        writerProperties.setFullCompressionMode(true);
        if (ownerPassword != null && !ownerPassword.trim().isEmpty()) {
            writerProperties.setStandardEncryption(
                    null,
                    ownerPassword.trim().getBytes(),
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
            );
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // Reserve space at the bottom for stamped "Page x of y" (see PdfFooterPageNumberStamper).
            document.setMargins(36, 36, PdfFooterPageNumberStamper.RECOMMENDED_BOTTOM_MARGIN_PT, 36);

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
            leftCell.add(new Paragraph("\n" + TenantBranding.companyName(context)).setBold().setFontSize(16));
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(14));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(14));
            leftCell.add(new Paragraph("Website: " + TenantBranding.companyWebsiteShort(context)).setFontSize(14));
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
            table.addHeaderCell(new Cell().add(new Paragraph(formatVatColumnHeader(vatRate)).setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total (€)").setBold()));

            for (int i = 0; i < descriptions.size(); i++) {
                double lineTotal = lineTotals.get(i);
                double vatAmount = Math.round(lineTotal * vatRate * 100.0) / 100.0;
                double total = Math.round((lineTotal + vatAmount) * 100.0) / 100.0;

                table.addCell(new Cell().add(new Paragraph(descriptions.get(i))));
                table.addCell(new Cell().add(new Paragraph(formatEuroAmount(lineTotal))));
                table.addCell(new Cell().add(new Paragraph(formatEuroAmount(vatAmount))));
                table.addCell(new Cell().add(new Paragraph(formatEuroAmount(total))));

                baseTotalExclVat += lineTotal;
                vatTotal += vatAmount;
                grandTotalInclVat += total;
            }

            document.add(table);

            appendCustomQuotationPaymentSummary(document, baseTotalExclVat, vatTotal, grandTotalInclVat, vatRate, depositPercent);

            appendCustomQuotationAdditionalWorksTerms(document);

            appendQuotationImages(document, context, imageUris);

            String signerTitle = SessionManager.getTitle(context);
            if (signerTitle == null || signerTitle.trim().isEmpty()) {
                signerTitle = "Technician";
            }
            String signerName = SessionManager.getName(context);
            if (signerName == null) {
                signerName = "";
            }
            ReportPdfSignatureSection.addTechnicianAndCustomerSignOff(
                    document, context, signerTitle, signerName.trim(), technicianSignatureUri);

            document.close();
            byte[] stampPw = (ownerPassword != null && !ownerPassword.trim().isEmpty())
                    ? ownerPassword.trim().getBytes() : null;
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), stampPw);

            if (persistAndFinish) {
                ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(context);
                dbHelper.insertBirdQuote(
                        quoteNumber,
                        currentDate,
                        address,
                        quoteDescription,
                        grandTotalInclVat,
                        userEmail,
                        mobileNumber
                );

                Toast.makeText(context, "Quotation PDF Generated and Saved Successfully!", Toast.LENGTH_SHORT).show();

                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).finish();
                }
            }

            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error Creating Bird Quotation PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sanitize a string for use in filenames: spaces to underscore, remove characters invalid on file systems.
     */
    public static String sanitizeFilenamePart(String part) {
        if (part == null) return "";
        String s = part.trim().replaceAll("\\s+", "_");
        s = s.replaceAll("[\\\\/:*?\"<>|]", "");
        return s.isEmpty() ? "unknown" : s;
    }

    /**
     * Generate a catalog-driven quotation PDF (same style as generic quote).
     * Supports one or two lines; filename CustomerName_IssueKey.pdf or CustomerName_Key1_Key2.pdf.
     */
    public static File generateCatalogQuotation(
            String customerName, String address, String dateStr,
            SalesCatalog.SalesItem item, double basePrice,
            SalesCatalog.SalesItem item2, double basePrice2,
            String additionalInfo,
            String userEmail, String mobileNumber, String ownerPassword,
            Context context) {
        return generateCatalogQuotation(customerName, address, dateStr, item, basePrice, item2, basePrice2,
                additionalInfo, userEmail, mobileNumber, ownerPassword, context, null, true, VAT_RATE_STANDARD);
    }

    public static File generateCatalogQuotation(
            String customerName, String address, String dateStr,
            SalesCatalog.SalesItem item, double basePrice,
            SalesCatalog.SalesItem item2, double basePrice2,
            String additionalInfo,
            String userEmail, String mobileNumber, String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish) {
        return generateCatalogQuotation(customerName, address, dateStr, item, basePrice, item2, basePrice2,
                additionalInfo, userEmail, mobileNumber, ownerPassword, context, outputDirectory,
                persistAndFinish, VAT_RATE_STANDARD);
    }

    public static File generateCatalogQuotation(
            String customerName, String address, String dateStr,
            SalesCatalog.SalesItem item, double basePrice,
            SalesCatalog.SalesItem item2, double basePrice2,
            String additionalInfo,
            String userEmail, String mobileNumber, String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish,
            double vatRate) {

        File quotesFolder = outputDirectory != null
                ? outputDirectory
                : new File(context.getExternalFilesDir(null), TenantBranding.quotesFolderName(context));
        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        String safeName = sanitizeFilenamePart(customerName);
        String pdfFileName = safeName + "_Quotation.pdf";
        File pdfFile = new File(quotesFolder, pdfFileName);

        double base1 = basePrice;
        double vat1 = Math.round(base1 * vatRate * 100.0) / 100.0;
        double lineTotal1 = Math.round((base1 + vat1) * 100.0) / 100.0;
        boolean hasSecondLine = item2 != null && basePrice2 > 0;
        double base2 = hasSecondLine ? basePrice2 : 0;
        double vat2 = hasSecondLine ? Math.round(base2 * vatRate * 100.0) / 100.0 : 0;
        double lineTotal2 = hasSecondLine ? Math.round((base2 + vat2) * 100.0) / 100.0 : 0;
        double grandTotal = lineTotal1 + lineTotal2;

        String editableBreakdown = additionalInfo != null ? additionalInfo.trim() : "";
        // Quote breakdown: editable field overrides catalog text for the primary line
        String quoteBreakdown1 = !editableBreakdown.isEmpty()
                ? editableBreakdown
                : (item != null ? item.getQuoteBreakdown() : "");
        String quoteBreakdown2 = hasSecondLine && item2 != null ? item2.getQuoteBreakdown() : "";
        // Description section (under Quote Breakdown): short text from sales.json "description" only
        String shortDesc = "";
        if (item != null && item.getDescription() != null && !item.getDescription().trim().isEmpty()) shortDesc = item.getDescription().trim();
        if (shortDesc.isEmpty() && hasSecondLine && item2 != null && item2.getDescription() != null && !item2.getDescription().trim().isEmpty()) shortDesc = item2.getDescription().trim();
        if (shortDesc.isEmpty() && item != null) shortDesc = item.getDisplayName();
        // Table line labels: display name only (description is shown once in Description section above)
        String line1Label = item != null ? item.getDisplayName() : "";
        String line2Label = (hasSecondLine && item2 != null) ? item2.getDisplayName() : "";

        WriterProperties writerProperties = new WriterProperties();
        writerProperties.setFullCompressionMode(true);
        if (ownerPassword != null && !ownerPassword.trim().isEmpty()) {
            writerProperties.setStandardEncryption(
                    null,
                    ownerPassword.trim().getBytes(),
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
            );
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200);

            float[] headerWidths = {1, 1};
            Table headerTable = new Table(headerWidths).setWidth(UnitValue.createPercentValue(100));
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER);
            leftCell.add(logo);
            leftCell.add(new Paragraph("\n" + TenantBranding.companyName(context)).setBold().setFontSize(16));
            leftCell.add(new Paragraph("Mobile: " + (mobileNumber != null ? mobileNumber : "")).setFontSize(14));
            leftCell.add(new Paragraph("Email: " + (userEmail != null ? userEmail : "")).setFontSize(14));
            leftCell.add(new Paragraph("Website: " + TenantBranding.companyWebsiteShort(context)).setFontSize(14));
            headerTable.addCell(leftCell);

            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + dateStr).setFontSize(14).setBold());
            rightCell.add(new Paragraph("Customer: " + customerName).setFontSize(14).setBold());
            rightCell.add(new Paragraph("\nCustomer Address:").setBold());
            rightCell.add(new Paragraph(address != null ? address : "").setFontSize(14));
            headerTable.addCell(rightCell);

            document.add(headerTable);

            document.add(new Paragraph("\nQuote Breakdown:").setFontSize(16).setBold().setUnderline());
            if (quoteBreakdown1 != null && !quoteBreakdown1.isEmpty()) {
                document.add(new Paragraph(quoteBreakdown1).setFontSize(14));
            }
            if (hasSecondLine && quoteBreakdown2 != null && !quoteBreakdown2.isEmpty()) {
                document.add(new Paragraph(quoteBreakdown2).setFontSize(14));
            }

            document.add(new Paragraph("\nDescription:").setFontSize(16).setBold().setUnderline());
            if (shortDesc != null && !shortDesc.isEmpty()) {
                document.add(new Paragraph(shortDesc).setFontSize(14));
            }
            boolean usedEditableAsBreakdown = !editableBreakdown.isEmpty();
            if (additionalInfo != null && !additionalInfo.trim().isEmpty() && !usedEditableAsBreakdown) {
                document.add(new Paragraph("\nAdditional information:").setFontSize(14).setBold());
                document.add(new Paragraph(additionalInfo.trim()).setFontSize(14));
            }

            float[] columnWidths = {300f, 100f, 100f, 100f};
            Table table = new Table(columnWidths).setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Line Total (€)").setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph(formatVatColumnHeader(vatRate)).setBold()));
            table.addHeaderCell(new Cell().add(new Paragraph("Total (€)").setBold()));

            table.addCell(new Cell().add(new Paragraph(line1Label)));
            table.addCell(new Cell().add(new Paragraph(formatEuroAmount(base1))));
            table.addCell(new Cell().add(new Paragraph(formatEuroAmount(vat1))));
            table.addCell(new Cell().add(new Paragraph(formatEuroAmount(lineTotal1))));

            if (hasSecondLine) {
                table.addCell(new Cell().add(new Paragraph(line2Label)));
                table.addCell(new Cell().add(new Paragraph(formatEuroAmount(base2))));
                table.addCell(new Cell().add(new Paragraph(formatEuroAmount(vat2))));
                table.addCell(new Cell().add(new Paragraph(formatEuroAmount(lineTotal2))));
            }

            document.add(table);

            document.add(new Paragraph("\nPayment Summary:").setFontSize(18).setBold().setUnderline());
            document.add(new Paragraph("Total Payment: " + formatEuroAmount(grandTotal)).setFontSize(16).setBold());
            document.add(new Paragraph("Note: Payment to be made on initial visit.").setFontSize(12).setItalic());

            document.close();
            byte[] stampPwCat = (ownerPassword != null && !ownerPassword.trim().isEmpty())
                    ? ownerPassword.trim().getBytes() : null;
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), stampPwCat);

            if (persistAndFinish) {
                String quoteNumber = "Catalog-" + safeName + "_Quotation";
                String dbDescription = shortDesc != null ? shortDesc : (hasSecondLine ? (line1Label + " | " + line2Label) : line1Label);
                ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(context);
                dbHelper.insertBirdQuote(quoteNumber, dateStr, address != null ? address : "", dbDescription, grandTotal, userEmail != null ? userEmail : "", mobileNumber != null ? mobileNumber : "");

                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).finish();
                }
            }

            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error creating quotation PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Payment summary table: totals derived from line items (ex-VAT base, VAT, incl. VAT, optional deposit rows).
     */
    private static void appendCustomQuotationPaymentSummary(
            Document document,
            double baseTotalExclVat,
            double vatTotal,
            double grandTotalInclVat,
            double vatRate,
            int depositPercent) {
        document.add(new Paragraph("\nPayment Summary").setFontSize(18).setBold().setUnderline());

        float[] summaryWidths = {3f, 1.5f};
        Table summaryTable = new Table(summaryWidths).setWidth(UnitValue.createPercentValue(100));

        summaryTable.addHeaderCell(new Cell().add(new Paragraph("Item").setBold()));
        summaryTable.addHeaderCell(new Cell().add(new Paragraph("Amount").setBold()));

        summaryTable.addCell(new Cell().add(new Paragraph("Total project cost excluding VAT")));
        summaryTable.addCell(new Cell().add(new Paragraph(formatEuroAmount(baseTotalExclVat))));

        summaryTable.addCell(new Cell().add(new Paragraph(formatVatSummaryLabel(vatRate))));
        summaryTable.addCell(new Cell().add(new Paragraph(formatEuroAmount(vatTotal))));

        summaryTable.addCell(new Cell().add(new Paragraph("Total project cost including VAT").setBold()));
        summaryTable.addCell(new Cell().add(new Paragraph(formatEuroAmount(grandTotalInclVat)).setBold()));

        if (depositPercent == 30 || depositPercent == 50) {
            double rate = depositPercent / 100.0;
            double depositValue = Math.round(grandTotalInclVat * rate * 100.0) / 100.0;
            double balanceValue = Math.round((grandTotalInclVat - depositValue) * 100.0) / 100.0;
            summaryTable.addCell(new Cell().add(new Paragraph(
                    depositPercent + "% deposit required prior to commencement").setBold()));
            summaryTable.addCell(new Cell().add(new Paragraph(formatEuroAmount(depositValue)).setBold()));
            summaryTable.addCell(new Cell().add(new Paragraph(
                    "Remaining balance due upon practical completion").setBold()));
            summaryTable.addCell(new Cell().add(new Paragraph(formatEuroAmount(balanceValue)).setBold()));
        }

        document.add(summaryTable);
    }

    private static String formatVatSummaryLabel(double vatRate) {
        if (Math.abs(vatRate - VAT_RATE_REDUCED) < 0.001) {
            return "VAT 23%";
        }
        return "VAT 13.5%";
    }

    private static String formatEuroAmount(double amount) {
        return CurrencyFormatter.formatEuro(amount);
    }

    private static void appendCustomQuotationAdditionalWorksTerms(Document document) {
        document.add(new Paragraph("\n"));
        document.add(new Paragraph(CUSTOM_QUOTE_ADDITIONAL_WORKS_INTRO).setFontSize(10));
        document.add(new Paragraph(CUSTOM_QUOTE_ADDITIONAL_WORKS_DETAIL).setFontSize(10).setMarginTop(6));
    }

    private static String formatVatColumnHeader(double vatRate) {
        if (Math.abs(vatRate - VAT_RATE_REDUCED) < 0.001) {
            return "VAT (23%) (€)";
        }
        return "VAT (13.5%) (€)";
    }

    private static void appendQuotationImages(Document document, Context context, List<Uri> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) return;
        document.add(new Paragraph("\n"));
        for (int i = 0; i < imageUris.size(); i++) {
            Uri uri = imageUris.get(i);
            if (uri == null) continue;
            try {
                document.add(new Paragraph("Image " + (i + 1)).setFontSize(12).setBold());
                ImageData imageData = ImageDataFactory.create(
                        context.getContentResolver().openInputStream(uri).readAllBytes());
                Image image = new Image(imageData)
                        .scaleToFit(300, 300)
                        .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
                document.add(image);
            } catch (IOException e) {
                Toast.makeText(context, "Error loading image: " + uri, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
