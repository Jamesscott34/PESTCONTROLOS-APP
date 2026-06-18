package com.grpc.grpc.quotations.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.quotations.model.BirdMaterialItem;
import com.grpc.grpc.quotations.pdf.BirdQuotationPDFGenerator;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.grpc.grpc.reports.ui.ReportPreviewActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BirdQuotationActivity.java
 *
 * This activity allows users to create a bird control service quotation.
 * Users can input customer details, add line items to the quotation,
 * and generate a PDF file containing all the details.
 *
 * Features:
 * - Input validation for customer details
 * - Ability to add multiple line items with descriptions and prices
 * - PDF generation for quotations
 * - Automatic field clearing after PDF generation
 * - Navigation back to the previous activity with user details
 *
 * Author: GRPC
 */

public class BirdQuotationActivity extends AppCompatActivity {

    private static final int REQUEST_PREVIEW_BIRD_QUOTE = 934;

    private EditText companyNameInput;
    private EditText addressInput, quoteDescriptionInput, userEmailInput, mobileNumberInput;
    private EditText itemDescriptionInput, itemPriceInput;
    private Button addItemButton, generatePdfButton, selectImageButton, previewPdfButton;
    private CheckBox checkDeposit30;
    private CheckBox checkDeposit50;
    private CheckBox passwordProtectCheckbox;
    private RadioGroup vatRadioGroup;
    private RadioButton vat13Radio;
    private RadioButton vat23Radio;
    private RecyclerView materialsRecyclerView;
    private Button addMaterialButton;
    private BirdMaterialsAdapter materialsAdapter;

    private String userName;

    private final List<String> descriptions = new ArrayList<>();
    private final List<Double> lineTotals = new ArrayList<>();
    private final List<BirdMaterialItem> materials = new ArrayList<>();
    private final List<Uri> selectedImageUris = new ArrayList<>();

    /** Keeps the quote number deterministic between preview and Confirm & Save. */
    private Integer lastPreviewQuoteRandomNum;

    /**
     * Initializes the activity, retrieves the username from intent,
     * and sets up UI elements. Handles button click events for adding
     * items and generating PDFs.
     *
     * @param savedInstanceState If the activity is being re-initialized
     *                           after previously being shut down, this
     *                           Bundle contains the most recent data.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bird_quotation);

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
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
        selectImageButton = findViewById(R.id.selectImageButton);
        checkDeposit30 = findViewById(R.id.check30PercentDeposit);
        checkDeposit50 = findViewById(R.id.check50PercentDeposit);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        // 30% and 50% deposit are mutually exclusive: either one or none
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
        vatRadioGroup = findViewById(R.id.vatRadioGroup);
        vat13Radio = findViewById(R.id.vat13Radio);
        vat23Radio = findViewById(R.id.vat23Radio);
        materialsRecyclerView = findViewById(R.id.materialsRecyclerView);
        addMaterialButton = findViewById(R.id.addMaterialButton);

        if (materialsRecyclerView != null) {
            materialsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            materialsAdapter = new BirdMaterialsAdapter(materials);
            materialsRecyclerView.setAdapter(materialsAdapter);
        }

        // Load email and number from centralized session (Firestore users/{StaffID})
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            String email = SessionManager.getEmail(this);
            String mobile = SessionManager.getNumber(this);
            if (userEmailInput != null && email != null && !email.trim().isEmpty())
                userEmailInput.setText(email.trim());
            if (mobileNumberInput != null && mobile != null && !mobile.trim().isEmpty())
                mobileNumberInput.setText(mobile.trim());
            if (userEmailInput != null && userEmailInput.getText().toString().trim().isEmpty())
                userEmailInput.setHint("Email (from session)");
            if (mobileNumberInput != null && mobileNumberInput.getText().toString().trim().isEmpty())
                mobileNumberInput.setHint("Number (from session)");
        }));

        addItemButton.setOnClickListener(v -> addItem());
        if (addMaterialButton != null) {
            addMaterialButton.setOnClickListener(v -> addMaterialRow());
        }
        if (selectImageButton != null) {
            selectImageButton.setOnClickListener(v -> openImageSelector());
        }
        if (previewPdfButton != null) {
            previewPdfButton.setOnClickListener(v -> previewBirdQuote());
        }
        generatePdfButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, password -> generatePdf(password, null));
            } else {
                generatePdf(null, null);
            }
        });

        // Restore state after configuration change
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        }
    }

    /**
     * Adds a line item to the quotation, including a description and a price.
     * Validates the input fields before adding them to the list.
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

    private void addMaterialRow() {
        materials.add(new BirdMaterialItem());
        if (materialsAdapter != null) {
            materialsAdapter.notifyItemInserted(materials.size() - 1);
        }
    }

    /**
     * Generates a PDF for the quotation with all the entered details.
     * Cost now comes solely from the single Item Price field; materials are descriptive only.
     */
    private void generatePdf(String ownerPassword) {
        generatePdf(ownerPassword, null);
    }

    private void generatePdf(String ownerPassword, @Nullable Integer quoteRandomNumOverride) {
        String companyName = companyNameInput != null
                ? companyNameInput.getText().toString().trim()
                : "";
        String address = addressInput.getText().toString().trim();
        String quoteDescription = quoteDescriptionInput.getText().toString().trim();
        String userEmail = userEmailInput.getText().toString().trim();
        String mobileNumber = mobileNumberInput.getText().toString().trim();

        if (address.isEmpty() || quoteDescription.isEmpty()) {
            Toast.makeText(this, "Please fill in address and quote description!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> pdfDescriptions = new ArrayList<>(descriptions);
        List<Double> pdfLineTotals = new ArrayList<>(lineTotals);

        String priceText = itemPriceInput != null ? itemPriceInput.getText().toString().trim() : "";
        String itemDesc = itemDescriptionInput != null ? itemDescriptionInput.getText().toString().trim() : "";

        // Support both flows:
        // 1) previously added line items already in descriptions/lineTotals
        // 2) the current visible item description/price fields used directly without tapping Add
        if (pdfDescriptions.isEmpty() || pdfLineTotals.isEmpty()) {
            if (priceText.isEmpty()) {
                Toast.makeText(this, "Please enter the item price!", Toast.LENGTH_SHORT).show();
                return;
            }

            double price;
            try {
                price = Double.parseDouble(priceText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid item price format!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (itemDesc.isEmpty()) itemDesc = quoteDescription;
            pdfDescriptions.add(itemDesc);
            pdfLineTotals.add(price);
        }

        int depositPercent = 0;
        if (checkDeposit30 != null && checkDeposit30.isChecked()) depositPercent = 30;
        else if (checkDeposit50 != null && checkDeposit50.isChecked()) depositPercent = 50;
        double vatRate = 0.135;
        if (vat23Radio != null && vat23Radio.isChecked()) {
            vatRate = 0.23;
        }

        BirdQuotationPDFGenerator.BirdQuotationResult result =
                BirdQuotationPDFGenerator.generateBirdQuotationPair(
                        companyName,
                        address,
                        quoteDescription,
                        pdfDescriptions,
                        pdfLineTotals,
                        materials,
                        userEmail,
                        mobileNumber,
                        depositPercent,
                        vatRate,
                        selectedImageUris.isEmpty() ? null : new ArrayList<>(selectedImageUris),
                        ownerPassword,
                        this,
                        null,
                        true,
                        quoteRandomNumOverride
                );

        if (result == null || result.getOfficePdf() == null || result.getCustomerPdf() == null) {
            Toast.makeText(this, "Error generating Bird Quotation PDFs", Toast.LENGTH_SHORT).show();
            return;
        }

        QuotationStorageUploader.uploadQuotationPdf(
                result.getOfficePdf(),
                null,
                e -> Toast.makeText(this, "Quotation upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
        QuotationStorageUploader.uploadQuotationPdf(
                result.getCustomerPdf(),
                null,
                e -> Toast.makeText(this, "Quotation upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );

        Toast.makeText(this, "PDF Generated Successfully!", Toast.LENGTH_SHORT).show();

        // Clear all fields after generating the PDF
        clearFields();

        // Navigate back to the previous activity with a result
        navigateBack();
    }

    private void previewBirdQuote() {
        String companyName = companyNameInput != null ? companyNameInput.getText().toString().trim() : "";
        String address = addressInput != null ? addressInput.getText().toString().trim() : "";
        String quoteDescription = quoteDescriptionInput != null ? quoteDescriptionInput.getText().toString().trim() : "";
        String userEmail = userEmailInput != null ? userEmailInput.getText().toString().trim() : "";
        String mobileNumber = mobileNumberInput != null ? mobileNumberInput.getText().toString().trim() : "";

        if (address.isEmpty() || quoteDescription.isEmpty()) {
            Toast.makeText(this, "Please fill in address and quote description!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> pdfDescriptions = new ArrayList<>(descriptions);
        List<Double> pdfLineTotals = new ArrayList<>(lineTotals);

        String priceText = itemPriceInput != null ? itemPriceInput.getText().toString().trim() : "";
        String itemDesc = itemDescriptionInput != null ? itemDescriptionInput.getText().toString().trim() : "";

        // Support both flows:
        // 1) previously added line items already in descriptions/lineTotals
        // 2) the current visible item description/price fields used directly without tapping Add
        if (pdfDescriptions.isEmpty() || pdfLineTotals.isEmpty()) {
            if (priceText.isEmpty()) {
                Toast.makeText(this, "Please enter the item price!", Toast.LENGTH_SHORT).show();
                return;
            }

            double price;
            try {
                price = Double.parseDouble(priceText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid item price format!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (itemDesc.isEmpty()) itemDesc = quoteDescription;
            pdfDescriptions.add(itemDesc);
            pdfLineTotals.add(price);
        }

        int depositPercent = 0;
        if (checkDeposit30 != null && checkDeposit30.isChecked()) depositPercent = 30;
        else if (checkDeposit50 != null && checkDeposit50.isChecked()) depositPercent = 50;

        double vatRate = 0.135;
        if (vat23Radio != null && vat23Radio.isChecked()) {
            vatRate = 0.23;
        }

        lastPreviewQuoteRandomNum = 1000 + new Random().nextInt(9000);

        File previewDir = new File(getCacheDir(), "report_previews/bird_quotes");
        if (!previewDir.exists()) previewDir.mkdirs();

        BirdQuotationPDFGenerator.BirdQuotationResult result =
                BirdQuotationPDFGenerator.generateBirdQuotationPair(
                        companyName,
                        address,
                        quoteDescription,
                        pdfDescriptions,
                        pdfLineTotals,
                        materials,
                        userEmail,
                        mobileNumber,
                        depositPercent,
                        vatRate,
                        selectedImageUris.isEmpty() ? null : new ArrayList<>(selectedImageUris),
                        null, // Preview: do not encrypt so ReportPreviewActivity can render it
                        this,
                        previewDir,
                        false, // Preview only: skip DB insert + finish()
                        lastPreviewQuoteRandomNum
                );

        if (result == null || result.getOfficePdf() == null) {
            Toast.makeText(this, "Error generating Bird Quotation preview", Toast.LENGTH_SHORT).show();
            return;
        }

        File previewPdf = result.getOfficePdf();
        if (previewPdf == null || !previewPdf.exists()) previewPdf = result.getCustomerPdf();
        if (previewPdf == null || !previewPdf.exists()) return;

        Intent previewIntent = new Intent(this, ReportPreviewActivity.class);
        previewIntent.putExtra(ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, previewPdf.getAbsolutePath());
        startActivityForResult(previewIntent, REQUEST_PREVIEW_BIRD_QUOTE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (companyNameInput != null) {
            outState.putString("companyName", companyNameInput.getText().toString());
        }
        outState.putString("address", addressInput != null ? addressInput.getText().toString() : "");
        outState.putString("quoteDescription", quoteDescriptionInput != null ? quoteDescriptionInput.getText().toString() : "");
        outState.putString("itemDescription", itemDescriptionInput != null ? itemDescriptionInput.getText().toString() : "");
        outState.putString("itemPrice", itemPriceInput != null ? itemPriceInput.getText().toString() : "");
        outState.putString("userEmail", userEmailInput != null ? userEmailInput.getText().toString() : "");
        outState.putString("mobileNumber", mobileNumberInput != null ? mobileNumberInput.getText().toString() : "");
        outState.putBoolean("deposit30", checkDeposit30 != null && checkDeposit30.isChecked());
        outState.putBoolean("deposit50", checkDeposit50 != null && checkDeposit50.isChecked());
        outState.putBoolean("vatIs23", vat23Radio != null && vat23Radio.isChecked());

        outState.putStringArrayList("itemDescriptions", new ArrayList<>(descriptions));
        ArrayList<Double> lineTotalsCopy = new ArrayList<>(lineTotals);
        double[] totalsArray = new double[lineTotalsCopy.size()];
        for (int i = 0; i < lineTotalsCopy.size(); i++) {
            totalsArray[i] = lineTotalsCopy.get(i);
        }
        outState.putDoubleArray("itemLineTotals", totalsArray);

        ArrayList<String> materialNames = new ArrayList<>();
        ArrayList<String> materialQtyText = new ArrayList<>();
        int[] materialQty = new int[materials.size()];
        double[] materialPrices = new double[materials.size()];
        for (int i = 0; i < materials.size(); i++) {
            BirdMaterialItem item = materials.get(i);
            materialNames.add(item.getMaterialName());
            materialQtyText.add(item.getQuantityDisplay());
            materialQty[i] = item.getQuantity();
            materialPrices[i] = item.getUnitPrice();
        }
        outState.putStringArrayList("materialNames", materialNames);
        outState.putStringArrayList("materialQtyText", materialQtyText);
        outState.putIntArray("materialQty", materialQty);
        outState.putDoubleArray("materialPrices", materialPrices);

        ArrayList<String> imageUriStrings = new ArrayList<>();
        for (Uri uri : selectedImageUris) {
            if (uri != null) imageUriStrings.add(uri.toString());
        }
        outState.putStringArrayList("imageUris", imageUriStrings);
    }

    private void restoreInstanceState(Bundle savedInstanceState) {
        if (companyNameInput != null) {
            companyNameInput.setText(savedInstanceState.getString("companyName", ""));
        }
        if (addressInput != null) {
            addressInput.setText(savedInstanceState.getString("address", ""));
        }
        if (quoteDescriptionInput != null) {
            quoteDescriptionInput.setText(savedInstanceState.getString("quoteDescription", ""));
        }
        if (itemDescriptionInput != null) {
            itemDescriptionInput.setText(savedInstanceState.getString("itemDescription", ""));
        }
        if (itemPriceInput != null) {
            itemPriceInput.setText(savedInstanceState.getString("itemPrice", ""));
        }
        if (userEmailInput != null) {
            userEmailInput.setText(savedInstanceState.getString("userEmail", ""));
        }
        if (mobileNumberInput != null) {
            mobileNumberInput.setText(savedInstanceState.getString("mobileNumber", ""));
        }
        if (checkDeposit30 != null) {
            checkDeposit30.setChecked(savedInstanceState.getBoolean("deposit30", false));
        }
        if (checkDeposit50 != null) {
            checkDeposit50.setChecked(savedInstanceState.getBoolean("deposit50", false));
        }
        boolean vatIs23 = savedInstanceState.getBoolean("vatIs23", false);
        if (vat23Radio != null && vat13Radio != null) {
            vat23Radio.setChecked(vatIs23);
            vat13Radio.setChecked(!vatIs23);
        }

        descriptions.clear();
        lineTotals.clear();
        ArrayList<String> descs = savedInstanceState.getStringArrayList("itemDescriptions");
        double[] totals = savedInstanceState.getDoubleArray("itemLineTotals");
        if (descs != null && totals != null && descs.size() == totals.length) {
            for (int i = 0; i < descs.size(); i++) {
                descriptions.add(descs.get(i));
                lineTotals.add(totals[i]);
            }
        }

        materials.clear();
        ArrayList<String> materialNames = savedInstanceState.getStringArrayList("materialNames");
        ArrayList<String> materialQtyText = savedInstanceState.getStringArrayList("materialQtyText");
        int[] materialQty = savedInstanceState.getIntArray("materialQty");
        double[] materialPrices = savedInstanceState.getDoubleArray("materialPrices");
        if (materialNames != null && materialQty != null && materialPrices != null
                && materialNames.size() == materialQty.length
                && materialNames.size() == materialPrices.length) {
            for (int i = 0; i < materialNames.size(); i++) {
                BirdMaterialItem item = new BirdMaterialItem(materialNames.get(i), materialQty[i], materialPrices[i]);
                if (materialQtyText != null && materialQtyText.size() > i) {
                    item.setQuantityText(materialQtyText.get(i));
                }
                materials.add(item);
            }
        }
        if (materialsAdapter != null) {
            materialsAdapter.notifyDataSetChanged();
        }

        selectedImageUris.clear();
        ArrayList<String> imageUriStrings = savedInstanceState.getStringArrayList("imageUris");
        if (imageUriStrings != null) {
            for (String s : imageUriStrings) {
                try {
                    selectedImageUris.add(Uri.parse(s));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void openImageSelector() {
        startActivityForResult(com.grpc.grpc.core.ReportImageStorage.createImagePickerIntent(), 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW_BIRD_QUOTE) {
            if (resultCode == RESULT_OK
                    && data != null
                    && data.getBooleanExtra(ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
                Integer override = lastPreviewQuoteRandomNum;
                if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                    PdfPasswordPrompt.prompt(this, pw -> generatePdf(pw, override));
                } else {
                    generatePdf(null, override);
                }
            }
            return;
        }
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            List<Uri> persisted = com.grpc.grpc.core.ReportImageStorage.persistFromPickerResult(this, data);
            if (!persisted.isEmpty()) {
                selectedImageUris.addAll(persisted);
            }
            Toast.makeText(this, selectedImageUris.size() + " images selected!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Clears all input fields and resets the lists of descriptions and prices.
     * This is called after a PDF is successfully generated.
     */
    private void clearFields() {
        addressInput.setText("");
        quoteDescriptionInput.setText("");
        userEmailInput.setText("");
        mobileNumberInput.setText("");
        itemDescriptionInput.setText("");
        itemPriceInput.setText("");
        descriptions.clear();
        lineTotals.clear();
        materials.clear();
        selectedImageUris.clear();
        if (materialsAdapter != null) {
            materialsAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Navigates back to the previous activity, passing the username back
     * as a result to maintain user session.
     */
    private void navigateBack() {
        Intent backIntent = new Intent();
        backIntent.putExtra("USER_NAME", userName);
        setResult(RESULT_OK, backIntent);
        finish();
    }
}
