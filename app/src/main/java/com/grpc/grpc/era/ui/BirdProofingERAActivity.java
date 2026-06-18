package com.grpc.grpc.era.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.R;
import com.grpc.grpc.core.ReportSignatureHelper;
import com.grpc.grpc.era.pdf.BirdProofingERAPDFGenerator;
import com.grpc.grpc.reports.services.ReportStorageService;
import com.grpc.grpc.reports.ui.ReportPreviewActivity;

import java.io.File;

public class BirdProofingERAActivity extends AppCompatActivity {
    private static final int REQUEST_PREVIEW_ERA = 939;

    private EditText editCompanyName;
    private EditText editAddress;
    private EditText editEmail;
    private Uri technicianSignatureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nontox_era);

        editCompanyName = findViewById(R.id.editCompanyName);
        editAddress = findViewById(R.id.editAddress);
        editEmail = findViewById(R.id.editEmail);

        String name = getIntent().getStringExtra("COMPANY_NAME");
        String address = getIntent().getStringExtra("ADDRESS");
        if (name != null) editCompanyName.setText(name);
        if (address != null) editAddress.setText(address);

        View signatureSection = findViewById(R.id.reportSignatureButtonsInclude);
        if (signatureSection != null) {
            signatureSection.setVisibility(View.VISIBLE);
        }
        setupSignatureButtons();

        Button btnGeneratePDF = findViewById(R.id.btnGeneratePDF);
        Button btnPreviewPDF = findViewById(R.id.btnPreviewPDF);
        if (btnGeneratePDF != null) btnGeneratePDF.setOnClickListener(view -> generatePDF());
        if (btnPreviewPDF != null) btnPreviewPDF.setOnClickListener(view -> previewPDF());
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW_ERA
                && resultCode == RESULT_OK
                && data != null
                && data.getBooleanExtra(ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
            generatePDF();
        }
    }

    private void previewPDF() {
        FormData formData = readAndValidate();
        if (formData == null) return;

        File previewDir = new File(getCacheDir(), "report_previews/era");
        if (!previewDir.exists()) previewDir.mkdirs();

        String pdfPath = BirdProofingERAPDFGenerator.generateBirdProofingEnvironmentalRiskAssessment(
                this,
                formData.companyName,
                formData.address,
                formData.email,
                technicianSignatureUri,
                previewDir
        );

        if (pdfPath == null) {
            Toast.makeText(this, "Unable to generate preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent previewIntent = new Intent(this, ReportPreviewActivity.class);
        previewIntent.putExtra(ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, pdfPath);
        startActivityForResult(previewIntent, REQUEST_PREVIEW_ERA);
    }

    private void generatePDF() {
        FormData formData = readAndValidate();
        if (formData == null) return;

        String pdfPath = BirdProofingERAPDFGenerator.generateBirdProofingEnvironmentalRiskAssessment(
                this,
                formData.companyName,
                formData.address,
                formData.email,
                technicianSignatureUri
        );
        if (pdfPath == null) {
            Toast.makeText(this, "Error creating PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        File pdf = new File(pdfPath);
        String contractId = getIntent().getStringExtra("CONTRACT_ID");
        ReportStorageService.uploadGeneratedPdf(
                pdf,
                contractId,
                "era_bird_proofing",
                formData.companyName,
                () -> runOnUiThread(() -> {
                    Toast.makeText(this, "Bird Proofing ERA saved: " + pdf.getName(), Toast.LENGTH_LONG).show();
                    finish();
                }),
                error -> runOnUiThread(() ->
                        Toast.makeText(this, "Saved locally, but upload failed: " + error.getMessage(), Toast.LENGTH_LONG).show())
        );
    }

    @Nullable
    private FormData readAndValidate() {
        String companyName = editCompanyName != null ? editCompanyName.getText().toString().trim() : "";
        String address = editAddress != null ? editAddress.getText().toString().trim() : "";
        String email = editEmail != null ? editEmail.getText().toString().trim() : "";
        if (companyName.isEmpty() || address.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return null;
        }
        return new FormData(companyName, address, email);
    }

    private static final class FormData {
        final String companyName;
        final String address;
        final String email;

        FormData(String companyName, String address, String email) {
            this.companyName = companyName;
            this.address = address;
            this.email = email;
        }
    }
}
