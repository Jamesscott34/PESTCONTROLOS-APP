package com.grpc.grpc.reports.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Structured prep / product usage entry for Create Report and Action Form.
 */
public class ProductUsageItem {

    public static final String TYPE_RODENTICIDE = "rodenticide";
    public static final String TYPE_INSECTICIDE = "insecticide";
    public static final String OTHER_CUSTOM_LABEL = "Other / Custom";

    private String type;
    private String productName;
    @Nullable
    private String customProductName;
    @Nullable
    private String quantity;
    @Nullable
    private String batchNumber;
    @Nullable
    private String location;

    public ProductUsageItem() {
    }

    public ProductUsageItem(String type, String productName, @Nullable String customProductName,
                            @Nullable String quantity, @Nullable String batchNumber,
                            @Nullable String location) {
        this.type = type;
        this.productName = productName;
        this.customProductName = customProductName;
        this.quantity = quantity;
        this.batchNumber = batchNumber;
        this.location = location;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Nullable
    public String getCustomProductName() {
        return customProductName;
    }

    public void setCustomProductName(@Nullable String customProductName) {
        this.customProductName = customProductName;
    }

    @Nullable
    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(@Nullable String quantity) {
        this.quantity = quantity;
    }

    @Nullable
    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(@Nullable String batchNumber) {
        this.batchNumber = batchNumber;
    }

    @Nullable
    public String getLocation() {
        return location;
    }

    public void setLocation(@Nullable String location) {
        this.location = location;
    }

    /** Resolved display / PDF name: custom name when Other / Custom is selected. */
    @NonNull
    public String getResolvedProductName() {
        if (isOtherCustom()) {
            return customProductName != null ? customProductName.trim() : "";
        }
        return productName != null ? productName.trim() : "";
    }

    public boolean isOtherCustom() {
        return OTHER_CUSTOM_LABEL.equalsIgnoreCase(productName != null ? productName.trim() : "");
    }

    public boolean isRodenticide() {
        return TYPE_RODENTICIDE.equalsIgnoreCase(type);
    }

    public boolean isInsecticide() {
        return TYPE_INSECTICIDE.equalsIgnoreCase(type);
    }
}
