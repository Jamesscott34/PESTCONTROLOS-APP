package com.grpc.grpc.reports.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grpc.grpc.reports.model.ServiceReportTemplate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Loads {@code service_report_templates.json} from main assets (all flavours).
 */
public final class ServiceReportTemplatesLoader {

    private static final String TAG = "ServiceReportTemplatesLoader";
    private static final String ASSET_NAME = "service_report_templates.json";

    private ServiceReportTemplatesLoader() {
    }

    @Nullable
    public static List<ServiceReportTemplate> loadAll(@Nullable Context context) {
        ServiceReportCatalog catalog = load(context);
        return catalog != null ? catalog.getAllTemplates() : Collections.emptyList();
    }

    @Nullable
    public static ServiceReportCatalog load(@Nullable Context context) {
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

    private static ServiceReportCatalog parse(String json) throws Exception {
        JSONArray root = new JSONArray(json);
        List<ServiceReportCatalog.Category> categories = new ArrayList<>();
        for (int i = 0; i < root.length(); i++) {
            JSONObject catObj = root.optJSONObject(i);
            if (catObj == null) continue;
            String categoryKey = catObj.optString("categoryKey", "");
            String categoryName = catObj.optString("categoryName", "");
            JSONArray templatesArr = catObj.optJSONArray("templates");
            List<ServiceReportTemplate> templates = new ArrayList<>();
            if (templatesArr != null) {
                for (int j = 0; j < templatesArr.length(); j++) {
                    JSONObject t = templatesArr.optJSONObject(j);
                    if (t == null) continue;
                    String key = t.optString("key", "");
                    String displayName = t.optString("displayName", "");
                    String text = t.optString("text", "");
                    if (!displayName.isEmpty()) {
                        templates.add(new ServiceReportTemplate(key, displayName, text, categoryName));
                    }
                }
            }
            categories.add(new ServiceReportCatalog.Category(categoryKey, categoryName, templates));
        }
        return new ServiceReportCatalog(categories);
    }

    public static final class ServiceReportCatalog {
        private final List<Category> categories;

        public ServiceReportCatalog(List<Category> categories) {
            this.categories = categories != null ? categories : Collections.emptyList();
        }

        @NonNull
        public List<ServiceReportTemplate> getAllTemplates() {
            List<ServiceReportTemplate> out = new ArrayList<>();
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
            public final List<ServiceReportTemplate> templates;

            Category(String categoryKey, String categoryName, List<ServiceReportTemplate> templates) {
                this.categoryKey = categoryKey != null ? categoryKey : "";
                this.categoryName = categoryName != null ? categoryName : "";
                this.templates = templates != null ? templates : Collections.emptyList();
            }
        }
    }
}
