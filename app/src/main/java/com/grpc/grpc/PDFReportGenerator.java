/**
 * PDFReportGenerator.java
 *
 * This class handles the generation of a PDF report for Good Riddance Pest Control.
 * It allows the user to create a structured PDF document containing event details and images.
 *
 * Key Features:
 * - Generates a PDF with structured text and image content.
 * - Adds a watermark and footer to every page.
 * - Stores the PDF locally and provides user feedback upon successful creation.
 */

package com.grpc.grpc;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.LineSeparator;
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
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PDFReportGenerator.java
 *
 * This class handles the generation of structured PDF reports for Good Riddance Pest Control.
 * Users can create a report containing event details and optional images, with a watermark and footer applied.
 * The generated PDF is saved locally and includes company branding for professional documentation.
 *
 * Features:
 * - Generates a structured PDF with report details and images
 * - Applies a watermark and footer on every page for branding
 * - Saves the report locally in the designated folder
 * - Formats report content with structured headings and separators
 * - Allows users to attach images to the report for additional documentation
 *
 * Author: James Scott
 */

public class PDFReportGenerator {

    /**
     * Generates a structured PDF report with event details and optional images.
     *
     * @param reportType The type of report being generated.
     * @param reportName The name of the report (used in the file name).
     * @param content    The content details of the report, formatted as key-value pairs.
     * @param context    The Android application context.
     * @param imageUris  A list of image URIs to be added to the report.
     * @return The generated PDF file or null if an error occurred.
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReport(String reportType, String reportName, String content, Context context, List<Uri> imageUris) {
        // Define the folder for storing reports
        File pdfFolder = new File(context.getExternalFilesDir(null), "GRPEST REPORTS");
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();  // Create the directory if it does not exist
        }

        // Generate a timestamped file name for the PDF report
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String sanitizedReportName = reportName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + currentDate + ".pdf";
        File pdfFile = new File(pdfFolder, sanitizedReportName);

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile))) {
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            // Apply watermark and footer event handler
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PdfWatermarkAndFooterHandler(context));

            // Adding a logo image at the top of the report
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo);

            // Adding a title to the report
            Paragraph title = new Paragraph("Good Riddance Pest Control Report")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(ColorConstants.BLUE);
            document.add(title);
            document.add(new Paragraph("\n"));  // Adding spacing after the title

            // Normalize line breaks before splitting
            String normalizedContent = content.replace("\r\n", "\n");
            String[] reportDetails = normalizedContent.split("\n");

// Create a black line separator
            LineSeparator blackSeparator = new LineSeparator(new SolidLine()).setStrokeColor(ColorConstants.BLACK);

// Define headings that should have separators (except "Premise Name" at the top)
            Set<String> headingsWithSeparator = new HashSet<>(Arrays.asList(
                    "Address", "Date", "Visit Type", "Site Inspection", "Recommendations", "Follow-Up", "Prep", "Tech"
            ));

            for (String detail : reportDetails) {
                String[] splitDetail = detail.split(":", 2);

                if (splitDetail.length == 2) {
                    String labelText = splitDetail[0].trim();
                    String valueText = splitDetail[1].trim().isEmpty() ? "N/A" : splitDetail[1].trim();

                    // Add top separator **only if the heading is not "Premise Name"**
                    if (headingsWithSeparator.contains(labelText)) {
                        document.add(blackSeparator);
                    }

                    // Add a heading with reduced spacing
                    Paragraph labelParagraph = new Paragraph(labelText)
                            .setFontColor(ColorConstants.BLACK)
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                            .setBold()
                            .setFontSize(14)
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginBottom(8)
                            .setMarginTop(8);

                    // Add the heading to the document
                    document.add(labelParagraph);

                    // Add bottom separator **after the heading**
                    document.add(blackSeparator);

                    // Add the value text with tight spacing
                    Paragraph valueParagraph = new Paragraph(valueText)
                            .setFontColor(ColorConstants.BLACK)
                            .setFontSize(12)
                            .setTextAlignment(TextAlignment.LEFT)
                            .setMargin(0)
                            .setMultipliedLeading(1.2f);

                    document.add(valueParagraph);

                } else {
                    // For single-line paragraphs, reduce spacing
                    document.add(new Paragraph(detail.trim())
                            .setFontColor(ColorConstants.BLACK)
                            .setFontSize(12)
                            .setTextAlignment(TextAlignment.LEFT)
                            .setMargin(0)
                            .setMultipliedLeading(1.2f));
                }
            }










            // Adding images if provided
            if (imageUris != null && !imageUris.isEmpty()) {
                for (int i = 0; i < imageUris.size(); i++) {
                    Uri uri = imageUris.get(i);
                    try {
                        document.add(new Paragraph("Images " + (i + 1)).setFontSize(16).setBold());
                        ImageData imageData = ImageDataFactory.create(context.getContentResolver().openInputStream(uri).readAllBytes());
                        Image image = new Image(imageData).scaleToFit(300, 300).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
                        document.add(image);
                    } catch (IOException e) {
                        Toast.makeText(context, "Error loading image: " + uri.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            document.close();  // Close the document after content is added
            Toast.makeText(context, "PDF Created Successfully!", Toast.LENGTH_SHORT).show();


            return pdfFile;

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Custom event handler to apply watermark and footer on every page of the PDF.
     */
    static class PdfWatermarkAndFooterHandler implements IEventHandler {
        private final Context context;

        /**
         * Constructor initializes the event handler with the application context.
         * @param context The Android application context for resource access.
         */
        public PdfWatermarkAndFooterHandler(Context context) {
            this.context = context;
        }

        /**
         * Handles the event for applying watermark and footer on each page.
         */
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

                // Applying a watermark image
                int watermarkResourceId = context.getResources().getIdentifier("bk", "drawable", context.getPackageName());
                ImageData watermarkData = ImageDataFactory.create(context.getResources().openRawResource(watermarkResourceId).readAllBytes());
                Image watermark = new Image(watermarkData)
                        .scaleToFit(500, 500)
                        .setFixedPosition(pageWidth / 4, pageHeight / 4);
                watermark.setOpacity(0.1f);
                doc.add(watermark);

                // Adding footer text
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
