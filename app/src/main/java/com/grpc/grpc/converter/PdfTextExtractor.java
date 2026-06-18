package com.grpc.grpc.converter;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Hybrid PDF text extraction: iText {@link LocationTextExtractionStrategy} for embedded text
 * (improved spacing in {@link #postProcessEmbedded(String)}), ML Kit OCR when a page has little
 * text (scanned pages). Password-protected PDFs use {@link ReaderProperties#setPassword}; a
 * temporary decrypted copy may be written so {@link PdfRenderer} can rasterize for OCR.
 */
public final class PdfTextExtractor {

    public static final int MAX_PAGES = 200;
    /** If embedded text has fewer non-whitespace chars than this, run OCR when possible. */
    public static final int MIN_EMBEDDED_CHARS_FOR_SKIP_OCR = 28;
    /** Max width of rendered page bitmap for OCR (px). */
    public static final int OCR_MAX_BITMAP_WIDTH = 1400;

    private PdfTextExtractor() {
    }

    /**
     * @param passwordBytes UTF-8 bytes of user password; null or empty to try opening without a password first
     */
    public static String extract(File pdfFile,
                                 @Nullable byte[] passwordBytes,
                                 File cacheDir) throws IOException, PdfPasswordRequiredException {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.canRead()) {
            throw new IOException("Cannot read PDF file.");
        }

        TextRecognizer ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        File decryptTemp = null;
        try (PdfReader reader = createReader(pdfFile, passwordBytes);
             PdfDocument pdfDoc = new PdfDocument(reader)) {

            boolean encrypted = reader.isEncrypted();
            int total = pdfDoc.getNumberOfPages();
            int pages = Math.min(Math.max(0, total), MAX_PAGES);
            StringBuilder sb = new StringBuilder(Math.min(total, pages) * 600);

            if (total > MAX_PAGES) {
                sb.append("(Note: Only the first ").append(MAX_PAGES).append(" of ")
                        .append(total).append(" pages were extracted.)\n\n");
            }

            PdfRenderer pdfRenderer = tryOpenPdfRenderer(pdfFile);
            if (pdfRenderer == null && encrypted) {
                decryptTemp = new File(cacheDir, "pdf_unlock_" + System.currentTimeMillis() + ".pdf");
                writeUnencryptedCopy(pdfFile, passwordBytes, decryptTemp);
                pdfRenderer = tryOpenPdfRenderer(decryptTemp);
            }

            try {
                for (int i = 1; i <= pages; i++) {
                    if (i > 1) {
                        sb.append("\n\n");
                    }
                    sb.append("--- Page ").append(i).append(" ---\n\n");

                    String embeddedRaw = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(
                            pdfDoc.getPage(i),
                            new LocationTextExtractionStrategy());
                    String embedded = postProcessEmbedded(
                            embeddedRaw != null ? embeddedRaw : "");

                    int embeddedStrength = countNonWhitespace(embedded);
                    String pageBody;

                    if (embeddedStrength >= MIN_EMBEDDED_CHARS_FOR_SKIP_OCR) {
                        pageBody = embedded;
                    } else if (pdfRenderer != null) {
                        String ocrText = ocrPageBitmap(pdfRenderer, i - 1, ocr);
                        if (ocrText != null && countNonWhitespace(ftrim(ocrText)) > embeddedStrength) {
                            pageBody = ocrText;
                            if (embeddedStrength > 0) {
                                pageBody = "(OCR — scanned or low text layer)\n\n" + pageBody;
                            }
                        } else if (embeddedStrength > 0) {
                            pageBody = embedded;
                        } else if (ocrText != null && !ftrim(ocrText).isEmpty()) {
                            pageBody = "(OCR)\n\n" + ocrText;
                        } else {
                            pageBody = "(No text could be extracted from this page.)";
                        }
                    } else if (embeddedStrength > 0) {
                        pageBody = embedded;
                    } else {
                        pageBody = "(No embedded text; could not rasterize this PDF for OCR.)";
                    }

                    sb.append(pageBody);
                }
            } finally {
                if (pdfRenderer != null) {
                    pdfRenderer.close();
                }
            }

            return sb.toString();
        } finally {
            ocr.close();
            if (decryptTemp != null && decryptTemp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                decryptTemp.delete();
            }
        }
    }

    private static String ftrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static PdfReader createReader(File f, @Nullable byte[] passwordBytes)
            throws IOException, PdfPasswordRequiredException {
        try {
            if (passwordBytes != null && passwordBytes.length > 0) {
                ReaderProperties rp = new ReaderProperties().setPassword(passwordBytes);
                return new PdfReader(f.getAbsolutePath(), rp);
            }
            return new PdfReader(f.getAbsolutePath());
        } catch (RuntimeException e) {
            if (isPasswordRelated(e)) {
                if (passwordBytes == null || passwordBytes.length == 0) {
                    throw new PdfPasswordRequiredException();
                }
                throw new PdfPasswordRequiredException("Incorrect PDF password.", e);
            }
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Cannot read PDF", e);
        } catch (IOException e) {
            if (isPasswordRelated(e)) {
                if (passwordBytes == null || passwordBytes.length == 0) {
                    throw new PdfPasswordRequiredException();
                }
                throw new PdfPasswordRequiredException("Incorrect PDF password.", e);
            }
            throw e;
        }
    }

    private static boolean isPasswordRelated(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String cn = t.getClass().getName().toLowerCase();
            if (cn.contains("badpassword") || cn.contains("password")) {
                return true;
            }
            String m = t.getMessage();
            if (m != null) {
                String ml = m.toLowerCase();
                if (ml.contains("password")
                        || ml.contains("encrypt")
                        || ml.contains("owner")
                        || ml.contains("user")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static PdfRenderer tryOpenPdfRenderer(File file) {
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                    file, ParcelFileDescriptor.MODE_READ_ONLY);
            return new PdfRenderer(pfd);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    /**
     * Writes an unencrypted copy for {@link PdfRenderer}. Uses password when non-null/non-empty.
     */
    private static void writeUnencryptedCopy(File src, @Nullable byte[] password, File dest) throws IOException {
        try {
            ReaderProperties rp = new ReaderProperties();
            if (password != null && password.length > 0) {
                rp.setPassword(password);
            }
            try (PdfReader r = new PdfReader(src.getAbsolutePath(), rp);
                 PdfWriter w = new PdfWriter(dest);
                 PdfDocument inDoc = new PdfDocument(r);
                 PdfDocument outDoc = new PdfDocument(w)) {
                inDoc.copyPagesTo(1, inDoc.getNumberOfPages(), outDoc);
            }
        } catch (RuntimeException e) {
            throw new IOException(e.getMessage() != null ? e.getMessage() : "Decrypt failed", e);
        }
    }

    /**
     * Normalizes embedded text: line endings, collapses excessive blank lines.
     */
    static String postProcessEmbedded(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String t = s.replace("\r\n", "\n").replace('\r', '\n');
        t = t.replaceAll("[ \\t]+\\n", "\n");
        t = t.replaceAll("\\n{4,}", "\n\n\n");
        return t.trim();
    }

    private static int countNonWhitespace(String s) {
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    private static String ocrPageBitmap(PdfRenderer renderer, int pageIndexZero, TextRecognizer recognizer) {
        if (pageIndexZero < 0 || pageIndexZero >= renderer.getPageCount()) {
            return null;
        }
        PdfRenderer.Page page = renderer.openPage(pageIndexZero);
        Bitmap bmp = null;
        try {
            int w = page.getWidth();
            int h = page.getHeight();
            float scale = Math.min(1f, OCR_MAX_BITMAP_WIDTH / (float) Math.max(1, w));
            int bw = Math.max(1, Math.round(w * scale));
            int bh = Math.max(1, Math.round(h * scale));
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(0xFFFFFFFF);
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        } finally {
            page.close();
        }
        if (bmp == null) {
            return null;
        }
        try {
            InputImage image = InputImage.fromBitmap(bmp, 0);
            Text result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image));
            return spatialTextFromMlKit(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        } finally {
            bmp.recycle();
        }
    }

    /**
     * Builds text in rough reading order from ML Kit boxes (top-down, left-right).
     */
    static String spatialTextFromMlKit(Text result) {
        if (result == null) {
            return "";
        }
        List<Text.TextBlock> blocks = new ArrayList<>(result.getTextBlocks());
        blocks.sort(ComparatorRects.BLOCK_TOP_LEFT);

        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < blocks.size(); b++) {
            Text.TextBlock block = blocks.get(b);
            List<Text.Line> lines = new ArrayList<>(block.getLines());
            lines.sort(ComparatorRects.LINE_TOP_LEFT);
            for (Text.Line line : lines) {
                String lt = line.getText();
                if (lt != null && !lt.isEmpty()) {
                    sb.append(lt).append('\n');
                }
            }
            if (b < blocks.size() - 1 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static final class ComparatorRects {
        static final java.util.Comparator<Text.TextBlock> BLOCK_TOP_LEFT = (a, bb) -> {
            Rect ra = a.getBoundingBox();
            Rect rb = bb.getBoundingBox();
            return compareRects(ra, rb);
        };
        static final java.util.Comparator<Text.Line> LINE_TOP_LEFT = (a, bb) -> {
            Rect ra = a.getBoundingBox();
            Rect rb = bb.getBoundingBox();
            return compareRects(ra, rb);
        };

        private static int compareRects(@Nullable Rect ra, @Nullable Rect rb) {
            if (ra == null && rb == null) {
                return 0;
            }
            if (ra == null) {
                return 1;
            }
            if (rb == null) {
                return -1;
            }
            int dy = Integer.compare(ra.top, rb.top);
            if (Math.abs(ra.top - rb.top) < 12) {
                dy = Integer.compare(ra.left, rb.left);
            }
            if (dy != 0) {
                return dy;
            }
            return Integer.compare(ra.left, rb.left);
        }
    }
}
