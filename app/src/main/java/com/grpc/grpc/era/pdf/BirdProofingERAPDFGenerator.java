package com.grpc.grpc.era.pdf;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import android.widget.Toast;

import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.reports.pdf.ReportPdfSignatureSection;
import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;
import com.grpc.grpc.safety.template.SafetyStatementTemplate;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class BirdProofingERAPDFGenerator {
    private BirdProofingERAPDFGenerator() {}

    public static String generateBirdProofingEnvironmentalRiskAssessment(
            Context context,
            String companyName,
            String address,
            String email,
            Uri technicianSignatureUri
    ) {
        return generateBirdProofingEnvironmentalRiskAssessment(
                context, companyName, address, email, technicianSignatureUri, null);
    }

    public static String generateBirdProofingEnvironmentalRiskAssessment(
            Context context,
            String companyName,
            String address,
            String email,
            Uri technicianSignatureUri,
            File outputDirectory
    ) {
        File assessmentsFolder = outputDirectory != null
                ? outputDirectory
                : new File(context.getExternalFilesDir(null), "EnvironmentalRiskAssessments");
        if (!assessmentsFolder.exists() && !assessmentsFolder.mkdirs()) {
            Toast.makeText(context, "Error creating folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        File pdfFile = new File(assessmentsFolder, generatePdfFileName(companyName));

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200)
                    .setHorizontalAlignment(com.itextpdf.layout.property.HorizontalAlignment.CENTER);
            document.add(logo);

            document.add(new Paragraph("Bird Proofing Environmental Risk Assessment")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(ColorConstants.BLACK));
            document.add(new Paragraph("\n"));

            String date = new SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(new Date());
            Table detailsTable = new Table(UnitValue.createPercentArray(2)).useAllAvailableWidth();
            String userName = SessionManager.getName(context);
            String userTitle = SessionManager.getTitle(context);
            String userEmail = SessionManager.getEmail(context);
            detailsTable.addCell(new Cell()
                    .add(new Paragraph("Company Name: " + TenantBranding.companyName(context)).setBold())
                    .add(new Paragraph("Name: " + (userName != null ? userName : "")))
                    .add(new Paragraph("Title: " + (userTitle != null ? userTitle : "")))
                    .add(new Paragraph("Email: " + (userEmail != null ? userEmail : "")))
                    .add(new Paragraph("Date: " + date))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.LEFT));
            detailsTable.addCell(new Cell()
                    .add(new Paragraph("Customer Name:\n" + companyName).setBold())
                    .add(new Paragraph("Customer Address:\n" + address))
                    .add(new Paragraph("Customer Email:\n" + email))
                    .setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.RIGHT));
            document.add(detailsTable);

            for (SafetyStatementTemplate.Section section : SafetyStatementTemplate.sections()) {
                addSection(document, section.title, section.body);
            }
            String role = SessionManager.getTitle(context);
            if (role == null || role.trim().isEmpty()) {
                role = "Technician";
            }
            ReportPdfSignatureSection.addTechnicianSignOff(document, context, role, technicianSignatureUri);

            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), null);
            return pdfFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e("BirdProofingERA", "Error creating PDF", e);
            return null;
        }
    }

    private static void addSection(Document document, String title, String content) {
        document.add(new Paragraph("\n" + title).setBold().setFontSize(14).setBackgroundColor(ColorConstants.LIGHT_GRAY));
        document.add(new Paragraph(content).setFontSize(12));
    }

    private static String generatePdfFileName(String companyName) {
        String datePart = new SimpleDateFormat("ddMM", Locale.UK).format(new Date());
        String formattedCompanyName = companyName != null ? companyName.replaceAll("[^a-zA-Z0-9]", "") : "";
        if (formattedCompanyName.isEmpty()) formattedCompanyName = "Customer";
        return formattedCompanyName + "_Bird_Proofing_ERA_" + datePart + ".pdf";
    }
}
