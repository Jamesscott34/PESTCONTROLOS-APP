package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

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

    private EditText addressInput, quoteDescriptionInput, userEmailInput, mobileNumberInput;
    private EditText itemDescriptionInput, itemPriceInput;
    private Button addItemButton, generatePdfButton;
    private CheckBox passwordProtectCheckbox;

    private String userName;

    private final List<String> descriptions = new ArrayList<>();
    private final List<Double> lineTotals = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_quotation);

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize inputs
        addressInput = findViewById(R.id.addressInput);
        quoteDescriptionInput = findViewById(R.id.quoteDescriptionInput);
        userEmailInput = findViewById(R.id.userEmailInput);
        mobileNumberInput = findViewById(R.id.mobileNumberInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        itemPriceInput = findViewById(R.id.itemPriceInput);
        addItemButton = findViewById(R.id.addItemButton);
        generatePdfButton = findViewById(R.id.generatePdfButton);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        // Add Item Button Listener
        addItemButton.setOnClickListener(v -> addItem());

        // Generate PDF Button Listener
        generatePdfButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generatePdf(pw));
            } else {
                generatePdf(null);
            }
        });
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
        String address = addressInput.getText().toString().trim();
        String quoteDescription = quoteDescriptionInput.getText().toString().trim();
        String userEmail = userEmailInput.getText().toString().trim();
        String mobileNumber = mobileNumberInput.getText().toString().trim();

        // Validate inputs
        if (address.isEmpty() || quoteDescription.isEmpty() || userEmail.isEmpty() || mobileNumber.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            Toast.makeText(this, "Please enter a valid email address!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mobileNumber.matches("\\d{10,15}")) { // Check for a valid mobile number
            Toast.makeText(this, "Please enter a valid mobile number!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (descriptions.isEmpty() || lineTotals.isEmpty()) {
            Toast.makeText(this, "Please add at least one line item!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate the PDF
        GeneralQuotationPDF.generateQuotation(
                address,
                quoteDescription,
                descriptions,
                lineTotals,
                userEmail,
                mobileNumber,
                ownerPassword,
                this
        );

        Toast.makeText(this, "PDF Generated Successfully!", Toast.LENGTH_SHORT).show();

        // Clear all fields after generating the PDF
        clearFields();

        // Navigate back to the previous activity with a result
        navigateBack();
    }

    /**
     * Clears all input fields and resets the lists.
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
