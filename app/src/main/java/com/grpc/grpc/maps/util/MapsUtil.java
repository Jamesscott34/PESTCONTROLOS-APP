package com.grpc.grpc.maps.util;

import android.content.Context;
import android.graphics.Bitmap;

import com.grpc.grpc.core.TenantBranding;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MapsUtil {
    public static final String LOCAL_MAPS_FOLDER = "site_maps";

    private MapsUtil() {}

    public static File getLocalMapsFolder(Context context) {
        File folder = new File(context.getExternalFilesDir(null), LOCAL_MAPS_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static boolean isMapFileName(String fileName) {
        return fileName != null
                && fileName.toLowerCase(Locale.getDefault()).endsWith(".pdf")
                && fileName.contains("_Map_");
    }

    public static String buildMapFileName(String companyName) {
        String safeName = sanitizeFileName(
                companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : "Site"
        );
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        return safeName + "_Map_" + date + ".pdf";
    }

    public static String sanitizeFileName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "Site";
        }
        return raw.trim().replaceAll("[^a-zA-Z0-9]", "_");
    }

    public static File generateLandscapeMapPdf(
            Context context,
            String companyName,
            String address,
            Bitmap mapBitmap
    ) throws Exception {
        File folder = getLocalMapsFolder(context);
        File pdfFile = new File(folder, buildMapFileName(companyName));

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile))) {
            PdfDocument pdfDocument = new PdfDocument(writer);
            PageSize pageSize = PageSize.A4.rotate();
            Document document = new Document(pdfDocument, pageSize, false);
            document.setMargins(0, 0, 0, 0);

            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();

            // Map image fills the entire page.
            ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
            mapBitmap.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream);
            ImageData imageData = ImageDataFactory.create(bitmapStream.toByteArray());
            Image mapImage = new Image(imageData);
            mapImage.setFixedPosition(0, 0, pageWidth);
            mapImage.setHeight(pageHeight);
            document.add(mapImage);

            // Small legend strip pinned to the bottom-left over the image.
            String legend = TenantBranding.companyName(context) + "  |  " + safeValue(companyName)
                    + "  |  " + safeValue(address)
                    + "  |  Green=internal  Red=external  Blue=fly  Black=insect";
            Paragraph legendPara = new Paragraph(legend)
                    .setFontSize(7)
                    .setFontColor(ColorConstants.BLACK)
                    .setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(255, 255, 255), 0.65f)
       