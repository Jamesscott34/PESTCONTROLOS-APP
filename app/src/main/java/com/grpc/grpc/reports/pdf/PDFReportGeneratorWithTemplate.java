package com.grpc.grpc.reports.pdf;

import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.data.PdfTemplateSettings;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.TextAlignment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Template-aware report generator. Use GRPC = existing behavior unchanged.
 * Use MY_TEMPLATE = custom logo/watermark/header blocks, same body layout as GRPC.
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class PDFReportGeneratorWithTemplate {

    private static final float LOGO_MAX_WIDTH = 200f;
    private static final float LOGO_MAX_HEIGHT = 200f;
    private static final float HEADER_IMAGE_MAX_WIDTH = 400f;

    /**
     * Generate report PDF. If templateSelection is GRPC, delegates to PDFReportGenerator (unchanged).
     * If MY_TEMPLATE, uses settings for logo/watermark/header and same body as GRPC.
     * @param compress when true, use full compression for smaller file size (recommended).
     */
    public static File generatePdf(String reportType, String reportName, String content,
                                   Context context, List<Uri> imageUris, String reportDate,
                                   String ownerPassword, PdfTemplateSettings settings, boolean compress) {
        if (settings == null || PdfTemplateSettings.GRPC.equals(settings.getTemplateSelection())) {
            return PDFReportGenerator.generatePDFReport(
                    reportType, reportName, content, context, imageUris, reportDate, ownerPassword);
        }

        return generateWithMyTemplate(reportType, reportName, content, context, imageUris, reportDate, ownerPassword, settings, compress);
    }

    /** Same as above with compress = true (default). */
    public static File generatePdf(String reportType, String reportName, String content,
                                   Context context, List<Uri> imageUris, String reportDate,
                                   String ownerPassword, PdfTemplateSettings settings) {
        return generatePdf(reportType, reportName, content, context, imageUris, reportDate, ownerPassword, settings, true);
    }

    private static File generateWithMyTemplate(String reportType, String reportName, String content,
                                                Context context, List<Uri> imageUris, String reportDate,
                                                String ownerPassword, PdfTemplateSettings settings, boolean compress) {
        File pdfFolder = new File(context.getExternalFilesDir(null), TenantBranding.reportsFolderName(context));
        if (!pdfFolder.exists()) pdfFolder.mkdirs();

        String dateToUse = formatReportDate(reportDate);
        String sanitizedReportName = reportName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + dateToUse + ".pdf";
        File pdfFile = new File(pdfFolder, sanitizedReportName);

        WriterProperties writerProperties = new WriterProperties();
        writerProperties.setFullCompressionMode(compress);
        if (ownerPassword != null && !ownerPassword.isEmpty()) {
            writerProperties.setStandardEncryption(
                    null, ownerPassword.getBytes(),
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128);
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties)) {
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new CustomWatermarkAndFooterHandler(context, settings));

            addCustomHeader(document, context, settings);
            PDFReportGenerator.addReportBodyToDocument(document, content, context, imageUris, settings.getHeaderSize(), settings.getBodyTextSize());

            document.close();
            Toast.makeText(context, "PDF Created Successfully!", Toast.LENGTH_SHORT).show();
            return pdfFile;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static String formatReportDate(String reportDate) {
        if (reportDate != null && !reportDate.isEmpty()) {
            try {
                if (reportDate.contains("/")) {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    Date parsedDate = inputFormat.parse(reportDate);
                    return outputFormat.format(parsedDate);
                }
                return reportDate;
            } catch (Exception e) {
                // ignore
            }
        }
        return new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
    }

    private static void addCustomHeader(Document document, Context context, PdfTemplateSettings settings) {
        // Logo first (same size as PDFReportGenerator: 200x200), then main header below
        Image logo = loadLogoImage(context, settings);
        if (logo != null) {
            logo.scaleToFit(LOGO_MAX_WIDTH, LOGO_MAX_HEIGHT).setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(logo);
        }
        String mainHeader = settings.getMainHeaderText() != null ? settings.getMainHeaderText().trim() : "";
        if (!mainHeader.isEmpty()) {
            Color headerColor = parseHexColor(settings.getMainHeaderColorHex());
            float mainHeaderFontSize = getMainHeaderFontSize(settings.getHeaderSize());
            Paragraph mainHeaderP = new Paragraph(mainHeader)
                    .setFontSize(mainHeaderFontSize).setBold().setFontColor(headerColor).setTextAlignment(TextAlignment.CENTER);
            document.add(mainHeaderP);
        }
        document.add(new Paragraph("\n"));
    }

    private static Color parseHexColor(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) return ColorConstants.BLUE;
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return new DeviceRgb(r, g, b);
        } catch (Exception e) {
            return ColorConstants.BLUE;
        }
    }

    private static Image loadLogoImage(Context context, PdfTemplateSettings settings) {
        String path = settings.getLogoPath();
        if (path == null || path.isEmpty()) return null;
        if (PdfTemplateSettings.USE_DEFAULT_LOGO.equals(path)) {
            try {
                int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
                if (logoResourceId != 0) {
                    byte[] bytes = context.getResources().openRawResource(logoResourceId).readAllBytes();
                    return new Image(ImageDataFactory.create(bytes));
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        File f = new File(path);
        if (f.exists()) {
            try {
                byte[] bytes = new byte[(int) f.length()];
                try (FileInputStream fis = new FileInputStream(f)) {
                    fis.read(bytes);
                }
                return new Image(ImageDataFactory.create(bytes));
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static Image loadHeaderImage(Context context, String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) return null;
        File f = new File(imagePath);
        if (!f.exists()) return null;
        try {
            byte[] bytes = new byte[(int) f.length()];
            try (FileInputStream fis = new FileInputStream(f)) {
                fis.read(bytes);
            }
            return new Image(ImageDataFactory.create(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    private static float getMainHeaderFontSize(String headerSize) {
        if (PdfTemplateSettings.HEADER_SIZE_BIGGER.equals(headerSize)) return 22f;
        if (PdfTemplateSettings.HEADER_SIZE_SMALLER.equals(headerSize)) return 14f;
        return 18f;
    }

    private static Paragraph formatHeaderText(String text, String textStyle) {
        if (text == null || text.trim().isEmpty()) return null;
        Paragraph p = new Paragraph(text.trim());
        if (PdfTemplateSettings.STYLE_H1.equals(textStyle)) {
            p.setFontSize(18).setBold().setFontColor(ColorConstants.BLUE).setTextAlignment(TextAlignment.CENTER);
        } else if (PdfTemplateSettings.STYLE_H2.equals(textStyle)) {
            p.setFontSize(14).setBold().setTextAlignment(TextAlignment.CENTER);
        } else {
            p.setFontSize(12).setTextAlignment(TextAlignment.LEFT);
        }
        return p;
    }

    private static final class CustomWatermarkAndFooterHandler implements IEventHandler {
        private final Context context;
        private final PdfTemplateSettings settings;

        CustomWatermarkAndFooterHandler(Context context, PdfTemplateSettings settings) {
            this.context = context;
            this.settings = settings;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent pdfEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = pdfEvent.getDocument();
            PdfPage page = pdfEvent.getPage();
            Rectangle pageSize = page.getPageSize();
            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();

            try {
                Document doc = new Document(pdfDoc);

                if (settings.isWatermarkEnabled()) {
                    if (PdfTemplateSettings.WATERMARK_IMAGE.equals(settings.getWatermarkType()) && settings.getWatermarkImagePath() != null) {
                        File f = new File(settings.getWatermarkImagePath());
                        if (f.exists()) {
                            byte[] bytes = new byte[(int) f.length()];
                            try (FileInputStream fis = new FileInputStream(f)) {
                                fis.read(bytes);
                            }
                            Image wm = new Image(ImageDataFactory.create(bytes)).scaleToFit(500, 500);
                            float w = wm.getImageScaledWidth();
                            float h = wm.getImageScaledHeight();
                            wm.setFixedPosition((pageWidth - w) / 2, (pageHeight - h) / 2);
                            wm.setOpacity(0.1f);
                            doc.add(wm);
                        }
                    } else {
                        String wmTextOnly = settings.getWatermarkText() != null ? settings.getWatermarkText().trim() : "";
                        if (!wmTextOnly.isEmpty()) {
                            Paragraph wmText = new Paragraph(wmTextOnly)
                                    .setFontSize(48).setFontColor(ColorConstants.LIGHT_GRAY).setOpacity(0.3f);
                            float approxWidth = Math.min(pageWidth * 0.8f, 400);
                            wmText.setFixedPosition((pageWidth - approxWidth) / 2, pageHeight / 2 - 24, approxWidth);
                            wmText.setTextAlignment(TextAlignment.CENTER);
                            doc.add(wmText);
                        }
                    }
                }

                String footerStr = settings.getFooterText();
                if (footerStr == null || footerStr.trim().isEmpty()) footerStr = TenantBranding.defaultFooterText(context);
                Paragraph footer = new Paragraph(footerStr.trim())
                        .setFontSize(12).setTextAlignment(TextAlignment.CENTER)
                        .setFixedPosition(pageWidth / 2 - 150, 20, 300);
                doc.add(footer);
            } catch (Exception e) {
                Toast.makeText(context, "Error adding watermark or footer!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
