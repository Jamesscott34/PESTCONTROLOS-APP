package com.grpc.grpc;

/**
 * Simple model for a materials line item in Bird Quotation.
 * Holds material name, quantity, and unit price.
 */
public class BirdMaterialItem {

    private String materialName;
    private int quantity;
    private double unitPrice;
    /** Exact text for Qty (e.g. "2 days", "As needed") for customer PDF; null/empty = use quantity number. */
    private String quantityText;

    public BirdMaterialItem() {
        this("", 0, 0.0);
    }

    public BirdMaterialItem(String materialName, int quantity, double unitPrice) {
        this.materialName = materialName != null ? materialName : "";
        this.quantity = Math.max(0, quantity);
        this.unitPrice = unitPrice;
        this.quantityText = null;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName != null ? materialName : "";
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }

    /** Display string for Qty (whatever is written). Empty when no qty entered (no leading 0). */
    public String getQuantityDisplay() {
        if (quantityText != null && !quantityText.trim().isEmpty()) return quantityText.trim();
        if (quantity <= 0) return "";
        return String.valueOf(quantity);
    }

    public void setQuantityText(String quantityText) {
        this.quantityText = quantityText != null ? quantityText : "";
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getLineTotal() {
        return quantity * unitPrice;
    }
}

