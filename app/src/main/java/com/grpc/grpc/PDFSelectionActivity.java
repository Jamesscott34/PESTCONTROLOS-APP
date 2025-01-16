package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class PDFSelectionActivity extends AppCompatActivity {

    private Button buttonViewReports, buttonViewQuotation;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_selection);

        buttonViewReports = findViewById(R.id.buttonViewReports);
        buttonViewQuotation = findViewById(R.id.buttonViewQuotation);

        // Navigate to CreateReportActivity
        buttonViewReports.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReportViewActivity.class);
            startActivity(intent);
        });

        // Navigate to CreateQuotationActivity
        buttonViewQuotation.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuotationViewActivity.class);
            startActivity(intent);
        });
    }
}
