package com.grpc.grpc.core;

import com.grpc.grpc.BuildConfig;

/**
 * Firebase Storage layout for platform invoices (isolated from report folders).
 * PDFs: {@code companies/{companyId}/invoices/NAME.pdf} — {@code companyId} matches {@link BuildConfig#FLAVOR}.
 */
public final class InvoiceStoragePaths {

    private InvoiceStoragePaths() {}

    public static String companyIdFromBuild() {
        return BuildConfig.FLAVOR != null ? BuildConfig.FLAVOR : "grpc";
    }

    public static String invoicesDirectoryForCompany(String companyId) {
        String id = companyId != null && !companyId.trim().isEmpty() ? companyId.trim() : companyIdFromBuild();
        return "companies/" + id + "/invoices";
    }

    public static String invoicesDirectoryForCurrentBuild() {
        return invoicesDirectoryForCompany(companyIdFromBuild());
    }
}
