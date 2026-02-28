package com.grpc.grpc;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ViewLeadsActivity.java
 *
 * This activity allows users to view, search, manage, and categorize sales leads stored in Firebase Firestore.
 * Leads are displayed in a structured format, with options to mark invoices as paid, edit material costs,
 * and delete leads (for admin users). The user can search leads based on the "Added By" field.
 *
 * Features:
 * - Loads and displays leads dynamically from Firebase Firestore
 * - Provides a search bar to filter leads by the "Added By" field
 * - Categorizes leads as paid or unpaid based on invoice status
 * - Allows marking invoices as paid with an automatic timestamp
 * - Supports editing materials cost and recalculating commission for jobs
 * - Enables lead deletion for admin users (role-based)
 * - Provides an intuitive UI with click and long-press options for lead management
 *
 * Author: GRPC
 */


public class ViewLeadsActivity extends AppCompatActivity {

    private EditText searchBar;
    private LinearLayout leadsContainer;
    private Button backButton;
    private TextView totalLeads, paidLeads, unpaidLeads;
    private List<Map<String, Object>> allLeads = new ArrayList<>();
    private FirebaseFirestore db;

    private String userName;
    private String userId; // StaffID (3 digits) when available
    private int total = 0, paid = 0, unpaid = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_leads);

        db = FirebaseFirestore.getInstance();

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = StaffDirectory.getUserId(userName);

        // Initialize UI elements
        searchBar = findViewById(R.id.searchBar);
        leadsContainer = findViewById(R.id.leadsContainer);
        backButton = findViewById(R.id.backButton);
        totalLeads = findViewById(R.id.totalLeads);
        paidLeads = findViewById(R.id.paidLeads);
        unpaidLeads = findViewById(R.id.unpaidLeads);

        // Load all leads
        loadAllLeads();

        // Optional: pre-fill search from global Search screen
        String initialQuery = getIntent().getStringExtra(SearchActivity.EXTRA_SEARCH_QUERY);
        if (initialQuery != null && !initialQuery.trim().isEmpty() && searchBar != null) {
            searchBar.setText(initialQuery.trim());
            searchBar.setSelection(searchBar.getText().length());
        }

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
        query = query != null ? query.toLowerCase().trim() : "";
        leadsContainer.removeAllViews(); // Clear the current view

        // Create a list to hold filtered leads
        List<Map<String, Object>> filteredLeads = new ArrayList<>();

        // Iterate over all loaded leads and filter based on the "Added By" field
        for (Map<String, Object> lead : allLeads) {
            if (query.isEmpty() || leadMatchesQuery(lead, query)) {
                filteredLeads.add(lead);
            }
        }

        // Display the filtered leads
        displayLeads(filteredLeads);
    }

    private boolean leadMatchesQuery(Map<String, Object> lead, String query) {
        if (lead == null || query == null) return false;

        // Prefer common fields (faster + more relevant ordering)
        String[] keys = new String[] {
                "Premise Name",
                "Premise Address",
                "Added By",
                "Number",
                "Mobile",
                "Email",
                "Reason",
                "Date"
        };
        for (String k : keys) {
            Object v = lead.get(k);
            if (v != null && String.valueOf(v).toLowerCase().contains(query)) return true;
        }

        // Fallback: search across any string-like fields
        for (Map.Entry<String, Object> e : lead.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            if (v instanceof String) {
                if (((String) v).toLowerCase().contains(query)) return true;
            } else if (v instanceof Number) {
                if (String.valueOf(v).toLowerCase().contains(query)) return true;
            }
        }
        return false;
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

                            // RBAC: admins see all; others see only leads they added (Added By matches userName)
                            String addedBy = (String) lead.get("Added By");
                            SessionManager.ensureLoaded(this, null);
                            if (SessionManager.isAdmin(this) || (addedBy != null && addedBy.equalsIgnoreCase(userName))) {
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
        leadBox.setBackgroundResource(R.drawable.surface_frame);

        String addedBy = (String) lead.get("Added By");
        String premiseName = (String) lead.get("Premise Name");
        double priceQuoted = (double) lead.get("Price Quoted");
        double commission = (double) lead.get("Commission");
        String dateSubmitted = (String) lead.get("Date");
        String reason = (String) lead.get("Reason");
        String invoiceStatus = (String) lead.get("Invoice Status");
        String paymentDate = (String) lead.get("Payment Date");
        double materialsCost = lead.get("Materials Cost") != null ? (double) lead.get("Materials Cost") : 0.0;

        // Format Invoice Status
        String formattedInvoiceStatus;
        if ("Paid".equalsIgnoreCase(invoiceStatus) && paymentDate != null) {
            formattedInvoiceStatus = "Invoice Paid on " + paymentDate;
        } else {
            formattedInvoiceStatus = "Invoice: Empty";
        }

        TextView leadDetails = new TextView(this);
        String leadInfo = "Added By: " + addedBy + "\n" +
                "Premise Name: " + premiseName + "\n" +
                "Price Quoted: €" + String.format(Locale.getDefault(), "%.2f", priceQuoted) + "\n";

        if ("Job".equalsIgnoreCase(reason)) {
            leadInfo += "Materials/Contractors Cost: €" + String.format(Locale.getDefault(), "%.2f", materialsCost) + "\n";
        }

        leadInfo += "Commission: €" + String.format(Locale.getDefault(), "%.2f", commission) + "\n" +
                "Reason: " + reason + "\n" +
                "Date Submitted: " + dateSubmitted + "\n" +
                formattedInvoiceStatus;

        leadDetails.setText(leadInfo);

        // Mark as Paid / Edit Materials: RBAC (admin by default; can be overridden by staff profile flags)
        SessionManager.ensureLoaded(this, null);
        boolean canMarkPaidOrEditMaterials = SessionManager.canMarkPaidLeads(this);
        leadBox.setOnClickListener(v -> {
            if (!canMarkPaidOrEditMaterials) {
                Toast.makeText(this, "You do not have permission to mark as paid or edit materials.", Toast.LENGTH_SHORT).show();
                return;
            }
            AlertDialog.Builder optionsDialog = new AlertDialog.Builder(this);
            optionsDialog.setTitle("Select an Action");

            if ("Contract".equalsIgnoreCase(reason)) {
                optionsDialog.setItems(new String[]{"Mark as Paid"}, (dialog, which) -> {
                    if (which == 0) markAsPaid(documentId, lead);
                });
            } else if ("Job".equalsIgnoreCase(reason)) {
                optionsDialog.setItems(new String[]{"Mark as Paid", "Add/Edit Materials"}, (dialog, which) -> {
                    if (which == 0) markAsPaid(documentId, lead);
                    else if (which == 1) showEditMaterialsDialog(lead, documentId);
                });
            }

            optionsDialog.setNegativeButton("Cancel", null);
            optionsDialog.show();
        });

        // Long press: Delete — RBAC (admin)
        leadBox.setOnLongClickListener(v -> {
            if (SessionManager.isAdmin(this)) {
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

    private void markAsPaid(String documentId, Map<String, Object> lead) {
        String currentDate = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        db.collection("Leads").document(documentId)
                .update("Invoice Status", "Paid", "Payment Date", currentDate)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Invoice marked as paid!", Toast.LENGTH_SHORT).show();
                    notifyAssigneeLeadUpdated(lead, documentId, "marked the invoice as paid");
                    loadAllLeads(); // Reload leads to reflect changes
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update invoice: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showEditMaterialsDialog(Map<String, Object> lead, String documentId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Add/Edit Materials Cost");

        // Layout for dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // Materials Cost input
        EditText materialsCostInput = new EditText(this);
        materialsCostInput.setHint("Materials/Contractors Cost");
        materialsCostInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        materialsCostInput.setText(String.valueOf(lead.get("Materials Cost") != null ? (double) lead.get("Materials Cost") : 0.0));
        layout.addView(materialsCostInput);

        dialog.setView(layout);

        dialog.setPositiveButton("Save", (dialogInterface, which) -> {
            String materialsCostStr = materialsCostInput.getText().toString().trim();
            if (materialsCostStr.isEmpty()) {
                Toast.makeText(this, "Materials cost is required.", Toast.LENGTH_SHORT).show();
                return;
            }

            double materialsCost = Double.parseDouble(materialsCostStr);
            double priceQuoted = (double) lead.get("Price Quoted");
            double newCommission = (priceQuoted - materialsCost) * 0.10;
            if (newCommission < 0) newCommission = 0; // Prevent negative commission

            // Update Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("Materials Cost", materialsCost);
            updates.put("Commission", newCommission);
            updates.put("Last Edited By", userName);

            db.collection("Leads").document(documentId).update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Materials cost updated successfully!", Toast.LENGTH_SHORT).show();
                        notifyAssigneeLeadUpdated(lead, documentId, "updated materials/commission");
                        loadAllLeads(); // Reload leads
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to update materials cost: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void notifyAssigneeLeadUpdated(Map<String, Object> lead, String documentId, String action) {
        try {
            if (lead == null) return;
            String assignedTo = asString(lead.get("Added By"));
            if (assignedTo.isEmpty()) return;
            if (userName != null && assignedTo.equalsIgnoreCase(userName)) return; // don't notify self

            String premiseName = asString(lead.get("Premise Name"));
            String title = "Lead updated";
            String body = (premiseName.isEmpty() ? "A lead assigned to you was updated." : ("Lead: " + premiseName + " was updated."))
                    + (action == null || action.trim().isEmpty() ? "" : ("\n" + userName + " " + action + "."));

            Map<String, Object> data = new HashMap<>();
            data.put("leadId", documentId);
            data.put("premiseName", premiseName);
            data.put("targetUser", assignedTo);

            String notifDocId = "lead_update_" + documentId + "_" + System.currentTimeMillis();
            NotificationUtils.writeInAppNotification(assignedTo, notifDocId, title, body, "lead_update", data);
        } catch (Exception ignored) {
        }
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }




    private void updateStatistics(int total, int paid, int unpaid) {
        totalLeads.setText("Total Leads: " + total);
        paidLeads.setText("Paid: " + paid);
        unpaidLeads.setText("Unpaid: " + unpaid);
    }
}
