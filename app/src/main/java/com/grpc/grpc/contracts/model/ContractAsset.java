package com.grpc.grpc.contracts.model;

/**
 * One contract asset line: a name and a whole-number quantity.
 */
public class ContractAsset {

    public final String name;
    public final int quantity;
    public final boolean custom;

    public ContractAsset(String name, int quantity, boolean custom) {
        this.name = name == null ? "" : name;
        this.quantity = quantity;
        this.custom = custom;
    }
}
