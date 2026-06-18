package com.grpc.grpc.quotations.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.core.QuotationStorageUploader;
import com.grpc.grpc.quotations.model.GeneralQuotationPDF;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GeneralQuotationActivity.java
 *
 * This activity allows users to create a general quotation by entering customer details,
 * adding multiple line items, and generating a structured PDF report.
 * The quotation is validated before generating the final document.
 *
 * Features:
 * - User input validation for address, email, and mobile number
 * - Allows adding multiple line items with descriptions and prices
 * - Generates a formatted PDF quotation
 * - Clears input fields after PDF generation
 * - Navigates back to the previous activity with user details
 *
 * Author: GRPC
 */


public class GeneralQuotationActivity extends AppCompatActivity {
    private static final int REQUEST_PREVIEW_GENERAL_QUOTATION = 920;
    private static final int REQUEST_PICK_IMAGES = 921;
    /** Ensures Preview and Confirm render the exact same quote number. */
    private Integer lastPreviewQuoteRandomNum;

    private EditText companyNameInput, addressInput, quoteDescriptionInput, userEmailInput, mobileNumberInput;
    private EditText itemDescriptionInput, itemPriceInput;
    private Button addItemButton, generatePdfButton, previewPdfButton, addImageButton;
    private CheckBox passwordProtectCheckbox;
    private CheckBox checkDeposit30;
    private CheckBox checkDeposit50;
    private RadioGroup vatRateGroup;
    private RadioButton vat13_5, vat23;
    private HorizontalScrollView imagesScrollView;
    private LinearLayout imagesContainer;

    private String userName;

    private final List<String> descriptions = new ArrayList<>();
    private final List<Double> lineTotals = new ArrayList<>();
    private final List<Uri> selectedImageUris = new ArrayList<>();
    private Uri technicianSignatureUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_quotation);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.trim().isEmpty()) {
            String sessionName = SessionManager.getName(this);
            userName = sessionName != null ? sessionName.trim() : "";
        }

        // Initialize inputs
        companyNameInput = findViewById(R.id.companyNameInput);
        addressInput = findViewById(R.id.addressInput);
        quoteDescriptionInput = findViewById(R.id.quoteDescriptionInput);
        userEmailInput = findViewById(R.id.userEmailInput);
        mobileNumberInput = findViewById(R.id.mobileNumberInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        itemPriceInput = findViewById(R.id.itemPriceInput);
        addItemButton = findViewById(R.id.addItemButton);
        generatePdfButton = findViewById(R.id.generatePdfButton);
        previewPdfButton = findViewById(R.id.previewPdfButton);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);
        checkDeposit30 = findViewById(R.id.check30PercentDeposit);
        checkDeposit50 = findViewById(R.id.check50PercentDeposit);
        if (checkDeposit30 != null) {
            checkDeposit30.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && checkDeposit50 != null) checkDeposit50.setChecked(false);
            });
        }
        if (checkDeposit50 != null) {
            checkDeposit50.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && checkDeposit30 != null) checkDeposit30.setChecked(false);
            });
        }
        vatRateGroup = findViewById(R.id.vatRateGroup);
        vat13_5 = findViewById(R.id.vat13_5);
        vat23 = findViewById(R.id.vat23);
        addImageButton = findViewById(R.id.addImageButton);
        imagesScrollView = findViewById(R.id.imagesScrollView);
        imagesContainer = findViewById(R.id.imagesContainer);

        // Add Item Button Listener
        addItemButton.setOnClickListener(v -> addItem());
        if (addImageButton != null) {
            addImageButton.setOnClickListener(v -> openImageSelector());
        }

        // Generate PDF Button Listener
        generatePdfButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generatePdf(pw));
            } else {
                generatePdf(null);
            }
        });
        previewPdfButton.setOnClickListener(v -> previewPdf());

        setupSignatureButton();

        // Hidden fields: autofill from Firestore session (same as Generic Quotation / Bird Quote).
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> applySessionContactToFields()));
    }

    private void setupSignatureButton() {
        Button technicianButton = findViewById(R.id.technicianSignatureButton);
        if (technicianButton != null) {
            technicianButton.setOnClickListener(v -> ReportSignatureHelper.showCaptureDialog(
                    this,
                    ReportSignatureHelper.TYPE_TECHNICIAN,
                    (uri, type) -> technicianSignatureUri = uri));
        }
    }

    /**
     * Adds a line item to the quotation.
     */
    private void addItem() {
        String description = itemDescriptionInput.getText().toString().trim();
        String priceText = itemPriceInput.getText().toString().trim();

        if (description.isEmpty() || priceText.isEmpty()) {
            Toast.makeText(this, "Please fill in both description and price!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double price = Double.parseDouble(priceText);

            // Add the inputs to the lists
            descriptions.add(description);
            lineTotals.add(price);

            // Clear the input fields after adding
            itemDescriptionInput.setText("");
            itemPriceInput.setText("");

            Toast.makeText(this, "Item added! You can add another.", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Generates a PDF for the quotation.
     */
    private void generatePdf(String ownerPassword) {
        generatePdf(ownerPassword, null);
    }

    private void generatePdf(String ownerPassword, Integer quoteRandomNumOverride) {
        File generatedFile = generatePdfInternal(ownerPassword, null, true, quoteRandomNumOverride);
        if (generatedFile == null) return;
        QuotationStorageUploader.uploadQuotationPdf(
                generatedFile,
                null,
                e -> Toast.makeText(this, "Quotation upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
        Toast.makeText(this, "PDF Generated Successfully!", Toast.LENGTH_SHORT).show();

        // Clear all fields after generating the PDF
        clearFields();

        // Navigate back to the previous activity with a result
        navigateBack();
    }

    private File generatePdfInternal(String ownerPassword, File outputDirectory, boolean persistAndFinish, Integer quoteRandomNumOverride) {
        String companyName = companyNameInput.getText().toString().trim();
        String address = addressInput.getText().toString().trim();
        String quoteDescription = quoteDescriptionInput.getText().toString().trim();
        String userEmail = resolveSessionEmail();
        String mobileNumber = resolveSessionMobile();
        String currentItemDescription = itemDescriptionInput.getText().toString().trim();
        String currentItemPrice = itemPriceInput.getText().toString().trim();

        // Validate customer-facing inputs only; email/mobile come from signed-in profile.
        if (companyName.isEmpty() || address.isEmpty() || quoteDescription.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields!", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Could not load your email from profile. Please log in again.", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (mobileNumber.isEmpty()) {
            Toast.makeText(this, "Could not load your mobile number from profile. Please log in again.", Toast.LENGTH_SHORT).show();
            return null;
        }

        List<String> pdfDescriptions = new ArrayList<>(descriptions);
        List<Double> pdfLineTotals = new ArrayList<>(lineTotals);
        // Include every added line item plus the current row if filled (same as Bird Quote).
        if (!currentItemDescription.isEmpty() || !currentItemPrice.isEmpty()) {
            if (currentItemDescription.isEmpty() || currentItemPrice.isEmpty()) {
                Toast.makeText(this, "Please complete both item description and price, or clear the item fields.", Toast.LENGTH_SHORT).show();
                return null;
            }
            try {
                pdfDescriptions.add(currentItemDescription);
                pdfLineTotals.add(Double.parseDouble(currentItemPrice));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid price format!", Toast.LENGTH_SHORT).show();
                return null;
            }
        }
        if (pdfDescriptions.isEmpty() || pdfLineTotals.isEmpty()
                || pdfDescriptions.size() != pdfLineTotals.size()) {
            Toast.makeText(this, "Please add at least one line item!", Toast.LENGTH_SHORT).show();
            return null;
        }

        return GeneralQuotationPDF.generateQuotation(
                companyName,
                address,
                quoteDescription,
                pdfDescriptions,
                pdfLineTotals,
                userEmail,
                mobileNumber,
                ownerPassword,
                this,
                outputDirectory,
                persistAndFinish,
                quoteRandomNumOverride,
                getSelectedVatRate(),
                getSelectedDepositPercent(),
                selectedImageUris.isEmpty() ? null : new ArrayList<>(selectedImageUris),
                technicianSignatureUri
        );
    }

    /** Prefers Firestore session; falls back to hidden field if user overrode it. */
    private String resolveSessionEmail() {
        String fromSession = SessionManager.getEmail(this);
        if (fromSession != null && !fromSession.trim().isEmpty()) {
            return fromSession.trim();
        }
        if (userEmailInput != null) {
            String fromField = userEmailInput.getText().toString().trim();
            if (!fromField.isEmpty()) return fromField;
        }
        return "";
    }

    /** Prefers Firestore session Number/Mobile/Phone; falls back to hidden field. */
    private String resolveSessionMobile() {
        String fromSession = SessionManager.getNumber(this);
        if (fromSession != null && !fromSession.trim().isEmpty()) {
            return fromSession.trim();
        }
        if (mobileNumberInput != null) {
            String fromField = mobileNumberInput.getText().toString().trim();
            if (!fromField.isEmpty()) return fromField;
        }
        return "";
    }

    private void applySessionContactToFields() {
        try {
            if (userEmailInput != null) {
                String existing = userEmailInput.getText() != null
                        ? userEmailInput.getText().toString().trim() : "";
                String emailFromSession = SessionManager.getEmail(this);
                if (existing.isEmpty() && emailFromSession != null && !emailFromSession.trim().isEmpty()) {
                    userEmailInput.setText(emailFromSession.trim());
                }
            }
            if (mobileNumberInput != null) {
                String existing = mobileNumberInput.getText() != null
                        ? mobileNumberInput.getText().toString().trim() : "";
                String mobileFromSession = SessionManager.getNumber(this);
                if (existing.isEmpty() && mobileFromSession != null && !mobileFromSession.trim().isEmpty()) {
                    mobileNumberInput.setText(mobileFromSession.trim());
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 0 = no deposit; 30 or 50 when a deposit checkbox is selected (same as Bird Quote). */
    private int getSelectedDepositPercent() {
        if (checkDeposit30 != null && checkDeposit30.isChecked()) return 30;
        if (checkDeposit50 != null && checkDeposit50.isChecked()) return 50;
        return 0;
    }

    private double getSelectedVatRate() {
        if (vat23 != null && vat23.isChecked()) {
            return GeneralQuotationPDF.VAT_RATE_REDUCED;
        }
        return GeneralQuotationPDF.VAT_RATE_STANDARD;
    }

    private void openImageSelector() {
        startActivityForResult(
                com.grpc.grpc.core.ReportImageStorage.createImagePickerIntent(),
                REQUEST_PICK_IMAGES);
    }

    private void refreshImagePreview() {
        if (imagesContainer == null || imagesScrollView == null) return;
        imagesContainer.removeAllViews();
        if (selectedImageUris.isEmpty()) {
            imagesScrollView.setVisibility(View.GONE);
            return;
        }
        imagesScrollView.setVisibility(View.VISIBLE);
        List<Uri> snapshot = new ArrayList<>(selectedImageUris);
        for (int i = 0; i < snapshot.size(); i++) {
            Uri uri = snapshot.get(i);
            Button chip = new Button(this);
            chip.setText("Image " + (i + 1) + " ✕");
            chip.setOnClickListener(v -> {
                selectedImageUris.remove(uri);
                refreshImagePreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            chip.setLayoutParams(lp);
            imagesContainer.addView(chip);
        }
    }

    private void previewPdf() {
        File previewDir = new File(getCacheDir(), "report_previews");
        if (!previewDir.exists()) previewDir.mkdirs();
        lastPreviewQuoteRandomNum = 1000 + new Random().nextInt(9000);
        File previewFile = generatePdfInternal(null, previewDir, false, lastPreviewQuoteRandomNum);
        if (previewFile == null || !previewFile.exists()) return;
        Intent previewIntent = new Intent(this, com.grpc.grpc.reports.ui.ReportPreviewActivity.class);
        previewIntent.putExtra(com.grpc.grpc.reports.ui.ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, previewFile.getAbsolutePath());
        startActivityForResult(previewIntent, REQUEST_PREVIEW_GENERAL_QUOTATION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW_GENERAL_QUOTATION
                && resultCode == RESULT_OK
                && data != null
                && data.getBooleanExtra(com.grpc.grpc.reports.ui.ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generatePdf(pw, lastPreviewQuoteRandomNum));
            } else {
                generatePdf(null, lastPreviewQuoteRandomNum);
            }
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGES && resultCode == RESULT_OK && data != null) {
            selectedImageUris.clear();
            selectedImageUris.addAll(com.grpc.grpc.core.ReportImageStorage.persistFromPickerResult(this, data));
            refreshImagePreview();
            Toast.makeText(this, selectedImageUris.size() + " image(s) added", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Clears all input fields and resets the lists.
     */
    private void clearFields() {
        if (companyNameInput != null) companyNameInput.setText("");
        addressInput.setText("");
        quoteDescriptionInput.setText("");
        itemDescriptionInput.setText("");
        applySessionContactToFields();
        itemPriceInput.setText("");
        descriptions.clear();
        lineTotals.clear();
        selectedImageUris.clear();
        refreshImagePreview();
    }

    /**
     * Navigates back to the previous activity with the username as a result.
     */
    private void navigateBack() {
        Intent backIntent = new Intent();
        backIntent.putExtra("USER_NAME", userName);
        setResult(RESULT_OK, backIntent);
        finish();
    }
}
