package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class ReportSelectionActivity extends AppCompatActivity {

    private Button createReportButton, createQuotationButton, createBirdQuotationButton;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_selection);

        createReportButton = findViewById(R.id.buttonCreateReport);
        createQuotationButton = findViewById(R.id.buttonCreateQuotation);
        createBirdQuotationButton = findViewById(R.id.buttonCreateBirdQuotation); // Fixed ID

        // Navigate to ReportActivity
        createReportButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportActivity.class);
            startActivity(intent);
        });

        // Navigate to CreateReportActivity
        createQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateReportActivity.class);
            startActivity(intent);
        });

        // Navigate to BirdQuotationPDFGenerator
        createBirdQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, BirdQuotationActivity.class);
            startActivity(intent);
        });
    }
}
