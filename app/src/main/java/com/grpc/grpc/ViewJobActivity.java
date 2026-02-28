/**
 * ============================================================================
 * GRPest Control Application - Job Management & Assignment System
 * ============================================================================
 * 
 * BUSINESS OVERVIEW:
 * This activity serves as the comprehensive job management system for GRPest Control,
 * allowing technicians and administrators to view, assign, track, and manage all
 * pest control jobs. It provides real-time job status tracking, technician assignment,
 * payment processing, and automated communication features for efficient service delivery.
 * 
 * CORE FUNCTIONALITIES:
 * 
 * 1. JOB DISPLAY & MANAGEMENT
 *    - Real-time job loading from Firebase Firestore
 *    - Job categorization (completed, pending, overdue)
 *    - Search and filter capabilities across all job data
 *    - Job status tracking and updates
 *    - Technician assignment and reassignment
 * 
 * 2. JOB ACTIONS & OPERATIONS
 *    - Job acceptance and completion marking
 *    - Job deletion and cleanup operations
 *    - Follow-up date scheduling and management
 *    - Customer contact information management
 *    - Payment method and VAT handling
 * 
 * 3. COMMUNICATION & NOTIFICATIONS
 *    - WhatsApp integration for technician notifications
 *    - Automated reminder system for follow-ups
 *    - Customer contact management (email, phone)
 *    - Real-time status updates and alerts
 *    - Payment confirmation notifications
 * 
 * 4. LOCATION & NAVIGATION
 *    - Google Maps integration for job locations
 *    - Address validation and formatting
 *    - Navigation assistance for technicians
 *    - Location-based job filtering
 *    - Geographic job distribution tracking
 * 
 * 5. REPORTING & DOCUMENTATION
 *    - Automated report generation for completed jobs
 *    - Job history and audit trails
 *    - Payment records and financial tracking
 *    - Customer service documentation
 *    - Performance analytics and statistics
 * 
 * TECHNICAL FEATURES:
 * - Firebase Firestore for real-time job data
 * - WhatsApp Business API integration
 * - Google Maps API for location services
 * - Real-time search and filtering
 * - Notification system for job updates
 * - Payment processing and VAT calculations
 * 
 * USER ROLES & PERMISSIONS:
 * - Technicians: View assigned jobs, update status, access navigation
 * - Administrators: Full job management, assignment, and reporting
 * - Managers: Job oversight, technician assignment, payment processing
 * - All users: Job viewing and basic status updates
 * 
 * Author: GRPC
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

package com.grpc.grpc;

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

public class ViewJobActivity extends AppCompatActivity {

    // ============================================================================
    // UI COMPONENTS - User interface elements
    // ============================================================================
    
    private EditText searchBar;
    private LinearLayout jobsContainer;
    private Button backButton;
    private TextView totalJobs, completedJobs, pendingJobs;
    
    // ============================================================================
    // DATA MANAGEMENT - Job data and statistics
    // ============================================================================
    
    private List<Map<String, Object>> allJobs = new ArrayList<>();
    private FirebaseFirestore db;
    private String userName;
    private String userId; // StaffID (3 digits) when available
    private String initialSearchQuery;
    private int total = 0, completed = 0, pending = 0;

    /**
     * Main entry point of the job management system
     * Initializes the user interface, loads job data,
     * and sets up search and filtering capabilities
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_jobs);

        // ============================================================================
        // FIREBASE INITIALIZATION
        // ============================================================================
        
        // Initialize Firebase Firestore for job data management
        db = FirebaseFirestore.getInstance();
        
        // ============================================================================
        // USER AUTHENTICATION & VALIDATION
        // ============================================================================
        
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = StaffDirectory.getUserId(userName);

        // ============================================================================
        // UI COMPONENT INITIALIZATION
        // ============================================================================
        
        // Initialize all UI components for job display and interaction
        initializeUIComponents();

        // Optional: pre-fill search from global Search screen
        initialSearchQuery = getIntent().getStringExtra(SearchActivity.EXTRA_SEARCH_QUERY);
        if (initialSearchQuery != null && !initialSearchQuery.trim().isEmpty() && searchBar != null) {
            searchBar.setText(initialSearchQuery.trim());
            searchBar.setSelection(searchBar.getText().length());
        }
        
        // Load all jobs from Firebase and display them
        loadAllJobs();
        
        // Set up navigation and search functionality
        setupNavigationAndSearch();
    }

    /**
     * Initialize all UI components by finding them in the layout
     * and setting up their basic properties
     */
    private void initializeUIComponents() {
        searchBar = findViewById(R.id.searchBar);
        jobsContainer = findViewById(R.id.jobsContainer);
        backButton = findViewById(R.id.backButton);
        totalJobs = findViewById(R.id.totalJobs);
        completedJobs = findViewById(R.id.completedJobs);
        pendingJobs = findViewById(R.id.pendingJobs);
    }

    /**
     * Set up navigation button and search functionality
     * Configures back navigation and real-time job filtering
     */
    private void setupNavigationAndSearch() {
        // Back button - return to previous screen
        backButton.setOnClickListener(view -> finish());
        
        // Search functionality - real-time job filtering
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed before text changes
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Filter jobs in real-time as user types
                filterJobs(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed after text changes
            }
        });
    }

    private void loadAllJobs() {
        com.google.firebase.firestore.Query baseQuery = db.collection("JobWork");
        // RBAC: admins (or flag) see all jobs; tech see only assigned jobs
        SessionManager.ensureLoaded(this, null);
        if (!SessionManager.seesAllJobs(this)) {
            baseQuery = baseQuery.whereEqualTo("AssignedTech", userName);
        }
        baseQuery.addSnapshotListener((snapshots, error) -> {
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

                // ✅ Check if reminder needed
                String techName = getOrDefault(job, "AssignedTech");
                String followUpDate = getOrDefault(job, "FollowUpDate");

                if (shouldNotifyTechnician(followUpDate)) {
                    String techId = StaffDirectory.getUserId(techName);
                    String mobile = StaffDirectory.getMobileForUserId(techId);
                    if (mobile != null && !mobile.isEmpty())
                        sendWhatsAppReminder(mobile, job);
                }
            }

            // If user has a search query (e.g., from global Search), apply it after loading.
            String q = (searchBar != null && searchBar.getText() != null) ? searchBar.getText().toString().trim() : "";
            if (q.isEmpty()) q = (initialSearchQuery != null) ? initialSearchQuery.trim() : "";
            if (!q.isEmpty()) {
                filterJobs(q);
            } else {
                displayJobs(allJobs);
            }
            updateStatistics(total, completed, pending);
        });

    }


    private boolean shouldNotifyTechnician(String followUpDateStr) {
        if (followUpDateStr == null || followUpDateStr.equalsIgnoreCase("N/A")) return false;

        List<String> formats = Arrays.asList("dd/MM/yyyy HH:mm", "dd/MM/yy HH:mm", "dd/MM/yyyy", "dd/MM/yy");

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                Date followUp = sdf.parse(followUpDateStr);
                Date now = new Date();

                long diffMillis = followUp.getTime() - now.getTime();
                long hours = TimeUnit.MILLISECONDS.toHours(diffMillis);

                return hours <= 24 && hours > 0; // Between now and 24h ahead

            } catch (Exception ignored) {}
        }
        return false;
    }

    private void sendWhatsAppReminder(String phoneNumber, Map<String, Object> job) {
        String customer = getOrDefault(job, "CustomerName");
        String contact = getOrDefault(job, "CustomerContact");
        String email = getOrDefault(job, "CustomerEmail");
        String issue = getOrDefault(job, "IssueDetails");
        String address = getOrDefault(job, "Address");
        String followUp = getOrDefault(job, "FollowUpDate");

        String message = "📢 Follow-Up Reminder\n\n" +
                "🧑 Customer: " + customer + "\n" +
                "📞 Contact: " + contact + "\n" +
                "📧 Email: " + email + "\n" +
                "🏠 Address: " + address + "\n" +
                "📝 Issue: " + issue + "\n" +
                "📅 Follow-Up: " + followUp + "\n\n" +
                "⚠️ This visit is due in 24 hours.";

        try {
            String formatted = formatIrishMobile(phoneNumber);
            String url = "https://wa.me/" + formatted + "?text=" + Uri.encode(message);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch WhatsApp", Toast.LENGTH_SHORT).show();
        }
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
        String customerEmail = getOrDefault(job, "CustomerEmail");
        String customerContact = getOrDefault(job, "CustomerContact");
        String issue = getOrDefault(job, "IssueDetails");
        String setupDate = getOrDefault(job, "SetupDate");
        String paymentAmount = getOrDefault(job, "PaymentAmount");
        String paymentMethod = getOrDefault(job, "PaymentMethod");
        String followUpDate = getOrDefault(job, "FollowUpDate"); // Format: dd/MM/yyyy

        // 🔥 Apply color coding based on follow-up rules
        jobBox.setBackgroundColor(getJobBackgroundColor(followUpDate));

        String jobDetailsText = "Technician: " + techName + "\n" +
                "Customer Name: " + customerName + "\n" +
                "Customer Email: " + customerEmail + "\n" +
                "Customer Contact: " + customerContact + "\n" +
                "Issue: " + issue + "\n" +
                "Address: " + address + "\n" +
                "Initial Setup: " + setupDate + "\n" +
                "Payment: " + paymentAmount + " (" + paymentMethod + ")\n" +
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
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
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

        db.collection("JobWork").document(documentId).get().addOnSuccessListener(document -> {
            if (document.exists()) {
                // Retrieve Customer Name and Address from Firestore
                String customerName = document.getString("CustomerName"); // Use CustomerName as CompanyName
                String address = document.getString("Address");

                // Ensure data is not null
                if (customerName == null) customerName = "N/A";
                if (address == null) address = "N/A";

                // ✅ Start ReportActivity with auto-populated name and address (same as ViewContractActivity)
                Intent createReportIntent = new Intent(ViewJobActivity.this, ReportActivity.class);
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
        db.collection("JobWork").document(documentId).get().addOnSuccessListener(documentSnapshot -> {
            boolean initialSetupDone = documentSnapshot.contains("SetupDate");
            boolean paymentDone = documentSnapshot.contains("PaymentAmount") && documentSnapshot.getDouble("PaymentAmount") != null;

            List<String> options = new ArrayList<>();
            if (!initialSetupDone) options.add("Initial Setup");
            if (!paymentDone) options.add("Payment");
            options.add("Change Technician");
            options.add("Add/Change Email"); // ✅ Always show this option
            if (canDeleteJobs()) options.add("Delete");
            options.add("Add Follow-Up");

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
                        else if (selectedOption.equals("Add Follow-Up")) {
                            showFollowUpDialog(documentId);
                        }
                    })
                    .show();
        });
    }
    private void showFollowUpDialog(String documentId) {
        EditText input = new EditText(this);
        input.setHint("dd/MM/yyyy or dd/MM/yy [HH:mm or 930] or N/A");

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
                        Toast.makeText(this, "Invalid format. Use dd/MM/yyyy or 09/02/26 or dd/MM/yy HH:mm", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveFollowUpDate(documentId, normalized);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Normalizes date/time input. Accepts dd/MM/yyyy, dd/MM/yy (e.g. 09/02/26), and optional time. Returns dd/MM/yyyy [HH:mm] or null. */
    private String normalizeDateTimeInput(String input) {
        input = input.trim();

        // Date only: dd/MM/yyyy
        if (input.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            return input;
        }

        // Date only: dd/MM/yy (e.g. 09/02/26) -> convert to dd/MM/yyyy
        if (input.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
            String expanded = expandTwoDigitYear(input);
            if (expanded != null) return expanded;
        }

        // Date + standard time: dd/MM/yyyy HH:mm or dd/MM/yy HH:mm
        if (input.matches("^\\d{2}/\\d{2}/\\d{4}\\s\\d{1,2}:\\d{2}$")) {
            return input;
        }
        if (input.matches("^\\d{2}/\\d{2}/\\d{2}\\s\\d{1,2}:\\d{2}$")) {
            String[] parts = input.split("\\s");
            String dateExpanded = expandTwoDigitYear(parts[0]);
            if (dateExpanded != null) return dateExpanded + " " + parts[1];
        }

        // Date + time as 930 or 0930: dd/MM/yyyy 930 or dd/MM/yy 930
        if (input.matches("^\\d{2}/\\d{2}/\\d{4}\\s\\d{3,4}$")) {
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
        if (input.matches("^\\d{2}/\\d{2}/\\d{2}\\s\\d{3,4}$")) {
            try {
                String[] parts = input.split("\\s");
                String dateExpanded = expandTwoDigitYear(parts[0]);
                if (dateExpanded == null) return null;
                String timeRaw = parts[1];
                if (timeRaw.length() == 3) timeRaw = "0" + timeRaw;
                String hour = timeRaw.substring(0, 2);
                String min = timeRaw.substring(2, 4);
                return dateExpanded + " " + hour + ":" + min;
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    /** Converts dd/MM/yy to dd/MM/yyyy (yy 00-99 -> 2000-2099). */
    private String expandTwoDigitYear(String ddMMyy) {
        if (ddMMyy == null || !ddMMyy.matches("^\\d{2}/\\d{2}/\\d{2}$")) return null;
        try {
            String[] parts = ddMMyy.split("/");
            int yy = Integer.parseInt(parts[2]);
            int fullYear = yy >= 0 && yy <= 99 ? (2000 + yy) : yy;
            return parts[0] + "/" + parts[1] + "/" + fullYear;
        } catch (Exception e) {
            return null;
        }
    }


    private void saveFollowUpDate(String documentId, String dateTime) {
        db.collection("JobWork").document(documentId)
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
        EditText input = new EditText(this);
        input.setHint("Enter date (dd/MM/yyyy or dd/MM/yyyy HH:mm)");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Initial Setup Date")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String userInput = input.getText().toString().trim();
                    if (isValidDateFormat(userInput)) {
                        db.collection("JobWork").document(documentId)
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
        if (!canDeleteJobs()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("To delete a job get in touch with an administrator.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        db.collection("JobWork").document(documentId).delete();
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
