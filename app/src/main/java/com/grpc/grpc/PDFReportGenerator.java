package com.grpc.grpc;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.element.Text;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PDFReportGenerator {

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReport(String reportType, String reportName, String content, Context context, List<Uri> imageUris) {
        File pdfFolder = new File(context.getExternalFilesDir(null), "GRPEST REPORTS");
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String sanitizedReportName = reportName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + currentDate + ".pdf";
        File pdfFile = new File(pdfFolder, sanitizedReportName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile))) {
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            // Register watermark and footer for all pages
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PdfWatermarkAndFooterHandler(context));

            // Manually add the centered logo and title only on the first page
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData)
                    .scaleToFit(150, 150)
                    .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo);

            // Center the report title
            Paragraph title = new Paragraph("Good Riddance Pest Control Report")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(24)
                    .setBold()
                    .setFontColor(ColorConstants.BLUE);
            document.add(title);

            // Adding space before content
            document.add(new Paragraph("\n"));

            // Content begins after logo and title
            String[] reportDetails = content.split("\\n");
            for (String detail : reportDetails) {
                if (!detail.trim().endsWith(".")) {
                    detail = detail.trim() + ".";
                }

                // Split and format label and value with "N/A" fallback
                String[] splitDetail = detail.split(":", 2);
                if (splitDetail.length == 2) {
                    String labelText = splitDetail[0].trim();
                    String valueText = splitDetail[1].trim().isEmpty() ? "N/A" : splitDetail[1].trim();
                    Text label = new Text(labelText).setFontColor(ColorConstants.BLACK).setUnderline().setBold().setFontSize(16);
                    document.add(new Paragraph(label));

                    Paragraph valuePara = new Paragraph(valueText).setFontColor(ColorConstants.BLACK).setFontSize(14);
                    document.add(valuePara);
                } else {
                    String fallbackText = detail.trim().isEmpty() ? "N/A" : detail.trim();
                    document.add(new Paragraph(fallbackText).setFontColor(ColorConstants.BLACK).setFontSize(14));
                }
            }

            document.close();
            Toast.makeText(context, "PDF Created Successfully!", Toast.LENGTH_SHORT).show();

            // Launch MainActivity after generation
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);

            return pdfFile;

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    /**
     * Custom Event Handler for watermark and footer on every page.
     * The watermark is added to every page while the logo and title appear only on the first page.
     */
    private static class PdfWatermarkAndFooterHandler implements IEventHandler {
        private final Context context;

        public PdfWatermarkAndFooterHandler(Context context) {
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

                // Watermark applied to every page
                int watermarkResourceId = context.getResources().getIdentifier("bk", "drawable", context.getPackageName());
                ImageData watermarkData = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    watermarkData = ImageDataFactory.create(context.getResources().openRawResource(watermarkResourceId).readAllBytes());
                }
                Image watermark = new Image(watermarkData)
                        .scaleToFit(500, 500)
                        .setFixedPosition(pageWidth / 4, pageHeight / 4);
                watermark.setOpacity(0.1f);
                doc.add(watermark);

                // Footer applied to every page
                Paragraph footer = new Paragraph("This report was generated by GRPC Reporting System")
                        .setFontSize(12)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFixedPosition(pageWidth / 2 - 150, 20, 300);
                doc.add(footer);

            } catch (Exception e) {
                Toast.makeText(context, "Error adding watermark or footer!", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }
}
