package com.grpc.grpc.reports.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grpc.grpc.reports.model.ProductUsageItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON serialization for structured prep product lists (SQLite / metadata).
 */
public final class PrepProductsSerializer {

    /** Stored in prep column when structured data exists (backward-compat detection). */
    public static final String LEGACY_PREP_JSON_PREFIX = "__GRPC_PREP_JSON__";

    private PrepProductsSerializer() {
    }

    @NonNull
    public static String toJson(@Nullable List<ProductUsageItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        try {
            JSONArray arr = new JSONArray();
            for (ProductUsageItem item : items) {
                if (item == null) continue;
                JSONObject obj = new JSONObject();
                obj.put("type", nullToEmpty(item.getType()));
                obj.put("productName", nullToEmpty(item.getProductName()));
                obj.put("customProductName", nullToEmpty(item.getCustomProductName()));
                obj.put("quantity", nullToEmpty(item.getQuantity()));
                obj.put("batchNumber", nullToEmpty(item.getBatchNumber()));
                obj.put("location", nullToEmpty(item.getLocation()));
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @NonNull
    public static List<ProductUsageItem> fromJson(@Nullable String json) {
        List<ProductUsageItem> items = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return items;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith(LEGACY_PREP_JSON_PREFIX)) {
            trimmed = trimmed.substring(LEGACY_PREP_JSON_PREFIX.length());
        }
        try {
            JSONArray arr = new JSONArray(trimmed);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                ProductUsageItem item = new ProductUsageItem(
                        obj.optString("type", ""),
                        obj.optString("productName", ""),
                        emptyToNull(obj.optString("customProductName", "")),
                        emptyToNull(obj.optString("quantity", "")),
                        emptyToNull(obj.optString("batchNumber", "")),
                        emptyToNull(obj.optString("location", ""))
                );
                items.add(item);
            }
        } catch (Exception ignored) {
            // Plain legacy prep text — not JSON
        }
        return items;
    }

    /** Value for prep DB column: JSON prefix + payload, or plain legacy text only. */
    @NonNull
    public static String toPrepColumnValue(@Nullable List<ProductUsageItem> items,
                                           @Nullable String legacyPlainPrep) {
        String json = toJson(items);
        if (!json.isEmpty()) {
            return LEGACY_PREP_JSON_PREFIX + json;
        }
        return legacyPlainPrep != null ? legacyPlainPrep : "";
    }

    /**
     * Reads prep column: returns structured list if JSON, otherwise empty list with legacy text elsewhere.
     */
    public static boolean isStructuredPrepColumn(@Nullable String prepColumn) {
        return prepColumn != null && prepColumn.startsWith(LEGACY_PREP_JSON_PREFIX);
    }

    @NonNull
    public static String legacyPlainFromPrepColumn(@Nullable String prepColumn) {
        if (prepColumn == null || isStructuredPrepColumn(prepColumn)) {
            return "";
        }
        return prepColumn;
    }

    @NonNull
    private static String nullToEmpty(@Nullable String s) {
        return s != null ? s : "";
    }

    @Nullable
    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
