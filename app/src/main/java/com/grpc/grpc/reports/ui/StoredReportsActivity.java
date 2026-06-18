package com.grpc.grpc.reports.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Entry point kept for manifests and deep links. Uses the same UI as
 * {@link CloudStorageBrowserActivity} (report-folder style browser).
 */
public class StoredReportsActivity extends AppCompatActivity {

    /** Opens this folder path directly (e.g. {@code Reports25/January}). Forwarded to {@link CloudStorageBrowserActivity}. */
    public static final String EXTRA_OPEN_FOLDER_PATH = CloudStorageBrowserActivity.EXTRA_OPEN_FOLDER_PATH;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) {
            userName = "Unknown";
        }
        if (BuildConfig.IS_OFFLINE || "Offline".equals(userName) || "Offline User".equals(userName)) {
            Toast.makeText(this, R.string.stored_reports_offline_unavailable, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) {
            return;
        }

        Intent intent = new Intent(this, CloudStorageBrowserActivity.class);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_STORED_REPORTS);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
        String openPath = getIntent().getStringExtra(EXTRA_OPEN_FOLDER_PATH);
        if (openPath != null && !openPath.trim().isEmpty()) {
            intent.putExtra(CloudStorageBrowserActivity.EXTRA_OPEN_FOLDER_PATH, openPath.trim());
        }
        startActivity(intent);
        finish();
    }
}
