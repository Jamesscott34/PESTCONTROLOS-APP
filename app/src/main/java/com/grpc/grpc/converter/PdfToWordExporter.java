package com.grpc.grpc.converter;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Renders each PDF page to a bitmap and embeds it in a .docx so layout matches the PDF visually.
 * Users can then edit in Word / Google Docs and re-save.
 */
public final class PdfToWordExporter {

    /** Cap page count to avoid OOM on huge files. */
    public static final int MAX_PAGES = 40;
    private static final int MAX_PAGE_WIDTH_PX = 1100;

    private PdfToWordExporter() {
    }

    /**
     * @return number of pages written (may be less than total PDF pages if capped)
     */
    public static int exportPdfFileToDocx(File pdfFile, File outDocx) throws IOException {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.canRead()) {
            throw new IOException("Cannot read PDF file.");
        }

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd);
             XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(outDocx)) {

            int total = renderer.getPageCount();
            int pages = Math.min(total, MAX_PAGES);

            if (total > MAX_PAGES) {
                XWPFParagraph note = doc.createParagraph();
                XWPFRun noteRun = note.createRun();
                noteRun.setText("Note: Only the first " + MAX_PAGES + " of " + total + " pages were exported. Split the PDF to convert the rest.");
                noteRun.addBreak();
            }

            for (int i = 0; i < pages; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                try {
                    int pw = page.getWidth();
                    int ph = page.getHeight();
                    float scale = Math.min(1f, MAX_PAGE_WIDTH_PX / (float) Math.max(1, pw));
                    int w = Math.max(1, Math.round(pw * scale));
                    int h = Math.max(1, Math.round(ph * scale));
                    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 92, baos);
                    } finally {
                        bitmap.recycle();
                    }
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun run = p.createRun();
                    try (ByteArrayInputStream bis = new ByteArrayInputStream(baos.toByteArray())) {
                        try {
                            run.addPicture(
                                    bis,
                                    XWPFDocument.PICTURE_TYPE_PNG,
                                    "page_" + (i + 1) + ".png",
                                    Units.pixelToEMU(w),
                                    Units.pixelToEMU(h));
                        } catch (InvalidFormatException e) {
                            throw new IOException("Could not embed page " + (i + 1), e);
                        }
                    }
                } finally {
                    page.close();
                }
            }
            doc.write(fos);
            return pages;
        }
    }
}
