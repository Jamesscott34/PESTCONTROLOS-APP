package com.grpc.grpc.stock.model;

import androidx.annotation.NonNull;

/**
 * One line on a stock request. Quantity is free text, such as "2" or "2 boxes".
 */
public class StockRequestItem {

    private final String productName;
    private final String quantity;
    private final String productType;

    public StockRequestItem(@NonNull String productName, @NonNull String quantity, @NonNull String productType) {
        this.productName = productName;
        this.quantity = quantity;
        this.productType = productType;
    }

    @NonNull
    public String getProductName() {
        return productName;
    }

    @NonNull
    public String getQuantity() {
        return quantity;
    }

    @NonNull
    public String getProductType() {
        return productType;
    }
}
