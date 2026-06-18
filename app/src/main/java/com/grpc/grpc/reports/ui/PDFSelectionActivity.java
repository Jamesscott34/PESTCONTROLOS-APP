package com.grpc.grpc.reports.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.contracts.ui.BehindsListViewActivity;
import com.grpc.grpc.era.ui.ERAViewActivity;
import com.grpc.grpc.quotations.ui.QuotationViewActivity;
import com.grpc.grpc.serviceagreements.ui.ServiceAgreementViewActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * PDFSelectionActivity.java
 *
 * This activity provides a selection screen for users to navigate between different types of PDF documents,
 * including reports, quotations, service agreements, environmental risk assessments (ERA), and stored reports.
 * The username is retrieved from the intent and passed to subsequent activities for continuity.
 *
 * Features:
 * - Displays a welcome message with the user's name
 * - Provides navigation to various PDF-related activities
 * - Ensures USER_NAME is always passed to the next activity
 * - Supports viewing different document categories such as reports, quotations, agreements, and ERA files
 *
 * Author: GRPC
 */


public class PDFSelectionActivity extends AppCompatActivity {

    private Button buttonViewReports, buttonViewQuotation, buttonViewAgreements, buttonStoredReports, buttonViewERA;
    private TextView welcomeTextView;
    private String userName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_selection);

        // Retrieve the username from the intent
        userName = getIntent().getStringExtra("USER_NAME");

        // Initialize the welcome TextView
        welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText(getString(R.string.welcome_back_user, userName != null ? userName : "User"));
        }
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (welcomeTextView == null) return;
            String name = SessionManager.getName(this);
            if (name != null && !name.trim().isEmpty()) {
                welcomeTextView.setText(getString(R.string.welcome_back_user, name.trim()));
            }
        }));

        // Initialize buttons
        buttonViewReports = findViewById(R.id.buttonViewReports);
        buttonViewQuotation = findViewById(R.id.buttonViewQuotation);
        buttonViewAgreements = findViewById(R.id.buttonViewAgreements);
        buttonViewERA = findViewById(R.id.buttonViewERA);
        buttonStoredReports = findViewById(R.id.buttonStoredReports);
        Button cloudContractsBtn = findViewById(R.id.buttonCloudContracts);
        Button cloudQuotationsBtn = findViewById(R.id.buttonCloudQuotations);
        Button cloudReportsBtn = findViewById(R.id.buttonCloudReports);
        Button managementJobsBtn = findViewById(R.id.buttonManagementJobs);
        Button jobWorkReportsBtn = findViewById(R.id.buttonJobWorkReports);
        Button viewBehindsBtn = findViewById(R.id.buttonViewBehinds);

        // Set button listeners
        buttonViewReports.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, ReportViewActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username to the next activity
            startActivity(intent);
        });

        buttonViewQuotation.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, QuotationViewActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });
        buttonViewAgreements.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, ServiceAgreementViewActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username to the next activity
            startActivity(intent);
        });
        buttonViewERA.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, ERAViewActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username to the next activity
            startActivity(intent);
        });
        buttonStoredReports.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, CloudStorageBrowserActivity.class);
            intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_STORED_REPORTS);
            intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
            startActivity(intent);
        });

        if (cloudContractsBtn != null) {
            cloudContractsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(PDFSelectionActivity.this, CloudStorageBrowserActivity.class);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_CONTRACTS);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
                startActivity(intent);
            });
        }
        if (cloudQuotationsBtn != null) {
            cloudQuotationsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(PDFSelectionActivity.this, CloudStorageBrowserActivity.class);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_FIXED_ROOT);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_PATH, "quotations");
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_TITLE, "Cloud Quotations");
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
                startActivity(intent);
            });
        }
        if (cloudReportsBtn != null) {
            cloudReportsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(PDFSelectionActivity.this, CloudStorageBrowserActivity.class);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_REPORTS);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
                startActivity(intent);
            });
        }
        if (managementJobsBtn != null) {
            managementJobsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(PDFSelectionActivity.this, CloudStorageBrowserActivity.class);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_FIXED_ROOT);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_PATH,
                        com.grpc.grpc.core.ContractStoragePathHelper.preferredManagementJobsRootName());
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_TITLE, "Management Jobs");
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
                startActivity(intent);
            });
        }
        if (jobWorkReportsBtn != null) {
            jobWorkReportsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(PDFSelectionActivity.this, CloudStorageBrowserActivity.class);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_FIXED_ROOT);
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_PATH, "JobWorkReports");
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_TITLE, "Jobs Folder");
                intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
                startActivity(intent);
            });
        }
        if (viewBehindsBtn != null) {
            viewBehindsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(PDFSelectionActivity.this, BehindsListViewActivity.class);
                intent.putExtra("USER_NAME", userName);
                intent.putExtra(BehindsListViewActivity.EXTRA_FOLDER_NAME, "BEHINDS LIST");
                intent.putExtra(BehindsListViewActivity.EXTRA_SCREEN_TITLE, "View Behinds");
                intent.putExtra(BehindsListViewActivity.EXTRA_BACK_TO_VIEW_REPORTS, true);
                startActivity(intent);
            });
        }

        boolean cloudBlocked = BuildConfig.IS_OFFLINE
                || "Offline".equals(userName)
                || "Offline User".equals(userName);
        if (cloudBlocked) {
            if (buttonStoredReports != null) {
                buttonStoredReports.setVisibility(View.GONE);
            }
            if (cloudContractsBtn != null) {
                cloudContractsBtn.setVisibility(View.GONE);
            }
            if (cloudQuotationsBtn != null) {
                cloudQuotationsBtn.setVisibility(View.GONE);
            }
            if (cloudReportsBtn != null) {
                cloudReportsBtn.setVisibility(View.GONE);
            }
            if (managementJobsBtn != null) {
                managementJobsBtn.setVisibility(View.GONE);
            }
            if (jobWorkReportsBtn != null) {
                jobWorkReportsBtn.setVisibility(View.GONE);
            }
            if (buttonViewQuotation != null) {
                buttonViewQuotation.setVisibility(View.GONE);
            }
        }
    }
}
