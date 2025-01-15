package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class ReportSelectionActivity extends AppCompatActivity {

    private Button createReportButton, createQuotationButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_selection);

        createReportButton = findViewById(R.id.buttonCreateReport);
        createQuotationButton = findViewById(R.id.buttonCreateQuotation);

        // Navigate to CreateReportActivity
        createReportButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportActivity.class);
            startActivity(intent);
        });

        // Navigate to CreateQuotationActivity
        createQuotationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateReportActivity.class);
            startActivity(intent);
        });
    }
}
