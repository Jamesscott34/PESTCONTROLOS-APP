package com.grpc.grpc.safety.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.R;
import com.grpc.grpc.core.ReportSignatureHelper;
import com.grpc.grpc.reports.services.ReportStorageService;
import com.grpc.grpc.safety.model.SafetyStatementData;
import com.grpc.grpc.safety.pdf.SafetyStatementPdfGenerator;

import java.io.File;

public class SafetyStatementActivity extends AppCompatActivity {
    public static final String EXTRA_CONTRACT_ID = "CONTRACT_ID";
    public static final String EXTRA_COMPANY_NAME = "COMPANY_NAME";
    public static final String EXTRA_ADDRESS = "ADDRESS";

    private EditText editCompanyName;
    private EditText editCompanyAddress;
    private Button buttonGenerate;
    private Uri technicianSignatureUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_statement);

        editCompanyName = findViewById(R.id.editSafetyCompanyName);
        editCompanyAddress = findViewById(R.id.editSafetyCompanyAddress);
        buttonGenerate = findViewById(R.id.buttonGenerateSafetyStatement);

        String name = getIntent().getStringExtra(EXTRA_COMPANY_NAME);
        String address = getIntent().getStringExtra(EXTRA_ADDRESS);
        if (name != null) editCompanyName.setText(name);
        if (address != null) editCompanyAddress.setText(address);

        setupSignatureButtons();
        buttonGenerate.setOnClickListener(v -> generate());
    }

    private void setupSignatureButtons() {
        Button technicianButton = findViewById(R.id.technicianSignatureButton);
        if (technicianButton != null) {
            technicianButton.setOnClickListener(v -> ReportSignatureHelper.showCaptureDialog(
                    this,
                    ReportSignatureHelper.TYPE_TECHNICIAN,
                    (uri, type) -> technicianSignatureUri = uri));
        }
    }

    private void generate() {
        String companyName = editCompanyName.getText() != null ? editCompanyName.getText().toString().trim() : "";
        String companyAddress = editCompanyAddress.getText() != null ? editCompanyAddress.getText().toString().trim() : "";
        if (companyName.isEmpty() || companyAddress.isEmpty()) {
            Toast.makeText(this, "Company name and address are required.", Toast.LENGTH_LONG).show();
            return;
        }

        buttonGenerate.setEnabled(false);
        SafetyStatementData data = new SafetyStatementData(companyName, companyAddress);
        File pdf = SafetyStatementPdfGenerator.generate(this, data, technicianSignatureUri);
        if (pdf == null) {
            buttonGenerate.setEnabled(true);
            return;
        }

        String contractId = getIntent().getStringExtra(EXTRA_CONTRACT_ID);
        ReportStorageService.uploadGeneratedPdf(
                pdf,
                contractId,
                "safety_statement",
                companyName,
                () -> runOnUiThread(() -> {
                    Toast.makeText(this, "Safety Statement saved: " + pdf.getName(), Toast.LENGTH_LONG).show();
                    buttonGenerate.setEnabled(true);
                    finish();
                }),
                error -> runOnUiThread(() -> {
                    Toast.makeText(this, "Saved locally, but upload failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    buttonGenerate.setEnabled(true);
                })
        );
    }
}
