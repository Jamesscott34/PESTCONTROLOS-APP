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

    private Button createReportButton, createQuotationButton, createBirdQuotationButton, buttonCreateGeneralQuotation,buttonGenericReport, buttonActionForm;
    private TextView welcomeTextView;
    private String userName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_selection);

        // Retrieve the username from the intent
        userName = getIntent().getStringExtra("USER_NAME");

        // Initialize the welcome TextView and set the welcome message
        welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Initialize buttons
        createReportButton = findViewById(R.id.buttonCreateReport);
        createQuotationButton = findViewById(R.id.buttonCreateQuotation);
        createBirdQuotationButton = findViewById(R.id.buttonCreateBirdQuotation);
        buttonCreateGeneralQuotation = findViewById(R.id.buttonCreateGeneralQuotation);
        buttonGenericReport = findViewById(R.id.buttonGenericReport);
        buttonActionForm = findViewById(R.id.buttonActionForm);

        // Navigate to ReportActivity
        createReportButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);
        });

        // Navigate to CreateReportActivity
        createQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateReportActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);
        });

        // Navigate to BirdQuotationActivity
        createBirdQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, BirdQuotationActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);
        });

        // Navigate to GeneralQuotationActivity
        buttonCreateGeneralQuotation.setOnClickListener(v -> {
            Intent intent = new Intent(this, GeneralQuotationActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username
            startActivity(intent);

        });
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
