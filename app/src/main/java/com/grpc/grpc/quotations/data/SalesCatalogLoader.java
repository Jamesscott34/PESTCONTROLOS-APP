package com.grpc.grpc.quotations.data;

import com.grpc.grpc.core.*;
import com.grpc.grpc.quotations.model.SalesCatalog;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Loads shared sales.json from {@code app/src/main/assets} (all tenant flavors).
 * Flavor-specific {@code sales.json} files are not used; maintain a single catalog in main assets.
 */
public final class SalesCatalogLoader {

    private static final String TAG = "SalesCatalogLoader";
    private static final String ASSET_NAME = "sales.json";

    @Nullable
    public static SalesCatalog load(Context context) {
        if (context == null) return null;
        try {
            InputStream is = context.getAssets().open(ASSET_NAME);
            String json = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
            is.close();
            return parse(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + ASSET_NAME + ": " + e.getMessage());
            return null;
        }
    }

    private static SalesCatalog parse(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        List<SalesCatalog.SalesCategory> categories = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject catObj = arr.getJSONObject(i);
            String categoryKey = catObj.optString("categoryKey", "");
            String categoryName = catObj.optString("categoryName", "");
            JSONArray itemsArr = catObj.optJSONArray("items");
            List<SalesCatalog.SalesItem> items = new ArrayList<>();
            if (itemsArr != null) {
                for (int j = 0; j < itemsArr.length(); j++) {
                    JSONObject itemObj = itemsArr.getJSONObject(j);
                    String key = itemObj.optString("key", "");
                    String displayName = itemObj.optString("displayName", "");
                    String quoteBreakdown = itemObj.optString("quoteBreakdown", itemObj.optString("description", ""));
                    String descriptionShort = itemObj.has("quoteBreakdown") ? itemObj.optString("description", "") : "";
                    int defaultVisits = itemObj.optInt("defaultVisits", 0);
                    double defaultPrice = itemObj.optDouble("defaultPrice", 0);
                    String notes = itemObj.has("notes") ? itemObj.optString("notes") : null;
                    String serviceIncludes = itemObj.has("serviceIncludes") ? itemObj.optString("serviceIncludes") : null;
                    String exclusions = itemObj.has("exclusions") ? itemObj.optString("exclusions") : null;
                    items.add(new SalesCatalog.SalesItem(key, displayName, quoteBreakdown, descriptionShort, defaultVisits, defaultPrice, notes, serviceIncludes, exclusions));
                }
            }
            categories.add(new SalesCatalog.SalesCategory(categoryKey, categoryName, items));
        }
        return new SalesCatalog(categories);
    }
}
