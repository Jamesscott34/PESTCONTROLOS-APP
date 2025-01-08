package com.grpc.grpc;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;

import java.io.File;

/**
 * Utility class for handling storage permissions and file management.
 */
public class StorageHelper {

    /**
     * Requests storage permission for Android 6.0 (API 23+) and above.
     * @param activity The activity requesting the permission.
     */
    public static void requestStoragePermission(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
    }

    /**
     * Gets the appropriate report directory depending on the Android version.
     * @param context The application context for directory access.
     * @return The report directory as a File object.
     */
    public static File getReportDirectory(Context context) {
        File baseFolder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Scoped storage for Android 10 and above
            baseFolder = context.getExternalFilesDir(null);
        } else {
            // Public storage access for Android below API 29
            baseFolder = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "GRPEST REPORTS");
        }

        if (baseFolder != null && !baseFolder.exists()) {
            baseFolder.mkdirs();
        }
        return baseFolder;
    }
}
