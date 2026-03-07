package com.grpc.grpc.reports.pdf;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.borders.SolidBorder;

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
 * ActionFormPdfGenerator.java
 *
 * This class handles the generation of password-protected PDF Action Forms for Good Riddance Pest Control.
 * Users can create a professional Action Form containing service details with encryption protection.
 * The generated PDF is saved locally and includes company branding for professional documentation.
 *
 * Features:
 * - Generates a structured PDF with Action Form details and images
 * - Applies password protection with owner password for editing restrictions
 * - Applies a watermark and footer on every page for branding
 * - Saves the report locally in the designated folder
 * - Formats report content with structured headings and separators
 * - Allows users to attach images to the report for additional documentation
 *
 * Author: GRPC
 */

public class ActionFormPdfGenerator {

    /**
     * Generates a password-protected PDF Action Form with service details and optional images.
     *
     * @param premisesName The name of the premises (used in the file name).
     * @param dateTime The date and time of the service.
     * @param serviceType The type of service performed.
     * @param serviceNumber The service number.
     * @param premisesAddress The address of the premises.
     * @param prep The preparation details.
     * @param serviceReport The service report content.
     * @param recommendations The recommendations provided.
     * @param followUp1 The first follow-up item.
     * @param followUp2 The second follow-up item.
     * @param followUp3 The third follow-up item.
     * @param role The role of the technician.
     * @param ownerPassword The password required for editing the PDF.
     * @param context The Android application context.
     * @param imageUris A list of image URIs to be added to the report.
     * @return The generated PDF file or null if an error occurred.
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePasswordProtectedPDF(String premisesName, String dateTime, 
            String serviceType, String serviceNumber, String premisesAddress, String prep, 
            String serviceReport, String recommendations, String followUp1, String followUp2, 
            String followUp3, String role, String ownerPassword, Context context, List<Uri> imageUris, 
            Uri technicianSignatureUri, Uri customerSignatureUri, boolean followUpToggleOn) {
        return generateActionFormPDF(premisesName, dateTime, serviceType, serviceNumber, premisesAddress,
                prep, serviceReport, recommendations, followUp1, followUp2, followUp3, role,
                ownerPassword, context, imageUris, technicianSignatureUri, customerSignatureUri, followUpToggleOn);
    }

    /**
     * Generates an Action Form PDF (no password protection). Use this for standard Action Forms.
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static File generatePDF(String premisesName, String dateTime, 
            String serviceType, String serviceNumber, String premisesAddress, String prep, 
            String serviceReport, String recommendations, String followUp1, String followUp2, 
            String followUp3, String role, Context context, List<Uri> imageUris, 
            Uri technicianSignatureUri, Uri customerSignatureUri, boolean followUpToggleOn) {
        return generateActionFormPDF(premisesName, dateTime, serviceType, serviceNumber, premisesAddress,
                prep, serviceReport, recommendations, followUp1, followUp2, followUp3, role,
                null, context, imageUris, technicianSignatureUri, customerSignatureUri, followUpToggleOn);
    }

    private static File generateActionFormPDF(String premisesName, String dateTime, 
            String serviceType, String serviceNumber, String premisesAddress, String prep, 
            String serviceReport, String recommendations, String followUp1, String followUp2, 
            String followUp3, String role, String ownerPassword, Context context, List<Uri> imageUris, 
            Uri technicianSignatureUri, Uri customerSignatureUri, boolean followUpToggleOn) {
        
        // Define the folder for storing reports
        File pdfFolder = new File(context.getExternalFilesDir(null), TenantBranding.reportsFolderName(context));
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }

        // Generate a timestamped file name for the PDF report
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        String sanitizedPremisesName = premisesName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + currentDate + ".pdf";
        File pdfFile = new File(pdfFolder, sanitizedPremisesName);

        try {
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
            PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument, com.itextpdf.kernel.geom.PageSize.A4, false);
            document.setMargins(20, 20, 20, 20); // Tight margins for single page

            // Apply watermark and footer event handler
            boolean passwordProtected = (ownerPassword != null && !ownerPassword.isEmpty());
            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new ActionFormWatermarkAndFooterHandler(context, passwordProtected));

            // Adding a logo image at the top of the report (smaller for single page)
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(120, 120).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo);

            // Adding a title to the report (smaller for single page)
            Paragraph title = new Paragraph(TenantBranding.companyName(context) + " - " + TenantBranding.actionFormTitle(context))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(14)
                    .setBold()
                    .setFontColor(ColorConstants.BLUE);
            document.add(title);
            document.add(new Paragraph("\n"));

            // Create a black line separator
            LineSeparator blackSeparator = new LineSeparator(new SolidLine()).setStrokeColor(ColorConstants.BLACK);

            // Define headings that should have separators
            Set<String> headingsWithSeparator = new HashSet<>(Arrays.asList(
                    "Service Type", "Service Number", "Premises Address", "Prep", "Service Report", 
                    "Recommendations", "Follow-Up Details", "Role"
            ));

            // Add header information in rows as requested
            addHeaderRow(document, blackSeparator, dateTime, serviceType, serviceNumber);
            addPremisesAndPrepRow(document, blackSeparator, premisesName, premisesAddress, prep);
            addFormSection(document, blackSeparator, headingsWithSeparator, "Service Report", serviceReport);
            addFormSection(document, blackSeparator, headingsWithSeparator, "Recommendations", recommendations);
            
            // Add individual follow-up sections when toggle is ON
            if (followUpToggleOn) {
                addFormSection(document, blackSeparator, headingsWithSeparator, "Follow-Up 1", followUp1);
            }
            
            // Add signature section with header row
            addSignatureSection(document, blackSeparator, role, technicianSignatureUri, customerSignatureUri, context);

            // Adding images if provided (only when images are added, as per requirement)
            if (imageUris != null && !imageUris.isEmpty()) {
                for (int i = 0; i < imageUris.size(); i++) {
                    Uri uri = imageUris.get(i);
                    try {
                        document.add(new Paragraph("Images " + (i + 1)).setFontSize(10).setBold());
                        ImageData imageData = ImageDataFactory.create(context.getContentResolver().openInputStream(uri).readAllBytes());
                        Image image = new Image(imageData).scaleToFit(200, 200).setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
                        document.add(image);
                    } catch (IOException e) {
                        Toast.makeText(context, "Error loading image: " + uri.toString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            document.close();
            Toast.makeText(context, "Action Form PDF Created Successfully!", Toast.LENGTH_SHORT).show();

            return pdfFile;

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error Creating PDF!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Add a form section with proper formatting (compact for single page) - 1x2 table with spacing
     */
    private static void addFormSection(Document document, LineSeparator separator, Set<String> headingsWithSeparator, String label, String value) {
        String labelText = label;
        // For follow-up fields, leave empty if no content (don't add N/A)
        String valueText = value == null || value.trim().isEmpty() ? "" : value.trim();

        // Add top separator if the heading should have one
        if (headingsWithSeparator.contains(labelText)) {
            document.add(separator);
        }

        // Add header row (1x2 table)
        Paragraph headerRow = new Paragraph(labelText)
                .setFontColor(ColorConstants.BLACK)
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBold()
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2)
                .setMarginTop(2)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8);

        document.add(headerRow);

        // Add spacing between header and content
        document.add(new Paragraph("\n"));

        // Add content row underneath (1x2 table)
        Paragraph contentRow = new Paragraph(valueText)
                .setFontColor(ColorConstants.BLACK)
                .setFontSize(13)
                .setTextAlignment(TextAlignment.LEFT)
                .setMargin(0)
                .setMultipliedLeading(1.0f)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8);

        document.add(contentRow);

        // Add spacing after content
        document.add(new Paragraph("\n"));
    }

    /**
     * Add a follow-up section with empty box structure (1x2 table) with spacing
     */
    private static void addFollowUpSection(Document document, LineSeparator separator, Set<String> headingsWithSeparator, String followUpContent) {
        // Add top separator
        document.add(separator);

        // Add header row (1x2 table)
        Paragraph headerRow = new Paragraph("Follow-Up: / /")
                .setFontColor(ColorConstants.BLACK)
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBold()
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2)
                .setMarginTop(2)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8);

        document.add(headerRow);

        // Add spacing between header and content
        document.add(new Paragraph("\n"));

        // Add empty box row underneath (1x2 table)
        Paragraph emptyBox = new Paragraph("")
                .setFontColor(ColorConstants.BLACK)
                .setFontSize(13)
                .setTextAlignment(TextAlignment.LEFT)
                .setMargin(0)
                .setMultipliedLeading(1.0f)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8)
                .setHeight(50); // Empty box height

        document.add(emptyBox);

        // Add spacing after content
        document.add(new Paragraph("\n"));
    }

    /**
     * Add horizontal layout for Date, Service Type, and Service Number (like the image)
     */
    private static void addHeaderRow(Document document, LineSeparator separator, String dateTime, String serviceType, String serviceNumber) {
        document.add(separator);
        
        // Create a 3-column table for Date, Service Type, and Service Number
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth();
        
        // Add header cells
        headerTable.addHeaderCell(new Cell().add(new Paragraph("Date").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        headerTable.addHeaderCell(new Cell().add(new Paragraph("Service Type").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        headerTable.addHeaderCell(new Cell().add(new Paragraph("Service Number").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        
        // Add data cells
        headerTable.addCell(new Cell().add(new Paragraph(dateTime).setFontSize(13))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        headerTable.addCell(new Cell().add(new Paragraph(serviceType).setFontSize(13))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        headerTable.addCell(new Cell().add(new Paragraph(serviceNumber).setFontSize(13))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        
        document.add(headerTable);
        document.add(new Paragraph("\n")); // Add spacing
    }
    
    /**
     * Add horizontal layout for Premises and Prep (like the image)
     */
    private static void addPremisesAndPrepRow(Document document, LineSeparator separator, String premisesName, String premisesAddress, String prep) {
        document.add(separator);
        
        // Create a 2-column table for Premises and Prep (60:40 ratio)
        Table premisesTable = new Table(UnitValue.createPercentArray(new float[]{3, 2})).useAllAvailableWidth();
        
        // Add header cells
        premisesTable.addHeaderCell(new Cell().add(new Paragraph("Premises Name & Address").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        premisesTable.addHeaderCell(new Cell().add(new Paragraph("Prep").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        
        // Add data cells
        premisesTable.addCell(new Cell().add(new Paragraph(premisesName + "\n" + premisesAddress).setFontSize(13))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        premisesTable.addCell(new Cell().add(new Paragraph(prep).setFontSize(13))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        
        document.add(premisesTable);
        document.add(new Paragraph("\n")); // Add spacing
    }

    /**
     * Add horizontal layout for signatures (like the image)
     */
    private static void addSignatureSection(Document document, LineSeparator separator, String role, 
            Uri technicianSignatureUri, Uri customerSignatureUri, Context context) {
        document.add(separator);
        
        // Create a 3-column table for signatures
        Table signatureTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth();
        
        // Add header cells
        signatureTable.addHeaderCell(new Cell().add(new Paragraph("Technician Name").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        signatureTable.addHeaderCell(new Cell().add(new Paragraph("Role").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        signatureTable.addHeaderCell(new Cell().add(new Paragraph("Customer Signature").setBold().setFontSize(14))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        
        // Add technician signature cell
        Cell techCell = new Cell().setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1)).setPadding(8);
        if (technicianSignatureUri != null) {
            try {
                ImageData techSigData = ImageDataFactory.create(context.getContentResolver().openInputStream(technicianSignatureUri).readAllBytes());
                Image techSigImage = new Image(techSigData).scaleToFit(100, 50);
                techCell.add(techSigImage);
            } catch (Exception e) {
                techCell.add(new Paragraph("_________________________").setFontSize(13));
            }
        } else {
            techCell.add(new Paragraph("_________________________").setFontSize(13));
        }
        signatureTable.addCell(techCell);
        
        // Add role cell
        signatureTable.addCell(new Cell().add(new Paragraph(role).setFontSize(13))
                .setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        
        // Add customer signature cell
        Cell customerCell = new Cell().setBorder(new com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1)).setPadding(8);
        if (customerSignatureUri != null) {
            try {
                ImageData customerSigData = ImageDataFactory.create(context.getContentResolver().openInputStream(customerSignatureUri).readAllBytes());
                Image customerSigImage = new Image(customerSigData).scaleToFit(100, 50);
                customerCell.add(customerSigImage);
            } catch (Exception e) {
                customerCell.add(new Paragraph("_________________________").setFontSize(13));
            }
        } else {
            customerCell.add(new Paragraph("_________________________").setFontSize(13));
        }
        signatureTable.addCell(customerCell);
        
        document.add(signatureTable);
        document.add(new Paragraph("\n")); // Add spacing
    }

    /**
     * Custom event handler to apply watermark and footer on every page of the Action Form PDF.
     */
    static class ActionFormWatermarkAndFooterHandler implements IEventHandler {
        private final Context context;
        private final boolean passwordProtected;

        public ActionFormWatermarkAndFooterHandler(Context context) {
            this(context, false);
        }

        /**
         * Constructor initializes the event handler with the application context.
         * @param context The Android application context for resource access.
         * @param passwordProtected Whether the PDF is password protected (affects footer text).
         */
        public ActionFormWatermarkAndFooterHandler(Context context, boolean passwordProtected) {
            this.context = context;
            this.passwordProtected = passwordProtected;
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
                        .scaleToFit(500, 500);
                
                // Center the watermark dynamically
                float watermarkWidth = watermark.getImageScaledWidth();
                float watermarkHeight = watermark.getImageScaledHeight();
                watermark.setFixedPosition((pageWidth - watermarkWidth) / 2, (pageHeight - watermarkHeight) / 2);
                watermark.setOpacity(0.1f);
                doc.add(watermark);

                // Adding footer text (company-specific from strings)
                String footerText = context.getString(R.string.pdf_action_form_footer) + (passwordProtected ? context.getString(R.string.pdf_action_form_footer_password_protected) : "");
                Paragraph footer = new Paragraph(footerText)
                        .setFontSize(12)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFixedPosition(pageWidth / 2 - 200, 20, 400);
                doc.add(footer);

            } catch (Exception e) {
                Toast.makeText(context, "Error adding watermark or footer!", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }
} 