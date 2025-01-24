package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
                contractsContainer.removeAllViews();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Map<String, Object> contract = document.getData();
                    addContractToView(contract, document.getId());
                }
            } else {
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

        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        String nextVisit = calculateNextVisit(contract);

        TextView contractDetails = new TextView(this);
        contractDetails.setText(
                "Name: " + (contract.get("name") != null ? contract.get("name") : "N/A") + "\n" +
                        "Address: " + (contract.get("address") != null ? contract.get("address") : "N/A") + "\n" +
                        "Last Visit: " + lastVisit + "\n" +
                        "Next Visit: " + nextVisit
        );
        contractDetails.setTextColor(Color.BLACK);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("Mark as Done");
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                showRoutinePopup(documentId, nextVisit, checkBox);
            }
        });

        contractBox.setOnClickListener(view -> showContractOptions(contract, lastVisit, documentId));
        contractBox.setOnLongClickListener(view -> {
            showEditOrDeleteDialog(documentId, contract);
            return true;
        });

        contractBox.addView(contractDetails);
        contractBox.addView(checkBox);
        contractsContainer.addView(contractBox);
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
        dateInput.setHint("Enter Last Visit Date (dd/MM/yyyy)");
        dateInput.setSingleLine();
        layout.addView(dateInput);

        dialog.setView(layout);

        dialog.setPositiveButton("Save Date", (dialogInterface, which) -> {
            String newDate = dateInput.getText().toString();
            if (!newDate.isEmpty()) {
                String[] dateParts = newDate.split("/");
                if (dateParts.length == 3) {
                    String day = dateParts[0].length() == 1 ? "0" + dateParts[0] : dateParts[0];
                    String month = dateParts[1].length() == 1 ? "0" + dateParts[1] : dateParts[1];
                    String year = dateParts[2];
                    String formattedDate = day + "/" + month + "/" + year;
                    updateVisitDates(documentId, formattedDate);
                } else {
                    Toast.makeText(this, "Invalid date format!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Date cannot be empty!", Toast.LENGTH_SHORT).show();
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
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        int visits = contract.get("visits") != null ? Integer.parseInt(contract.get("visits").toString()) : 0;

        if (lastVisit.equals("N/A") || visits == 0) {
            return "N/A";
        }

        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(dateFormat.parse(lastVisit));
        } catch (Exception e) {
            return "Invalid Date";
        }

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

        return dateFormat.format(calendar.getTime());
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
