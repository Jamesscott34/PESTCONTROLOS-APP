package com.grpc.grpc.reports.pdf;



import androidx.annotation.Nullable;



import com.grpc.grpc.reports.model.ProductUsageItem;

import com.grpc.grpc.reports.util.PrepProductsFormatter;

import com.itextpdf.kernel.colors.ColorConstants;

import com.itextpdf.layout.Document;

import com.itextpdf.layout.element.Cell;

import com.itextpdf.layout.element.Paragraph;

import com.itextpdf.layout.property.TextAlignment;



import java.util.List;



/**

 * Renders structured prep / product lines in PDF documents (no tables or category headings).

 */

public final class PrepProductsPdfHelper {



    private PrepProductsPdfHelper() {

    }



    /**

     * Create Report body: plain legacy text or simple prep lines.

     */

    public static void addPrepSectionToDocument(Document document,

                                                @Nullable List<ProductUsageItem> products,

                                                @Nullable String legacyPrepText,

                                                float bodyFontSize) {

        if (products != null && !products.isEmpty()) {

            addProductLines(document, products, bodyFontSize);

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

            for (ProductUsageItem item : products) {

                if (item == null) continue;

                targetCell.add(new Paragraph(PrepProductsFormatter.formatRowLine(item))

                        .setFontSize(fontSize)

                        .setMarginBottom(2f));

            }

            return;

        }

        String text = legacyPrepText != null && !legacyPrepText.trim().isEmpty()

                ? legacyPrepText.trim() : "N/A";

        targetCell.add(new Paragraph(text).setFontSize(fontSize));

    }



    public static void addProductTables(Document document, List<ProductUsageItem> products, float fontSize) {

        addProductLines(document, products, fontSize);

    }



    private static void addProductLines(Document document, List<ProductUsageItem> products, float fontSize) {

        for (ProductUsageItem item : products) {

            if (item == null) continue;

            document.add(new Paragraph(PrepProductsFormatter.formatRowLine(item))

                    .setFontColor(ColorConstants.BLACK)

                    .setFontSize(fontSize)

                    .setTextAlignment(TextAlignment.LEFT)

                    .setMargin(0)

                    .setMarginBottom(2f)

                    .setMultipliedLeading(1.2f));

        }

    }

}

