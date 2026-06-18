package com.grpc.grpc.serviceagreements.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.serviceagreements.pdf.ServiceAgreementGenerator;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.grpc.grpc.reports.ui.ReportPreviewActivity;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;


/**
 * ServiceAgreementActivity.java
 *
 * This activity allows users to generate a structured service agreement for pest control services.
 * Users can input customer details, select the number of visits per year, and generate a professional
 * PDF service agreement.
 *
 * Features:
 * - Input validation for customer name, address, email, phone, VAT number, and service cost
 * - Allows users to select between 4, 6, 8, or 12 visits per year
 * - Calculates VAT and total cost based on user input
 * - Generates a structured PDF service agreement
 * - Clears input fields after successful PDF generation
 * - Navigates back to the previous activity with user details
 *
 * Author: GRPC
 */


public class ServiceAgreementActivity extends AppCompatActivity {
    private static final int REQUEST_PREVIEW_SERVICE_AGREEMENT = 936;

    private EditText etCustomerName, etCustomerAddress, etCustomerEmail, etCustomerPhone, etVatNumber, etGrpcOffice;
    private TextView welcomeTextView;
    private int selectedVisitsPerYear = 8; // Default visits
    private CheckBox passwordProtectCheckbox;
    private Button btnPreviewPdf;

    private String userName;

    @SuppressLint({"MissingInflatedId", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_agreement);

        // Wrap content in a ScrollView to allow scrolling
        ScrollView scrollView = findViewById(R.id.scrollView);

        // Retrieve the username safely
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) {
            userName = "Guest"; // Default value
        }


        // Initialize welcome text view safely
        welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                if (welcomeTextView == null) return;
                String name = SessionManager.getName(this);
                if (name != null && !name.trim().isEmpty()) {
                    userName = name.trim();
                    welcomeTextView.setText("Welcome, " + userName + "!");
                }
            }));
        } else {
            Log.e("ServiceAgreementActivity", "welcomeTextView is NULL! Check XML ID.");
        }

        etCustomerName = findViewById(R.id.etCustomerName);
        etCustomerAddress = findViewById(R.id.etCustomerAddress);
        etCustomerEmail = findViewById(R.id.etCustomerEmail);
        etCustomerPhone = findViewById(R.id.etCustomerPhone);
        etVatNumber = findViewById(R.id.etVatNumber);
        etGrpcOffice = findViewById(R.id.etGrpcOffice); // GRPC Office (should not be price)

        Button btnAgreement4 = findViewById(R.id.btnAgreement4);
        Button btnAgreement6 = findViewById(R.id.btnAgreement6);
        Button btnAgreement8 = findViewById(R.id.btnAgreement8);
        Button btnAgreement12 = findViewById(R.id.btnAgreement12);
        Button btnGeneratePdf = findViewById(R.id.btnGeneratePdf);
        btnPreviewPdf = findViewById(R.id.btnPreviewPdf);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        btnAgreement4.setOnClickListener(v -> selectVisitsPerYear(4));
        btnAgreement6.setOnClickListener(v -> selectVisitsPerYear(6));
        btnAgreement8.setOnClickListener(v -> selectVisitsPerYear(8));
        btnAgreement12.setOnClickListener(v -> selectVisitsPerYear(12));

        btnGeneratePdf.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generatePdf(pw));
            } else {
                generatePdf(null);
            }
        });

        if (btnPreviewPdf != null) {
            btnPreviewPdf.setOnClickListener(v -> previewAgreement());
        }
    }

    private void selectVisitsPerYear(int visits) {
        selectedVisitsPerYear = visits;
        Toast.makeText(this, visits + " Visits Per Year Selected", Toast.LENGTH_SHORT).show();
    }

    private void generatePdf(String ownerPassword) {
        Log.d("ServiceAgreementActivity", "Generating PDF for: " + userName);

        String name = etCustomerName.getText().toString();
        String address = etCustomerAddress.getText().toString();
        String email = etCustomerEmail.getText().toString();
        String phone = etCustomerPhone.getText().toString();
        String vat = etVatNumber.getText().toString();
        String grpcOffice = etGrpcOffice.getText().toString();

        // Ensure all fields are filled
        if (name.isEmpty() || address.isEmpty() || email.isEmpty() || phone.isEmpty() || vat.isEmpty() || grpcOffice.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_LONG).show();
            return;
        }

        // Convert VAT string to double safely
        double vatValue;
        try {
            vatValue = Double.parseDouble(vat);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid VAT format!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert grpcOffice to price safely
        double totalCost;
        try {
            totalCost = Double.parseDouble(grpcOffice);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate total price including VAT
        double totalPriceWithVat = totalCost + (totalCost * vatValue / 100);
        double pricePerQuarterWithVat = totalPriceWithVat / 4;

        // Call PDF generation method
        String pdfPath = ServiceAgreementGenerator.generateServiceAgreement(
                this,
                name,
                address,
                email,
                phone,
                vat,
                getTechnicianNameForPdf(),
                grpcOffice,
                totalPriceWithVat, // Now includes VAT
                selectedVisitsPerYear,
                ownerPassword
        );

        if (pdfPath != null) {
            Toast.makeText(this, "PDF Created: " + pdfPath, Toast.LENGTH_LONG).show();

            // Clear fields after successful PDF generation
            clearFields();

            // Navigate back to previous activity
            goBackToPreviousActivity();
        }
    }

    private String getTechnicianNameForPdf() {
        String sessionName = SessionManager.getName(this);
        if (sessionName != null && !sessionName.trim().isEmpty()) {
            return sessionName.trim();
        }
        if (userName != null && !userName.trim().isEmpty()) {
            return userName.trim();
        }
        return "User";
    }

    private void previewAgreement() {
        String name = etCustomerName != null ? etCustomerName.getText().toString().trim() : "";
        String address = etCustomerAddress != null ? etCustomerAddress.getText().toString().trim() : "";
        String email = etCustomerEmail != null ? etCustomerEmail.getText().toString().trim() : "";
        String phone = etCustomerPhone != null ? etCustomerPhone.getText().toString().trim() : "";
        String vat = etVatNumber != null ? etVatNumber.getText().toString().trim() : "";
        String grpcOffice = etGrpcOffice != null ? etGrpcOffice.getText().toString().trim() : "";

        if (name.isEmpty() || address.isEmpty() || email.isEmpty() || phone.isEmpty() || vat.isEmpty() || grpcOffice.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_LONG).show();
            return;
        }

        double vatValue;
        try {
            vatValue = Double.parseDouble(vat);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid VAT format!", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalCost;
        try {
            totalCost = Double.parseDouble(grpcOffice);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format!", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalPriceWithVat = totalCost + (totalCost * vatValue / 100);

        File previewDir = new File(getCacheDir(), "report_previews/service_agreements");
        if (!previewDir.exists()) previewDir.mkdirs();

        String pdfPath = ServiceAgreementGenerator.generateServiceAgreement(
                this,
                name,
                address,
                email,
                phone,
                vat,
                getTechnicianNameForPdf(),
                grpcOffice,
                totalPriceWithVat,
                selectedVisitsPerYear,
                null, // Preview: do not encrypt so it can render
                previewDir
        );

        if (pdfPath == null) {
            Toast.makeText(this, "Unable to generate preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        File previewPdf = new File(pdfPath);
        if (!previewPdf.exists()) {
            Toast.makeText(this, "Unable to generate preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent previewIntent = new Intent(this, ReportPreviewActivity.class);
        previewIntent.putExtra(ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, previewPdf.getAbsolutePath());
        startActivityForResult(previewIntent, REQUEST_PREVIEW_SERVICE_AGREEMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PREVIEW_SERVICE_AGREEMENT) return;

        if (resultCode == RESULT_OK
                && data != null
                && data.getBooleanExtra(ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generatePdf(pw));
            } else {
                generatePdf(null);
            }
        }
    }



    // Clear all input fields after generating the PDF
    private void clearFields() {
        etCustomerName.setText("");
        etCustomerAddress.setText("");
        etCustomerEmail.setText("");
        etCustomerPhone.setText("");
        etVatNumber.setText("");
        etGrpcOffice.setText("");
    }

    // Function to navigate back to the previous activity
    private void goBackToPreviousActivity() {
        Intent intent = new Intent();
        intent.putExtra("USER_NAME", userName); // Pass the correct username back if needed
        setResult(RESULT_OK, intent); // Set result to indicate successful completion
        finish(); // Close this activity and return to the previous one
    }

}
