package com.grpc.grpc.reports.pdf;

import android.content.Context;
import android.util.Log;

import com.grpc.grpc.R;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.VerticalAlignment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * After a PDF is fully written, stamps "Page x of n" on every page and draws the company footer
 * only on the last page. Used because total page count is not known while pages are still being laid out.
 */
public final class PdfFooterPageNumberStamper {

    private static final String TAG = "PdfFooterStamper";

    /** Minimum document bottom margin so body content clears stamped page numbers (drawn at bottom + 30pt). */
    public static final float RECOMMENDED_BOTTOM_MARGIN_PT = 54f;

    private PdfFooterPageNumberStamper() {
    }

    /**
     * @param footerLastPageOnly shown only on the last page (may be empty to skip)
     * @param ownerPasswordBytes if non-null/non-empty, reader and writer use the same owner password as the app generators
     */
    public static void stamp(Context context, File pdfFile, String footerLastPageOnly, byte[] ownerPasswordBytes) {
        if (context == null || pdfFile == null || !pdfFile.exists()) {
            return;
        }
        try {
            stampInternal(context, pdfFile, footerLastPageOnly != null ? footerLastPageOnly : "", ownerPasswordBytes);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stamp PDF: " + pdfFile.getAbsolutePath(), e);
        }
    }

    private static void stampInternal(Context context, File pdfFile, String footerLastPageOnly, byte[] ownerPasswordBytes) throws IOException {
        byte[] raw = Files.readAllBytes(pdfFile.toPath());
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length + 4096);

        ReaderProperties readerProperties = new ReaderProperties();
        if (ownerPasswordBytes != null && ownerPasswordBytes.length > 0) {
            readerProperties.setPassword(ownerPasswordBytes);
        }
        PdfReader reader = new PdfReader(new ByteArrayInputStream(raw), readerProperties);

        WriterProperties writerProperties = new WriterProperties().setFullCompressionMode(true);
        if (ownerPasswordBytes != null && ownerPasswordBytes.length > 0) {
            writerProperties.setStandardEncryption(
                    null,
                    ownerPasswordBytes,
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
            );
        }
        PdfWriter writer = new PdfWriter(out, writerProperties);
        PdfDocument pdfDoc = new PdfDocument(reader, writer);

        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        int n = pdfDoc.getNumberOfPages();
        for (int i = 1; i <= n; i++) {
            PdfPage page = pdfDoc.getPage(i);
            Rectangle area = page.getPageSizeWithRotation();
            float w = area.getWidth();
            float left = area.getLeft();
            float bottom = area.getBottom();

            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdfDoc);
            Canvas layoutCanvas = new Canvas(pdfCanvas, pdfDoc, area);

            String pageLabel = context.getString(R.string.pdf_page_number_format, i, n);
            Paragraph pageP = new Paragraph(pageLabel)
                    .setFont(font)
                    .setFontSize(10)
                    .setFontColor(ColorConstants.BLACK);
            // Keep a fixed right/bottom margin so page numbers stay out of body content.
            layoutCanvas.showTextAligned(pageP, left + w - 36f, bottom + 30f, TextAlignment.RIGHT, VerticalAlignment.BOTTOM);

            if (i == n && footerLastPageOnly != null && !footerLastPageOnly.isEmpty()) {
                Paragraph foot = new Paragraph(footerLastPageOnly)
                        .setFont(font)
                        .setFontSize(11)
                        .setFontColor(ColorConstants.BLACK)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMaxWidth(w - 72f);
                layoutCanvas.showTextAligned(foot, left + w / 2f, bottom + 18f, TextAlignment.CENTER, VerticalAlignment.BOTTOM);
            }

            layoutCanvas.close();
        }

        pdfDoc.close();
        Files.write(pdfFile.toPath(), out.toByteArray());
    }
}
