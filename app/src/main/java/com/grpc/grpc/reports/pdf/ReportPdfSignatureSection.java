package com.grpc.grpc.reports.pdf;

import android.content.Context;
import android.net.Uri;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;

/** Sign-off table with technician and customer signature images (Action Form layout). */
public final class ReportPdfSignatureSection {

    private ReportPdfSignatureSection() {}

    public static void addSignOff(
            Document document,
            Context context,
            String role,
            Uri technicianSignatureUri,
            Uri customerSignatureUri
    ) {
        document.add(new Paragraph("\nSign Off").setBold().setFontSize(13).setBackgroundColor(ColorConstants.LIGHT_GRAY));

        Table signatureTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth();

        signatureTable.addHeaderCell(headerCell("Technician Name"));
        signatureTable.addHeaderCell(headerCell("Role"));
        signatureTable.addHeaderCell(headerCell("Customer Signature"));

        signatureTable.addCell(signatureImageCell(context, technicianSignatureUri));
        signatureTable.addCell(new Cell()
                .add(new Paragraph(role != null && !role.trim().isEmpty() ? role.trim() : " ").setFontSize(11))
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));
        signatureTable.addCell(signatureImageCell(context, customerSignatureUri));

        document.add(signatureTable);
    }

    /**
     * Custom quotation sign-off: user title/name (left) and customer (right, blank for on-site signing).
     *
     * @param signerColumnHeader e.g. job title from session (not the literal word "Technician")
     * @param signerName         display name printed above the signature image
     */
    public static void addTechnicianAndCustomerSignOff(
            Document document,
            Context context,
            String signerColumnHeader,
            String signerName,
            Uri technicianSignatureUri
    ) {
        Div signOffBlock = new Div();
        signOffBlock.setKeepTogether(true);
        signOffBlock.setMarginTop(12);
        signOffBlock.setMarginBottom(8);

        signOffBlock.add(new Paragraph("Sign Off").setBold().setFontSize(13).setBackgroundColor(ColorConstants.LIGHT_GRAY));

        String leftHeader = signerColumnHeader != null && !signerColumnHeader.trim().isEmpty()
                ? signerColumnHeader.trim()
                : "Authorised Representative";

        Table signatureTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        signatureTable.setKeepTogether(true);
        signatureTable.addHeaderCell(headerCell(leftHeader));
        signatureTable.addHeaderCell(headerCell("Customer Signature"));

        Cell techCell = new Cell().setBorder(new SolidBorder(ColorConstants.BLACK, 1)).setPadding(8).setMinHeight(70);
        if (signerName != null && !signerName.trim().isEmpty()) {
            techCell.add(new Paragraph(signerName.trim()).setFontSize(11));
        }
        appendSignatureToCell(techCell, context, technicianSignatureUri);
        signatureTable.addCell(techCell);

        Cell customerCell = new Cell().setBorder(new SolidBorder(ColorConstants.BLACK, 1)).setPadding(8).setMinHeight(70);
        appendSignatureToCell(customerCell, context, null);
        signatureTable.addCell(customerCell);

        signOffBlock.add(signatureTable);
        document.add(signOffBlock);
    }

    /** Two-column sign-off: technician signature and role only. */
    public static void addTechnicianSignOff(
            Document document,
            Context context,
            String role,
            Uri technicianSignatureUri
    ) {
        document.add(new Paragraph("\nSign Off").setBold().setFontSize(13).setBackgroundColor(ColorConstants.LIGHT_GRAY));

        Table signatureTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        signatureTable.addHeaderCell(headerCell("Technician Signature"));
        signatureTable.addHeaderCell(headerCell("Role"));
        signatureTable.addCell(signatureImageCell(context, technicianSignatureUri));
        signatureTable.addCell(new Cell()
                .add(new Paragraph(role != null && !role.trim().isEmpty() ? role.trim() : " ").setFontSize(11))
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8));

        document.add(signatureTable);
    }

    private static Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text).setBold().setFontSize(11))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(8);
    }

    private static Cell signatureImageCell(Context context, Uri signatureUri) {
        Cell cell = new Cell().setBorder(new SolidBorder(ColorConstants.BLACK, 1)).setPadding(8);
        appendSignatureToCell(cell, context, signatureUri);
        return cell;
    }

    private static void appendSignatureToCell(Cell cell, Context context, Uri signatureUri) {
        if (signatureUri != null) {
            try {
                ImageData data = ImageDataFactory.create(
                        context.getContentResolver().openInputStream(signatureUri).readAllBytes());
                cell.add(new Image(data).scaleToFit(120, 55));
                return;
            } catch (Exception ignored) {
                // fall through to line placeholder
            }
        }
        cell.add(new Paragraph("_________________________").setFontSize(11));
    }
}
