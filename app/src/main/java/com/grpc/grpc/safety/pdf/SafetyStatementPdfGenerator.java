package com.grpc.grpc.safety.pdf;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.reports.pdf.ReportPdfSignatureSection;

import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;
import com.grpc.grpc.safety.model.SafetyStatementData;
import com.grpc.grpc.safety.template.SafetyStatementTemplate;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class SafetyStatementPdfGenerator {
    private SafetyStatementPdfGenerator() {}

    public static File generate(Context context, SafetyStatementData data) {
        return generate(context, data, null);
    }

    public static File generate(
            Context context,
            SafetyStatementData data,
            Uri technicianSignatureUri
    ) {
        File pdfFolder = new File(context.getExternalFilesDir(null), TenantBranding.reportsFolderName(context));
        if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
            Toast.makeText(context, "Error creating report folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        String datePart = new SimpleDateFormat("dd-MM-yyyy", Locale.UK).format(new Date());
        String safeName = sanitize(data.companyName);
        File pdfFile = new File(pdfFolder, TenantBranding.filenamePrefix(context) + "_Safety_Statement_" + safeName + "_" + datePart + ".pdf");

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(180, 120)
                    .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo);

            document.add(new Paragraph("Pest Control Safety Statement")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(ColorConstants.BLACK));
            document.add(new Paragraph(TenantBranding.companyName(context))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12));
            document.add(new Paragraph("\n"));

            addField(document, "Company Name", data.companyName);
            addField(document, "Company Address", data.companyAddress);
            addField(document, "Date", new SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(new Date()));

            for (SafetyStatementTemplate.Section section : SafetyStatementTemplate.sections()) {
                document.add(new Paragraph("\n" + section.title)
                        .setBold()
                        .setFontSize(13)
                        .setBackgroundColor(ColorConstants.LIGHT_GRAY));
                document.add(new Paragraph(section.body).setFontSize(11));
            }
            String role = SessionManager.getTitle(context);
            if (role == null || role.trim().isEmpty()) {
                role = "Technician";
            }
            ReportPdfSignatureSection.addTechnicianSignOff(document, context, role, technicianSignatureUri);

            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), null);
            return pdfFile;
        } catch (Exception e) {
            Toast.makeText(context, "Error creating Safety Statement PDF", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static void addField(Document document, String label, String value) {
        document.add(new Paragraph(label).setBold().setFontSize(11).setBackgroundColor(ColorConstants.LIGHT_GRAY));
        document.add(new Paragraph(value != null && !value.trim().isEmpty() ? value.trim() : "N/A").setFontSize(11));
    }

    private static String sanitize(String value) {
        String cleaned = value != null ? value.replaceAll("[^a-zA-Z0-9]", "_") : "Company";
        cleaned = cleaned.replaceAll("_+", "_");
        if (cleaned.trim().isEmpty()) cleaned = "Company";
        return cleaned;
    }
}
