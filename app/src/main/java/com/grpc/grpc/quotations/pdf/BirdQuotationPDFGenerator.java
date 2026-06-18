package com.grpc.grpc.quotations.pdf;

import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.data.ReportDatabaseHelper;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;
import com.grpc.grpc.quotations.model.BirdMaterialItem;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * BirdQuotationPDFGenerator.java
 *
 * This class generates a PDF quotation for bird control services.
 * It takes customer details, a list of items, and pricing details,
 * formats them into a structured PDF, and saves the file.
 * The generated PDF includes a company logo, quote number, date,
 * customer details, itemized pricing, and payment instructions.
 *
 * Features:
 * - Generates a structured PDF with customer and quotation details.
 * - Calculates VAT (13.5%) and total payment values.
 * - Saves the PDF file to a dedicated folder.
 * - Stores the quotation details in the local database.
 * - Displays success or error messages to the user.
 *
 * Author: GRPC
 */

public class BirdQuotationPDFGenerator {

    /**
     * Result holder for a dual Bird Quotation generation (customer + office).
     */
    public static class BirdQuotationResult {
        private final File customerPdf;
        private final File officePdf;
        private final double grandTotal;
        private final String quoteNumber;
        private final String date;

        public BirdQuotationResult(File customerPdf, File officePdf, double grandTotal, String quoteNumber, String date) {
            this.customerPdf = customerPdf;
            this.officePdf = officePdf;
            this.grandTotal = grandTotal;
            this.quoteNumber = quoteNumber;
            this.date = date;
        }

        public File getCustomerPdf() {
            return customerPdf;
        }

        public File getOfficePdf() {
            return officePdf;
        }

        public double getGrandTotal() {
            return grandTotal;
        }

        public String getQuoteNumber() {
            return quoteNumber;
        }

        public String getDate() {
            return date;
        }
    }

    /** Sanitize company/customer name for use in PDF filename: trim, replace spaces with underscore, remove invalid path chars. */
    private static String sanitizeNameForFilename(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll("\\s+", "_");
        return s.replaceAll("[\\\\/:*?\"<>|]", "");
    }

    /**
     * Generate TWO PDFs (customer + office) for Bird Quotation using a shared data set.
     *
     * Customer PDF hides prices in the materials table; office PDF shows all pricing.
     * Both share the same quote number and totals.
     */
    public static BirdQuotationResult generateBirdQuotationPair(
            String companyName,
            String address,
            String quoteDescription,
            List<String> descriptions,
            List<Double> lineTotals,
            List<BirdMaterialItem> materials,
            String userEmail,
            String mobileNumber,
            int depositPercent,
            double vatRate,
            List<Uri> imageUris,
            String ownerPassword,
            Context context
    ) {
        return generateBirdQuotationPair(
                companyName,
                address,
                quoteDescription,
                descriptions,
                lineTotals,
                materials,
                userEmail,
                mobileNumber,
                depositPercent,
                vatRate,
                imageUris,
                ownerPassword,
                context,
                null,
                true,
                null
        );
    }

    /**
     * Preview-capable overload: can write to an arbitrary output directory and optionally skip DB insert + finish().
     *
     * @param outputDirectory when non-null, the PDFs are written here instead of the permanent app folder
     * @param persistAndFinish when false, skips DB insert and does NOT finish the calling activity
     * @param quoteRandomNumOverride when non-null, keeps the quote number deterministic across preview/confirm
     */
    public static BirdQuotationResult generateBirdQuotationPair(
            String companyName,
            String address,
            String quoteDescription,
            List<String> descriptions,
            List<Double> lineTotals,
            List<BirdMaterialItem> materials,
            String userEmail,
            String mobileNumber,
            int depositPercent,
            double vatRate,
            List<Uri> imageUris,
            String ownerPassword,
            Context context,
            File outputDirectory,
            boolean persistAndFinish,
            Integer quoteRandomNumOverride
    ) {

        File quotesFolder = outputDirectory != null
                ? outputDirectory
                : new File(context.getExternalFilesDir(null), TenantBranding.quotesFolderName(context));
        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Generate a random 4-digit number for the quote number (company abbrev from branding)
        int randomNum = quoteRandomNumOverride != null
                ? quoteRandomNumOverride
                : 1000 + new Random().nextInt(9000);
        String abbrev = context != null ? TenantBranding.companyAbbrev(context) : "GRPC";
        String quoteNumber = abbrev + "-" + randomNum;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());

        // Filenames: name_quotenumber.pdf (customer) and name_quotenumber_office.pdf (office)
        String namePart = sanitizeNameForFilename(companyName);
        if (namePart.isEmpty()) namePart = "BirdQuote";
        String customerFileName = namePart + "_" + quoteNumber + ".pdf";
        String officeFileName = namePart + "_" + quoteNumber + "_office.pdf";
        File customerPdf = new File(quotesFolder, customerFileName);
        File officePdf = new File(quotesFolder, officeFileName);

        // Compute totals once for both PDFs.
        // Per requirements, the materials table is descriptive only and MUST NOT contribute to cost.
        double serviceBaseTotal = 0;
        if (lineTotals != null) {
            for (Double d : lineTotals) {
                if (d != null) serviceBaseTotal += d;
            }
        }
        // Office-only informational total: sum of unit prices only (excl. VAT); not added into grandTotal.
        double materialsBaseTotal = 0;
        if (materials != null) {
            for (BirdMaterialItem item : materials) {
                if (item != null) {
                    materialsBaseTotal += Math.max(0, item.getUnitPrice());
                }
            }
        }
        double vatAmountTotal = serviceBaseTotal * vatRate;
        double grandTotal = serviceBaseTotal + vatAmountTotal;

        try {
            // Office copy: full pricing
            createSingleBirdQuotationPdf(
                    officePdf,
                    companyName,
                    address,
                    quoteDescription,
                    descriptions,
                    lineTotals,
                    materials,
                    userEmail,
                    mobileNumber,
                    depositPercent,
                    vatRate,
                    false,
                    quoteNumber,
                    currentDate,
                    ownerPassword,
                    imageUris,
                    serviceBaseTotal,
                    materialsBaseTotal,
                    vatAmountTotal,
                    grandTotal,
                    context
            );

            // Customer copy: hide prices in materials table
            createSingleBirdQuotationPdf(
                    customerPdf,
                    companyName,
                    address,
                    quoteDescription,
                    descriptions,
                    lineTotals,
                    materials,
                    userEmail,
                    mobileNumber,
                    depositPercent,
                    vatRate,
                    true,
                    quoteNumber,
                    currentDate,
                    ownerPassword,
                    imageUris,
                    serviceBaseTotal,
                    materialsBaseTotal,
                    vatAmountTotal,
                    grandTotal,
                    context
            );

            // Save to database (single row for the quote; totals include VAT and materials)
            if (persistAndFinish) {
                ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(context);
                String safeDescription = quoteDescription != null ? quoteDescription : "";
                if (companyName != null && !companyName.trim().isEmpty()) {
                    safeDescription = companyName.trim() + " - " + safeDescription;
                }
                dbHelper.insertBirdQuote(
                        quoteNumber,
                        currentDate,
                        address != null ? address : "",
                        safeDescription,
                        grandTotal,
                        userEmail != null ? userEmail : "",
                        mobileNumber != null ? mobileNumber : ""
                );

                Toast.makeText(context, "Bird Quotation PDFs Generated and Saved Successfully!", Toast.LENGTH_SHORT).show();

                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).finish();
                }
            }

            return new BirdQuotationResult(customerPdf, officePdf, grandTotal, quoteNumber, currentDate);

        } catch (IOException e) {
            if (persistAndFinish) {
                Toast.makeText(context, "Error Creating Bird Quotation PDF", Toast.LENGTH_SHORT).show();
            }
            e.printStackTrace();
            return null;
        }
    }
    /**
     * Generates a PDF quotation for bird control services.
     * It formats the document, adds customer details, line items,
     * pricing, VAT calculations, and a payment summary.
     *
     * @param address         The customer's address.
     * @param quoteDescription A description of the bird control services quoted.
     * @param descriptions    A list of line item descriptions.
     * @param lineTotals      A list of corresponding line item prices.
     * @param userEmail              Company email (from users DB).
     * @param mobileNumber           Company mobile (from users DB).
     * @param include30PercentDeposit If true, show 30% due before job and remaining; if false, total payment only.
     * @param context                The Android context for file storage and UI feedback.
     * @return The generated PDF file.
     */
    public static File generateBirdQuotation(
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, boolean include30PercentDeposit, Context context) {
        return generateBirdQuotation(address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber, include30PercentDeposit, null, context);
    }

    public static File generateBirdQuotation(
            String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber, boolean include30PercentDeposit,
            String ownerPassword,
            Context context) {

        File quotesFolder = new File(context.getExternalFilesDir(null), TenantBranding.quotesFolderName(context));
        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Generate a random 4-digit number for the quote number (company abbrev from branding)
        int randomNum = 1000 + new Random().nextInt(9000);
        String abbrev = context != null ? TenantBranding.companyAbbrev(context) : "GRPC";
        String quoteNumber = abbrev + "-" + randomNum;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String namePart = "BirdQuote";
        String pdfFileName = namePart + "_" + quoteNumber + ".pdf";
        File pdfFile = new File(quotesFolder, pdfFileName);

        double grandTotal = 0;

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

            // Watermark on every page; footer only on last page (added at end below)
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new BirdWatermarkOnlyHandler(context));

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

            // Technician line from user profile: "Name -- Title"
            String techNameLegacy = SessionManager.getName(context);
            String techTitleLegacy = SessionManager.getTitle(context);
            StringBuilder techLineLegacy = new StringBuilder();
            if (techNameLegacy != null && !techNameLegacy.trim().isEmpty()) {
                techLineLegacy.append(techNameLegacy.trim());
            }
            if (techTitleLegacy != null && !techTitleLegacy.trim().isEmpty()) {
                if (techLineLegacy.length() > 0) techLineLegacy.append(" -- ");
                techLineLegacy.append(techTitleLegacy.trim());
            }
            if (techLineLegacy.length() > 0) {
                leftCell.add(new Paragraph(techLineLegacy.toString()).setFontSize(14));
            }

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
            document.add(new Paragraph("\nQuote Description:").setFontSize(16).setBold().setUnderline());
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
                table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(lineTotal))));
                table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(vatAmount))));
                table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(total))));

                grandTotal += total;
            }

            document.add(table);

            // Payment Summary
            document.add(new Paragraph("\nPayment Summary:").setFontSize(18).setBold().setUnderline());
            document.add(new Paragraph("Total Payment: " + CurrencyFormatter.formatEuro(grandTotal)).setFontSize(16).setBold());
            if (include30PercentDeposit) {
                document.add(new Paragraph("Payment Due (30%): " + CurrencyFormatter.formatEuro(grandTotal * 0.3)).setFontSize(16).setBold());
                document.add(new Paragraph("Note: 30% payment is due before the job commences.").setFontSize(12).setItalic());
                document.add(new Paragraph("Remaining Payment: " + CurrencyFormatter.formatEuro(grandTotal * 0.7)).setFontSize(16).setBold());
                document.add(new Paragraph("Note: The remaining balance must be paid upon completion of the works.").setFontSize(12).setItalic());
            }

            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context),
                    (ownerPassword != null && !ownerPassword.trim().isEmpty()) ? ownerPassword.trim().getBytes() : null);

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

            Toast.makeText(context, "Bird Quotation PDF Generated and Saved Successfully!", Toast.LENGTH_SHORT).show();

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

    /**
     * Internal helper to render a single Bird Quotation PDF (either customer or office copy).
     */
    private static void createSingleBirdQuotationPdf(
            File pdfFile,
            String companyName,
            String address,
            String quoteDescription,
            List<String> descriptions,
            List<Double> lineTotals,
            List<BirdMaterialItem> materials,
            String userEmail,
            String mobileNumber,
            int depositPercent,
            double vatRate,
            boolean hideMaterialPrices,
            String quoteNumber,
            String currentDate,
            String ownerPassword,
            List<Uri> imageUris,
            double serviceBaseTotal,
            double materialsBaseTotal,
            double vatAmountTotal,
            double grandTotal,
            Context context
    ) throws IOException {

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

            // Watermark on every page; footer only on last page (added at end of content below)
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new BirdWatermarkOnlyHandler(context));

            // Header: logo + company branding on left, quote/customer details on right
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200);

            float[] headerWidths = {1, 1};
            Table headerTable = new Table(headerWidths).setWidth(UnitValue.createPercentValue(100));

            Cell leftCell = new Cell().setBorder(Border.NO_BORDER);
            leftCell.add(logo);
            leftCell.add(new Paragraph("\n" + TenantBranding.companyName(context)).setBold().setFontSize(16));

            // Technician line from user profile: "Name -- Title"
            String techName = SessionManager.getName(context);
            String techTitle = SessionManager.getTitle(context);
            StringBuilder techLine = new StringBuilder();
            if (techName != null && !techName.trim().isEmpty()) {
                techLine.append(techName.trim());
            }
            if (techTitle != null && !techTitle.trim().isEmpty()) {
                if (techLine.length() > 0) techLine.append(" -- ");
                techLine.append(techTitle.trim());
            }
            if (techLine.length() > 0) {
                leftCell.add(new Paragraph(techLine.toString()).setFontSize(14));
            }

            leftCell.add(new Paragraph("Mobile: " + (mobileNumber != null ? mobileNumber : "")).setFontSize(14));
            leftCell.add(new Paragraph("Email: " + (userEmail != null ? userEmail : "")).setFontSize(14));
            leftCell.add(new Paragraph("Website: " + TenantBranding.companyWebsiteShort(context)).setFontSize(14));
            headerTable.addCell(leftCell);

            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + currentDate).setFontSize(14).setBold());
            rightCell.add(new Paragraph("Quote Number: " + quoteNumber).setFontSize(14).setBold());
            if (companyName != null && !companyName.trim().isEmpty()) {
                rightCell.add(new Paragraph("Customer: " + companyName.trim()).setFontSize(14).setBold());
            }
            rightCell.add(new Paragraph("\nCustomer Address:").setBold());
            rightCell.add(new Paragraph(address != null ? address : "").setFontSize(14));
            headerTable.addCell(rightCell);

            document.add(headerTable);

            // 1. Quote breakdown (long description text) first
            document.add(new Paragraph("\nQuote Description:").setFontSize(16).setBold().setUnderline());
            if (quoteDescription != null && !quoteDescription.trim().isEmpty()) {
                document.add(new Paragraph(quoteDescription.trim()).setFontSize(14));
            }

            // 2. Materials Required (descriptive only, no cost impact)
            if (materials != null && !materials.isEmpty()) {
                document.add(new Paragraph("\nMaterials Required:").setFontSize(16).setBold().setUnderline());

                if (hideMaterialPrices) {
                    // Customer PDF: materials with only Material and Qty (whatever is written), no cost
                    float[] matCols = {3f, 1f};
                    Table matTable = new Table(matCols).setWidth(UnitValue.createPercentValue(100));
                    matTable.addHeaderCell(new Cell().add(new Paragraph("Material").setBold()));
                    matTable.addHeaderCell(new Cell().add(new Paragraph("Qty").setBold()));

                    for (BirdMaterialItem item : materials) {
                        if (item == null) continue;
                        matTable.addCell(new Cell().add(new Paragraph(item.getMaterialName() != null ? item.getMaterialName() : "")));
                        matTable.addCell(new Cell().add(new Paragraph(item.getQuantityDisplay())));
                    }

                    document.add(matTable);
                } else {
                    // Office PDF: materials table with Material, Qty, Unit Price (no Line Total)
                    float[] matCols = {3f, 1f, 1f};
                    Table matTable = new Table(matCols).setWidth(UnitValue.createPercentValue(100));
                    matTable.addHeaderCell(new Cell().add(new Paragraph("Material").setBold()));
                    matTable.addHeaderCell(new Cell().add(new Paragraph("Qty").setBold()));
                    matTable.addHeaderCell(new Cell().add(new Paragraph("Unit Price (€)").setBold()));

                    for (BirdMaterialItem item : materials) {
                        if (item == null) continue;
                        String name = item.getMaterialName() != null ? item.getMaterialName() : "";
                        matTable.addCell(new Cell().add(new Paragraph(name)));
                        matTable.addCell(new Cell().add(new Paragraph(item.getQuantityDisplay())));
                        matTable.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(item.getUnitPrice()))));
                    }

                    document.add(matTable);
                }
            }

            // 3. Description (priced services table)
            if (descriptions != null && lineTotals != null && !descriptions.isEmpty() && descriptions.size() == lineTotals.size()) {
                float[] columnWidths = {300f, 100f, 100f, 100f};
                Table table = new Table(columnWidths).setWidth(UnitValue.createPercentValue(100));

                String vatHeader = String.format(Locale.ROOT, "VAT (%.1f%%) (€)", vatRate * 100.0);

                table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("Line Total (€)").setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph(vatHeader).setBold()));
                table.addHeaderCell(new Cell().add(new Paragraph("Total (€)").setBold()));

                for (int i = 0; i < descriptions.size(); i++) {
                    String desc = descriptions.get(i) != null ? descriptions.get(i) : "";
                    double base = lineTotals.get(i) != null ? lineTotals.get(i) : 0.0;
                    double vat = base * vatRate;
                    double total = base + vat;

                    table.addCell(new Cell().add(new Paragraph(desc)));
                    table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(base))));
                    table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(vat))));
                    table.addCell(new Cell().add(new Paragraph(CurrencyFormatter.formatEuro(total))));
                }

                document.add(table);
            }

            // 4. Total (payment summary under description)
            document.add(new Paragraph("\nPayment Summary:").setFontSize(18).setBold().setUnderline());
            // Office copy only: combined materials cost (informational, excl. VAT; not part of grand total)
            if (!hideMaterialPrices && materialsBaseTotal > 0) {
                document.add(new Paragraph("Materials Cost (office, excl. VAT): " +
                        CurrencyFormatter.formatEuro(materialsBaseTotal)).setFontSize(14));
            }
            document.add(new Paragraph("Services Total (excl. VAT): " +
                    CurrencyFormatter.formatEuro(serviceBaseTotal)).setFontSize(14));
            document.add(new Paragraph(
                    String.format(Locale.ROOT, "VAT (%.1f%%): %s", vatRate * 100.0, CurrencyFormatter.formatEuro(vatAmountTotal))
            ).setFontSize(14));
            document.add(new Paragraph("Total Payment: " +
                    CurrencyFormatter.formatEuro(grandTotal)).setFontSize(16).setBold());

            if (depositPercent == 30 || depositPercent == 50) {
                double rate = depositPercent / 100.0;
                double depositValue = grandTotal * rate;
                double balanceValue = grandTotal - depositValue;
                document.add(new Paragraph("Deposit Due: " + depositPercent + "% (" +
                        CurrencyFormatter.formatEuro(depositValue) + ")").setFontSize(14).setBold());
                document.add(new Paragraph("Balance Due: " +
                        CurrencyFormatter.formatEuro(balanceValue)).setFontSize(14));
            }

            // Optional images: include in office copy (hideMaterialPrices == false) for internal use
            if (!hideMaterialPrices && imageUris != null && !imageUris.isEmpty()) {
                for (int i = 0; i < imageUris.size(); i++) {
                    Uri uri = imageUris.get(i);
                    if (uri == null) continue;
                    try {
                        document.add(new Paragraph("Image " + (i + 1)).setFontSize(12).setBold());
                        byte[] compressed = compressImageUri(context, uri);
                        ImageData imageData = ImageDataFactory.create(compressed);
                        Image image = new Image(imageData)
                                .scaleToFit(300, 300)
                                .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
                        document.add(image);
                    } catch (Exception e) {
                        Toast.makeText(context, "Error loading image: " + uri.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            document.close();
        }
        PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context),
                (ownerPassword != null && !ownerPassword.trim().isEmpty()) ? ownerPassword.trim().getBytes() : null);
    }

    private static byte[] compressImageUri(Context context, Uri uri) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        try (InputStream boundsStream = context.getContentResolver().openInputStream(uri)) {
            if (boundsStream == null) {
                throw new IOException("Unable to open image: " + uri);
            }
            BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
        }

        int longestSide = Math.max(boundsOptions.outWidth, boundsOptions.outHeight);
        int sampleSize = 1;
        while (longestSide / sampleSize > 1024) {
            sampleSize *= 2;
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream imageStream = context.getContentResolver().openInputStream(uri)) {
            if (imageStream == null) {
                throw new IOException("Unable to open image: " + uri);
            }
            bitmap = BitmapFactory.decodeStream(imageStream, null, decodeOptions);
        }
        if (bitmap == null) {
            throw new IOException("Unable to decode image: " + uri);
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // iText does not support WebP. JPEG is universally supported.
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)) {
                throw new IOException("Unable to compress image: " + uri);
            }
            return output.toByteArray();
        } finally {
            bitmap.recycle();
        }
    }

    /** Watermark only (no footer). Footer is added once at end of document. */
    private static class BirdWatermarkOnlyHandler implements IEventHandler {
        private final Context context;

        BirdWatermarkOnlyHandler(Context context) {
            this.context = context;
        }

        @Override
        public void handleEvent(com.itextpdf.kernel.events.Event event) {
            PdfDocumentEvent pdfEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = pdfEvent.getDocument();
            PdfPage page = pdfEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();
            try {
                Document doc = new Document(pdfDoc);
                int watermarkResourceId = context.getResources().getIdentifier("bk", "drawable", context.getPackageName());
                ImageData watermarkData = ImageDataFactory.create(context.getResources().openRawResource(watermarkResourceId).readAllBytes());
                Image watermark = new Image(watermarkData)
                        .scaleToFit(500, 500)
                        .setFixedPosition(pageWidth / 4, pageHeight / 4);
                watermark.setOpacity(0.1f);
                doc.add(watermark);
            } catch (Exception e) {
                Toast.makeText(context, "Error adding watermark!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
