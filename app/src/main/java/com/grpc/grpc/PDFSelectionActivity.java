package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
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
 * Author: James Scott
 */


public class PDFSelectionActivity extends AppCompatActivity {

    private Button buttonViewReports, buttonViewQuotation,buttonViewAgreements,buttonStoredReports, buttonViewERA;
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
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Initialize buttons
        buttonViewReports = findViewById(R.id.buttonViewReports);
        buttonViewQuotation = findViewById(R.id.buttonViewQuotation);
        buttonViewAgreements = findViewById(R.id.buttonViewAgreements);
        buttonViewERA = findViewById(R.id.buttonViewERA);
        buttonStoredReports = findViewById(R.id.buttonStoredReports);

        // Set button listeners
        buttonViewReports.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, ReportViewActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username to the next activity
            startActivity(intent);
        });

        buttonViewQuotation.setOnClickListener(v -> {
            Intent intent = new Intent(PDFSelectionActivity.this, QuotationViewActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username to the next activity
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
            Intent intent = new Intent(PDFSelectionActivity.this, StoredReportsActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass username to the next activity
            startActivity(intent);
        });
    }
}
