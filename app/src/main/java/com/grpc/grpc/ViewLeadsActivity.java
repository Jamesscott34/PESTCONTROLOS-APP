package com.grpc.grpc;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ViewLeadsActivity extends AppCompatActivity {

    private EditText searchBar;
    private LinearLayout leadsContainer;
    private Button backButton;
    private TextView totalLeads, paidLeads, unpaidLeads;
    private List<Map<String, Object>> allLeads = new ArrayList<>();
    private FirebaseFirestore db;

    private String userName; // Dynamically retrieved username
    private int total = 0, paid = 0, unpaid = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_leads);

        db = FirebaseFirestore.getInstance();

        // Get username from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI elements
        searchBar = findViewById(R.id.searchBar);
        leadsContainer = findViewById(R.id.leadsContainer);
        backButton = findViewById(R.id.backButton);
        totalLeads = findViewById(R.id.totalLeads);
        paidLeads = findViewById(R.id.paidLeads);
        unpaidLeads = findViewById(R.id.unpaidLeads);

        // Load all leads
        loadAllLeads();

        // Back button action
        backButton.setOnClickListener(view -> finish());

        // Search bar filter
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterLeads(s.toString()); // Call the filterLeads method with the current query
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterLeads(String query) {
        query = query.toLowerCase(); // Convert query to lowercase for case-insensitive search
        leadsContainer.removeAllViews(); // Clear the current view

        // Create a list to hold filtered leads
        List<Map<String, Object>> filteredLeads = new ArrayList<>();

        // Iterate over all loaded leads and filter based on the "Added By" field
        for (Map<String, Object> lead : allLeads) {
            String addedBy = (String) lead.get("Added By");

            // Check if the query matches the "Added By" field
            if (addedBy != null && addedBy.toLowerCase().contains(query)) {
                filteredLeads.add(lead); // Add to filtered leads if it matches the query
            }
        }

        // Display the filtered leads
        displayLeads(filteredLeads);
    }



    private void loadAllLeads() {
        // Clear existing data
        allLeads.clear();
        total = 0;
        paid = 0;
        unpaid = 0;

        // Fetch leads from the global "Leads" collection
        db.collection("Leads")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> lead = document.getData();
                            lead.put("documentId", document.getId());

                            // Check if the current user can view the lead
                            String addedBy = (String) lead.get("Added By");
                            if (userName.equalsIgnoreCase("James") || userName.equalsIgnoreCase("Ian") ||
                                    (addedBy != null && addedBy.equalsIgnoreCase(userName))) {
                                allLeads.add(lead);

                                // Update counts
                                total++;
                                String invoiceStatus = (String) lead.get("Invoice Status");
                                if ("Paid".equalsIgnoreCase(invoiceStatus)) {
                                    paid++;
                                } else {
                                    unpaid++;
                                }
                            }
                        }

                        // Display the leads
                        displayLeads(allLeads);
                    } else {
                        Toast.makeText(this, "Failed to load leads: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayLeads(List<Map<String, Object>> leadsList) {
        // Sort leads alphabetically by "Premise Name"
        leadsList.sort((l1, l2) -> {
            String name1 = (String) l1.get("Premise Name");
            String name2 = (String) l2.get("Premise Name");
            return name1.compareToIgnoreCase(name2);
        });

        // Clear and display sorted leads
        leadsContainer.removeAllViews();
        for (Map<String, Object> lead : leadsList) {
            String documentId = (String) lead.get("documentId");
            addLeadToView(lead, documentId);
        }

        // Update statistics
        updateStatistics(total, paid, unpaid);
    }

    private void addLeadToView(Map<String, Object> lead, String documentId) {
        LinearLayout leadBox = new LinearLayout(this);
        leadBox.setOrientation(LinearLayout.VERTICAL);
        leadBox.setPadding(16, 16, 16, 16);
        leadBox.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        String addedBy = (String) lead.get("Added By");
        String premiseName = (String) lead.get("Premise Name");
        String premiseAddress = (String) lead.get("Premise Address");
        String commission = String.format(Locale.getDefault(), "%.2f", (double) lead.get("Commission"));
        String dateSubmitted = (String) lead.get("Date");
        String reason = (String) lead.get("Reason");
        String invoiceStatus = (String) lead.get("Invoice Status");
        String paymentDate = (String) lead.get("Payment Date");

        // Format Invoice Status
        String formattedInvoiceStatus;
        if ("Paid".equalsIgnoreCase(invoiceStatus) && paymentDate != null) {
            formattedInvoiceStatus = "Invoice Paid on " + paymentDate;
        } else {
            formattedInvoiceStatus = "Invoice: Empty";
        }

        TextView leadDetails = new TextView(this);
        leadDetails.setText(
                "Added By: " + addedBy + "\n" +
                "Premise Name: " + premiseName + "\n" +
                "Commission: €" + commission + "\n" +
                "Reason: " + reason + "\n" +
                "Date Submitted: " + dateSubmitted + "\n" +
                formattedInvoiceStatus
        );

        leadBox.setOnClickListener(v -> {
            if (userName.equalsIgnoreCase("James") || userName.equalsIgnoreCase("Ian")) {
                new AlertDialog.Builder(this)
                        .setTitle("Company Payment Made?")
                        .setMessage("Do you want to mark this invoice as paid?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
                            DocumentReference docRef = db.collection("Leads").document(documentId);
                            docRef.update("Invoice Status", "Paid", "Payment Date", currentDate)
                                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Invoice marked as paid!", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update lead: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        leadBox.setOnLongClickListener(v -> {
            if (userName.equalsIgnoreCase("James") || userName.equalsIgnoreCase("Ian")) {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Lead")
                        .setMessage("Are you sure you want to delete this lead?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            DocumentReference docRef = db.collection("Leads").document(documentId);
                            docRef.delete()
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(this, "Lead deleted successfully!", Toast.LENGTH_SHORT).show();
                                        loadAllLeads(); // Reload leads after deletion
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete lead: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
            return true;
        });

        leadBox.addView(leadDetails);
        leadsContainer.addView(leadBox);
    }

    private void updateStatistics(int total, int paid, int unpaid) {
        totalLeads.setText("Total Leads: " + total);
        paidLeads.setText("Paid: " + paid);
        unpaidLeads.setText("Unpaid: " + unpaid);
    }
}
