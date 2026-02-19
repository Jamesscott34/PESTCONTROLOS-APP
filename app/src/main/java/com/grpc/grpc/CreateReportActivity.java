package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * CreateReportActivity.java
 *
 * This activity allows users to generate a quotation report by entering customer details,
 * adding line items, and generating a structured PDF report. The report is then stored in
 * the database and can be reviewed later.
 *
 * Features:
 * - User input validation for customer details
 * - Dynamic addition of line items
 * - Auto-generated quote number with incremental updates
 * - PDF generation and database storage of the quotation
 * - Navigation back to the previous activity with user details
 *
 * Author: GRPC
 */

public class CreateReportActivity extends AppCompatActivity {

    private EditText dateInput, addressInput, quoteDescriptionInput, emailInput, mobileNumberInput;
    private LinearLayout lineItemsContainer;
    private Button addLineItemButton, generateQuoteButton, backButton;
    private CheckBox passwordProtectCheckbox;
    private static int quoteNumberCounter = 50001; // Auto-incrementing quote number
    private List<EditText> descriptionInputs = new ArrayList<>();
    private List<EditText> lineTotalInputs = new ArrayList<>();

    private String userName;
    /**
     * Initializes the activity, retrieves the username from intent, sets up UI elements,
     * and handles button click events for adding line items and generating reports.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quotation_report);
        
        // Handle keyboard properly
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Auto-fill company name and address if passed from ViewContractActivity
        String companyName = getIntent().getStringExtra("COMPANY_NAME");
        String address = getIntent().getStringExtra("ADDRESS");

        // Initialize UI components
        dateInput = findViewById(R.id.dateInput);
        addressInput = findViewById(R.id.addressInput);
        quoteDescriptionInput = findViewById(R.id.quoteDescriptionInput);
        emailInput = findViewById(R.id.emailInput);
        mobileNumberInput = findViewById(R.id.mobileNumberInput);
        lineItemsContainer = findViewById(R.id.lineItemsContainer);
        addLineItemButton = findViewById(R.id.addLineItemButton);
        generateQuoteButton = findViewById(R.id.generateQuoteButton);
        backButton = findViewById(R.id.backButton);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        // Auto-generate quote number
        EditText quoteNumberInput = findViewById(R.id.quoteNumberInput);
        quoteNumberInput.setText(String.valueOf(quoteNumberCounter));

        // Auto-fill company name and address if provided
        if (companyName != null && !companyName.isEmpty() && !companyName.equals("N/A")) {
            quoteDescriptionInput.setText(companyName);
        }
        if (address != null && !address.isEmpty() && !address.equals("N/A")) {
            addressInput.setText(address);
        }

        // Button Click Listeners
        addLineItemButton.setOnClickListener(v -> addLineItem());
        generateQuoteButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                showPasswordDialogAndGenerate();
            } else {
                generatePDFReport(null);
            }
        });
        backButton.setOnClickListener(v -> navigateBackToPreviousActivity());

        // Add the first line item field automatically
        addLineItem();
    }
    /**
     * Navigates back to the previous activity.
     * Simply finishes the current activity to return to the previous screen.
     */
    private void navigateBackToPreviousActivity() {
        finish();
    }
    /**
     * Dynamically adds a new line item input field for description and price.
     * Allows users to enter multiple items for the quotation.
     */
    private void addLineItem() {
        LinearLayout lineItemLayout = new LinearLayout(this);
        lineItemLayout.setOrientation(LinearLayout.HORIZONTAL);
        lineItemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText descriptionInput = new EditText(this);
        descriptionInput.setHint("Enter Description");
        lineItemLayout.addView(descriptionInput);

        EditText lineTotalInput = new EditText(this);
        lineTotalInput.setHint("Enter Line Total (€)");
        lineTotalInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        lineItemLayout.addView(lineTotalInput);

        lineItemsContainer.addView(lineItemLayout);
        descriptionInputs.add(descriptionInput);
        lineTotalInputs.add(lineTotalInput);
    }
    /**
     * Shows a password dialog; on confirm calls generatePDFReport with the entered password.
     */
    private void showPasswordDialogAndGenerate() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter password (min 6 characters)");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(20, 20, 20, 20);
        final EditText confirmInput = new EditText(this);
        confirmInput.setHint("Confirm password");
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmInput.setPadding(20, 20, 20, 20);
        layout.addView(passwordInput);
        layout.addView(confirmInput);

        new AlertDialog.Builder(this)
                .setTitle("Password protect PDF")
                .setMessage("Set an owner password. It will be required to edit the PDF.")
                .setView(layout)
                .setPositiveButton("Generate PDF", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    String confirm = confirmInput.getText().toString();
                    if (password.trim().isEmpty()) {
                        Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (password.length() < 6) {
                        Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!password.equals(confirm)) {
                        Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    generatePDFReport(password);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Gathers all input data, validates the fields, calculates the total price,
     * saves the report to the database, and generates a compressed PDF report.
     * @param ownerPassword If non-null, PDF is encrypted with this password (editing restricted).
     */
    private void generatePDFReport(String ownerPassword) {
        String userEmail = emailInput.getText().toString().trim();
        String mobileNumber = mobileNumberInput.getText().toString().trim();
        String date = dateInput.getText().toString().trim();
        String address = addressInput.getText().toString().trim();
        String quoteDescription = quoteDescriptionInput.getText().toString().trim();
        String quoteNumber = String.valueOf(quoteNumberCounter);

        // Collect line items from the dynamic fields
        List<String> descriptions = new ArrayList<>();
        List<Double> lineTotals = new ArrayList<>();

        for (int i = 0; i < descriptionInputs.size(); i++) {
            String description = descriptionInputs.get(i).getText().toString().trim();
            String lineTotalStr = lineTotalInputs.get(i).getText().toString().trim();
            if (!description.isEmpty() && !lineTotalStr.isEmpty()) {
                try {
                    double lineTotal = Double.parseDouble(lineTotalStr);
                    descriptions.add(description);
                    lineTotals.add(lineTotal);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid line total format.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }

        double totalAmount = calculateTotal(lineTotals);

        // Save the report to the database
        ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(this);
        dbHelper.insertQuote(
                quoteNumber, date, address, quoteDescription,
                totalAmount, userEmail, mobileNumber, true);

        // Generate the PDF (compressed; optional password)
        PDFQuotationReportGenerator.generateQuotationReport(
                quoteNumber, address, quoteDescription,
                descriptions, lineTotals, userEmail, mobileNumber, ownerPassword, this);

        Toast.makeText(this, "Report Generated Successfully!", Toast.LENGTH_SHORT).show();
        quoteNumberCounter++;

        navigateBackToPreviousActivity();
    }
    /**
     * Calculates the total amount based on the line item prices.
     *
     * @param lineTotals A list of double values representing item prices.
     * @return The total calculated amount.
     */
    private double calculateTotal(List<Double> lineTotals) {
        double total = 0;
        for (double lineTotal : lineTotals) {
            total += lineTotal;
        }
        return total;
    }
}
