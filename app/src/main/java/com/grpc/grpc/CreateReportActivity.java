package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for creating a detailed quotation report with dynamic line items and professional PDF generation.
 */
public class CreateReportActivity extends AppCompatActivity {

    private EditText dateInput, addressInput, quoteDescriptionInput, emailInput, mobileNumberInput;
    private LinearLayout lineItemsContainer;
    private Button addLineItemButton, generateQuoteButton, backButton;
    private static int quoteNumberCounter = 50001; // Auto-incrementing quote number
    private List<EditText> descriptionInputs = new ArrayList<>();
    private List<EditText> lineTotalInputs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quotation_report);

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
        backButton.setOnClickListener(v -> finish());

        // Add the first line item field automatically
        addLineItem();
    }

    /**
     * Adds a dynamic line item to the report.
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
     * Generates the PDF quotation report and saves it to the GRPEST_QUOTES folder.
     * Validates user inputs, collects line items, and stores the report in the database.
     */
    /**
     * Generates a PDF quotation report and saves it to the database.
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

        // Save the report to the database and generate the PDF
        double totalAmount = calculateTotal(lineTotals);
        ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(this);

// Insert the quote into the database with corrected arguments
        dbHelper.insertQuote(
                quoteNumber,                   // Quote Number
                date,                          // Date
                address,                       // Address
                quoteDescription,              // Quote Description
                totalAmount,                   // Total Amount
                userEmail,                     // User Email
                mobileNumber                   // Mobile Number
        );

// Generate the PDF using the corrected arguments
        PDFQuotationReportGenerator.generateQuotationReport(
                quoteNumber,                   // Quote Number
                address,                       // Customer Address
                quoteDescription,              // Quote Description
                descriptions,                  // List of Descriptions for Line Items
                lineTotals,                    // List of Line Totals
                userEmail,                     // User Email
                mobileNumber,                  // Mobile Number
                this                           // Context
        );

        Toast.makeText(this, "Report Generated Successfully!", Toast.LENGTH_SHORT).show();
        quoteNumberCounter++;
    }

    private double calculateTotal(List<Double> lineTotals) {
        double total = 0;
        for (double lineTotal : lineTotals) {
            total += lineTotal;
        }
        return total;
    }
}
