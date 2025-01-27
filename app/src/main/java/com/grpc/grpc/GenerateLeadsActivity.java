package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GenerateLeadsActivity extends AppCompatActivity {

    private EditText premiseNameEditText, premiseAddressEditText, priceQuotedEditText, reasonEditText;
    private TextView commissionTextView, dateTextView;
    private Button addLeadButton, backButton;

    private FirebaseFirestore db;
    private String userName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_leads);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Retrieve the username from the intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize the leads container and welcome message
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Initialize UI elements
        premiseNameEditText = findViewById(R.id.premiseNameEditText);
        premiseAddressEditText = findViewById(R.id.premiseAddressEditText);
        priceQuotedEditText = findViewById(R.id.priceQuotedEditText);
        reasonEditText = findViewById(R.id.reasonEditText); // Reason field
        commissionTextView = findViewById(R.id.commissionTextView);
        dateTextView = findViewById(R.id.dateTextView);
        addLeadButton = findViewById(R.id.addLeadButton);
        backButton = findViewById(R.id.backButton);

        // Set the current date
        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        dateTextView.setText("Date: " + currentDate);

        // Automatically calculate and update commission when price is entered or changed
        priceQuotedEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String priceQuotedStr = s.toString().trim();
                if (!priceQuotedStr.isEmpty()) {
                    try {
                        double priceQuoted = Double.parseDouble(priceQuotedStr);
                        double commission = priceQuoted * 0.10; // Calculate 10% commission
                        commissionTextView.setText("Commission: €" + String.format(Locale.getDefault(), "%.2f", commission));
                    } catch (NumberFormatException e) {
                        commissionTextView.setText("Commission: €0.00");
                        Toast.makeText(GenerateLeadsActivity.this, "Please enter a valid price.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    commissionTextView.setText("Commission: €0.00");
                }
            }
        });

        // Add Lead Button Listener
        addLeadButton.setOnClickListener(view -> {
            String premiseName = premiseNameEditText.getText().toString().trim();
            String premiseAddress = premiseAddressEditText.getText().toString().trim();
            String priceQuotedStr = priceQuotedEditText.getText().toString().trim();
            String reason = reasonEditText.getText().toString().trim(); // Capture reason

            // Validate required fields
            if (premiseName.isEmpty() || premiseAddress.isEmpty() || priceQuotedStr.isEmpty() || reason.isEmpty()) {
                Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            double priceQuoted;
            try {
                priceQuoted = Double.parseDouble(priceQuotedStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid price.", Toast.LENGTH_SHORT).show();
                return;
            }

            double commission = priceQuoted * 0.10;

            // Save lead to the global "Leads" collection
            CollectionReference leadsCollection = db.collection("Leads");
            leadsCollection.add(createLeadObject(premiseName, premiseAddress, priceQuoted, commission, currentDate, reason, userName))
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Lead added successfully", Toast.LENGTH_SHORT).show();
                        // Redirect to ViewLeadsActivity
                        Intent intent = new Intent(GenerateLeadsActivity.this, ViewLeadsActivity.class);
                        intent.putExtra("USER_NAME", userName); // Pass the username
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to add lead: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        // Back Button Listener
        backButton.setOnClickListener(view -> finish());
    }

    private Map<String, Object> createLeadObject(String premiseName, String premiseAddress, double priceQuoted, double commission, String date, String reason, String userName) {
        Map<String, Object> lead = new HashMap<>();
        lead.put("Premise Name", premiseName);
        lead.put("Premise Address", premiseAddress);
        lead.put("Price Quoted", priceQuoted);
        lead.put("Commission", commission);
        lead.put("Date", date);
        lead.put("Reason", reason); // Add reason to the database object
        lead.put("Added By", userName); // Add username to the database object
        return lead;
    }


}
