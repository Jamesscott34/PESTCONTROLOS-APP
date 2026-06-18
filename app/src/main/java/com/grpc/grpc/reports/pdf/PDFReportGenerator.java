/**
 * PDFReportGenerator.java
 *
 * This class handles the generation of a PDF report for [Company 1].
 * It allows the user to create a structured PDF document containing event details and images.
 *
 * Key Features:
 * - Generates a PDF with structured text and image content.
 * - Adds a watermark on every page; footer and page numbers are applied after layout (last page footer, all pages numbered).
 * - Stores the PDF locally and provides user feedback upon successful creation.
 */

package com.grpc.grpc.reports.pdf;

import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.data.PdfTemplateSettings;


import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.WriterProperties;
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
import com.grpc.grpc.reports.model.ProductUsageItem;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PDFReportGenerator.java
 *
 * This class handles the generation of structured PDF reports for [Company 1].
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
 * Author: GRPC
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
     * @param reportDate The date for the report (optional, uses current date if null).
     * @return The generated PDF file or null if an error occurred.
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReport(String reportType, String reportName, String content, Context context, List<Uri> imageUris, String reportDate) {
        return generatePDFReport(reportType, reportName, content, context, imageUris, reportDate, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReportToDirectory(
            String reportType,
            String reportName,
            String content,
            Context context,
            List<Uri> imageUris,
            String reportDate,
            File outputDirectory
    ) {
        return generatePDFReportToDirectory(
                reportType,
                reportName,
                content,
                context,
                imageUris,
                reportDate,
                null,
                outputDirectory
        );
    }

    /**
     * Generates a PDF report with optional password protection. Always uses full compression.
     * @param ownerPassword If non-null and non-empty, PDF is encrypted (view/print allowed; editing requires password).
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReport(String reportType, String reportName, String content, Context context, List<Uri> imageUris, String reportDate, String ownerPassword) {
        return generatePDFReportToDirectory(
                reportType,
                reportName,
                content,
                context,
                imageUris,
                reportDate,
                ownerPassword,
                null
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReportToDirectory(
            String reportType,
            String reportName,
            String content,
            Context context,
            List<Uri> imageUris,
            String reportDate,
            String ownerPassword,
            File outputDirectory
    ) {
        return generatePDFReportToDirectory(
                reportType, reportName, content, context, imageUris, reportDate,
                ownerPassword, outputDirectory, null, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDFReportToDirectory(
            String reportType,
            String reportName,
            String content,
            Context context,
            List<Uri> imageUris,
            String reportDate,
            String ownerPassword,
            File outputDirectory,
            List<ProductUsageItem> prepProducts,
            String legacyPrepText
    ) {
        File pdfFolder = outputDirectory;
        if (pdfFolder == null) {
            pdfFolder = new File(context.getExternalFilesDir(null), TenantBranding.reportsFolderName(context));
        }
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        // Generate a timestamped file name for the PDF report
        String dateToUse;
        if (reportDate != null && !reportDate.isEmpty()) {
            // Use the passed date, but format it properly
            try {
                // Parse the date if it's in dd/MM/yyyy format and convert to dd-MM-yyyy
                if (reportDate.contains("/")) {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    Date parsedDate = inputFormat.parse(reportDate);
                    dateToUse = outputFormat.format(parsedDate);
                } else {
                    dateToUse = reportDate;
                }
            } catch (Exception e) {
                // If parsing fails, use current date
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                dateToUse = sdf.format(new Date());
            }
        } else {
            // Use current date
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            dateToUse = sdf.format(new Date());
        }
        
        String sanitizedReportName = reportName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + dateToUse + ".pdf";
        File pdfFile = new File(pdfFolder, sanitizedReportName);

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

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties)) {
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
            Paragraph title = new Paragraph(TenantBranding.reportTitle(context))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(ColorConstants.BLACK);
            document.add(title);
            document.add(new Paragraph("\n"));  // Adding spacing after the title

            addReportBodyToDocument(document, content, context, imageUris, null, null, prepProducts, legacyPrepText);

            document.close();  // Close the document after content is added
            byte[] ownerBytes = (ownerPassword != null && !ownerPassword.isEmpty()) ? ownerPassword.getBytes() : null;
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), ownerBytes);
            Toast.makeText(context, "PDF Created Successfully!", Toast.LENGTH_SHORT).show();
            return pdfFile;

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Adds the report body (key-value sections + optional images) to the document.
     * Same layout as GRPC template: margins, fonts, separators, table-style sections.
     * If imageUris is null or empty, no image section is added (no placeholder).
     * Called by PDFReportGenerator and by PDFReportGeneratorWithTemplate for MY_TEMPLATE.
     */
    public static void addReportBodyToDocument(Document document, String content, Context context, List<Uri> imageUris) {
        addReportBodyToDocument(document, content, context, imageUris, null, null, null, null);
    }

    /**
     * Overload with optional header size for section labels (BIGGER=17, SMALLER=11, default=14).
     * headerSize may be null for default.
     */
    public static void addReportBodyToDocument(Document document, String content, Context context, List<Uri> imageUris, String headerSize) {
        addReportBodyToDocument(document, content, context, imageUris, headerSize, null, null, null);
    }

    /**
     * Parse body text size: DEFAULT or "12" -> 12pt, "8" -> 8pt, "10" -> 10pt, "14" -> 14pt.
     * Legacy SMALLER->10, BIGGER->14. Unknown values default to 12.
     */
    private static boolean isKnownReportSectionLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        String[] labels = {
                "Premise Name", "Address", "Date", "Visit Type", "Site Inspection",
                "Recommendations", "Follow-Up", "Prep", "Prep / Products Used", "Tech"
        };
        for (String label : labels) {
            if (trimmed.startsWith(label + ":")) {
                return true;
            }
        }
        return false;
    }

    private static float parseBodyTextSize(String bodyTextSize) {
        if (bodyTextSize == null || bodyTextSize.isEmpty()) return 12f;
        if (PdfTemplateSettings.BODY_TEXT_SIZE_DEFAULT.equals(bodyTextSize) || "12".equals(bodyTextSize)) return 12f;
        if ("8".equals(bodyTextSize)) return 8f;
        if ("10".equals(bodyTextSize) || PdfTemplateSettings.BODY_TEXT_SIZE_SMALLER.equals(bodyTextSize)) return 10f;
        if ("14".equals(bodyTextSize) || PdfTemplateSettings.BODY_TEXT_SIZE_BIGGER.equals(bodyTextSize)) return 14f;
        return 12f;
    }

    /**
     * Overload with optional header size and body text size. Body text size (DEFAULT=12pt, or 8/10/12/14pt)
     * is capped so body text is never larger than the section header (label) font size.
     */
    public static void addReportBodyToDocument(Document document, String content, Context context, List<Uri> imageUris, String headerSize, String bodyTextSize) {
        addReportBodyToDocument(document, content, context, imageUris, headerSize, bodyTextSize, null, null);
    }

    public static void addReportBodyToDocument(Document document, String content, Context context, List<Uri> imageUris, String headerSize, String bodyTextSize, List<ProductUsageItem> prepProducts, String legacyPrepText) {
        float labelFontSize = 14f;
        if (headerSize != null) {
            if (PdfTemplateSettings.HEADER_SIZE_BIGGER.equals(headerSize)) labelFontSize = 17f;
            else if (PdfTemplateSettings.HEADER_SIZE_SMALLER.equals(headerSize)) labelFontSize = 11f;
        }
        float bodyFontSize = parseBodyTextSize(bodyTextSize);
        bodyFontSize = Math.min(bodyFontSize, labelFontSize);
        String normalizedContent = content.replace("\r\n", "\n");
        String[] reportDetails = normalizedContent.split("\n");

        LineSeparator blackSeparator = new LineSeparator(new SolidLine()).setStrokeColor(ColorConstants.BLACK);
        Set<String> headingsWithSeparator = new HashSet<>(Arrays.asList(
                "Address", "Date", "Visit Type", "Site Inspection", "Recommendations", "Follow-Up",
                "Prep", "Prep / Products Used", "Tech"
        ));

        boolean skipEmbeddedPrepSummary = false;
        final boolean structuredPrep = prepProducts != null && !prepProducts.isEmpty();

        for (String detail : reportDetails) {
            if (skipEmbeddedPrepSummary) {
                if (isKnownReportSectionLine(detail)) {
                    skipEmbeddedPrepSummary = false;
                } else {
                    continue;
                }
            }

            String[] splitDetail = detail.split(":", 2);

            if (splitDetail.length == 2) {
                String labelText = splitDetail[0].trim();
                String valueText = splitDetail[1].trim().isEmpty() ? "N/A" : splitDetail[1].trim();

                if (headingsWithSeparator.contains(labelText)
                        || ("Prep".equals(labelText) && structuredPrep)) {
                    document.add(blackSeparator);
                }

                String sectionLabel = ("Prep".equals(labelText) && structuredPrep)
                        ? "Prep / Products Used"
                        : labelText;

                Paragraph labelParagraph = new Paragraph(sectionLabel)
                        .setFontColor(ColorConstants.BLACK)
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                        .setBold()
                        .setFontSize(labelFontSize)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(8)
                        .setMarginTop(8);

                document.add(labelParagraph);
                document.add(blackSeparator);

                if ("Prep".equals(labelText) && structuredPrep) {
                    PrepProductsPdfHelper.addPrepSectionToDocument(
                            document, prepProducts, legacyPrepText, bodyFontSize);
                    skipEmbeddedPrepSummary = true;
                } else {
                    String displayValue = valueText;
                    if ("Prep".equals(labelText) && legacyPrepText != null && !legacyPrepText.trim().isEmpty()) {
                        displayValue = legacyPrepText.trim();
                    }
                    if (!("Prep".equals(labelText) && structuredPrep)) {
                        Paragraph valueParagraph = new Paragraph(displayValue)
                                .setFontColor(ColorConstants.BLACK)
                                .setFontSize(bodyFontSize)
                                .setTextAlignment(TextAlignment.LEFT)
                                .setMargin(0)
                                .setMultipliedLeading(1.2f);
                        document.add(valueParagraph);
                    }
                }

            } else {
                String trimmed = detail.trim();
                if (!trimmed.isEmpty()) {
                    document.add(new Paragraph(trimmed)
                            .setFontColor(ColorConstants.BLACK)
                            .setFontSize(bodyFontSize)
                            .setTextAlignment(TextAlignment.LEFT)
                            .setMargin(0)
                            .setMultipliedLeading(1.2f));
                }
            }
        }

        if (imageUris != null && !imageUris.isEmpty()) {
            for (int i = 0; i < imageUris.size(); i++) {
                Uri uri = imageUris.get(i);
                try {
                    document.add(new Paragraph("Images " + (i + 1)).setFontSize(16).setBold());
                    byte[] compressed = compressImageUri(context, uri);
                    ImageData imageData = ImageDataFactory.create(compressed);
                    Image image = new Image(imageData).scaleToFit(300, 300).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
                    document.add(image);
                } catch (Exception e) {
                    // Catches both java.io.IOException and iText RuntimeExceptions so a
                    // bad image skips gracefully rather than aborting the entire PDF.
                    Toast.makeText(context, "Error loading image: " + uri.toString(), Toast.LENGTH_SHORT).show();
                }
            }
        }
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

    /**
     * Applies the watermark on every page. Footer and "Page x of n" are added by {@link PdfFooterPageNumberStamper}
     * after the document is closed.
     */
    public static class PdfWatermarkAndFooterHandler implements IEventHandler {
        private final Context context;

        /**
         * Constructor initializes the event handler with the application context.
         * @param context The Android application context for resource access.
         */
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

                int watermarkResourceId = context.getResources().getIdentifier("bk", "drawable", context.getPackageName());
                ImageData watermarkData = ImageDataFactory.create(context.getResources().openRawResource(watermarkResourceId).readAllBytes());
                Image watermark = new Image(watermarkData)
                        .scaleToFit(500, 500)
                        .setFixedPosition(pageWidth / 4, pageHeight / 4);
                watermark.setOpacity(0.1f);
                doc.add(watermark);

            } catch (Exception e) {
                Toast.makeText(context, "Error adding watermark!", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }
}
