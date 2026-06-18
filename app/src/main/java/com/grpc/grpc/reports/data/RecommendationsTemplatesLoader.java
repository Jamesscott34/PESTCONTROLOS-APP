package com.grpc.grpc.reports.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grpc.grpc.reports.model.RecommendationTemplate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Loads {@code recommendations.json} from main assets (all flavours).
 */
public final class RecommendationsTemplatesLoader {

    private static final String TAG = "RecommendationsTemplatesLoader";
    private static final String ASSET_NAME = "recommendations.json";

    private RecommendationsTemplatesLoader() {
    }

    @NonNull
    public static List<RecommendationTemplate> loadAll(@Nullable Context context) {
        RecommendationsCatalog catalog = load(context);
        return catalog != null ? catalog.getAllTemplates() : Collections.emptyList();
    }

    @Nullable
    public static RecommendationsCatalog load(@Nullable Context context) {
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

    private static RecommendationsCatalog parse(String json) throws Exception {
        JSONArray root = new JSONArray(json);
        List<RecommendationsCatalog.Category> categories = new ArrayList<>();
        for (int i = 0; i < root.length(); i++) {
            JSONObject catObj = root.optJSONObject(i);
            if (catObj == null) continue;
            String categoryKey = catObj.optString("categoryKey", "");
            String categoryName = catObj.optString("categoryName", "");
            JSONArray templatesArr = catObj.optJSONArray("templates");
            List<RecommendationTemplate> templates = new ArrayList<>();
            if (templatesArr != null) {
                for (int j = 0; j < templatesArr.length(); j++) {
                    JSONObject t = templatesArr.optJSONObject(j);
                    if (t == null) continue;
                    String key = t.optString("key", "");
                    String displayName = t.optString("displayName", "");
                    String text = t.optString("text", "");
                    if (!displayName.isEmpty()) {
                        templates.add(new RecommendationTemplate(key, displayName, text, categoryName));
                    }
                }
            }
            categories.add(new RecommendationsCatalog.Category(categoryKey, categoryName, templates));
        }
        return new RecommendationsCatalog(categories);
    }

    public static final class RecommendationsCatalog {
        private final List<Category> categories;

        public RecommendationsCatalog(List<Category> categories) {
            this.categories = categories != null ? categories : Collections.emptyList();
        }

        @NonNull
        public List<RecommendationTemplate> getAllTemplates() {
            List<RecommendationTemplate> out = new ArrayList<>();
            for (Category cat : categories) {
                if (cat.templates != null) {
                    out.addAll(cat.templates);
                }
            }
            return out;
        }

        public static final class Category {
            public final String categoryKey;
            public final String categoryName;
            public final List<RecommendationTemplate> templates;

            Category(String categoryKey, String categoryName, List<RecommendationTemplate> templates) {
                this.categoryKey = categoryKey != null ? categoryKey : "";
                this.categoryName = categoryName != null ? categoryName : "";
                this.templates = templates != null ? templates : Collections.emptyList();
            }
        }
    }
}
