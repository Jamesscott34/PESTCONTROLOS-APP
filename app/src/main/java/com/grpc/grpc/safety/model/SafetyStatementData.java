package com.grpc.grpc.safety.model;

public final class SafetyStatementData {
    public final String companyName;
    public final String companyAddress;

    public SafetyStatementData(String companyName, String companyAddress) {
        this.companyName = companyName != null ? companyName.trim() : "";
        this.companyAddress = companyAddress != null ? companyAddress.trim() : "";
    }
}
