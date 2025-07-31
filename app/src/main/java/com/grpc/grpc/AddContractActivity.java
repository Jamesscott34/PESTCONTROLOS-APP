package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
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
    private Spinner assignedTechSpinner;

    private static final String[] TECHNICIANS = {"James", "Dean", "Ian"};

    private boolean canAssignContracts() {
        return "james".equalsIgnoreCase(userName)
                || "ian".equalsIgnoreCase(userName)
                || "kristine".equalsIgnoreCase(userName);
    }

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
        assignedTechSpinner = findViewById(R.id.assignedTechSpinner);

        // Assign-to dropdown for admins (prevents typos and ensures notifications go to correct inbox)
        if (assignedTechSpinner != null) {
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    TECHNICIANS
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            assignedTechSpinner.setAdapter(adapter);

            // Default selection = current user when possible
            int sel = 0;
            for (int i = 0; i < TECHNICIANS.length; i++) {
                if (TECHNICIANS[i].equalsIgnoreCase(userName)) {
                    sel = i;
                    break;
                }
            }
            assignedTechSpinner.setSelection(sel);

            boolean show = canAssignContracts();
            assignedTechSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
            android.view.View label = findViewById(R.id.assignTechLabel);
            if (label != null) label.setVisibility(show ? View.VISIBLE : View.GONE);
        }

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

            String owner = userName;
            if (assignedTechSpinner != null && assignedTechSpinner.getVisibility() == View.VISIBLE) {
                Object sel = assignedTechSpinner.getSelectedItem();
                if (sel != null) owner = String.valueOf(sel).trim();
            }
            if (owner == null || owner.trim().isEmpty()) owner = userName;

            String tableName = owner + " Contracts";
            addContractToFirestore(tableName, name, address, email, contact, visits, owner);
        });

        backButton.setOnClickListener(view -> {
            // Simply go back to the previous screen
            finish();
        });
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
        Map<String, Object> contract = createContractObject(name, address, email, contact, visits, owner, userName);

        contractsCollection.add(contract)
                .addOnSuccessListener(documentReference -> {
                    writeInAppContractAssignmentIfNeeded(documentReference.getId(), name, owner, userName);
                    writeInAppContractOversightIfNeeded(documentReference.getId(), name, owner, userName);
                    Toast.makeText(AddContractActivity.this, "Contract added successfully to " + tableName, Toast.LENGTH_SHORT).show();
                    clearFields();
                    returnToContractsActivity();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(AddContractActivity.this, "Failed to add contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * In-app notification history (NOT system push).
     * If anyone assigns a contract to another technician's contracts, notify that technician.
     */
    private void writeInAppContractAssignmentIfNeeded(String contractId, String contractName, String owner, String createdBy) {
        try {
            if (contractId == null || contractId.trim().isEmpty()) return;
            String creatorLower = createdBy != null ? createdBy.trim().toLowerCase(Locale.getDefault()) : "";

            String ownerName = owner != null ? owner.trim() : "";
            String ownerLower = ownerName.toLowerCase(Locale.getDefault());
            if (ownerLower.isEmpty()) return;
            if (ownerLower.equals(creatorLower)) return;

            Map<String, Object> data = new HashMap<>();
            data.put("contractId", contractId);
            data.put("contractName", contractName);
            data.put("userName", ownerName);
            data.put("createdBy", createdBy);
            data.put("type", "contract_update");

            NotificationUtils.writeInAppNotification(
                    ownerName,
                    "contract_assigned_" + contractId,
                    "📋 New Contract Assigned",
                    (contractName != null ? contractName : "A contract") + " has been assigned to you"
                            + (createdBy != null && !createdBy.trim().isEmpty() ? ("\nAssigned by " + createdBy) : ""),
                    "contract_update",
                    data
            );
        } catch (Exception ignored) {
        }
    }

    /**
     * Oversight notification: when Dean adds a contract, notify Ian/James/Kristine.
     */
    private void writeInAppContractOversightIfNeeded(String contractId, String contractName, String owner, String createdBy) {
        try {
            if (contractId == null || contractId.trim().isEmpty()) return;
            String creator = createdBy != null ? createdBy.trim() : "";
            String creatorLower = creator.toLowerCase(Locale.getDefault());
            if (!"dean".equals(creatorLower)) return;

            String ownerName = owner != null ? owner.trim() : "";
            if (ownerName.isEmpty()) ownerName = "Dean";

            Map<String, Object> data = new HashMap<>();
            data.put("contractId", contractId);
            data.put("contractName", contractName);
            data.put("userName", ownerName); // open Dean's contracts
            data.put("createdBy", creator);
            data.put("type", "contract_update");

            String title = "📋 Contract added";
            String body = "Dean added a contract"
                    + (contractName != null && !contractName.trim().isEmpty() ? (": " + contractName.trim()) : "")
                    + " (assigned to " + ownerName + ")";

            NotificationUtils.writeInAppNotification("ian", "contract_added_by_dean_" + contractId + "_ian", title, body, "contract_update", data);
            NotificationUtils.writeInAppNotification("james", "contract_added_by_dean_" + contractId + "_james", title, body, "contract_update", data);
            NotificationUtils.writeInAppNotification("kristine", "contract_added_by_dean_" + contractId + "_kristine", title, body, "contract_update", data);
        } catch (Exception ignored) {
        }
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
    private Map<String, Object> createContractObject(String name, String address, String email, String contact, String visits, String owner, String createdBy) {
        Map<String, Object> contract = new HashMap<>();
        contract.put("name", name);
        contract.put("address", address);
        contract.put("email", email);
        contract.put("contact", contact);
        contract.put("visits", visits);
        contract.put("addedBy", owner);
        contract.put("createdBy", createdBy != null ? createdBy : owner);
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
