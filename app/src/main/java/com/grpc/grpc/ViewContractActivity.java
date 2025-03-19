package com.grpc.grpc;

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
import android.content.SharedPreferences;


/**
 * ViewContractActivity.java
 *
 * This activity allows users to view, search, edit, update, and manage contracts stored in Firebase Firestore.
 * It retrieves contracts based on the logged-in user and provides options to update last visit dates, mark contracts as completed,
 * navigate to contract locations, and generate reports. Admin users (James, Ian, Kristine) can edit or delete contracts.
 *
 * Features:
 * - Loads and displays contracts dynamically from Firebase Firestore
 * - Provides search functionality to filter contracts by name
 * - Categorizes contracts as behind, due, or up-to-date based on visit dates
 * - Allows updating last visit dates and calculates the next visit date
 * - Supports marking contracts as completed with automatic updates
 * - Enables navigation to contract locations using Google Maps
 * - Allows administrators to edit, transfer, or delete contracts
 * - Generates routine, callout, and initial setup reports based on contract status
 *
 * Author: James Scott
 */


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
            // Load both Ian & James contracts in parallel
            String[] contractCollections = {"Ian Contracts", "James Contracts"};
            List<Map<String, Object>> allContracts = new ArrayList<>();
            Map<String, List<Map<String, Object>>> groupedContracts = new HashMap<>();

            int[] loadedCount = {0}; // To track when all async calls return

            for (String collectionName : contractCollections) {
                db.collection(collectionName).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Map<String, Object>> techContracts = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> contract = document.getData();
                            contract.put("documentId", document.getId());
                            contract.put("owner", collectionName.replace(" Contracts", ""));
                            allContracts.add(contract);
                            techContracts.add(contract);
                        }

                        // Save for WhatsApp grouping
                        String techName = collectionName.replace(" Contracts", "");
                        groupedContracts.put(techName, techContracts);

                    } else {
                        Toast.makeText(this, "Failed to load " + collectionName, Toast.LENGTH_SHORT).show();
                    }

                    // When both collections return, proceed
                    loadedCount[0]++;
                    if (loadedCount[0] == contractCollections.length) {
                        handleContractsData(allContracts);

                        // 🟢 Send WhatsApp summary for each tech
                        for (String tech : groupedContracts.keySet()) {
                            sendDailyBehindSummaryIfNeeded(tech, groupedContracts.get(tech));
                        }
                    }
                });
            }

        } else {
            // Default load for Ian or James
            String tableName = userName + " Contracts";

            db.collection(tableName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Map<String, Object>> contractsList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        contract.put("owner", userName);
                        contractsList.add(contract);
                    }
                    handleContractsData(contractsList);
                    sendDailyBehindSummaryIfNeeded(userName, contractsList);
                } else {
                    Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    private void sendDailyBehindSummaryIfNeeded(String technician, List<Map<String, Object>> contracts) {
        SharedPreferences prefs = getSharedPreferences("ContractReminders", MODE_PRIVATE);
        String key = "sent_" + technician.toLowerCase();
        String lastSentDate = prefs.getString(key, "");

        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        if (today.equals(lastSentDate)) return; // Already sent today ❌

        // Filter overdue contracts
        List<String> overdueSummaries = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            String nextVisit = calculateNextVisit(contract);
            if (isPastDue(nextVisit)) {
                String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
                String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
                overdueSummaries.add("🔹 " + name + "\n📍 " + address + "\n📅 Next Visit: " + nextVisit);
            }
        }

        if (!overdueSummaries.isEmpty()) {
            String message = "🛑 Overdue Contracts for " + technician + ":\n\n" + String.join("\n\n", overdueSummaries);
            String number = technician.equalsIgnoreCase("James") ? "0879000271" : "0879134971";
            launchWhatsAppMessage(number, message);

            // Store date as sent
            prefs.edit().putString(key, today).apply();
        }
    }

    private void launchWhatsAppMessage(String phoneNumber, String message) {
        try {
            String formatted = phoneNumber.replaceFirst("^0", "353"); // Irish number formatting
            String url = "https://wa.me/" + formatted + "?text=" + Uri.encode(message);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }



    private void handleContractsData(List<Map<String, Object>> contractsList) {
        int totalContracts = contractsList.size();
        int behindContracts = 0;
        int dueContracts = 0;
        int upToDateContracts = 0;

        for (Map<String, Object> contract : contractsList) {
            String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
            String nextVisit = calculateNextVisit(contract);

            if ("N/A".equals(lastVisit) || isPastDue(nextVisit)) {
                behindContracts++; // Mark as behind if no last visit or next visit is overdue
            } else if (isDueSoon(nextVisit)) {
                dueContracts++; // Mark as due if it's within 7 days
            } else {
                upToDateContracts++; // Otherwise, it's up to date
            }
        }

        // Update UI with new statistics
        updateStatistics(totalContracts, behindContracts, dueContracts, upToDateContracts);

        // Sort contracts alphabetically
        contractsList.sort((c1, c2) -> {
            String name1 = c1.get("name") != null ? c1.get("name").toString() : "";
            String name2 = c2.get("name") != null ? c2.get("name").toString() : "";
            return name1.compareToIgnoreCase(name2);
        });

        // Clear and add updated contract views
        contractsContainer.removeAllViews();
        for (Map<String, Object> contract : contractsList) {
            String documentId = contract.get("documentId").toString();
            addContractToView(contract, documentId);
        }
    }

    private boolean isDueSoon(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return false; // Not due soon if missing
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            long diffInMillis = nextVisitDate.getTime() - currentDate.getTime();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            return diffInDays >= 0 && diffInDays < 7; // True if due in the next 7 days
        } catch (Exception e) {
            Log.e("DateError", "Error parsing next visit date: " + nextVisit, e);
            return false; // Assume not due if error occurs
        }
    }

    private boolean isPastDue(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return true; // Consider past due if missing
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            return nextVisitDate.before(currentDate); // True if next visit date has passed
        } catch (Exception e) {
            Log.e("DateError", "Error parsing next visit date: " + nextVisit, e);
            return true; // Assume past due if parsing fails
        }
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

        String owner = contract.get("owner") != null ? contract.get("owner").toString() : "Unknown";
        String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
        String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
        String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
        String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        String nextVisit = calculateNextVisit(contract);

        // Determine background color based on nextVisit conditions
        contractBox.setBackgroundColor(getBackgroundColor(lastVisit, nextVisit));

        TextView contractDetails = new TextView(this);
        contractDetails.setText(
                "Owner: " + owner + "\n" +
                        "Name: " + name + "\n" +
                        "Address: " + address + "\n" +
                        "Email: " + email + "\n" +
                        "Contact: " + contact + "\n" +
                        "Last Visit: " + lastVisit + "\n" +
                        "Next Visit: " + nextVisit
        );
        contractDetails.setTextColor(Color.BLACK);

        // ✅ Add the "Mark as Done" Checkbox
        CheckBox markDoneCheckBox = new CheckBox(this);
        markDoneCheckBox.setText("Mark as Done");

        // Disable checkbox if last visit is today
        String todayDate = dateFormat.format(new Date());
        if (lastVisit.equals(todayDate)) {
            markDoneCheckBox.setChecked(false);
            markDoneCheckBox.setEnabled(true);
        }

        // Handle checkbox click event
        markDoneCheckBox.setOnClickListener(v -> {
            if (markDoneCheckBox.isChecked()) {
                showRoutinePopup(name, documentId, markDoneCheckBox);
            }
        });

        // Click Listener for Showing Contract Options
        contractBox.setOnClickListener(v -> showContractOptions(contract, lastVisit, documentId));

        // Long Click Listener for Edit/Delete Dialog
        contractBox.setOnLongClickListener(v -> {
            if ("James".equalsIgnoreCase(userName) || "Ian".equalsIgnoreCase(userName) || "Kristine".equalsIgnoreCase(userName)) {
                showEditOrDeleteDialog(documentId, contract);
            } else {
                Toast.makeText(this, "You do not have permission to edit or delete this contract.", Toast.LENGTH_SHORT).show();
            }
            return true; // Indicate that the long press was handled
        });

        // ✅ Add views to contract box
        contractBox.addView(contractDetails);
        contractBox.addView(markDoneCheckBox);  // ✅ Add checkbox

        // ✅ Add the contract box to the container
        contractsContainer.addView(contractBox);
    }

    private int getBackgroundColor(String lastVisit, String nextVisit) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()); // Adjust to match Firestore format

        if (lastVisit == null || lastVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(lastVisit)) {
            return Color.RED; // Red if last visit is missing
        }

        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return Color.RED; // Red if next visit is missing
        }

        try {
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            if (nextVisitDate.before(currentDate)) {
                return Color.RED; // Red if next visit date has already passed
            }

            long diffInMillis = nextVisitDate.getTime() - currentDate.getTime();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            if (diffInDays < 7) {
                return Color.YELLOW; // Yellow if next visit is within 7 days
            }
        } catch (Exception e) {
            Log.e("DateError", "Error parsing next visit date: " + nextVisit, e);
            return Color.RED; // Default to red if there's an error
        }

        return Color.WHITE; // Default to white
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











    private void updateStatistics(int total, int behind, int due, int upToDate) {
        TextView totalContractsText = findViewById(R.id.totalContracts);
        TextView behindContractsText = findViewById(R.id.behindContracts);
        TextView dueContractsText = findViewById(R.id.dueContracts);
        TextView upToDateContractsText = findViewById(R.id.upToDateContracts);

        totalContractsText.setText("Total: " + total);
        behindContractsText.setText("Behind: " + behind);
        dueContractsText.setText("Due: " + due);
        upToDateContractsText.setText("Up-to-Date: " + upToDate);
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


        dialog.setItems(new CharSequence[]{"Routine", "Callout", "Update Last Visit", "Route", "InitialSetup"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            switch (which) {
                case 0: // Routine
                    showRoutineDialog(documentId, contract); // Open No Activity / Activity options
                    break;

                case 1: // Callout
                   showCallOutDialog(documentId, contract);
                    break;

                case 2: // Update Last Visit
                    showUpdateVisitDialog(documentId);
                    break;

                case 3: // Route
                    openInMaps(address);
                    break;

                case 4: // Initial Setup
                    Intent initialsetupIntent = new Intent(ViewContractActivity.this, RodentInitialActivity.class);
                    initialsetupIntent.putExtra("ROUTINE_TYPE", "InitialSetup");
                    initialsetupIntent.putExtra("USER_NAME", userName);
                    initialsetupIntent.putExtra("COMPANY_NAME", companyName);
                    initialsetupIntent.putExtra("ADDRESS", address);
                    initialsetupIntent.putExtra("DOCUMENT_ID", documentId);
                    startActivity( initialsetupIntent);
            }
        });

        dialog.setNegativeButton("Report", (dialogInterface, which) -> {
            showRoutineDialog(documentId, contract);
        });

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

    private void showRoutineDialog(String documentId, Map<String, Object> contract) {
        AlertDialog.Builder routineDialog = new AlertDialog.Builder(this);
        routineDialog.setTitle("Routine Type");

        routineDialog.setItems(new CharSequence[]{"No Activity", "Activity"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            Intent intent;

            switch (which) {
                case 0: // No Activity
                    intent = new Intent(ViewContractActivity.this, RodentRoutineActivity.class);
                    intent.putExtra("ROUTINE_TYPE", "No Activity"); // ✅ Explicitly setting correct routine type
                    break;
                case 1: // Activity
                    intent = new Intent(ViewContractActivity.this, RodentActivityRoutine.class);
                    intent.putExtra("ROUTINE_TYPE", "Activity"); // ✅ Explicitly setting correct routine type
                    break;
                default:
                    return; // Exit if something unexpected happens
            }

            intent.putExtra("USER_NAME", userName);
            intent.putExtra("COMPANY_NAME", companyName);
            intent.putExtra("ADDRESS", address);
            intent.putExtra("DOCUMENT_ID", documentId);

            startActivity(intent);
        });

        routineDialog.show();
    }



    private void showCallOutDialog(String documentId, Map<String, Object> contract){
        AlertDialog.Builder routineDialog = new AlertDialog.Builder(this);
        routineDialog.setTitle("Routine Type");

        routineDialog.setItems(new CharSequence[]{"Internal", "External"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            Intent intent;

            switch (which) {
                case 0: // No Activity
                    intent = new Intent(ViewContractActivity.this, RodentCallOutActivity.class);
                    intent.putExtra("ROUTINE_TYPE", "Internal"); // ✅ Explicitly setting correct routine type
                    break;
                case 1: // Activity
                    intent = new Intent(ViewContractActivity.this, RodentCallOutExternalActivity.class);
                    intent.putExtra("ROUTINE_TYPE", "External"); // ✅ Explicitly setting correct routine type
                    break;
                default:
                    return; // Exit if something unexpected happens
            }

            intent.putExtra("USER_NAME", userName);
            intent.putExtra("COMPANY_NAME", companyName);
            intent.putExtra("ADDRESS", address);
            intent.putExtra("DOCUMENT_ID", documentId);

            startActivity(intent);
        });

        routineDialog.show();
    }





    private void showUpdateVisitDialog(String documentId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Update Last Visit");

        EditText dateInput = new EditText(this);
        dateInput.setHint("Enter Last Visit Date (dd/MM/yy)");
        dateInput.setSingleLine();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        layout.addView(dateInput);

        dialog.setView(layout);

        dialog.setPositiveButton("Update", (dialogInterface, which) -> {
            String newDate = dateInput.getText().toString().trim();
            if (!newDate.isEmpty() && newDate.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
                updateVisitDates(documentId, newDate);
            } else {
                Toast.makeText(this, "Invalid date format! Use dd/MM/yy.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setNegativeButton("Cancel", null);
        dialog.show();
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
