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

    @NonNull
    public static List<ProductUsageItem> rodenticides(@Nullable List<ProductUsageItem> all) {
        return filterByType(all, true);
    }

    @NonNull
    public static List<ProductUsageItem> insecticides(@Nullable List<ProductUsageItem> all) {
        return filterByType(all, false);
    }

    @NonNull
    private static List<ProductUsageItem> filterByType(@Nullable List<ProductUsageItem> all, boolean rodenticide) {
        List<ProductUsageItem> out = new ArrayList<>();
        if (all == null) return out;
        for (ProductUsageItem item : all) {
            if (item == null) continue;
            if (rodenticide && item.isRodenticide()) {
                out.add(item);
            } else if (!rodenticide && item.isInsecticide()) {
                out.add(item);
            }
        }
        return out;
    }

    /**
     * Plain-text prep for content strings / read-back when structured products exist.
     */
    @NonNull
    public static String formatPlainSummary(@Nullable List<ProductUsageItem> items,
                                            @Nullable String legacyPlainPrep) {
        if (items != null && !items.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            appendGroup(sb, "Rodenticides Used", rodenticides(items));
            appendGroup(sb, "Insecticides Used", insecticides(items));
            return sb.toString().trim();
        }
        return legacyPlainPrep != null ? legacyPlainPrep.trim() : "";
    }

    private static void appendGroup(StringBuilder sb, String heading, List<ProductUsageItem> group) {
        if (group.isEmpty()) return;
        if (sb.length() > 0) sb.append("\n");
        sb.append(heading).append("\n");
        for (ProductUsageItem item : group) {
            sb.append("  ").append(item.getResolvedProductName());
            if (hasText(item.getQuantity())) {
                sb.append(" | Qty: ").append(item.getQuantity().trim());
            }
            if (hasText(item.getBatchNumber())) {
                sb.append(" | Batch: ").append(item.getBatchNumber().trim());
            }
            if (hasText(item.getLocation())) {
                sb.append(" | Loc: ").append(item.getLocation().trim());
            }
            sb.append("\n");
        }
    }

    private static boolean hasText(@Nullable String s) {
        return s != null && !s.trim().isEmpty();
    }

    public static boolean hasStructuredProducts(@Nullable List<ProductUsageItem> items) {
        return items != null && !items.isEmpty();
    }
}
