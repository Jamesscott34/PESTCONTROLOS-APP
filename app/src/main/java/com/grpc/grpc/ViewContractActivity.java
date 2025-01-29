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

        backButton.setOnClickListener(view -> {
            Intent intent = new Intent(ViewContractActivity.this, ContractsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });

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
        if ("Kristine".equalsIgnoreCase(userName)) {
            // Query each top-level collection manually
            String[] contractCollections = {"Ian Contracts", "James Contracts"}; // Add all relevant collections here
            List<Map<String, Object>> allContracts = new ArrayList<>();

            for (String collectionName : contractCollections) {
                db.collection(collectionName).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> contract = document.getData();
                            contract.put("documentId", document.getId());
                            contract.put("owner", collectionName.replace(" Contracts", "")); // Extract owner name
                            allContracts.add(contract);
                        }

                        // After all collections are processed, display the contracts
                        handleContractsData(allContracts);
                    } else {
                        Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            // Default behavior for specific user's contracts
            String tableName = userName + " Contracts";

            db.collection(tableName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Map<String, Object>> contractsList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        contract.put("owner", userName); // Add the current username as the owner
                        contractsList.add(contract);
                    }
                    handleContractsData(contractsList);
                } else {
                    Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void handleContractsData(List<Map<String, Object>> contractsList) {
        int total = 0, behind = 0, upToDate = 0;

        for (Map<String, Object> contract : contractsList) {
            total++;
            String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
            String nextVisit = calculateNextVisit(contract);

            if ("N/A".equals(lastVisit) || isWithinFiveDays(nextVisit)) {
                behind++;
            } else {
                upToDate++;
            }
        }

        contractsList.sort((c1, c2) -> {
            String name1 = c1.get("name") != null ? c1.get("name").toString() : "";
            String name2 = c2.get("name") != null ? c2.get("name").toString() : "";
            return name1.compareToIgnoreCase(name2);
        });

        contractsContainer.removeAllViews();

        for (Map<String, Object> contract : contractsList) {
            String documentId = contract.get("documentId").toString();
            addContractToView(contract, documentId);
        }

        updateStatistics(total, behind, upToDate);
    }




    private void filterContracts(String query) {
        query = query.toLowerCase();
        contractsContainer.removeAllViews();

        List<Map<String, Object>> filteredContracts = new ArrayList<>();

        // If Kristine is the user, search across all relevant collections
        if ("Kristine".equalsIgnoreCase(userName)) {
            String[] contractCollections = {"Ian Contracts", "James Contracts"}; // Add all relevant collections
            for (String collectionName : contractCollections) {
                String finalQuery = query;
                db.collection(collectionName).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> contract = document.getData();
                            String name = contract.get("name") != null ? contract.get("name").toString().toLowerCase() : "";
                            if (name.contains(finalQuery)) {
                                contract.put("documentId", document.getId());
                                contract.put("owner", collectionName.replace(" Contracts", "")); // Add owner info
                                filteredContracts.add(contract);
                            }
                        }
                        // Display the filtered contracts
                        displayContracts(filteredContracts);
                    } else {
                        Toast.makeText(this, "Failed to search contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            // Default behavior for specific user's contracts
            String tableName = userName + " Contracts";
            String finalQuery1 = query;
            db.collection(tableName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        String name = contract.get("name") != null ? contract.get("name").toString().toLowerCase() : "";
                        if (name.contains(finalQuery1)) {
                            contract.put("documentId", document.getId());
                            contract.put("owner", userName);
                            filteredContracts.add(contract);
                        }
                    }
                    // Display the filtered contracts
                    displayContracts(filteredContracts);
                } else {
                    Toast.makeText(this, "Failed to search contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void displayContracts(List<Map<String, Object>> contracts) {
        contractsContainer.removeAllViews();
        for (Map<String, Object> contract : contracts) {
            String documentId = contract.get("documentId").toString();
            addContractToView(contract, documentId);
        }
    }



    private void addContractToView(Map<String, Object> contract, String documentId) {
        LinearLayout contractBox = new LinearLayout(this);
        contractBox.setOrientation(LinearLayout.VERTICAL);
        contractBox.setPadding(16, 16, 16, 16);
        contractBox.setBackgroundResource(android.R.drawable.dialog_holo_light_frame); // Adds a grey border

        // Retrieve contract details
        String owner = contract.get("owner") != null ? contract.get("owner").toString() : "Unknown";
        String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
        String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
        String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
        String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        String nextVisit = calculateNextVisit(contract);

        // Log values for debugging
        Log.d("NextVisitDebug", "Contract: " + name + ", Owner: " + owner + ", Next Visit: " + nextVisit);

        // Determine background color based on nextVisit
        if ("N/A".equals(nextVisit)) {
            contractBox.setBackgroundColor(Color.RED); // Red for missing next visit
        } else if (isWithinFiveDays(nextVisit)) {
            contractBox.setBackgroundColor(Color.YELLOW); // Yellow for upcoming visits
        } else {
            contractBox.setBackgroundColor(Color.WHITE); // Default for others
        }

        // Create contract details TextView
        TextView contractDetails = new TextView(this);
        contractDetails.setText(
                "Owner: " + owner + "\n" + // Display the contract owner
                "Name: " + name + "\n" +
                "Address: " + address + "\n" +
                "Email: " + email + "\n" +
                "Contact: " + contact + "\n" +
                "Last Visit: " + lastVisit + "\n" +
                "Next Visit: " + nextVisit
        );
        contractDetails.setTextColor(Color.BLACK);

        // "Mark Done" checkbox
        CheckBox markDoneCheckBox = new CheckBox(this);
        markDoneCheckBox.setText("Mark Done");

        // Disable checkbox if last visit is today
        String todayDate = dateFormat.format(new Date());
        if (lastVisit.equals(todayDate)) {
            markDoneCheckBox.setChecked(true);
            markDoneCheckBox.setEnabled(false);
        }

        // Add short press action
        contractBox.setOnClickListener(v -> {
            showContractOptions(contract, lastVisit, documentId);
        });

        // Add long press action
        contractBox.setOnLongClickListener(v -> {
            // Allow only James, Ian, and Kristine to edit or delete
            if ("James".equalsIgnoreCase(userName) || "Ian".equalsIgnoreCase(userName) || "Kristine".equalsIgnoreCase(userName)) {
                showEditOrDeleteDialog(documentId, contract);
            } else {
                Toast.makeText(this, "You do not have permission to edit or delete this contract.", Toast.LENGTH_SHORT).show();
            }
            return true; // Indicate that the long press was handled
        });

        markDoneCheckBox.setOnClickListener(v -> showRoutinePopup(name, documentId, markDoneCheckBox));

        // Add views to contract box
        contractBox.addView(contractDetails);

        contractBox.addView(markDoneCheckBox);

        // Add the contract box to the container
        contractsContainer.addView(contractBox);
    }

    private void showEditOrDeleteDialog(String documentId, Map<String, Object> contract) {
        // Ensure only James, Ian, and Kristine can access this
        if ("James".equalsIgnoreCase(userName) || "Ian".equalsIgnoreCase(userName) || "Kristine".equalsIgnoreCase(userName)) {
            // Create a layout for the dialog
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(16, 16, 16, 16);

            // Editable fields for the contract details
            EditText nameInput = new EditText(this);
            nameInput.setHint("Name");
            nameInput.setText(contract.get("name") != null ? contract.get("name").toString() : "N/A");
            layout.addView(nameInput);

            EditText addressInput = new EditText(this);
            addressInput.setHint("Address");
            addressInput.setText(contract.get("address") != null ? contract.get("address").toString() : "N/A");
            layout.addView(addressInput);

            EditText emailInput = new EditText(this);
            emailInput.setHint("Email");
            emailInput.setText(contract.get("email") != null ? contract.get("email").toString() : "N/A");
            layout.addView(emailInput);

            EditText contactInput = new EditText(this);
            contactInput.setHint("Contact");
            contactInput.setText(contract.get("contact") != null ? contract.get("contact").toString() : "N/A");
            layout.addView(contactInput);

            EditText visitsInput = new EditText(this);
            visitsInput.setHint("Visits");
            visitsInput.setText(contract.get("visits") != null ? contract.get("visits").toString() : "N/A");
            layout.addView(visitsInput);

            EditText ownerInput = new EditText(this);
            ownerInput.setHint("Owner");
            ownerInput.setText(contract.get("owner") != null ? contract.get("owner").toString() : "N/A");
            layout.addView(ownerInput);

            new AlertDialog.Builder(this)
                    .setTitle("Edit or Delete Contract")
                    .setView(layout)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String newName = nameInput.getText().toString().trim();
                        String newAddress = addressInput.getText().toString().trim();
                        String newEmail = emailInput.getText().toString().trim();
                        String newContact = contactInput.getText().toString().trim();
                        String newVisits = visitsInput.getText().toString().trim();
                        String newOwner = ownerInput.getText().toString().trim();

                        // Validate required fields
                        if (newName.isEmpty() || newAddress.isEmpty()) {
                            Toast.makeText(this, "Name and Address are required.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // If the owner has changed, transfer the contract to the new owner's collection
                        String currentOwner = contract.get("owner") != null ? contract.get("owner").toString() : "N/A";
                        if (!newOwner.equalsIgnoreCase(currentOwner)) {
                            transferContractToNewOwner(currentOwner, newOwner, documentId, newName, newAddress, newEmail, newContact, newVisits);
                        } else {
                            // Update the contract in the current owner's collection
                            updateContractField(documentId, "name", newName);
                            updateContractField(documentId, "address", newAddress);
                            updateContractField(documentId, "email", newEmail);
                            updateContractField(documentId, "contact", newContact);
                            updateContractField(documentId, "visits", newVisits);
                        }
                    })
                    .setNegativeButton("Delete", (dialog, which) -> deleteContract(documentId))
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            Toast.makeText(this, "You do not have permission to edit or delete this contract.", Toast.LENGTH_SHORT).show();
        }
    }

    private void transferContractToNewOwner(String currentOwner, String newOwner, String documentId,
                                            String name, String address, String email, String contact, String visits) {
        String currentCollection = currentOwner + " Contracts";
        String newCollection = newOwner + " Contracts";

        // Remove the contract from the current owner's collection
        db.collection(currentCollection).document(documentId).delete().addOnSuccessListener(aVoid -> {
            // Add the contract to the new owner's collection
            Map<String, Object> newContract = new HashMap<>();
            newContract.put("name", name);
            newContract.put("address", address);
            newContract.put("email", email);
            newContract.put("contact", contact);
            newContract.put("visits", visits);
            newContract.put("owner", newOwner);

            db.collection(newCollection).add(newContract).addOnSuccessListener(documentReference -> {
                Toast.makeText(this, "Contract transferred to " + newOwner + ".", Toast.LENGTH_SHORT).show();
                loadContracts(); // Refresh contracts list
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to add contract to new owner: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to delete contract from current owner: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
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







    private void showRoutinePopup(String contractName, String documentId, CheckBox checkBox) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Routine Confirmation");
        dialog.setMessage("Was a routine done on " + contractName + "?");

        dialog.setPositiveButton("Yes", (dialogInterface, which) -> {
            String currentDate = dateFormat.format(Calendar.getInstance().getTime());

            // Update Firestore with the new lastVisit and calculate nextVisit
            updateVisitDates(documentId, currentDate);

            // Disable checkbox after updating
            checkBox.setChecked(true);
            checkBox.setEnabled(false);

            Toast.makeText(this, "Routine marked complete for " + contractName, Toast.LENGTH_SHORT).show();
        });

        dialog.setNegativeButton("No", (dialogInterface, which) -> checkBox.setChecked(false));

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

        dialog.setPositiveButton("Update", (dialogInterface, which) -> {
            String newDate = dateInput.getText().toString().trim();
            if (!newDate.isEmpty() && newDate.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
                updateVisitDates(documentId, newDate); // Save the date if valid
            } else {
                Toast.makeText(this, "Invalid date format! Please use dd/MM/yy (e.g., 15/01/25).", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setNeutralButton("Route", (dialogInterface, which) -> {
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
            openInMaps(address);
        });

        dialog.setNegativeButton("Report", null);
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
