package com.grpc.grpc;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for sales.json catalog. Supports categories and items per category.
 * Used by General Quotation (catalog-driven) feature.
 */
public final class SalesCatalog {

    private final List<SalesCategory> categories;

    public SalesCatalog(List<SalesCategory> categories) {
        this.categories = categories != null ? new ArrayList<>(categories) : new ArrayList<>();
    }

    public List<SalesCategory> getCategories() {
        return new ArrayList<>(categories);
    }

    /** Flattened list of all items (category name + displayName for spinner). */
    public List<SalesItemWithCategory> getAllItems() {
        List<SalesItemWithCategory> out = new ArrayList<>();
        for (SalesCategory cat : categories) {
            if (cat == null || cat.getItems() == null) continue;
            for (SalesItem item : cat.getItems()) {
                if (item != null) out.add(new SalesItemWithCategory(item, cat.getCategoryName()));
            }
        }
        return out;
    }

    public static final class SalesCategory {
        private final String categoryKey;
        private final String categoryName;
        private final List<SalesItem> items;

        public SalesCategory(String categoryKey, String categoryName, List<SalesItem> items) {
            this.categoryKey = categoryKey != null ? categoryKey : "";
            this.categoryName = categoryName != null ? categoryName : "";
            this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        }

        public String getCategoryKey() { return categoryKey; }
        public String getCategoryName() { return categoryName; }
        public List<SalesItem> getItems() { return new ArrayList<>(items); }
    }

    public static final class SalesItem {
        private final String key;
        private final String displayName;
        /** Long text for PDF quote breakdown section. */
        private final String quoteBreakdown;
        /** Short 1–2 line summary for PDF description (e.g. "Rodent 3 part rodent riddance"). */
        private final String description;
        private final int defaultVisits;
        private final double defaultPrice;
        @Nullable private final String notes;
        @Nullable private final String serviceIncludes;
        @Nullable private final String exclusions;

        public SalesItem(String key, String displayName, String quoteBreakdown, String description,
                         int defaultVisits, double defaultPrice,
                         @Nullable String notes, @Nullable String serviceIncludes, @Nullable String exclusions) {
            this.key = key != null ? key : "";
            this.displayName = displayName != null ? displayName : "";
            this.quoteBreakdown = quoteBreakdown != null ? quoteBreakdown : "";
            this.description = description != null ? description : "";
            this.defaultVisits = defaultVisits;
            this.defaultPrice = defaultPrice;
            this.notes = notes;
            this.serviceIncludes = serviceIncludes;
            this.exclusions = exclusions;
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        /** Long text for PDF quote breakdown. */
        public String getQuoteBreakdown() { return quoteBreakdown; }
        /** Short description for PDF (1–2 lines). */
        public String getDescription() { return description; }
        public int getDefaultVisits() { return defaultVisits; }
        public double getDefaultPrice() { return defaultPrice; }
        @Nullable public String getNotes() { return notes; }
        @Nullable public String getServiceIncludes() { return serviceIncludes; }
        @Nullable public String getExclusions() { return exclusions; }
    }

    /** Wrapper so spinner can show "Category - displayName" and we keep reference to item. */
    public static final class SalesItemWithCategory {
        private final SalesItem item;
        private final String categoryName;

        public SalesItemWithCategory(SalesItem item, String categoryName) {
            this.item = item;
            this.categoryName = categoryName != null ? categoryName : "";
        }

        public SalesItem getItem() { return item; }
        public String getCategoryName() { return categoryName; }
        public String getSpinnerLabel() {
            return categoryName.isEmpty() ? item.getDisplayName() : categoryName + " – " + item.getDisplayName();
        }
    }
}
