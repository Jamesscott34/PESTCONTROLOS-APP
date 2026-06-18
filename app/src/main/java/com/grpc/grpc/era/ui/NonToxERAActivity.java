package com.grpc.grpc.era.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.era.pdf.NonToxERAPDFGenerator;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.reports.ui.ReportPreviewActivity;

import java.io.File;


/**
 * NonToxERAActivity.java
 *
 * This activity allows users to generate a Non-Toxic Environmental Risk Assessment (ERA) report.
 * Users can enter company details and capture a signature before generating a structured PDF report.
 *
 * Features:
 * - Input validation for company name, address, and email
 * - Captures a signature for inclusion in the report
 * - Generates a professionally formatted PDF document
 * - Clears input fields after generating the report
 * - Displays a confirmation message upon successful PDF creation
 *
 * Author: GRPC
 */


public class NonToxERAActivity extends AppCompatActivity {
    private static final int REQUEST_PREVIEW_ERA = 938;

    private EditText editCompanyName, editAddress, editEmail;

    private Bitmap signatureBitmap = null;
    private static final int REQUEST_SIGNATURE_CAPTURE = 1;

    private Button btnPreviewPDF;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nontox_era);

        // Initialize input fields
        editCompanyName = findViewById(R.id.editCompanyName);
        editAddress = findViewById(R.id.editAddress);
        editEmail = findViewById(R.id.editEmail);

        Button btnGeneratePDF = findViewById(R.id.btnGeneratePDF);
        btnPreviewPDF = findViewById(R.id.btnPreviewPDF);


        // Generate PDF
        btnGeneratePDF.setOnClickListener(view -> generatePDF());

        if (btnPreviewPDF != null) {
            btnPreviewPDF.setOnClickListener(v -> previewPDF());
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW_ERA) {
            if (resultCode == RESULT_OK
                    && data != null
                    && data.getBooleanExtra(ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
                generatePDF();
            }
            return;
        }
        if (requestCode == REQUEST_SIGNATURE_CAPTURE && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                signatureBitmap = (Bitmap) extras.get("data");

            }
        }
    }

    private void previewPDF() {
        String companyName = editCompanyName != null ? editCompanyName.getText().toString().trim() : "";
        String address = editAddress != null ? editAddress.getText().toString().trim() : "";
        String email = editEmail != null ? editEmail.getText().toString().trim() : "";

        if (companyName.isEmpty() || address.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        File previewDir = new File(getCacheDir(), "report_previews/era");
        if (!previewDir.exists()) previewDir.mkdirs();

        String pdfPath = NonToxERAPDFGenerator.generateNonToxicEnvironmentalRiskAssessment(
                this,
                companyName,
                address,
                email,
                signatureBitmap,
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
        String companyName = editCompanyName.getText().toString().trim();
        String address = editAddress.getText().toString().trim();
        String email = editEmail.getText().toString().trim();

        if (companyName.isEmpty() || address.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String pdfPath = NonToxERAPDFGenerator.generateNonToxicEnvironmentalRiskAssessment(this, companyName, address, email, signatureBitmap);
        if (pdfPath != null) {
            Toast.makeText(this, "PDF Saved: " + pdfPath, Toast.LENGTH_LONG).show();

            // Clear all input fields
            editCompanyName.setText("");
            editAddress.setText("");
            editEmail.setText("");

        } else {
            Toast.makeText(this, "Error creating PDF", Toast.LENGTH_SHORT).show();
        }
    }

}
