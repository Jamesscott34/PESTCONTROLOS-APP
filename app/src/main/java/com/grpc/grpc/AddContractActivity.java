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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AddContractActivity.java
 *
 * This activity handles the addition of contracts to the Firestore database.
 * Users input contract details, and the data is validated before being stored.
 * Admin users can choose which technician's contracts to add to; technicians add to their own.
 *
 * Features:
 * - User input validation
 * - Contract storage in Firestore
 * - Navigation to the ContractsActivity
 *
 * Author: GRPC
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

    private List<StaffDirectory.OwnerOption> ownerOptions = new ArrayList<>();
    private String[] ownerOptionKeys = new String[0]; // ContractKey values for spinner storage

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

        if (assignedTechSpinner != null) {
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    new String[]{"Loading..."}
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            assignedTechSpinner.setAdapter(adapter);
            android.view.View label = findViewById(R.id.assignTechLabel);

            // Resolve admin role after session is loaded so admins see the owner spinner and can add to another person.
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                boolean show = SessionManager.isAdmin(this);
                assignedTechSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
                if (label != null) label.setVisibility(show ? View.VISIBLE : View.GONE);

                if (show) {
                    StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
                        ownerOptions = options != null ? options : new ArrayList<>();
                        ownerOptionKeys = new String[ownerOptions.size()];
                        String[] display = new String[ownerOptions.size()];
                        for (int i = 0; i < ownerOptions.size(); i++) {
                            StaffDirectory.OwnerOption o = ownerOptions.get(i);
                            ownerOptionKeys[i] = o != null ? o.ownerKey : "";
                            display[i] = o != null ? o.display : ""; // ContractKey (from fetchOwnerOptions)
                        }

                        android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                display
                        );
                        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        assignedTechSpinner.setAdapter(a);

                        String currentKey = SessionManager.getContractKey(this);
                        if (currentKey == null || currentKey.trim().isEmpty()) currentKey = userName;
                        int sel = 0;
                        if (currentKey != null) {
                            for (int i = 0; i < ownerOptionKeys.length; i++) {
                                if (currentKey.equalsIgnoreCase(ownerOptionKeys[i])) {
                                    sel = i;
                                    break;
                                }
                            }
                        }
                        assignedTechSpinner.setSelection(sel);
                    }));
                }
            }));
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

            String ownerKey = SessionManager.getContractKey(this);
            if (ownerKey == null || ownerKey.trim().isEmpty()) ownerKey = userName;
            if (assignedTechSpinner != null && assignedTechSpinner.getVisibility() == View.VISIBLE) {
                int pos = assignedTechSpinner.getSelectedItemPosition();
                if (pos >= 0 && pos < ownerOptionKeys.length) {
                    ownerKey = ownerOptionKeys[pos];
                }
            }
            if (ownerKey == null || ownerKey.trim().isEmpty()) {
                Toast.makeText(AddContractActivity.this, "Assigned technician could not be resolved. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            String tableName = StaffDirectory.getContractsCollectionNameFromAnyKey(ownerKey.trim());
            addContractToFirestore(tableName, name, address, email, contact, visits, ownerKey.trim());
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
     * @param ownerKey  ContractKey/owner key (used in contract doc field).
     */
    private void addContractToFirestore(String tableName, String name, String address, String email, String contact, String visits, String ownerKey) {
        CollectionReference contractsCollection = db.collection(tableName);
        Map<String, Object> contract = createContractObject(name, address, email, contact, visits, ownerKey, userName);

        contractsCollection.add(contract)
                .addOnSuccessListener(documentReference -> {
                    writeInAppContractAssignmentIfNeeded(documentReference.getId(), name, ownerKey, userName);
                    writeInAppContractOversightIfNeeded(documentReference.getId(), name, ownerKey, userName);
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
    private void writeInAppContractAssignmentIfNeeded(String contractId, String contractName, String ownerKey, String createdBy) {
        try {
            if (contractId == null || contractId.trim().isEmpty()) return;
            String owner = ownerKey != null ? ownerKey.trim() : "";
            if (owner.isEmpty()) return;

            String me = SessionManager.getContractKey(this);
            if (me == null || me.trim().isEmpty()) me = userName;
            if (me != null && !me.trim().isEmpty() && me.trim().equalsIgnoreCase(owner)) return;

            Map<String, Object> data = new HashMap<>();
            data.put("contractId", contractId);
            data.put("contractName", contractName);
            data.put("userName", owner);
            data.put("createdBy", createdBy);
            data.put("type", "contract_update");

            NotificationUtils.writeInAppNotification(
                    owner,
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

    /** Write contract notification (admin fan-out handled by NotificationUtils). */
    private void writeInAppContractOversightIfNeeded(String contractId, String contractName, String ownerKey, String createdBy) {
        try {
            if (contractId == null || contractId.trim().isEmpty()) return;
            String ownerName = ownerKey != null ? ownerKey.trim() : "";
            String creatorDisplay = (createdBy != null) ? createdBy : "";

            Map<String, Object> data = new HashMap<>();
            data.put("contractId", contractId);
            data.put("contractName", contractName);
            data.put("userName", ownerName);
            data.put("createdBy", createdBy);
            data.put("type", "contract_update");

            String title = "📋 Contract added";
            String body = creatorDisplay + " added a contract"
                    + (contractName != null && !contractName.trim().isEmpty() ? (": " + contractName.trim()) : "")
                    + " (assigned to " + ownerName + ")";
            NotificationUtils.writeInAppNotification(creatorDisplay, "contract_added_" + contractId + "_" + System.currentTimeMillis(), title, body, "contract_update", data);
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
        contract.put("owner", owner);
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
