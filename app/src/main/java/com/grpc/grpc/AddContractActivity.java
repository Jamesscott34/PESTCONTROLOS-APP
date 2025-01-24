package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddContractActivity extends AppCompatActivity {

    private EditText nameEditText, addressEditText, emailEditText, contactEditText, visitsEditText;
    private Button addButton, backButton;
    private FirebaseFirestore db;
    private String userName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contract);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI elements
        nameEditText = findViewById(R.id.editTextName);
        addressEditText = findViewById(R.id.editTextAddress);
        emailEditText = findViewById(R.id.editTextEmail);
        contactEditText = findViewById(R.id.editTextContact);
        visitsEditText = findViewById(R.id.editTextVisits);
        addButton = findViewById(R.id.buttonAdd);
        backButton = findViewById(R.id.buttonBack);

        // Add Button Listener
        addButton.setOnClickListener(view -> {
            String name = nameEditText.getText().toString().trim();
            String address = addressEditText.getText().toString().trim();
            String email = emailEditText.getText().toString().trim();
            String contact = contactEditText.getText().toString().trim();
            String visits = visitsEditText.getText().toString().trim();

            // Validate required fields
            if (name.isEmpty() || address.isEmpty()) {
                Toast.makeText(AddContractActivity.this, "Name and Address are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Validate Visits field
            if (visits.isEmpty()) {
                visits = "N/A"; // Default to "N/A" if blank
            } else {
                try {
                    int visitsValue = Integer.parseInt(visits);
                    if (visitsValue < 1 || visitsValue > 13) {
                        Toast.makeText(AddContractActivity.this, "Visits must be between 1 and 13.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(AddContractActivity.this, "Visits must be a number between 1 and 13.", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Default empty email and contact to "N/A"
            if (email.isEmpty()) email = "N/A";
            if (contact.isEmpty()) contact = "N/A";

            // Use the extracted user name to create the collection
            String tableName = userName + " Contracts"; // e.g., "James Contracts"

            // Add contract to Firestore
            CollectionReference contractsCollection = db.collection(tableName);
            contractsCollection.add(createContractObject(name, address, email, contact, visits))
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(AddContractActivity.this, "Contract added successfully", Toast.LENGTH_SHORT).show();
                        clearFields();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddContractActivity.this, "Failed to add contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        // Back Button Listener
        backButton.setOnClickListener(view -> finish());
    }

    private Map<String, Object> createContractObject(String name, String address, String email, String contact, String visits) {
        Map<String, Object> contract = new HashMap<>();
        contract.put("name", name);
        contract.put("address", address);
        contract.put("email", email);
        contract.put("contact", contact);
        contract.put("visits", visits);
        return contract;
    }

    private void clearFields() {
        nameEditText.setText("");
        addressEditText.setText("");
        emailEditText.setText("");
        contactEditText.setText("");
        visitsEditText.setText("");
    }
}
