package com.grpc.grpc.billing.pdf;

import com.grpc.grpc.R;
import com.grpc.grpc.core.BrandingAssets;
import com.grpc.grpc.core.TenantBranding;

import android.content.Context;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.VerticalAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Sales-style customer invoice PDF (flavor logo + company details).
 */
public final class InvoicePdfGenerator {

    private static final DeviceRgb HEADER_GRAY = new DeviceRgb(235, 235, 235);

    private InvoicePdfGenerator() {}

    public static File generate(
            Context context,
            File outputFile,
            String invoiceNumber,
            Date issueDate,
            String customerName,
            String customerEmail,
            String customerAddress,
            String title,
            String description,
            double subtotalAmount,
            double vatRatePercent,
            double vatAmountValue,
            double totalAmount,
            String notes,
            String templateLabel
    ) throws Exception {
        if (context == null || outputFile == null) throw new IllegalArgumentException("context/output");

        String displayDate = new SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(issueDate);
        String invoiceTo = buildInvoiceTo(customerName, customerEmail, customerAddress);
        String lineDescription = buildLineDescription(title, description);

        try (FileOutputStream fos = new FileOutputStream(outputFile);
             PdfWriter writer = new PdfWriter(fos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.setMargins(36, 36, 48, 36);

            float[] headerWidths = {1.4f, 1f};
            Table header = new Table(headerWidths).useAllAvailableWidth();

            Cell companyCell = new Cell().setBorder(Border.NO_BORDER);
            companyCell.add(new Paragraph(TenantBranding.companyName(context)).setBold().setFontSize(13));
            companyCell.add(new Paragraph(TenantBranding.invoiceCompanyAddress(context)).setFontSize(9));
            companyCell.add(new Paragraph(TenantBranding.companyWebsiteShort(context)).setFontSize(9));
            String phone = TenantBranding.invoiceTelephone(context);
            if (phone != null && !phone.trim().isEmpty()) {
                companyCell.add(new Paragraph("Telephone: " + phone.trim()).setFontSize(9));
            }
            String accountsEmail = TenantBranding.invoiceAccountsEmail(context);
            if (accountsEmail != null && !accountsEmail.trim().isEmpty()) {
                companyCell.add(new Paragraph("Email: " + accountsEmail.trim()).setFontSize(9));
            }
            header.addCell(companyCell);

            Cell metaCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
            byte[] logoBytes = BrandingAssets.readTenantLogoPngBytes(context);
            if (logoBytes != null && logoBytes.length > 0) {
                Image logo = new Image(ImageDataFactory.create(logoBytes));
                logo.setWidth(100);
                logo.setHorizontalAlignment(HorizontalAlignment.RIGHT);
                metaCell.add(logo);
            }
            metaCell.add(new Paragraph(context.getString(R.string.invoice_pdf_sales_title))
                    .setBold()
                    .setFontSize(15)
                    .setTextAlignment(TextAlignment.RIGHT));
            metaCell.add(new Paragraph(context.getString(R.string.invoice_pdf_invoice_date, displayDate))
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.RIGHT));
            metaCell.add(new Paragraph(context.getString(R.string.invoice_pdf_due_date, displayDate))
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.RIGHT));
            metaCell.add(new Paragraph(context.getString(R.string.invoice_pdf_invoice_number, invoiceNumber))
                    .setBold()
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.RIGHT));
            header.addCell(metaCell);
            doc.add(header);

            doc.add(new Paragraph(" ").setFontSize(6));
            doc.add(new Paragraph(context.getString(R.string.invoice_pdf_invoice_to)).setBold().setFontSize(10));
            Table toBox = new Table(1).useAllAvailableWidth();
            Cell toCell = new Cell().setBorder(new SolidBorder(ColorConstants.BLACK, 0.75f));
            for (String line : invoiceTo.split("\n")) {
                if (!line.trim().isEmpty()) {
                    toCell.add(new Paragraph(line.trim()).setFontSize(9));
                }
            }
            toBox.addCell(toCell);
            doc.add(toBox);

            doc.add(new Paragraph(" ").setFontSize(8));

            float[] lineCols = {0.7f, 3.2f, 0.9f, 1.1f, 0.8f, 1f};
            Table lines = new Table(lineCols).useAllAvailableWidth();
            addHeaderCell(lines, "Code");
            addHeaderCell(lines, "Description");
            addHeaderCell(lines, "Qty/Hrs");
            addHeaderCell(lines, "Price/Rate");
            addHeaderCell(lines, "VAT %");
            addHeaderCell(lines, "Net");

            lines.addCell(bodyCell(TenantBranding.invoiceLineCode(context)));
            lines.addCell(bodyCell(lineDescription));
            lines.addCell(bodyCell("1.00"));
            lines.addCell(bodyCell(moneyTable(subtotalAmount)));
            lines.addCell(bodyCell(rateTable(vatRatePercent)));
            lines.addCell(bodyCell(moneyTable(subtotalAmount)));
            doc.add(lines);

            doc.add(new Paragraph(" ").setFontSize(10));

            float[] bottomCols = {1.5f, 1f};
            Table bottom = new Table(bottomCols).useAllAvailableWidth();

            Cell vatSummary = new Cell().setBorder(Border.NO_BORDER);
            Table vatTable = new Table(new float[] {1.6f, 1f, 1f}).useAllAvailableWidth();
            addHeaderCell(vatTable, "VAT Rate");
            addHeaderCell(vatTable, "Net");
            addHeaderCell(vatTable, "VAT");
            vatTable.addCell(bodyCell(vatRateLabel(vatRatePercent)));
            vatTable.addCell(bodyCell(moneyEuro(subtotalAmount)));
            vatTable.addCell(bodyCell(moneyEuro(vatAmountValue)));
            vatSummary.add(vatTable);
            bottom.addCell(vatSummary);

            Cell totalsCell = new Cell().setBorder(Border.NO_BORDER);
            Table totals = new Table(1).useAllAvailableWidth();
            totals.addCell(totalRow("Total Net:", moneyTable(subtotalAmount), false));
            totals.addCell(totalRow("Total VAT:", moneyTable(vatAmountValue), false));
            totals.addCell(totalRow("TOTAL:", moneyEuro(totalAmount), true));
            totalsCell.add(totals);
            bottom.addCell(totalsCell);
            doc.add(bottom);

            doc.add(new Paragraph(" ").setFontSize(8));
            doc.add(new Paragraph(context.getString(R.string.invoice_pdf_thank_you)).setFontSize(9));
            doc.add(new Paragraph(TenantBranding.invoicePaymentTerms(context)).setFontSize(8).setItalic());

            if (notes != null && !notes.trim().isEmpty()) {
                doc.add(new Paragraph(" ").setFontSize(4));
                doc.add(new Paragraph(context.getString(R.string.invoice_pdf_notes_header)).setBold().setFontSize(9));
                doc.add(new Paragraph(notes.trim()).setFontSize(8));
            }

            doc.add(new Paragraph(" ").setFontSize(6));
            doc.add(new Paragraph(context.getString(R.string.invoice_pdf_deliver_to)).setBold().setFontSize(10));
            Table deliverBox = new Table(1).useAllAvailableWidth();
            Cell deliverCell = new Cell().setBorder(new SolidBorder(ColorConstants.BLACK, 0.75f));
            for (String line : invoiceTo.split("\n")) {
                if (!line.trim().isEmpty()) {
                    deliverCell.add(new Paragraph(line.trim()).setFontSize(9));
                }
            }
            deliverBox.addCell(deliverCell);
            doc.add(deliverBox);

            doc.add(new Paragraph(" ").setFontSize(14));
            doc.add(new Paragraph(context.getString(R.string.invoice_pdf_company_registration)).setFontSize(7));
            doc.add(new Paragraph(context.getString(R.string.invoice_pdf_vat_registration)).setFontSize(7));
            doc.add(new Paragraph(context.getString(R.string.invoice_pdf_registered_address)).setFontSize(7));
            doc.add(new Paragraph("Page 1 of 1")
                    .setFontSize(7)
                    .setTextAlignment(TextAlignment.RIGHT));
        }

        return outputFile;
    }

    /** Backward-compatible entry for callers still passing formatted money strings. */
    public static File generate(
            Context context,
            File outputFile,
            String invoiceNumber,
            Date issueDate,
            String customerName,
            String customerEmail,
            String title,
            String description,
            String amount,
            String subtotal,
            String vatRate,
            String vatAmount,
            String total,
            String notes,
            String templateLabel
    ) throws Exception {
        double subtotalVal = parseMoney(subtotal);
        double vatRateVal = parseRate(vatRate);
        double vatAmountVal = parseMoney(vatAmount);
        double totalVal = parseMoney(total);
        if (subtotalVal <= 0 && totalVal > 0) subtotalVal = totalVal - vatAmountVal;
        return generate(
                context,
                outputFile,
                invoiceNumber,
                issueDate,
                customerName,
                customerEmail,
                "",
                title,
                description,
                subtotalVal,
                vatRateVal,
                vatAmountVal,
                totalVal,
                notes,
                templateLabel
        );
    }

    private static void addHeaderCell(Table table, String text) {
        table.addHeaderCell(new Cell()
                .add(new Paragraph(text).setBold().setFontSize(8))
                .setBackgroundColor(HEADER_GRAY)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(4));
    }

    private static Cell bodyCell(String text) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(8))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private static Cell totalRow(String label, String value, boolean bold) {
        Cell cell = new Cell()
                .setBackgroundColor(HEADER_GRAY)
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
        Paragraph p = new Paragraph(label + "  " + value).setFontSize(bold ? 10 : 9);
        if (bold) p.setBold();
        cell.add(p.setTextAlignment(TextAlignment.RIGHT));
        return cell;
    }

    private static String buildInvoiceTo(String name, String email, String address) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.trim().isEmpty()) sb.append(name.trim());
        if (address != null && !address.trim().isEmpty()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(address.trim());
        }
        if (email != null && !email.trim().isEmpty()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(email.trim());
        }
        return sb.toString();
    }

    private static String buildLineDescription(String title, String description) {
        String t = title != null ? title.trim() : "";
        String d = description != null ? description.trim() : "";
        if (!t.isEmpty() && !d.isEmpty() && !t.equals(d)) return t + ". " + d;
        return !t.isEmpty() ? t : d;
    }

    private static String vatRateLabel(double rate) {
        String label = rate % 1.0d == 0.0d
                ? String.format(Locale.UK, "%.0f", rate)
                : String.format(Locale.UK, "%.2f", rate).replaceAll("0+$", "").replaceAll("\\.$", "");
        return "Standard " + label + "% (" + label + "%)";
    }

    private static String moneyTable(double value) {
        return String.format(Locale.UK, "%.2f", value);
    }

    private static String moneyEuro(double value) {
        return String.format(Locale.UK, "€ %.2f", value);
    }

    private static String rateTable(double value) {
        return String.format(Locale.UK, "%.2f", value);
    }

    private static double parseMoney(String raw) {
        if (raw == null) return 0;
        String cleaned = raw.replace("EUR", "").replace("€", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static double parseRate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(raw.trim().replace(",", "."));
        } catch (Exception ex) {
            return 0;
        }
    }
}
