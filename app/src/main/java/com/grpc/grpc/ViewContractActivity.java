package com.grpc.grpc;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Map;

public class ViewContractActivity extends AppCompatActivity {

    private EditText searchBar;
    private LinearLayout contractsContainer;
    private ScrollView scrollView;
    private Button backButton;
    private FirebaseFirestore db;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_contract);

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
        searchBar = findViewById(R.id.searchBar);
        contractsContainer = findViewById(R.id.contractsContainer);
        scrollView = findViewById(R.id.scrollView);
        backButton = findViewById(R.id.backButton);

        // Load contracts for the logged-in user
        loadContracts();

        // Back Button Listener
        backButton.setOnClickListener(view -> finish());
    }

    // Load all contracts for the specific user
    private void loadContracts() {
        String tableName = userName + " Contracts";

        db.collection(tableName)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        contractsContainer.removeAllViews(); // Clear the container before adding new data
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Display each contract
                            Map<String, Object> contract = document.getData();
                            addContractToView(contract, document.getId());
                        }
                    } else {
                        Toast.makeText(ViewContractActivity.this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Add a contract's details to the view
    private void addContractToView(Map<String, Object> contract, String documentId) {
        TextView contractDetails = new TextView(this);
        contractDetails.setText(
                "Name: " + (contract.get("name") != null ? contract.get("name") : "N/A") + "\n" +
                        "Address: " + (contract.get("address") != null ? contract.get("address") : "N/A") + "\n" +
                        "Email: " + (contract.get("email") != null ? contract.get("email") : "N/A") + "\n" +
                        "Contact: " + (contract.get("contact") != null ? contract.get("contact") : "N/A") + "\n" +
                        "Visits: " + (contract.get("visits") != null ? contract.get("visits") : "N/A") + "\n"
        );
        contractDetails.setPadding(16, 16, 16, 16);
        contractDetails.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        contractDetails.setLayoutParams(params);

        // Single press listener
        contractDetails.setOnClickListener(view -> showContractDialog(contract));

        // Long press listener
        contractDetails.setOnLongClickListener(view -> {
            showEditOrDeleteDialog(documentId, contract);
            return true;
        });

        contractsContainer.addView(contractDetails);
    }

    // Show dialog with options for the contract
    private void showContractDialog(Map<String, Object> contract) {
        String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

        new AlertDialog.Builder(this)
                .setTitle("Contract Options")
                .setMessage("Select an action for this contract:")
                .setPositiveButton("Open in Maps", (dialog, which) -> {
                    openInMaps(address);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Open address in Google Maps
    private void openInMaps(String address) {
        if (!address.equals("N/A")) {
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "No address available to open in Maps.", Toast.LENGTH_SHORT).show();
        }
    }

    // Show dialog to edit or delete the contract
    private void showEditOrDeleteDialog(String documentId, Map<String, Object> contract) {
        new AlertDialog.Builder(this)
                .setTitle("Edit or Delete")
                .setMessage("What do you want to do with this contract?")
                .setPositiveButton("Edit", (dialog, which) -> {
                    // Logic to edit the contract
                    Toast.makeText(this, "Edit functionality will be added later.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Delete", (dialog, which) -> {
                    deleteContract(documentId);
                })
                .setNeutralButton("Cancel", null)
                .show();
    }

    // Delete the contract from Firestore
    private void deleteContract(String documentId) {
        String tableName = userName + " Contracts";
        db.collection(tableName).document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Contract deleted successfully.", Toast.LENGTH_SHORT).show();
                    loadContracts(); // Reload contracts
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
