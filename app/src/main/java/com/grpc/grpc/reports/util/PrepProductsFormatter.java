package com.grpc.grpc.reports.util;



import androidx.annotation.NonNull;

import androidx.annotation.Nullable;



import com.grpc.grpc.reports.model.ProductUsageItem;



import java.util.ArrayList;

import java.util.List;



/**

 * Text formatting for preview, read-back, and legacy plain prep fallback.

 */

public final class PrepProductsFormatter {



    private PrepProductsFormatter() {

    }



    /** Single prep row: Name | Qty | BN | Location (empty fields omitted visually as blank). */

    @NonNull

    public static String formatRowLine(@NonNull ProductUsageItem item) {

        return joinParts(

                item.getResolvedProductName(),

                item.getQuantity(),

                item.getBatchNumber(),

                item.getLocation());

    }



    @NonNull

    private static String joinParts(@NonNull String name,

                                    @Nullable String qty,

                                    @Nullable String batch,

                                    @Nullable String location) {

        return name

                + " | " + displayPart(qty)

                + " | " + displayPart(batch)

                + " | " + displayPart(location);

    }



    @NonNull

    private static String displayPart(@Nullable String value) {

        return value != null ? value.trim() : "";

    }



    /**

     * Plain-text prep for content strings / read-back when structured products exist.

     */

    @NonNull

    public static String formatPlainSummary(@Nullable List<ProductUsageItem> items,

                                            @Nullable String legacyPlainPrep) {

        if (items != null && !items.isEmpty()) {

            StringBuilder sb = new StringBuilder();

            for (ProductUsageItem item : items) {

                if (item == null) continue;

                if (sb.length() > 0) sb.append("\n");

                sb.append(formatRowLine(item));

            }

            return sb.toString().trim();

        }

        return legacyPlainPrep != null ? legacyPlainPrep.trim() : "";

    }



    public static boolean hasStructuredProducts(@Nullable List<ProductUsageItem> items) {

        return items != null && !items.isEmpty();

    }



    /** @deprecated Grouping no longer used in UI/PDF; kept for callers that filter saved data. */

    @NonNull

    @Deprecated

    public static List<ProductUsageItem> rodenticides(@Nullable List<ProductUsageItem> all) {

        return filterByType(all, ProductUsageItem.TYPE_RODENTICIDE);

    }



    /** @deprecated Grouping no longer used in UI/PDF; kept for callers that filter saved data. */

    @NonNull

    @Deprecated

    public static List<ProductUsageItem> insecticides(@Nullable List<ProductUsageItem> all) {

        return filterByType(all, ProductUsageItem.TYPE_INSECTICIDE);

    }



    @NonNull

    private static List<ProductUsageItem> filterByType(@Nullable List<ProductUsageItem> all, String type) {

        List<ProductUsageItem> out = new ArrayList<>();

        if (all == null) return out;

        for (ProductUsageItem item : all) {

            if (item != null && type.equalsIgnoreCase(item.getType())) {

                out.add(item);

            }

        }

        return out;

    }

}

