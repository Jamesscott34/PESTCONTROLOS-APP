package com.grpc.grpc.core;

import com.grpc.grpc.R;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.InputStream;

/**
 * Loads branding images (like logos) from assets when present.
 *
 * Put your logo at: app/src/main/assets/logo.png (or .jpg/.jpeg/.webp)
 */
public final class BrandingAssets {
    private BrandingAssets() {}

    private static final String[] DEFAULT_LOGO_ASSET_PATHS = new String[] {
            "logo.png",
            "logo.jpg",
            "logo.jpeg",
            "logo.webp",
            "branding/logo.png",
            "branding/logo.jpg",
            "branding/logo.jpeg",
            "branding/logo.webp"
    };

    /**
     * Attempts to load a logo from assets into the provided ImageView.
     * If no asset exists (or decoding fails), it leaves the current src as-is.
     */
    public static void trySetLogoFromAssets(ImageView imageView) {
        if (imageView == null) return;

        Context context = imageView.getContext();
        if (context == null) return;

        AssetManager am = context.getAssets();
        if (am == null) return;

        for (String assetPath : DEFAULT_LOGO_ASSET_PATHS) {
            if (assetPath == null || assetPath.trim().isEmpty()) continue;
            try (InputStream is = am.open(assetPath)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    imageView.setImageBitmap(bmp);
                    return;
                }
            } catch (Exception ignored) {
                // Try next candidate
            }
        }
    }
}

