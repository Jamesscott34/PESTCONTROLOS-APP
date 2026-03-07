package com.grpc.grpc.contracts.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.messaging.NotificationUtils;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.util.Log;
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

    private static final String TAG = "AddContractActivity";
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

    // Admin assign-to dropdown (UID-based)
    private List<UserRepository.AssignableUser> assignableUsers = new ArrayList<>();
    private String[] assignableUserUids = new String[0];

    /** Prevents double-tap / double submit when adding a contract. */
    private boolean addInProgress = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contract);
        
        // Handle keyboard properly
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // Initialize Firestore (online flavors only)
        if (!BuildConfig.IS_OFFLINE) {
            db = FirebaseFirestore.getInstance();
        }

        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Debug: log identity context for contract permissions investigation.
        try {
            com.google.firebase.auth.FirebaseUser authUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String authUid = authUser != null ? authUser.getUid() : "null";
            String authEmail = authUser != null && authUser.getEmail() != null ? authUser.getEmail() : "";
            SessionManager.Session session = SessionManager.getCached(this);
            String role = session != null ? session.roleNorm : "unknown";
            String contractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
            Log.d(TAG, "Init AddContractActivity authUid=" + authUid
                    + ", email=" + authEmail
                    + ", role=" + role
                    + ", contractKey=" + (contractKey != null ? contractKey : "")
                    + ", userName=" + userName);
        } catch (Exception e) {
            Log.w(TAG, "Failed to log identity context in AddContractActivity: " + e.getMessage());
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

            // Resolve admin role after session is loaded so admins see the assign-to spinner.
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                boolean isAdmin = SessionManager.isAdmin(this);
                boolean show = isAdmin && !BuildConfig.IS_OFFLINE;
                assignedTechSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
                if (label != null) label.setVisibility(show ? View.VISIBLE : View.GONE);

                if (show) {
                    // UID-based assignable users list (admin + tech)
                    UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
                        // Only show users that have a ContractKey; label = ContractKey.
                        assignableUsers = users != null ? users : new ArrayList<>();
                        java.util.List<String> uidList = new ArrayList<>();
                        java.util.List<String> labelList = new ArrayList<>();
                        for (UserRepository.AssignableUser u : assignableUsers) {
                            if (u == null) continue;
                            if (u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                            uidList.add(u.uid != null ? u.uid : "");
                            labelList.add(u.contractKey.trim());
                        }
                        assignableUserUids = uidList.toArray(new String[0]);
                        String[] display = labelList.toArray(new String[0]);

                        android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(
                                this,
                                android.R.layout.simple_spinner_item,
                                display
                        );
                        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        assignedTechSpinner.setAdapter(a);

                        // Default selection: current user (if present)
                        String myUid = null;
                        try {
                            com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                            if (u != null) myUid = u.getUid();
                        } catch (Exception ignored) {}
                        int sel = 0;
                        if (myUid != null) {
                            for (int i = 0; i < assignableUserUids.length; i++) {
                                if (myUid.equals(assignableUserUids[i])) {
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
            if (addInProgress) return;
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

            // Determine assigned technician (UID + display/contractKey).
            String assignedUid = null;
            String assignedName = null;
            String assignedStaffId = null;
            String assignedContractKey = null;

            try {
                com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (u != null) assignedUid = u.getUid();
            } catch (Exception ignored) {}

            boolean isAdminUser = SessionManager.isAdmin(this);

            if (isAdminUser && assignedTechSpinner != null && assignedTechSpinner.getVisibility() == View.VISIBLE && assignableUserUids.length > 0) {
                // Admin/super_admin: pick from spinner (all users with a ContractKey).
                int pos = assignedTechSpinner.getSelectedItemPosition();
                if (pos >= 0 && pos < assignableUserUids.length) {
                    assignedUid = assignableUserUids[pos];
                    UserRepository.AssignableUser sel = (pos < assignableUsers.size()) ? assignableUsers.get(pos) : null;
                    if (sel != null) {
                        assignedName = sel.name;
                        assignedStaffId = sel.staffId;
                        assignedContractKey = sel.contractKey;
                    }
                }
            } else {
                // Tech/non-admin: force assignment to their own profile.
                if (assignedName == null || assignedName.trim().isEmpty()) {
                    String selfName = SessionManager.getName(this);
                    if (selfName != null && !selfName.trim().isEmpty()) {
                        assignedName = selfName.trim();
                    }
                }
                if (assignedContractKey == null || assignedContractKey.trim().isEmpty()) {
                    String ck = SessionManager.getContractKey(this);
                    if (ck != null && !ck.trim().isEmpty()) {
                        assignedContractKey = ck.trim();
                    }
                }
            }

            if (assignedUid == null || assignedUid.trim().isEmpty()) {
                Toast.makeText(AddContractActivity.this, "Assigned technician UID could not be resolved. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Debug: log final assignment and target collection before write.
            try {
                SessionManager.Session session = SessionManager.getCached(this);
                String role = session != null ? session.roleNorm : "unknown";
                String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
                Log.d(TAG, "Creating contract in collection=" + FirestorePaths.CONTRACTS
                        + " for authUid=" + assignedUid
                        + ", role=" + role
                        + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "")
                        + ", assignedContractKey=" + (assignedContractKey != null ? assignedContractKey : "")
                        + ", assignedName=" + (assignedName != null ? assignedName : ""));
            } catch (Exception e) {
                Log.w(TAG, "Failed to log contract creation context: " + e.getMessage());
            }

            addInProgress = true;
            addButton.setEnabled(false);
            addContractToFirestore(name, address, email, contact, visits,
                    assignedUid, assignedName, assignedStaffId, assignedContractKey, userName);
        });

        backButton.setOnClickListener(view -> {
            // Simply go back to the previous screen
            finish();
        });
    }
    /**
     * Adds the contract to the shared contracts collection.
     * Stores technician UID + display fields alongside contract details.
     */
    private void addContractToFirestore(String name,
                                        String address,
                                        String email,
                                        String contact,
                                        String visits,
                                        String assignedUid,
                                        String assignedName,
                                        String assignedStaffId,
                                        String assignedContractKey,
                                        String createdByDisplayName) {
        if (BuildConfig.IS_OFFLINE || db == null) {
            addInProgress = false;
            if (addButton != null) addButton.setEnabled(true);
            Toast.makeText(this, "Contracts cannot be added in offline mode.", Toast.LENGTH_SHORT).show();
            return;
        }

        CollectionReference contractsCollection = db.collection(FirestorePaths.CONTRACTS);
        Map<String, Object> contract = createContractObject(
                name, address, email, contact, visits,
                assignedUid, assignedName, assignedStaffId, assignedContractKey, createdByDisplayName);

        contractsCollection.add(contract)
                .addOnSuccessListener(documentReference -> {
                    addInProgress = false;
                    writeInAppContractAssignmentIfNeeded(documentReference.getId(), name, assignedUid, createdByDisplayName, assignedContractKey);
                    // Admin/super_admin fan-out is handled inside NotificationUtils.writeInAppNotification for contract_update; do not also write to creator to avoid duplicate entries.
                    Toast.makeText(AddContractActivity.this, "Contract added successfully.", Toast.LENGTH_SHORT).show();
                    clearFields();
                    returnToContractsActivity();
                })
                .addOnFailureListener(e -> {
                    addInProgress = false;
                    if (addButton != null) addButton.setEnabled(true);
                    Toast.makeText(AddContractActivity.this, "Failed to add contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * In-app notification history (NOT system push).
     * If anyone assigns a contract to another technician, notify that technician.
     */
    private void writeInAppContractAssignmentIfNeeded(String contractId, String contractName, String assignedUid, String createdByDisplayName, String assignedContractKey) {
        try {
            if (contractId == null || contractId.trim().isEmpty()) return;
            String targetUid = assignedUid != null ? assignedUid.trim() : "";
            if (targetUid.isEmpty()) return;

            Map<String, Object> data = new HashMap<>();
            data.put("contractId", contractId);
            data.put("contractName", contractName);
            data.put("assignedTechUid", targetUid);
            data.put("createdBy", createdByDisplayName);
            data.put("type", "contract_update");
            if (assignedContractKey != null && !assignedContractKey.trim().isEmpty()) {
                data.put("userName", assignedContractKey.trim());
            }

            String title = contractName != null && !contractName.trim().isEmpty() ? contractName.trim() : "New contract assigned";
            NotificationUtils.writeInAppNotification(
                    targetUid,
                    "contract_assigned_" + contractId,
                    title,
                    "",
                    "contract_update",
                    data
            );
        } catch (Exception ignored) {
        }
    }

    /** Write contract notification (admin/super_admin fan-out handled by NotificationUtils). */
    private void writeInAppContractOversightIfNeeded(String contractId, String contractName, String assignedUid, String createdByDisplayName, String assignedContractKey) {
        try {
            if (contractId == null || contractId.trim().isEmpty()) return;
            String ownerUid = assignedUid != null ? assignedUid.trim() : "";

            Map<String, Object> data = new HashMap<>();
            data.put("contractId", contractId);
            data.put("contractName", contractName);
            data.put("assignedTechUid", ownerUid);
            data.put("createdBy", createdByDisplayName);
            data.put("type", "contract_update");
            // So notification list shows contract name only and tap opens that tech's contracts
            if (assignedContractKey != null && !assignedContractKey.trim().isEmpty()) {
                data.put("userName", assignedContractKey.trim());
            }

            String title = contractName != null && !contractName.trim().isEmpty() ? contractName.trim() : "Contract added";
            String body = "";
            NotificationUtils.writeInAppNotification(createdByDisplayName, "contract_added_" + contractId + "_" + System.currentTimeMillis(), title, body, "contract_update", data);
        } catch (Exception ignored) {
        }
    }
    /**
     * Creates a contract object formatted as a HashMap.
     * Stores contract details including name, address, email, contact, visits and technician fields.
     *
     * @param name     The name of the contract holder.
     * @param address  The address of the contract holder.
     * @param email    The email associated with the contract.
     * @param contact  The contact number associated with the contract.
     * @param visits   The number of visits assigned in the contract.
     * @param assignedUid Technician UID.
     * @return A Map containing the contract details.
     */
    private Map<String, Object> createContractObject(String name,
                                                     String address,
                                                     String email,
                                                     String contact,
                                                     String visits,
                                                     String assignedUid,
                                                     String assignedName,
                                                     String assignedStaffId,
                                                     String assignedContractKey,
                                                     String createdByDisplayName) {
        Map<String, Object> contract = new HashMap<>();
        contract.put("name", name);
        contract.put("address", address);
        contract.put("email", email);
        contract.put("contact", contact);
        contract.put("visits", visits);
        // New contracts start with no visits recorded yet.
        contract.put("lastVisit", "N/A");
        contract.put("nextVisit", "N/A");
        // Keep only a single assignment string field for contracts.
        // This is the technician's contractKey (normalized to lowercase) when available; otherwise their name.
        if (assignedContractKey != null && !assignedContractKey.trim().isEmpty()) {
            contract.put("assignedTech", assignedContractKey.trim().toLowerCase(java.util.Locale.getDefault()));
        } else if (assignedName != null && !assignedName.trim().isEmpty()) {
            contract.put("assignedTech", assignedName.trim().toLowerCase(java.util.Locale.getDefault()));
        }
        // Save only core fields (no createdAt, createdByName, createdByUid, updatedAt).
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
