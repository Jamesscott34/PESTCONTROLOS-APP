package com.grpc.grpc.contracts.pdf;

import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;

import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * ContractListPDFGenerator
 *
 * Generates a structured PDF containing a list of contracts exactly as they
 * appear in the contracts screen. Used for exporting all visible contracts
 * so they can be emailed/printed for office/admin use.
 */
public class ContractListPDFGenerator {

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generateContractsListPDF(String title, List<Map<String, Object>> contracts, Context context) {
        // Store in the same local reports folder used by generated reports.
        File pdfFolder = new File(context.getExternalFilesDir(null), TenantBranding.reportsFolderName(context));
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String safeTitle = title.replaceAll("[^a-zA-Z0-9]", "_");
        String pdfFileName = "ContractsList_" + safeTitle + "_" + currentDate + ".pdf";
        File pdfFile = new File(pdfFolder, pdfFileName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            // Reuse existing watermark/footer handler
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            addPdfContent(document, title, contracts, context);

            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), null);
            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error creating contracts PDF!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }

    private static void addPdfContent(Document document, String title, List<Map<String, Object>> contracts, Context context) {
        try {
            // Logo at top (same approach as behinds list)
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            if (logoResourceId != 0) {
                ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
                Image logo = new Image(logoData)
                        .scaleToFit(200, 200)
                        .setFixedPosition(200, 750);
                document.add(logo);
            }

            // Spacer under logo
            document.add(new Paragraph("\n\n\n\n\n"));

            // Main title
            Paragraph mainTitle = new Paragraph("CONTRACTS LIST REPORT")
                    .setFontSize(24)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20);
            document.add(mainTitle);

            // Separator
            LineSeparator titleSeparator = new LineSeparator(new SolidLine(2f));
            titleSeparator.setMarginTop(10);
            titleSeparator.setMarginBottom(20);
            document.add(titleSeparator);

            // Subtitle (view title, e.g. All Contracts / <Owner> Contracts)
            Paragraph subtitle = new Paragraph("View: " + title)
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(8);
            document.add(subtitle);

            Paragraph countTop = new Paragraph("Number of Contracts: " + contracts.size())
                    .setFontSize(14)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(countTop);

            // Date
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Paragraph date = new Paragraph("Generated on: " + sdf.format(new Date()))
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(30);
            document.add(date);

            // Separator
            LineSeparator separator = new LineSeparator(new SolidLine(1f));
            separator.setMarginBottom(20);
            document.add(separator);

            // Summary
            Paragraph summary = new Paragraph("Total Contracts in this list: " + contracts.size())
                    .setFontSize(14)
                    .setBold()
                    .setMarginBottom(20);
            document.add(summary);

            // Each contract
            int index = 1;
            for (Map<String, Object> contract : contracts) {
                String owner = firstNonBlank(contract, "owner", "assignedTech", "assignedTechName");
                if (owner.isEmpty()) owner = "N/A";
                String contractKey = firstNonBlank(contract, "contractKey", "assignedTech", "owner");
                if (contractKey.isEmpty()) contractKey = "N/A";
                String name = resolveContractDisplayName(contract);
                String address = firstNonBlank(contract, "address", "Address", "customerAddress", "CustomerAddress");
                if (address.isEmpty()) address = "N/A";
                String contact = firstNonBlank(contract, "contact", "Contact", "phone", "Phone", "customerPhone", "CustomerPhone");
                if (contact.isEmpty()) contact = "N/A";
                String email = firstNonBlank(contract, "email", "Email", "customerEmail", "CustomerEmail");
                if (email.isEmpty()) email = "N/A";
                String lastVisit = firstNonBlank(contract, "lastVisit", "LastVisit");
                if (lastVisit.isEmpty()) lastVisit = "N/A";
                String nextVisit = firstNonBlank(contract, "nextVisit", "NextVisit");
                if (nextVisit.isEmpty()) nextVisit = "N/A";
                String routineCounters = buildCounterDisplayLines(contract, "Routines", "Routine");
                String callOutCounters = buildCounterDisplayLines(contract, "Callout", "Callout");

                Paragraph header = new Paragraph(index + ". " + name + " (" + owner + ")")
                        .setFontSize(16)
                        .setBold()
                        .setMarginTop(15)
                        .setMarginBottom(5);
                document.add(header);

                document.add(new Paragraph("ContractKey: " + contractKey).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📍 Address: " + address).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📞 Contact: " + contact).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📧 Email: " + email).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📅 Last Visit: " + lastVisit).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("⏰ Next Visit: " + nextVisit).setFontSize(12).setMarginBottom(10));
                document.add(new Paragraph("Routine Visits: " + (routineCounters.isEmpty() ? "None recorded" : routineCounters))
                        .setFontSize(12)
                        .setMarginBottom(3));
                document.add(new Paragraph("Call Outs: " + (callOutCounters.isEmpty() ? "None recorded" : callOutCounters))
                        .setFontSize(12)
                        .setMarginBottom(10));

                if (index < contracts.size()) {
                    LineSeparator contractSeparator = new LineSeparator(new SolidLine(0.5f));
                    contractSeparator.setMarginBottom(10);
                    document.add(contractSeparator);
                }

                index++;
            }

            // Footer note
            Paragraph footerNote = new Paragraph(TenantBranding.autoGeneratedNote(context))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30);
            document.add(footerNote);

        } catch (Exception e) {
            Toast.makeText(context, "Error adding content to contracts PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static String resolveContractDisplayName(Map<String, Object> contract) {
        String value = firstNonBlank(contract,
                "name",
                "Name",
                "companyName",
                "Company",
                "CustomerName",
                "customerName",
                "contractName",
                "ContractName",
                "contractKey",
                "address");
        return value.isEmpty() ? "Unknown Contract" : value;
    }

    private static String firstNonBlank(Map<String, Object> contract, String... keys) {
        if (contract == null || keys == null) return "";
        for (String key : keys) {
            Object value = contract.get(key);
            if (value == null) continue;
            String text = value.toString().trim();
            if (!text.isEmpty() && !"N/A".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return "";
    }

    private static String buildCounterDisplayLines(Map<String, Object> contract, String storagePrefix, String displayPrefix) {
        if (contract == null || contract.isEmpty()) return "";

        TreeMap<Integer, String> newestFirst = new TreeMap<>((a, b) -> Integer.compare(b, a));
        for (Map.Entry<String, Object> entry : contract.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(storagePrefix)) continue;
            String suffix = key.substring(storagePrefix.length());
            if (!suffix.matches("\\d{2}")) continue;

            int count = readCounterNumberFromEntry(entry.getValue());
            if (count <= 0) continue;

            int year = 2000 + Integer.parseInt(suffix);
            newestFirst.put(year, displayPrefix + suffix + " - " + count);
        }

        StringBuilder out = new StringBuilder();
        for (String line : newestFirst.values()) {
            if (out.length() > 0) out.append("; ");
            out.append(line);
        }
        return out.toString();
    }

    private static int readCounterNumberFromEntry(Object entryValue) {
        if (entryValue instanceof Map) {
            Object number = ((Map<?, ?>) entryValue).get("number");
            return readCounterNumber(number);
        }
        return readCounterNumber(entryValue);
    }

    private static int readCounterNumber(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
