package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;

public class ToxicERAActivity extends AppCompatActivity {

    private EditText editCompanyName, editAddress, editEmail;

    private Bitmap signatureBitmap = null;
    private static final int REQUEST_SIGNATURE_CAPTURE = 1;

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



        // Generate PDF
        btnGeneratePDF.setOnClickListener(view -> generatePDF());
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGNATURE_CAPTURE && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                signatureBitmap = (Bitmap) extras.get("data");

            }
        }
    }

    private void generatePDF() {
        String companyName = editCompanyName.getText().toString().trim();
        String address = editAddress.getText().toString().trim();
        String email = editEmail.getText().toString().trim();

        if (companyName.isEmpty() || address.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        String pdfPath = ToxicERAPDFGenerator.generateEnvironmentalRiskAssessment(this, companyName, address, email, signatureBitmap);
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
