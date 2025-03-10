package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

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
 * Author: James Scott
 */

public class CreateReportActivity extends AppCompatActivity {

    private EditText dateInput, addressInput, quoteDescriptionInput, emailInput, mobileNumberInput;
    private LinearLayout lineItemsContainer;
    private Button addLineItemButton, generateQuoteButton, backButton;
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

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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

        // Auto-generate quote number
        EditText quoteNumberInput = findViewById(R.id.quoteNumberInput);
        quoteNumberInput.setText(String.valueOf(quoteNumberCounter));

        // Button Click Listeners
        addLineItemButton.setOnClickListener(v -> addLineItem());
        generateQuoteButton.setOnClickListener(v -> generatePDFReport());
        backButton.setOnClickListener(v -> navigateBackToPreviousActivity());

        // Add the first line item field automatically
        addLineItem();
    }
    /**
     * Navigates back to the previous activity, passing the username as a result
     * to maintain session continuity.
     */
    private void navigateBackToPreviousActivity() {
        Intent backIntent = new Intent();
        backIntent.putExtra("USER_NAME", userName); // Pass the username back
        setResult(RESULT_OK, backIntent); // Set result for the previous activity
        finish(); // Close the activity
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
     * Gathers all input data, validates the fields, calculates the total price,
     * saves the report to the database, and generates a PDF report.
     */
    private void generatePDFReport() {
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

        // Generate the PDF
        PDFQuotationReportGenerator.generateQuotationReport(
                quoteNumber, address, quoteDescription,
                descriptions, lineTotals, userEmail, mobileNumber, this);

        Toast.makeText(this, "Report Generated Successfully!", Toast.LENGTH_SHORT).show();
        quoteNumberCounter++;

        // Return to the previous activity with username
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
