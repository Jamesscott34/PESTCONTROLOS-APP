package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * AddContractActivity.java
 *
 * This activity handles the addition of contracts to the Firestore database.
 * Users input contract details, and the data is validated before being stored.
 * If the logged-in user is "Kristine," they can choose which user's contract
 * to add. Otherwise, the contract is stored under the logged-in user's name.
 *
 * Features:
 * - User input validation
 * - Contract storage in Firestore
 * - Navigation to the ContractsActivity
 *
 * Author: James Scott
 */

public class AddContractActivity extends AppCompatActivity {


    /**
     * Initializes the activity, retrieves user information, and sets up UI elements.
     * Handles button click events for adding contracts and returning to the previous screen.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */
    private EditText nameEditText, addressEditText, emailEditText, contactEditText, visitsEditText;
    private Button addButton, backButton;
    private FirebaseFirestore db;
    private String userName;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contract);
        
        // Handle keyboard properly
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

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

            if ("Kristine".equalsIgnoreCase(userName)) {
                // Show dialog to select which user's contract to add
                showUserSelectionDialog(name, address, email, contact, visits);
            } else {
                // Use the extracted user name to create the collection
                String tableName = userName + " Contracts"; // e.g., "James Contracts"
                addContractToFirestore(tableName, name, address, email, contact, visits, userName);
            }
        });

        backButton.setOnClickListener(view -> {
            // Simply go back to the previous screen
            finish();
        });
    }
    /**
     * Displays a dialog for user selection when "Kristine" is adding a contract.
     * Allows selection between different users for contract assignment.
     *
     * @param name     The name of the contract holder.
     * @param address  The address of the contract holder.
     * @param email    The email associated with the contract.
     * @param contact  The contact number associated with the contract.
     * @param visits   The number of visits assigned in the contract.
     */

    private void showUserSelectionDialog(String name, String address, String email, String contact, String visits) {
        // List of usernames (replace with your actual list)
        String[] userNames = {"James", "Ian"};

        // Create a dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select User")
                .setItems(userNames, (dialog, which) -> {
                    String selectedUser = userNames[which];
                    String tableName = selectedUser + " Contracts"; // e.g., "James Contracts"

                    // Add contract with the selected user as the owner
                    addContractToFirestore(tableName, name, address, email, contact, visits, selectedUser);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    /**
     * Adds the contract to Firestore under the specified user's contract collection.
     * Validates and processes the input before uploading.
     *
     * @param tableName The Firestore collection where the contract will be stored.
     * @param name      The name of the contract holder.
     * @param address   The address of the contract holder.
     * @param email     The email associated with the contract.
     * @param contact   The contact number associated with the contract.
     * @param visits    The number of visits assigned in the contract.
     * @param owner     The owner of the contract entry.
     */
    private void addContractToFirestore(String tableName, String name, String address, String email, String contact, String visits, String owner) {
        CollectionReference contractsCollection = db.collection(tableName);
        Map<String, Object> contract = createContractObject(name, address, email, contact, visits, owner);

        contractsCollection.add(contract)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(AddContractActivity.this, "Contract added successfully to " + tableName, Toast.LENGTH_SHORT).show();
                    clearFields();
                    returnToContractsActivity();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AddContractActivity.this, "Failed to add contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    /**
     * Creates a contract object formatted as a HashMap.
     * Stores contract details including name, address, email, contact, visits, and owner.
     *
     * @param name     The name of the contract holder.
     * @param address  The address of the contract holder.
     * @param email    The email associated with the contract.
     * @param contact  The contact number associated with the contract.
     * @param visits   The number of visits assigned in the contract.
     * @param owner    The owner of the contract entry.
     * @return A Map containing the contract details.
     */
    private Map<String, Object> createContractObject(String name, String address, String email, String contact, String visits, String owner) {
        Map<String, Object> contract = new HashMap<>();
        contract.put("name", name);
        contract.put("address", address);
        contract.put("email", email);
        contract.put("contact", contact);
        contract.put("visits", visits);
        contract.put("addedBy", owner); // Keep track of who added the contract
        return contract;
    }
    /**
     * Clears the input fields after a contract is successfully added.
     * Resets all EditText fields to blank.
     */
    private void clearFields() {
        nameEditText.setText("");
        addressEditText.setText("");
        emailEditText.setText("");
        contactEditText.setText("");
        visitsEditText.setText("");
    }
    /**
     * Returns to the ContractsActivity after successfully adding a contract.
     * Simply finishes the current activity to go back to the previous screen.
     */
    private void returnToContractsActivity() {
        finish();
    }


}
