package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * ReportSelectionActivity.java
 *
 * This activity serves as a hub for creating different types of reports and quotations.
 * Users can navigate to various report creation activities based on their selection.
 * The username is retrieved from the intent and passed to the selected report activity.
 *
 * Features:
 * - Displays a welcome message with the user's name
 * - Provides navigation to different report creation activities
 * - Ensures USER_NAME is always passed to the next activity
 * - Supports creating company reports, pest control quotations, bird control quotations, and general quotations
 *
 * Author: GRPC
 */


public class ReportSelectionActivity extends AppCompatActivity {

    private Button createReportButton, createQuotationButton, createBirdQuotationButton,
            buttonCreateGeneralQuotation, buttonGeneralQuotationCatalog, buttonGenericReport, buttonActionForm,
            buttonServiceAgreements, buttonEra;
    private TextView welcomeTextView;
    private String userName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_selection);

        // Offline trial: after N days, open website instead of Create Report hub
        if (OfflineTrialHelper.openWebsiteIfExpired(this, getString(R.string.main_website_url), getString(R.string.offline_trial_redirect_message))) {
            return;
        }

        // Retrieve the username from the intent
        userName = getIntent().getStringExtra("USER_NAME");

        // Initialize the welcome TextView and set the welcome message
        welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome!");
        }
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (welcomeTextView == null) return;
            String name = SessionManager.getName(this);
            if (name != null && !name.trim().isEmpty()) {
                welcomeTextView.setText("Welcome, " + name.trim() + "!");
            } else {
                welcomeTextView.setText("Welcome, " + userName + "!");
            }
        }));

        // Initialize buttons
        createReportButton = findViewById(R.id.buttonCreateReport);
        createQuotationButton = findViewById(R.id.buttonCreateQuotation);
        createBirdQuotationButton = findViewById(R.id.buttonCreateBirdQuotation);
        buttonCreateGeneralQuotation = findViewById(R.id.buttonCreateGeneralQuotation);
        buttonGeneralQuotationCatalog = findViewById(R.id.buttonGeneralQuotationCatalog);
        buttonGenericReport = findViewById(R.id.buttonGenericReport);
        buttonActionForm = findViewById(R.id.buttonActionForm);
        buttonServiceAgreements = findViewById(R.id.buttonServiceAgreements);
        buttonEra = findViewById(R.id.buttonEra);

        // Navigate to ReportActivity
        createReportButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);
        });

        // Navigate to Contract Quotations (4/6/8/12 + Custom Quote)
        createQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuotesActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);
        });

        // Navigate to BirdQuotationActivity
        createBirdQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, BirdQuotationActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);
        });

        // Navigate to GeneralQuotationActivity (Generic Quote - multi-line custom)
        buttonCreateGeneralQuotation.setOnClickListener(v -> {
            Intent intent = new Intent(this, GeneralQuotationActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        // Navigate to General Quotation (catalog-driven from sales.json)
        if (buttonGeneralQuotationCatalog != null) {
            buttonGeneralQuotationCatalog.setOnClickListener(v -> {
                Intent intent = new Intent(this, GeneralQuotationFromCatalogActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Navigate to GenericReportActivity
        buttonGenericReport.setOnClickListener(v -> {
            Intent intent = new Intent(this, GeneralReportActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);

        });

        // Navigate to ActionFormActivity
        buttonActionForm.setOnClickListener(v -> {
            Intent intent = new Intent(this, ActionFormActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);

        });

        // Service Agreements (moved from main screen into Create Report section)
        if (buttonServiceAgreements != null) {
            buttonServiceAgreements.setOnClickListener(v -> {
                Intent intent = new Intent(this, ServiceAgreementActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // ERA (moved from main screen into Create Report section)
        if (buttonEra != null) {
            buttonEra.setOnClickListener(v -> {
                Intent intent = new Intent(this, EnvironmentSelectionActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Custom Report: open PDF Template Settings (logo, watermark, header blocks)
        Button buttonCustomReport = findViewById(R.id.buttonCustomReport);
        if (buttonCustomReport != null) {
            buttonCustomReport.setOnClickListener(v -> {
                Intent intent = new Intent(this, PdfTemplateSettingsActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Create Custom Report: open Create Report with My Template (password protect, add image, compress)
        Button buttonCreateCustomReport = findViewById(R.id.buttonCreateCustomReport);
        if (buttonCreateCustomReport != null) {
            buttonCreateCustomReport.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportActivity.class);
                intent.putExtra("USER_NAME", userName);
                intent.putExtra("USE_MY_TEMPLATE", true);
                startActivity(intent);
            });
        }
    }
}
