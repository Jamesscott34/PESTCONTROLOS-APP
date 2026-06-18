package com.grpc.grpc.routes.pdf;

import android.content.Context;
import android.widget.Toast;

import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;
import com.grpc.grpc.routes.model.RouteStop;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.TextAlignment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BestRoutePdfGenerator {
    private static final String ROUTES_FOLDER = "BEST ROUTES";

    private BestRoutePdfGenerator() {
    }

    public static File generateBestRoutePdf(Context context, String ownerLabel, List<RouteStop> stops, Date routeDate) {
        if (context == null || stops == null || stops.isEmpty()) {
            return null;
        }

        File pdfFolder = new File(context.getExternalFilesDir(null), ROUTES_FOLDER);
        if (!pdfFolder.exists() && !pdfFolder.mkdirs()) {
            Toast.makeText(context, "Could not create BEST ROUTES folder.", Toast.LENGTH_SHORT).show();
            return null;
        }

        Date safeRouteDate = routeDate != null ? routeDate : new Date();
        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(safeRouteDate);
        String userPart = sanitizeFileNamePart(ownerLabel);
        if (userPart.isEmpty()) {
            userPart = "route";
        }
        File pdfFile = new File(pdfFolder, userPart + "_Best-Route_" + currentDate + ".pdf");

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));
            addContent(document, context, ownerLabel, stops, safeRouteDate);
            document.close();
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), null);
            return pdfFile;
        } catch (Exception e) {
            Toast.makeText(context, "Failed to create route PDF.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static void addContent(Document document, Context context, String ownerLabel, List<RouteStop> stops, Date routeDate) throws Exception {
        int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
        if (logoResourceId != 0) {
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(180, 180).setFixedPosition(210, 730);
            document.add(logo);
        }

        document.add(new Paragraph("\n\n\n\n\n"));
        document.add(new Paragraph("BEST ROUTE")
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Owner: " + safe(ownerLabel))
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(6)
                .setMarginBottom(4));
        document.add(new Paragraph("Route day: " + new SimpleDateFormat("EEEE dd/MM/yyyy", Locale.getDefault()).format(routeDate))
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));
        document.add(new Paragraph("Generated on: " + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()))
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(18));
        document.add(new LineSeparator(new SolidLine(1f)).setMarginBottom(14));
        document.add(new Paragraph("Stops in route: " + stops.size())
                .setFontSize(13)
                .setBold()
                .setMarginBottom(6));
        document.add(new Paragraph("Estimated total distance: " + formatDistance(totalDistance(stops))
                + " | Estimated drive time: " + formatMinutes(totalMinutes(stops))
                + " | Planned day: 08:30 - 17:30/18:00")
                .setFontSize(12)
                .setMarginBottom(14));

        for (int i = 0; i < stops.size(); i++) {
            RouteStop stop = stops.get(i);
            String status = stop.routeAnchor ? "Route anchor" : (stop.manualJob ? "Scheduled job" : (stop.behind ? "Behind" : "Contract"));
            document.add(new Paragraph((i + 1) + ". " + safe(stop.name))
                    .setFontSize(16)
                    .setBold()
                    .setMarginTop(i == 0 ? 0 : 10)
                    .setMarginBottom(4));
            document.add(new Paragraph("Address: " + safe(stop.address)).setFontSize(12).setMarginBottom(2));
            if (!stop.routeAnchor && !stop.manualJob) {
                document.add(new Paragraph("Next visit: " + safe(stop.nextVisit)).setFontSize(12).setMarginBottom(2));
            }
            document.add(new Paragraph("Priority: " + status).setFontSize(12).setMarginBottom(2));
            if (i > 0) {
                document.add(new Paragraph("From previous stop: " + formatDistance(stop.legDistanceKm)
                        + " | Estimated travel time: " + formatMinutes(stop.estimatedMinutes))
                        .setFontSize(12)
                        .setMarginBottom(2));
            }
            if (stop.plannedStartMinutes >= 0 || stop.plannedEndMinutes >= 0) {
                String label = stop.manualJob ? "Job time" : (stop.routeAnchor ? "Route time" : "Suggested contract slot");
                document.add(new Paragraph(label + ": " + formatClock(stop.plannedStartMinutes)
                        + " - " + formatClock(stop.plannedEndMinutes))
                        .setFontSize(12)
                        .setMarginBottom(2));
            }
            if (stop.hasCoordinates()) {
                String mapsUrl = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f", stop.latitude, stop.longitude);
                document.add(new Paragraph("Maps: " + mapsUrl).setFontSize(11).setMarginBottom(6));
            } else {
                document.add(new Paragraph("Maps: coordinates unavailable").setFontSize(11).setMarginBottom(6));
            }
            if (i < stops.size() - 1) {
                document.add(new LineSeparator(new SolidLine(0.5f)).setMarginBottom(4));
            }
        }

        document.add(new Paragraph(TenantBranding.autoGeneratedNote(context))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(22));
    }

    public static File getRoutesFolder(Context context) {
        return new File(context.getExternalFilesDir(null), ROUTES_FOLDER);
    }

    private static String safe(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : "N/A";
    }

    /**
     * Safe segment for filenames: letters, digits, underscore, hyphen; max length for path sanity.
     */
    private static String sanitizeFileNamePart(String label) {
        if (label == null) return "";
        String trimmed = label.trim();
        if (trimmed.isEmpty()) return "";
        StringBuilder out = new StringBuilder(trimmed.length());
        boolean lastWasSep = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!lastWasSep && out.length() > 0) {
                    out.append('_');
                    lastWasSep = true;
                }
                continue;
            }
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                out.append(c);
                lastWasSep = false;
            } else {
                if (!lastWasSep && out.length() > 0) {
                    out.append('_');
                    lastWasSep = true;
                }
            }
        }
        while (out.length() > 0 && (out.charAt(out.length() - 1) == '_' || out.charAt(out.length() - 1) == '-')) {
            out.setLength(out.length() - 1);
        }
        String s = out.toString();
        if (s.length() > 48) {
            s = s.substring(0, 48);
        }
        return s;
    }

    private static double totalDistance(List<RouteStop> stops) {
        double total = 0d;
        for (RouteStop stop : stops) {
            if (stop != null && !Double.isNaN(stop.legDistanceKm)) {
                total += stop.legDistanceKm;
            }
        }
        return total;
    }

    private static int totalMinutes(List<RouteStop> stops) {
        int total = 0;
        for (RouteStop stop : stops) {
            if (stop != null && stop.estimatedMinutes > 0) {
                total += stop.estimatedMinutes;
            }
        }
        return total;
    }

    private static String formatDistance(double distanceKm) {
        if (Double.isNaN(distanceKm) || distanceKm < 0d) return "Unavailable";
        return String.format(Locale.getDefault(), "%.1f km", distanceKm);
    }

    private static String formatMinutes(int minutes) {
        if (minutes < 0) return "Unavailable";
        if (minutes < 60) return minutes + " min";
        return String.format(Locale.getDefault(), "%dh %02dm", minutes / 60, minutes % 60);
    }

    private static String formatClock(int minutes) {
        if (minutes < 0) return "Unavailable";
        return String.format(Locale.getDefault(), "%02d:%02d", (minutes / 60) % 24, minutes % 60);
    }
}
