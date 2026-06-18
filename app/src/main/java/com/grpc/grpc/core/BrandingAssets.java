package com.grpc.grpc.core;

import com.grpc.grpc.R;

import androidx.annotation.Nullable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tenant branding bytes for PDFs (flavor {@code @drawable/logo} per build variant).
 */
public final class BrandingAssets {
    private BrandingAssets() {}

    /**
     * PNG bytes for the current flavor logo ({@code @drawable/logo}), or null.
     */
    @Nullable
    public static byte[] readTenantLogoPngBytes(Context context) {
        if (context == null) return null;
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
            if (bitmap == null) {
                int logoId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
                if (logoId != 0) {
                    bitmap = BitmapFactory.decodeResource(context.getResources(), logoId);
                }
            }
            if (bitmap == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return bos.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** @deprecated Use {@link #readTenantLogoPngBytes(Context)} for customer invoices. */
    @Nullable
    @Deprecated
    public static InputStream openInvoiceSenderLogoStream(Context context) {
        byte[] bytes = readTenantLogoPngBytes(context);
        if (bytes == null || bytes.length == 0) return null;
        return new java.io.ByteArrayInputStream(bytes);
    }

    /** Reads full stream into a byte array; closes the stream. */
    public static byte[] readStreamFullyAndClose(InputStream in) throws IOException {
        if (in == null) return null;
        try (InputStream input = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }
}

