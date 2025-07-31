package com.grpc.grpc;

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

/**
 * ContractListPDFGenerator
 *
 * Generates a structured PDF containing a list of contracts exactly as they
 * appear in the contracts screen. Used for exporting all visible contracts
 * so they can be emailed/printed (for example to Kristine).
 */
public class ContractListPDFGenerator {

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generateContractsListPDF(String title, List<Map<String, Object>> contracts, Context context) {
        // Store alongside existing behinds/due PDF reports
        File pdfFolder = new File(context.getExternalFilesDir(null), "BEHINDS LIST");
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

            // Subtitle (view title, e.g. All Contracts / Ian Contracts)
            Paragraph subtitle = new Paragraph("View: " + title)
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(subtitle);

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
                String owner = contract.get("owner") != null ? contract.get("owner").toString() : "N/A";
                String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
                String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
                String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
                String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
                String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
                String nextVisit = contract.get("nextVisit") != null ? contract.get("nextVisit").toString() : "N/A";

                Paragraph header = new Paragraph(index + ". " + name + " (" + owner + ")")
                        .setFontSize(16)
                        .setBold()
                        .setMarginTop(15)
                        .setMarginBottom(5);
                document.add(header);

                document.add(new Paragraph("📍 Address: " + address).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📞 Contact: " + contact).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📧 Email: " + email).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("📅 Last Visit: " + lastVisit).setFontSize(12).setMarginBottom(3));
                document.add(new Paragraph("⏰ Next Visit: " + nextVisit).setFontSize(12).setMarginBottom(10));

                if (index < contracts.size()) {
                    LineSeparator contractSeparator = new LineSeparator(new SolidLine(0.5f));
                    contractSeparator.setMarginBottom(10);
                    document.add(contractSeparator);
                }

                index++;
            }

            // Footer note
            Paragraph footerNote = new Paragraph("This report was automatically generated by Good Riddance Pest Control")
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30);
            document.add(footerNote);

        } catch (Exception e) {
            Toast.makeText(context, "Error adding content to contracts PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

