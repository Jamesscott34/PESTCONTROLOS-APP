package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ViewJobActivity extends AppCompatActivity {

    private EditText searchBar;
    private LinearLayout jobsContainer;
    private Button backButton;
    private TextView totalJobs, completedJobs, pendingJobs;
    private List<Map<String, Object>> allJobs = new ArrayList<>();
    private FirebaseFirestore db;
    private String userName;
    private int total = 0, completed = 0, pending = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_jobs);

        db = FirebaseFirestore.getInstance();
        userName = getIntent().getStringExtra("USER_NAME");

        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        searchBar = findViewById(R.id.searchBar);
        jobsContainer = findViewById(R.id.jobsContainer);
        backButton = findViewById(R.id.backButton);
        totalJobs = findViewById(R.id.totalJobs);
        completedJobs = findViewById(R.id.completedJobs);
        pendingJobs = findViewById(R.id.pendingJobs);

        loadAllJobs();

        backButton.setOnClickListener(view -> finish());
        // Search Bar Implementation
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed before text changes
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterJobs(s.toString().trim()); // Trim spaces for better search performance
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed after text changes
            }
        });
    }

    private void loadAllJobs() {
        allJobs.clear();
        total = 0;
        completed = 0;
        pending = 0;

        db.collection("JobWork")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> job = document.getData();
                            job.put("documentId", document.getId());

                            allJobs.add(job);
                            total++;

                            String status = (String) job.get("Status");
                            if ("Completed".equalsIgnoreCase(status)) {
                                completed++;
                            } else {
                                pending++;
                            }
                        }

                        displayJobs(allJobs);
                    } else {
                        Toast.makeText(this, "Failed to load jobs: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void filterJobs(String query) {
        query = query.toLowerCase();
        List<Map<String, Object>> filteredJobs = new ArrayList<>();

        for (Map<String, Object> job : allJobs) {
            String techName = getOrDefault(job, "AssignedTech").toLowerCase();
            String customerName = getOrDefault(job, "CustomerName").toLowerCase();
            String address = getOrDefault(job, "Address").toLowerCase();

            if (techName.contains(query) || customerName.contains(query) || address.contains(query)) {
                filteredJobs.add(job);
            }
        }

        displayJobs(filteredJobs);
    }

    private void displayJobs(List<Map<String, Object>> jobsList) {
        jobsContainer.removeAllViews();
        for (Map<String, Object> job : jobsList) {
            String documentId = (String) job.get("documentId");
            addJobToView(job, documentId);
        }

        updateStatistics(total, completed, pending);
    }

    private void addJobToView(Map<String, Object> job, String documentId) {
        LinearLayout jobBox = new LinearLayout(this);
        jobBox.setOrientation(LinearLayout.VERTICAL);
        jobBox.setPadding(16, 16, 16, 16);
        jobBox.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        String techName = getOrDefault(job, "AssignedTech");
        String customerName = getOrDefault(job, "CustomerName");
        String address = getOrDefault(job, "Address");
        String customerEmail = getOrDefault(job, "CustomerEmail");
        String customerContact = getOrDefault(job, "CustomerContact");
        String issue = getOrDefault(job, "IssueDetails");
        String setupDate = getOrDefault(job, "SetupDate");
        String paymentAmount = getOrDefault(job, "PaymentAmount");
        String paymentMethod = getOrDefault(job, "PaymentMethod");

        String jobDetailsText = "Technician: " + techName + "\n" +
                "Customer Name: " + customerName + "\n" +
                "Customer Email: " + customerEmail + "\n" +
                "Customer Contact: " + customerContact + "\n" +
                "Issue: " + issue + "\n" +
                "Address: " + address + "\n" +
                "Initial Setup: " + setupDate + "\n" +
                "Payment: " + paymentAmount + " (" + paymentMethod + ")";

        // Job Details TextView
        TextView jobDetails = new TextView(this);
        jobDetails.setText(jobDetailsText);

        // Copy Button
        Button copyButton = new Button(this);
        copyButton.setText("Copy Details");
        copyButton.setOnClickListener(view -> copyToClipboard(jobDetailsText));

        // Add Views to job box
        jobBox.addView(jobDetails);
        jobBox.addView(copyButton);

        // Job Click Listeners
        jobBox.setOnClickListener(view -> showAcceptOrDeleteDialog(documentId));
        jobBox.setOnLongClickListener(view -> {
            showJobOptions(documentId);
            return true;
        });

        jobsContainer.addView(jobBox);
    }


    private String getOrDefault(Map<String, Object> job, String key) {
        return job.containsKey(key) && job.get(key) != null ? job.get(key).toString() : "N/A";
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Job Details", text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Job details copied to clipboard!", Toast.LENGTH_SHORT).show();
    }


    private void showAcceptOrDeleteDialog(String documentId) {
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("Accepted"))) {
                showMapAndReportOptions(documentId); // Switch to Maps & Report after acceptance
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Job Action")
                    .setMessage("Do you want to accept this job or delete it?")
                    .setPositiveButton("Accept", (dialog, which) -> showAddressDialog(documentId))
                    .setNegativeButton("Delete", (dialog, which) -> deleteJob(documentId))
                    .show();
        });
    }

    private void showAddressDialog(String documentId) {
        EditText addressInput = new EditText(this);
        addressInput.setHint("Enter Address");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Address")
                .setView(addressInput)
                .setPositiveButton("Save", (dialog, which) -> {
                    String address = addressInput.getText().toString().trim();
                    if (!address.isEmpty()) {
                        db.collection("JobWork").document(documentId)
                                .update("Address", address, "Status", "Completed", "Accepted", true)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Job Accepted & Marked as Completed", Toast.LENGTH_SHORT).show();
                                    loadAllJobs(); // Refresh to show Maps & Report instead of Accept/Delete
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to Save Address", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMapAndReportOptions(String documentId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Job Options")
                .setItems(new String[]{"Open Maps", "Create Report"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openMaps(documentId);
                            break;
                        case 1:
                            createReport(documentId);
                            break;
                    }
                })
                .show();
    }

    private void openMaps(String documentId) {
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(document -> {
            if (document.exists() && document.contains("Address")) {
                String address = document.getString("Address");
                if (address != null && !address.isEmpty()) {
                    Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    startActivity(mapIntent);
                } else {
                    Toast.makeText(this, "Address not available!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void createReport(String documentId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("JobWork").document(documentId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                // Retrieve Customer Name and Address from Firestore
                String customerName = document.getString("CustomerName"); // Use CustomerName as CompanyName
                String address = document.getString("Address");

                // Ensure data is not null
                if (customerName == null) customerName = "N/A";
                if (address == null) address = "N/A";

                // ✅ Start RodentRoutineActivity with "InitialSetup" (same as case 4)
                Intent RodentRiddanceIntent = new Intent(ViewJobActivity.this, RodentJobActivity.class);
                RodentRiddanceIntent.putExtra("ROUTINE_TYPE", "Rodent Riddance");
                RodentRiddanceIntent.putExtra("USER_NAME", userName);
                RodentRiddanceIntent.putExtra("COMPANY_NAME", customerName); // Passing CustomerName as CompanyName
                RodentRiddanceIntent.putExtra("ADDRESS", address);
                RodentRiddanceIntent.putExtra("DOCUMENT_ID", documentId);
                startActivity(RodentRiddanceIntent);

            } else {
                Toast.makeText(this, "Job details not found!", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to retrieve job details!", Toast.LENGTH_SHORT).show();
        });
    }












    private void showJobOptions(String documentId) {
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
            boolean initialSetupDone = documentSnapshot.contains("SetupDate");
            boolean paymentDone = documentSnapshot.contains("PaymentAmount") && documentSnapshot.getDouble("PaymentAmount") != null;

            List<String> options = new ArrayList<>();
            if (!initialSetupDone) options.add("Initial Setup");
            if (!paymentDone) options.add("Payment");
            options.add("Change Technician");
            options.add("Add/Change Email"); // ✅ Always show this option
            options.add("Delete");

            String[] jobOptions = options.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Job Options")
                    .setItems(jobOptions, (dialog, which) -> {
                        String selectedOption = jobOptions[which];

                        if (selectedOption.equals("Initial Setup")) {
                            applyInitialSetup(documentId);
                        } else if (selectedOption.equals("Payment")) {
                            showPaymentDialog(documentId);
                        } else if (selectedOption.equals("Change Technician")) {
                            showChangeTechnicianDialog(documentId);
                        } else if (selectedOption.equals("Add/Change Email")) { // ✅ Updated option name
                            showAddEmailDialog(documentId);
                        } else if (selectedOption.equals("Delete")) {
                            deleteJob(documentId);
                        }
                    })
                    .show();
        });
    }



    private void showChangeTechnicianDialog(String documentId) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText techInput = new EditText(this);
        techInput.setHint("Enter New Technician Name");
        layout.addView(techInput);

        EditText mobileInput = new EditText(this);
        mobileInput.setHint("Enter Technician Mobile");
        layout.addView(mobileInput);

        EditText priceInput = new EditText(this);
        priceInput.setHint("Enter Price Given (€)");
        layout.addView(priceInput);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Technician")
                .setView(layout)
                .setPositiveButton("Save & Send WhatsApp", (dialog, which) -> {
                    String newTech = techInput.getText().toString().trim();
                    String newMobile = formatIrishMobile(mobileInput.getText().toString().trim());
                    String priceGiven = priceInput.getText().toString().trim();

                    if (!newTech.isEmpty()) {
                        db.collection("JobWork").document(documentId)
                                .update("AssignedTech", newTech, "TechnicianMobile", newMobile)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Technician Updated", Toast.LENGTH_SHORT).show();
                                    if (!newMobile.isEmpty() && !priceGiven.isEmpty()) {
                                        sendWhatsAppMessage(newMobile, newTech, priceGiven, documentId);
                                    }
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to Update Technician", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Formats Irish mobile numbers to international format (+353).
     */
    private String formatIrishMobile(String number) {
        if (number.startsWith("087") || number.startsWith("086") || number.startsWith("085") ||
                number.startsWith("089") || number.startsWith("083") || number.startsWith("088")) {
            return "+353" + number.substring(1);
        }
        return number;
    }

    /**
     * Opens WhatsApp with a pre-filled job message.
     */
    private void sendWhatsAppMessage(String mobile, String newTech, String price, String documentId) {
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                String customerName = document.getString("CustomerName");
                String customerContact = document.getString("CustomerContact");

                String message = "Hello, a new job has been assigned to you.\n\n" +
                        "🔹Technician Name: " + newTech + "\n" +
                        "🔹Customer Name: " + (customerName != null ? customerName : "N/A") + "\n" +
                        "🔹Customer Contact: " + (customerContact != null ? customerContact : "N/A") + "\n" +
                        "🔹Price Given: €" + price + "\n\n" +
                        "📞Please call the customer and open the GRPC App.";

                try {
                    Uri uri = Uri.parse("https://wa.me/" + mobile + "?text=" + Uri.encode(message));
                    Intent sendIntent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(sendIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "WhatsApp not installed or failed to send", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }



    private void showAddEmailDialog(String documentId) {
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
            String currentEmail = documentSnapshot.contains("CustomerEmail") ? documentSnapshot.getString("CustomerEmail") : "";

            EditText emailInput = new EditText(this);
            emailInput.setHint("Enter New Email");
            emailInput.setText(currentEmail); // ✅ Pre-fill current email for easier editing

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Add / Change Email")
                    .setView(emailInput)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String email = emailInput.getText().toString().trim();
                        if (!email.isEmpty()) {
                            db.collection("JobWork").document(documentId)
                                    .update("CustomerEmail", email)
                                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Email Updated Successfully", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to Update Email", Toast.LENGTH_SHORT).show());
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }



    private void applyInitialSetup(String documentId) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        db.collection("JobWork").document(documentId).update("SetupDate", date);
    }

    private void showPaymentDialog(String documentId) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText priceInput = new EditText(this);
        priceInput.setHint("Enter Price (€)");
        layout.addView(priceInput);

        final String[] paymentMethod = {""}; // Store selected payment method

        String[] paymentMethods = {"Cash", "Card (VAT Applied)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Payment Method")
                .setView(layout)
                .setSingleChoiceItems(paymentMethods, -1, (dialog, which) -> {
                    paymentMethod[0] = paymentMethods[which]; // Store selected method
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    String priceStr = priceInput.getText().toString().trim();

                    if (paymentMethod[0].isEmpty()) {
                        Toast.makeText(this, "Please select a payment method.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (priceStr.isEmpty()) {
                        Toast.makeText(this, "Please enter a price.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double price = Double.parseDouble(priceStr);

                    if (paymentMethod[0].equals("Card (VAT Applied)")) {
                        showVatSelectionDialog(price, documentId);
                    } else {
                        savePaymentToDatabase(price, "Cash", documentId);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showVatSelectionDialog(double price, String documentId) {
        String[] vatRates = {"13.5%", "23%"};
        final double[] selectedVatRate = {0};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select VAT Rate")
                .setSingleChoiceItems(vatRates, -1, (dialog, which) -> {
                    selectedVatRate[0] = which == 0 ? 0.135 : 0.23;
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    if (selectedVatRate[0] == 0) {
                        Toast.makeText(this, "Please select a VAT rate.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double finalPrice = price * (1 + selectedVatRate[0]);
                    savePaymentToDatabase(finalPrice, "Card (VAT Applied)", documentId);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void savePaymentToDatabase(double price, String method, String documentId) {
        db.collection("JobWork").document(documentId)
                .update("PaymentAmount", price, "PaymentMethod", method)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Payment Saved", Toast.LENGTH_SHORT).show();
                    loadAllJobs();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to Save Payment", Toast.LENGTH_SHORT).show());
    }


    private void deleteJob(String documentId) {
        db.collection("JobWork").document(documentId).delete();
        loadAllJobs();
    }

    private void updateStatistics(int total, int completed, int pending) {
        totalJobs.setText("Total Jobs: " + total);
        completedJobs.setText("Completed: " + completed);
        pendingJobs.setText("Pending: " + pending);
    }
}
