package com.grpc.grpc.reports.model;

import androidx.annotation.NonNull;

/**
 * Entry from service_report_templates.json for Service Report / Action Form quick-fill dropdown.
 */
public final class ServiceReportTemplate {

    private final String key;
    private final String displayName;
    private final String text;
    private final String categoryName;

    public ServiceReportTemplate(@NonNull String key,
                                 @NonNull String displayName,
                                 @NonNull String text,
                                 @NonNull String categoryName) {
        this.key = key;
        this.displayName = displayName;
        this.text = text != null ? text : "";
        this.categoryName = categoryName != null ? categoryName : "";
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    public String getCategoryName() {
        return categoryName;
    }

    @NonNull
    public String getSpinnerLabel() {
        if (categoryName.isEmpty()) {
            return displayName;
        }
        return categoryName + " – " + displayName;
    }
}
