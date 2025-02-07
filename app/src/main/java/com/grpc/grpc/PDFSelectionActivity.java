package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class PDFSelectionActivity extends AppCompatActivity {

    private Button buttonViewReports, buttonViewQuotation,buttonViewAgreements;
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
    }
}
