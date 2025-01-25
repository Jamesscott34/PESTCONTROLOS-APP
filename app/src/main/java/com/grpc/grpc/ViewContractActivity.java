package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ViewContractActivity extends AppCompatActivity {

    private EditText searchBar;
    private LinearLayout contractsContainer;
    private Button backButton;
    private FirebaseFirestore db;
    private String userName;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_contract);

        db = FirebaseFirestore.getInstance();
        userName = getIntent().getStringExtra("USER_NAME");

        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        searchBar = findViewById(R.id.searchBar);
        contractsContainer = findViewById(R.id.contractsContainer);
        backButton = findViewById(R.id.backButton);

        loadContracts();

        backButton.setOnClickListener(view -> finish());

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContracts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadContracts() {
        String tableName = userName + " Contracts";

        db.collection(tableName).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Create a list to hold the contracts
                List<Map<String, Object>> contractsList = new ArrayList<>();

                // Initialize counters
                int total = 0;
                int behind = 0;
                int upToDate = 0;

                // Add each contract to the list
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Map<String, Object> contract = document.getData();
                    contract.put("documentId", document.getId()); // Save document ID for later use
                    contractsList.add(contract);

                    // Update counters
                    total++;
                    String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
                    String nextVisit = calculateNextVisit(contract);

                    if ("N/A".equals(lastVisit) || isWithinFiveDays(nextVisit)) {
                        behind++;
                    } else {
                        upToDate++;
                    }
                }

                // Sort the list alphabetically by the "name" field
                contractsList.sort((c1, c2) -> {
                    String name1 = c1.get("name") != null ? c1.get("name").toString() : "";
                    String name2 = c2.get("name") != null ? c2.get("name").toString() : "";
                    return name1.compareToIgnoreCase(name2);
                });

                // Clear the container before adding sorted contracts
                contractsContainer.removeAllViews();

                // Add each sorted contract to the container
                for (Map<String, Object> contract : contractsList) {
                    String documentId = contract.get("documentId").toString();
                    addContractToView(contract, documentId);
                }

                // Update statistics on the UI
                updateStatistics(total, behind, upToDate);
            } else {
                // Handle error
                Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void filterContracts(String query) {
        query = query.toLowerCase();
        contractsContainer.removeAllViews();

        String tableName = userName + " Contracts";

        String finalQuery = query;
        db.collection(tableName).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Map<String, Object> contract = document.getData();
                    String name = contract.get("name") != null ? contract.get("name").toString().toLowerCase() : "";

                    if (name.startsWith(finalQuery)) {
                        addContractToView(contract, document.getId());
                    }
                }
            } else {
                Toast.makeText(this, "Failed to filter contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void addContractToView(Map<String, Object> contract, String documentId) {
        LinearLayout contractBox = new LinearLayout(this);
        contractBox.setOrientation(LinearLayout.VERTICAL);
        contractBox.setPadding(16, 16, 16, 16);
        contractBox.setBackgroundResource(android.R.drawable.dialog_holo_light_frame); // Adds a grey border

        // Retrieve contract details
        String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
        String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
        String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
        String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        String nextVisit = calculateNextVisit(contract);

        // Log values for debugging
        Log.d("NextVisitDebug", "Contract: " + name + ", Next Visit: " + nextVisit);

        // Determine background color based on nextVisit
        if ("N/A".equals(nextVisit)) {
            // Red background for missing or unavailable next visit date
            contractBox.setBackgroundColor(Color.RED);
        } else if (isWithinFiveDays(nextVisit)) {
            // Yellow background if next visit is within 5 days
            contractBox.setBackgroundColor(Color.YELLOW);
        } else {
            // Default background color for other cases
            contractBox.setBackgroundColor(Color.WHITE);
            
        }

        // Create contract details TextView
        TextView contractDetails = new TextView(this);
        contractDetails.setText(
                "Name: " + name + "\n" +
                "Address: " + address + "\n" +
                "Email: " + email + "\n" +
                "Contact: " + contact + "\n" +
                "Last Visit: " + lastVisit + "\n" +
                "Next Visit: " + nextVisit
        );
        contractDetails.setTextColor(Color.BLACK);

        // Create "Mark as Done" checkbox
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Mark as Done");
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showRoutinePopup(documentId, nextVisit, checkBox);
            }
        });

        // Set click listeners
        contractBox.setOnClickListener(view -> showContractOptions(contract, lastVisit, documentId));
        contractBox.setOnLongClickListener(view -> {
            showEditOrDeleteDialog(documentId, contract);
            return true;
        });

        // Add views to contract box
        contractBox.addView(contractDetails);
        contractBox.addView(checkBox);

        // Add the contract box to the container
        contractsContainer.addView(contractBox);
    }




    private boolean isWithinFiveDays(String nextVisit) {
        if (nextVisit == null || nextVisit.isEmpty() || "N/A".equals(nextVisit)) {
            return false; // Invalid or missing date
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            // Calculate the difference in days
            long diffInMillis = nextVisitDate.getTime() - currentDate.getTime();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            return diffInDays >= 0 && diffInDays <= 5; // Within 5 days
        } catch (Exception e) {
            e.printStackTrace();
            return false; // Error parsing date
        }
    }

    private void updateStatistics(int total, int behind, int upToDate) {
        TextView totalContracts = findViewById(R.id.totalContracts);
        TextView behindContracts = findViewById(R.id.behindContracts);
        TextView upToDateContracts = findViewById(R.id.upToDateContracts);

        totalContracts.setText("Total Contracts: " + total);
        behindContracts.setText("Behind: " + behind);
        upToDateContracts.setText("Up-to-Date: " + upToDate);
    }







    private void showRoutinePopup(String documentId, String nextVisit, CheckBox checkBox) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Routine Confirmation");
        dialog.setMessage("Have you completed a routine today?");

        dialog.setPositiveButton("Yes", (dialogInterface, which) -> {
            String currentDate = dateFormat.format(Calendar.getInstance().getTime());
            updateVisitDates(documentId, currentDate);
            checkBox.setEnabled(false);
        });

        dialog.setNegativeButton("No", (dialogInterface, which) -> {
            checkBox.setChecked(false);
        });

        dialog.show();
    }

    private void showContractOptions(Map<String, Object> contract, String lastVisit, String documentId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Contract Options");

        // Layout for dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // EditText for date input
        EditText dateInput = new EditText(this);
        dateInput.setHint("Enter Last Visit Date (dd/MM/yy)");
        dateInput.setSingleLine();
        layout.addView(dateInput);

        // Add TextWatcher for basic user feedback
        dateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 8) {
                    Toast.makeText(ViewContractActivity.this, "Date should be in format dd/MM/yy (e.g., 15/01/25).", Toast.LENGTH_SHORT).show();
                }
            }
        });

        dialog.setView(layout);

        dialog.setPositiveButton("Save Date", (dialogInterface, which) -> {
            String newDate = dateInput.getText().toString().trim();
            if (!newDate.isEmpty() && newDate.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
                updateVisitDates(documentId, newDate); // Save the date if valid
            } else {
                Toast.makeText(this, "Invalid date format! Please use dd/MM/yy (e.g., 15/01/25).", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setNeutralButton("Open in Maps", (dialogInterface, which) -> {
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
            openInMaps(address);
        });

        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }



    private String calculateNextVisit(Map<String, Object> contract) {
        // Use dd/MM/yy for two-digit years
        SimpleDateFormat shortYearFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        int visits = contract.get("visits") != null ? Integer.parseInt(contract.get("visits").toString()) : 0;

        if ("N/A".equals(lastVisit) || visits == 0) {
            return "N/A";
        }

        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(dateFormat.parse(lastVisit)); // Parse using full date format
        } catch (Exception e) {
            return "Invalid Date";
        }

        // Adjust the next visit date based on the number of visits
        switch (visits) {
            case 8:
                calendar.add(Calendar.WEEK_OF_YEAR, 6);
                break;
            case 12:
                calendar.add(Calendar.WEEK_OF_YEAR, 4);
                break;
            case 6:
                calendar.add(Calendar.WEEK_OF_YEAR, 8);
                break;
            case 4:
                calendar.add(Calendar.WEEK_OF_YEAR, 12);
                break;
            default:
                return "N/A";
        }

        // Return the next visit date in the short year format (dd/MM/yy)
        return shortYearFormat.format(calendar.getTime());
    }



    private void updateVisitDates(String documentId, String lastVisit) {
        String tableName = userName + " Contracts";

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", lastVisit);
        updates.put("nextVisit", calculateNextVisit(Map.of("lastVisit", lastVisit, "visits", 8))); // Example with 8 visits

        db.collection(tableName).document(documentId).update(updates).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Visit updated successfully.", Toast.LENGTH_SHORT).show();
            loadContracts();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update visit: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

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

    private void showEditOrDeleteDialog(String documentId, Map<String, Object> contract) {
        new AlertDialog.Builder(this)
                .setTitle("Edit or Delete")
                .setMessage("What do you want to do with this contract?")
                .setPositiveButton("Edit", (dialog, which) -> showEditContractDialog(documentId, contract))
                .setNegativeButton("Delete", (dialog, which) -> deleteContract(documentId))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void showEditContractDialog(String documentId, Map<String, Object> contract) {
        String[] fields = {"Name", "Address", "Email", "Contact", "Visits"};
        new AlertDialog.Builder(this)
                .setTitle("Edit Contract")
                .setItems(fields, (dialog, which) -> {
                    String fieldToEdit = fields[which];
                    EditText input = new EditText(this);
                    input.setHint("Enter new " + fieldToEdit);

                    new AlertDialog.Builder(this)
                            .setTitle("Edit " + fieldToEdit)
                            .setView(input)
                            .setPositiveButton("Save", (innerDialog, innerWhich) -> {
                                String newValue = input.getText().toString().trim();
                                if (!newValue.isEmpty()) {
                                    updateContractField(documentId, fieldToEdit.toLowerCase(), newValue);
                                } else {
                                    Toast.makeText(this, "Value cannot be empty.", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // This is the method to update a specific field in the Firestore database
    private void updateContractField(String documentId, String field, String newValue) {
        String tableName = userName + " Contracts";

        // Validate the 'Visits' field to ensure it's a valid single- or double-digit number
        if (field.equalsIgnoreCase("visits")) {
            try {
                int visits = Integer.parseInt(newValue);
                if (visits < 1 || visits > 99) { // Check if it's a valid single- or double-digit number
                    Toast.makeText(this, "Visits must be a number between 1 and 99.", Toast.LENGTH_SHORT).show();
                    return; // Exit without updating
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for Visits.", Toast.LENGTH_SHORT).show();
                return; // Exit without updating
            }
        }

        // Proceed with updating the field in Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put(field, newValue);

        db.collection(tableName).document(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Contract updated successfully.", Toast.LENGTH_SHORT).show();
                    loadContracts(); // Reload contracts after updating
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }



    private void deleteContract(String documentId) {
        String tableName = userName + " Contracts";
        db.collection(tableName).document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Contract deleted successfully.", Toast.LENGTH_SHORT).show();
                    loadContracts();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


}
