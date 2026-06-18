package com.grpc.grpc.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Copies picker/camera images into app-owned storage so PDF generation and later viewing
 * do not depend on transient content URI permissions.
 */
public final class ReportImageStorage {
    private static final String SUBDIR = "report_images";

    private ReportImageStorage() {}

    public static Intent createImagePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    /**
     * Persists images from a picker result into {@code context.getFilesDir()/report_images/}.
     * Returns file:// URIs backed by app-private files.
     */
    public static List<Uri> persistFromPickerResult(Context context, @Nullable Intent data) {
        List<Uri> persisted = new ArrayList<>();
        if (context == null || data == null) {
            return persisted;
        }
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                Uri stored = persistSingleUri(context, uri, true);
                if (stored != null) {
                    persisted.add(stored);
                }
            }
        } else if (data.getData() != null) {
            Uri stored = persistSingleUri(context, data.getData(), true);
            if (stored != null) {
                persisted.add(stored);
            }
        }
        return persisted;
    }

    @Nullable
    public static Uri persistSingleUri(Context context, @Nullable Uri source, boolean tryPersistablePermission) {
        if (context == null || source == null) {
            return null;
        }
        if (tryPersistablePermission) {
            try {
                context.getContentResolver().takePersistableUriPermission(
                        source, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }
        File dir = new File(context.getFilesDir(), SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        String name = "img_" + System.currentTimeMillis() + "_" + Math.abs(source.hashCode()) + ".jpg";
        File dest = new File(dir, name);
        if (!copyUriToFile(context, source, dest)) {
            Toast.makeText(context, "Could not save image: " + source, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!isValidImageFile(dest)) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            Toast.makeText(context, "Selected image is empty or unreadable.", Toast.LENGTH_SHORT).show();
            return null;
        }
        return Uri.fromFile(dest);
    }

    public static boolean copyUriToFile(Context context, Uri uri, File dest) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                return false;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidImageFile(File file) {
        return file != null && file.exists() && file.isFile() && file.length() > 0L;
    }

    public static boolean validatePdfFile(Context context, @Nullable File pdfFile) {
        if (!isValidImageFile(pdfFile)) {
            if (context != null) {
                Toast.makeText(context, "Report PDF is missing or empty.", Toast.LENGTH_LONG).show();
            }
            return false;
        }
        return true;
    }

    /** Shareable content URI for a file in app storage (upload / view). */
    @Nullable
    public static Uri fileProviderUri(Context context, File file) {
        if (context == null || file == null || !file.exists()) {
            return null;
        }
        try {
            return FileProvider.getUriForFile(
                    context,
                    context.getApplicationContext().getPackageName() + ".fileprovider",
                    file
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatValidationError(File pdfFile) {
        if (pdfFile == null) {
            return "PDF file path is null";
        }
        if (!pdfFile.exists()) {
            return "PDF does not exist: " + pdfFile.getAbsolutePath();
        }
        return String.format(Locale.getDefault(), "PDF is empty (0 bytes): %s", pdfFile.getName());
    }
}
