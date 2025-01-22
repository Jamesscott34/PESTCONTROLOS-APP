package com.grpc.grpc;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class BirdQuotationActivity extends AppCompatActivity {

    private EditText addressInput, quoteDescriptionInput, userEmailInput, mobileNumberInput;
    private EditText itemDescriptionInput, itemPriceInput;
    private Button addItemButton, generatePdfButton;

    private final List<String> descriptions = new ArrayList<>();
    private final List<Double> lineTotals = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bird_quotation);

        // Initialize inputs
        addressInput = findViewById(R.id.addressInput);
        quoteDescriptionInput = findViewById(R.id.quoteDescriptionInput);
        userEmailInput = findViewById(R.id.userEmailInput);
        mobileNumberInput = findViewById(R.id.mobileNumberInput);
        itemDescriptionInput = findViewById(R.id.itemDescriptionInput);
        itemPriceInput = findViewById(R.id.itemPriceInput);
        addItemButton = findViewById(R.id.addItemButton);
        generatePdfButton = findViewById(R.id.generatePdfButton);

        // Add line item to the list
        addItemButton.setOnClickListener(v -> {
            String description = itemDescriptionInput.getText().toString().trim();
            String priceText = itemPriceInput.getText().toString().trim();

            if (description.isEmpty() || priceText.isEmpty()) {
                Toast.makeText(this, "Please fill in both description and price!", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double price = Double.parseDouble(priceText);
                descriptions.add(description);
                lineTotals.add(price);
                itemDescriptionInput.setText("");
                itemPriceInput.setText("");
                Toast.makeText(this, "Item added!", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid price format!", Toast.LENGTH_SHORT).show();
            }
        });

        // Generate PDF
        generatePdfButton.setOnClickListener(v -> {
            String address = addressInput.getText().toString().trim();
            String quoteDescription = quoteDescriptionInput.getText().toString().trim();
            String userEmail = userEmailInput.getText().toString().trim();
            String mobileNumber = mobileNumberInput.getText().toString().trim();

            if (address.isEmpty() || quoteDescription.isEmpty() || userEmail.isEmpty() || mobileNumber.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (descriptions.isEmpty() || lineTotals.isEmpty()) {
                Toast.makeText(this, "Please add at least one line item!", Toast.LENGTH_SHORT).show();
                return;
            }

            BirdQuotationPDFGenerator.generateBirdQuotation(
                    address,
                    quoteDescription,
                    descriptions,
                    lineTotals,
                    userEmail,
                    mobileNumber,
                    this
            );
        });
    }
}
