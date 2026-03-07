package com.grpc.grpc.jobs.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.core.*;
import com.grpc.grpc.reports.ui.ReportActivity;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.android.material.color.MaterialColors;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ViewJobActivity.java
 *
 * This activity allows users to view, search, accept, delete, and manage jobs stored in Firebase Firestore.
 * It dynamically loads jobs assigned to technicians, categorizes them by status, and provides various actions,
 * including marking jobs as completed, changing technicians, adding payment details, and generating reports.
 *
 * Features:
 * - Loads and displays job assignments from Firebase Firestore
 * - Provides a search bar to filter jobs by technician name, customer name, or address
 * - Categorizes jobs as completed or pending
 * - Supports job acceptance, deletion, and technician reassignment
 * - Enables adding customer details such as email and payment method
 * - Integrates Google Maps for job location navigation
 * - Supports WhatsApp notifications for technician job assignment
 * - Generates routine pest control reports based on job details
 * - Provides an intuitive UI with click and long-press options for job management
 *
 * Author: GRPC
 */


public class ViewManagmentJobActivity extends AppCompatActivity {

    private EditText searchBar;
    private LinearLayout jobsContainer;
    private Button backButton;
    private TextView totalJobs, completedJobs, pendingJobs;
    private List<Map<String, Object>> allJobs = new ArrayList<>();
    private FirebaseFirestore db;
    private String userName;
    private String userId;
    private int total = 0, completed = 0, pending = 0;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_managment_jobs);

        db = FirebaseFirestore.getInstance();
        userName = getIntent().getStringExtra("USER_NAME");

        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = StaffDirectory.getUserId(userName);

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
        db.collection("ManagmentJobs").addSnapshotListener((snapshots, error) -> {
            if (error != null) {
                Toast.makeText(this, "Error loading jobs: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (snapshots == null || snapshots.isEmpty()) {
                Toast.makeText(this, "No jobs found.", Toast.LENGTH_SHORT).show();
                return;
            }

            allJobs.clear();
            total = completed = pending = 0;

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                QueryDocumentSnapshot document = dc.getDocument();
                Map<String, Object> job = document.getData();
                job.put("documentId", document.getId());
                allJobs.add(job);

                // Update counters
                String status = (String) job.get("Status");
                if ("Completed".equalsIgnoreCase(status)) {
                    completed++;
                } else {
                    pending++;
                }

            }

            displayJobs(allJobs);
            updateStatistics(total, completed, pending);
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
        jobBox.setBackgroundResource(R.drawable.surface_frame);

        String techName = getOrDefault(job, "AssignedTech");
        String customerName = getOrDefault(job, "CustomerName");
        String address = getOrDefault(job, "Address");

        String customerContact = getOrDefault(job, "CustomerContact");
        String issue = getOrDefault(job, "IssueDetails");
        String setupDate = getOrDefault(job, "SetupDate");

        String followUpDate = getOrDefault(job, "FollowUpDate"); // Format: dd/MM/yyyy

        // 🔥 Apply color coding based on follow-up rules
        jobBox.setBackgroundColor(getJobBackgroundColor(followUpDate));

        String jobDetailsText = "Technician: " + techName + "\n" +
                "Customer Name: " + customerName + "\n" +

                "Customer Contact: " + customerContact + "\n" +
                "Issue: " + issue + "\n" +
                "Address: " + address + "\n" +
                "Initial Setup: " + setupDate + "\n" +
                "Follow-Up Date: " + followUpDate;

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

        // Add to container
        jobsContainer.addView(jobBox);
    }


    private int getJobBackgroundColor(String followUpDateStr) {
        if (followUpDateStr.equalsIgnoreCase("N/A")) {
            return MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);
        }
        if (followUpDateStr.trim().isEmpty()) {
            return MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0);
        }

        followUpDateStr = followUpDateStr.trim();

        List<String> acceptedFormats = Arrays.asList(
                "dd/MM/yyyy HH:mm",
                "dd/MM/yy HH:mm",
                "dd/MM/yyyy",
                "dd/MM/yy"
        );

        for (String format : acceptedFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);

                Date followUpDate = sdf.parse(followUpDateStr);
                Date now = new Date();

                long diffMillis = followUpDate.getTime() - now.getTime();
                long diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis);

                Log.d("FollowUpCheck", "Parsed with format " + format + ": " + followUpDateStr + " → " + diffHours + "h");

                if (followUpDate.before(now)) return MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0);
                if (diffHours < 72) return MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, 0);

                return MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0);

            } catch (Exception e) {
                Log.e("FollowUpParse", "Failed to parse " + followUpDateStr + " with format " + format);
            }
        }

        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, 0); // default if all formats fail
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
        db.collection("ManagmentJobs").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("Accepted"))) {
                showMapAndReportOptions(documentId); // Switch to Maps & Report after acceptance
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            if (canDeleteJobs()) {
                builder.setTitle("Job Action")
                        .setMessage("Do you want to accept this job or delete it?")
                        .setPositiveButton("Accept", (dialog, which) -> showAddressDialog(documentId))
                        .setNegativeButton("Delete", (dialog, which) -> deleteJob(documentId))
                        .show();
            } else {
                builder.setTitle("Job Action")
                        .setMessage("Do you want to accept this job?")
                        .setPositiveButton("Accept", (dialog, which) -> showAddressDialog(documentId))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
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
                        db.collection("ManagmentJobs").document(documentId)
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
        db.collection("ManagmentJobs").document(documentId).get().addOnSuccessListener(document -> {
            if (document.exists() && document.contains("Address")) {
                String address = document.getString("Address");
                if (address != null && !address.isEmpty()) {
                    // Record this as the user's last "map opened" location
                    try {
                        FirebaseFirestore.getInstance()
                                .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                                .document(LocationSharing.userKey(userName))
                                .set(new java.util.HashMap<String, Object>() {{
                                    put("userName", userName);
                                    put("lastMapQuery", address);
                                    put("lastMapClientTimestampMs", System.currentTimeMillis());
                                    put("lastMapAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                    put("source", "map_open");
                                }}, com.google.firebase.firestore.SetOptions.merge());
                    } catch (Exception ignored) {}

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

        db.collection("ManagmentJobs").document(documentId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                // Retrieve Customer Name and Address from Firestore
                String customerName = document.getString("CustomerName"); // Use CustomerName as CompanyName
                String address = document.getString("Address");

                // Ensure data is not null
                if (customerName == null) customerName = "N/A";
                if (address == null) address = "N/A";

                // ✅ Start ReportActivity with auto-populated name and address (same as ViewContractActivity)
                Intent createReportIntent = new Intent(ViewManagmentJobActivity.this, ReportActivity.class);
                createReportIntent.putExtra("USER_NAME", userName);
                createReportIntent.putExtra("COMPANY_NAME", customerName); // Passing CustomerName as CompanyName
                createReportIntent.putExtra("ADDRESS", address);
                startActivity(createReportIntent);

            } else {
                Toast.makeText(this, "Job details not found!", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to retrieve job details!", Toast.LENGTH_SHORT).show();
        });
    }


    private void showJobOptions(String documentId) {
        db.collection("ManagmentJobs").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
            boolean initialSetupDone = documentSnapshot.contains("SetupDate");


            List<String> options = new ArrayList<>();
            if (!initialSetupDone) options.add("Initial Setup");
            options.add("Change Technician");
            if (canDeleteJobs()) options.add("Delete");
            options.add("Add Follow-Up");

            String[] jobOptions = options.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Job Options")
                    .setItems(jobOptions, (dialog, which) -> {
                        String selectedOption = jobOptions[which];

                        if (selectedOption.equals("Initial Setup")) {
                            applyInitialSetup(documentId);
                        } else if (selectedOption.equals("Change Technician")) {
                            showChangeTechnicianDialog(documentId);

                        } else if (selectedOption.equals("Delete")) {
                            deleteJob(documentId);
                        }
                        else if (selectedOption.equals("Add Follow-Up")) {
                            showFollowUpDialog(documentId);
                        }
                    })
                    .show();
        });
    }
    private void showFollowUpDialog(String documentId) {
        EditText input = new EditText(this);
        input.setHint("dd/MM/yyyy [HH:mm or 930] or N/A");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Follow-Up Date & Time")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String rawInput = input.getText().toString().trim();

                    if (rawInput.equalsIgnoreCase("N/A")) {
                        saveFollowUpDate(documentId, "N/A");
                        return;
                    }

                    String normalized = normalizeDateTimeInput(rawInput);
                    if (normalized == null) {
                        Toast.makeText(this, "Invalid format. Try dd/MM/yyyy or dd/MM/yyyy HHmm", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveFollowUpDate(documentId, normalized);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String normalizeDateTimeInput(String input) {
        input = input.trim();

        if (input.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            return input; // Date only
        }

        if (input.matches("^\\d{2}/\\d{2}/\\d{4}\\s\\d{1,2}:\\d{2}$")) {
            return input; // Date + standard time
        }

        if (input.matches("^\\d{2}/\\d{2}/\\d{4}\\s\\d{3,4}$")) {
            // Example: 26/03/2025 930 or 0930
            try {
                String[] parts = input.split("\\s");
                String datePart = parts[0];
                String timeRaw = parts[1];

                if (timeRaw.length() == 3) timeRaw = "0" + timeRaw;
                String hour = timeRaw.substring(0, 2);
                String min = timeRaw.substring(2, 4);

                return datePart + " " + hour + ":" + min;
            } catch (Exception e) {
                return null;
            }
        }

        return null; // Invalid format
    }


    private void saveFollowUpDate(String documentId, String dateTime) {
        db.collection("ManagmentJobs").document(documentId)
                .update("FollowUpDate", dateTime)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Follow-up saved", Toast.LENGTH_SHORT).show();
                    loadAllJobs();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error saving follow-up", Toast.LENGTH_SHORT).show());
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


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Technician")
                .setView(layout)
                .setPositiveButton("Save & Send WhatsApp", (dialog, which) -> {
                    String newTech = techInput.getText().toString().trim();
                    String newMobile = formatIrishMobile(mobileInput.getText().toString().trim());


                    if (!newTech.isEmpty()) {
                        db.collection("ManagmentJobs").document(documentId)
                                .update("AssignedTech", newTech, "TechnicianMobile", newMobile)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Technician Updated", Toast.LENGTH_SHORT).show();

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

    private void applyInitialSetup(String documentId) {
        EditText input = new EditText(this);
        input.setHint("Enter date (dd/MM/yyyy or dd/MM/yyyy HH:mm)");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Initial Setup Date")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String userInput = input.getText().toString().trim();
                    if (isValidDateFormat(userInput)) {
                        db.collection("ManagmentJobs").document(documentId)
                                .update("SetupDate", userInput)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Setup date saved", Toast.LENGTH_SHORT).show();
                                    loadAllJobs();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Error saving setup date", Toast.LENGTH_SHORT).show());
                    } else {
                        Toast.makeText(this, "Invalid date format. Use dd/MM/yyyy or dd/MM/yyyy HH:mm", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private boolean isValidDateFormat(String input) {
        List<String> formats = Arrays.asList("dd/MM/yyyy", "dd/MM/yyyy HH:mm");
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                sdf.parse(input); // throws exception if invalid
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }








    private void deleteJob(String documentId) {
        if (!canDeleteJobs()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("To delete a job get in touch with an administrator.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        db.collection("ManagmentJobs").document(documentId).delete();
        loadAllJobs();
    }

    private boolean canDeleteJobs() {
        SessionManager.ensureLoaded(this, null);
        return SessionManager.isAdmin(this);
    }

    private void updateStatistics(int total, int completed, int pending) {
        totalJobs.setText("Total Jobs: " + total);
        completedJobs.setText("Completed: " + completed);
        pendingJobs.setText("Pending: " + pending);
    }
}
