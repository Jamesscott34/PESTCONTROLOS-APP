package com.grpc.grpc.reports.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Loads shared product_lists.json from main assets (all flavours).
 */
public final class ProductListsLoader {

    private static final String TAG = "ProductListsLoader";
    private static final String ASSET_NAME = "product_lists.json";

    private ProductListsLoader() {
    }

    @NonNull
    public static List<String> loadRodenticides(@Nullable Context context) {
        ProductLists lists = load(context);
        return lists != null ? lists.rodenticides : Collections.emptyList();
    }

    @NonNull
    public static List<String> loadInsecticides(@Nullable Context context) {
        ProductLists lists = load(context);
        return lists != null ? lists.insecticides : Collections.emptyList();
    }

    @Nullable
    public static ProductLists load(@Nullable Context context) {
        if (context == null) return null;
        try (InputStream is = context.getAssets().open(ASSET_NAME);
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            String json = scanner.hasNext() ? scanner.next() : "";
            return parse(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + ASSET_NAME + ": " + e.getMessage());
            return null;
        }
    }

    private static ProductLists parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        List<String> rodenticides = readStringArray(root.optJSONArray("rodenticides"));
        List<String> insecticides = readStringArray(root.optJSONArray("insecticides"));
        return new ProductLists(rodenticides, insecticides);
    }

    @NonNull
    private static List<String> readStringArray(@Nullable JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String value = arr.optString(i, "").trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    public static final class ProductLists {
        public final List<String> rodenticides;
        public final List<String> insecticides;

        public ProductLists(List<String> rodenticides, List<String> insecticides) {
            this.rodenticides = rodenticides != null ? rodenticides : Collections.emptyList();
            this.insecticides = insecticides != null ? insecticides : Collections.emptyList();
        }
    }
}
