package com.grpc.grpc.reports.pdf;

import androidx.annotation.Nullable;

import com.grpc.grpc.reports.model.ProductUsageItem;
import com.grpc.grpc.reports.util.PrepProductsFormatter;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;

import java.util.List;

/**
 * Renders structured prep / product tables in PDF documents.
 */
public final class PrepProductsPdfHelper {

    private static final float HEADING_FONT = 12f;

    private PrepProductsPdfHelper() {
    }

    /**
     * Create Report body: plain legacy text or product tables.
     */
    public static void addPrepSectionToDocument(Document document,
                                                @Nullable List<ProductUsageItem> products,
                                                @Nullable String legacyPrepText,
                                                float bodyFontSize) {
        if (products != null && !products.isEmpty()) {
            addProductTables(document, products, bodyFontSize);
            return;
        }
        String text = legacyPrepText != null && !legacyPrepText.trim().isEmpty()
                ? legacyPrepText.trim() : "N/A";
        document.add(new Paragraph(text)
                .setFontColor(ColorConstants.BLACK)
                .setFontSize(bodyFontSize)
                .setTextAlignment(TextAlignment.LEFT)
                .setMargin(0)
                .setMultipliedLeading(1.2f));
    }

    /**
     * Action Form prep cell content.
     */
    public static void addPrepContentToCell(Cell targetCell,
                                            @Nullable List<ProductUsageItem> products,
                                            @Nullable String legacyPrepText,
                                            float fontSize) {
        if (products != null && !products.isEmpty()) {
            List<ProductUsageItem> rodents = PrepProductsFormatter.rodenticides(products);
            List<ProductUsageItem> insects = PrepProductsFormatter.insecticides(products);
            if (!rodents.isEmpty()) {
                targetCell.add(buildGroupTable("Rodenticides Used", rodents, fontSize));
            }
            if (!insects.isEmpty()) {
                targetCell.add(buildGroupTable("Insecticides Used", insects, fontSize));
            }
            return;
        }
        String text = legacyPrepText != null && !legacyPrepText.trim().isEmpty()
                ? legacyPrepText.trim() : "N/A";
        targetCell.add(new Paragraph(text).setFontSize(fontSize));
    }

    public static void addProductTables(Document document, List<ProductUsageItem> products, float fontSize) {
        List<ProductUsageItem> rodents = PrepProductsFormatter.rodenticides(products);
        List<ProductUsageItem> insects = PrepProductsFormatter.insecticides(products);
        if (!rodents.isEmpty()) {
            document.add(buildGroupTable("Rodenticides Used", rodents, fontSize));
            document.add(new Paragraph("\n"));
        }
        if (!insects.isEmpty()) {
            document.add(buildGroupTable("Insecticides Used", insects, fontSize));
        }
    }

    private static Table buildGroupTable(String heading, List<ProductUsageItem> items, float fontSize) {
        Table wrapper = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth();
        Cell headingCell = new Cell(1, 1)
                .add(new Paragraph(heading).setBold().setFontSize(HEADING_FONT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingBottom(4f);
        wrapper.addCell(headingCell);

        Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 2, 2}))
                .useAllAvailableWidth();
        addHeaderRow(table, "Product", "Qty", "Batch No.", "Location", fontSize);
        for (ProductUsageItem item : items) {
            table.addCell(dataCell(item.getResolvedProductName(), fontSize));
            table.addCell(dataCell(safe(item.getQuantity()), fontSize));
            table.addCell(dataCell(safe(item.getBatchNumber()), fontSize));
            table.addCell(dataCell(safe(item.getLocation()), fontSize));
        }
        Cell tableCell = new Cell().add(table)
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(4f);
        wrapper.addCell(tableCell);
        return wrapper;
    }

    private static void addHeaderRow(Table table, String c1, String c2, String c3, String c4, float fontSize) {
        table.addHeaderCell(headerCell(c1, fontSize));
        table.addHeaderCell(headerCell(c2, fontSize));
        table.addHeaderCell(headerCell(c3, fontSize));
        table.addHeaderCell(headerCell(c4, fontSize));
    }

    private static Cell headerCell(String text, float fontSize) {
        return new Cell().add(new Paragraph(text).setBold().setFontSize(fontSize))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(4f);
    }

    private static Cell dataCell(String text, float fontSize) {
        return new Cell().add(new Paragraph(text).setFontSize(fontSize))
                .setBorder(new SolidBorder(ColorConstants.BLACK, 1))
                .setPadding(4f);
    }

    private static String safe(@Nullable String s) {
        if (s == null || s.trim().isEmpty()) return "—";
        return s.trim();
    }
}
