package com.grpc.grpc.stock.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grpc.grpc.reports.data.ProductListsLoader;

import java.util.Collections;
import java.util.List;

/**
 * Reads report product names for stock suggestions.
 * Does not write product_lists.json or any report catalog.
 */
public final class StockProductTypes {

    public static final String RODENTICIDE = "rodenticide";
    public static final String INSECTICIDE = "insecticide";
    public static final String MATERIAL = "material";
    public static final String CUSTOM = "custom";

    private StockProductTypes() {
    }

    @NonNull
    public static List<String> suggestionNames(@Nullable Context context) {
        List<String> names = ProductListsLoader.loadAllProductNames(context);
        return names != null ? names : Collections.emptyList();
    }

    @NonNull
    public static String resolve(@Nullable Context context, @NonNull String productName) {
        ProductListsLoader.ProductLists lists = ProductListsLoader.load(context);
        if (lists == null) return CUSTOM;
        if (contains(lists.rodenticides, productName)) return RODENTICIDE;
        if (contains(lists.insecticides, productName)) return INSECTICIDE;
        if (contains(lists.materials, productName)) return MATERIAL;
        return CUSTOM;
    }

    private static boolean contains(@Nullable List<String> names, @NonNull String value) {
        if (names == null) return false;
        for (String name : names) {
            if (name != null && name.equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }

    @NonNull
    public static String normalizeName(@Nullable String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 120) trimmed = trimmed.substring(0, 120);
        return trimmed;
    }
}
