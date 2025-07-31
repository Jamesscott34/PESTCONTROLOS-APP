package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.*;
import java.util.HashMap;
import java.util.Map;

/**
 * AddJobsActivity.java
 *
 * This activity allows users to add job assignments to Firestore.
 * The form collects technician and customer details, validates input,
 * and stores the job in the database. It also sends a WhatsApp notification
 * to the assigned technician after a job is successfully added.
 *
 * Features:
 * - Input validation for job details
 * - Firestore database integration
 * - Automatic formatting of Irish mobile numbers
 * - WhatsApp notification for the assigned technician
 * - Navigation back to JobsActivity after submission
 *
 * Author: James Scott
 */

public class AddManagmentJobsActivity extends AppCompatActivity {
    private Spinner techNameSpinner;
    private EditText customerName,  customerContact, issueDetails;
    private Button submitButton;
    private FirebaseFirestore db;
    private String userName,  custName,  custContact, issueDetailsText; // Stores values for WhatsApp
    private static final String[] TECHNICIANS = {"James", "Dean", "Ian", "Kristine"};

    /**
     * Initializes the activity, retrieves user information, and sets up UI elements.
     * Handles button click events for job submission.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_managment_jobs);

        techNameSpinner = findViewById(R.id.techNameSpinner);

        customerName = findViewById(R.id.customerName);

        customerContact = findViewById(R.id.customerContact);
        issueDetails = findViewById(R.id.issueDetails);
        submitButton = findViewById(R.id.submitButton);

        db = FirebaseFirestore.getInstance();

        // Retrieve username from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: Username not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (techNameSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TECHNICIANS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            techNameSpinner.setAdapter(adapter);
            // Default selection = current user if present
            int sel = 0;
            for (int i = 0; i < TECHNICIANS.length; i++) {
                if (TECHNICIANS[i].equalsIgnoreCase(userName)) {
                    sel = i;
                    break;
                }
            }
            techNameSpinner.setSelection(sel);
        }

        submitButton.setOnClickListener(v -> validateAndSubmitJob());
    }
    /**
     * Validates the input fields and ensures all required fields are filled.
     * Formats the technician and customer mobile numbers before processing.
     * If valid, the job is submitted to Firestore.
     */
    private void validateAndSubmitJob() {
        String name = "";
        if (techNameSpinner != null && techNameSpinner.getSelectedItem() != null) {
            name = String.valueOf(techNameSpinner.getSelectedItem()).trim();
        }
        custName = customerName.getText().toString().trim();
        custContact = formatIrishMobile(customerContact.getText().toString().trim());
        issueDetailsText = issueDetails.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(custName) ||
                TextUtils.isEmpty(custContact) || TextUtils.isEmpty(issueDetailsText)) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // techMobileNumber removed from here
        addJobToFirestore(name, custName, custContact, issueDetailsText);
    }

    /**
     * Adds a job entry to Firestore under the "JobWork" collection.
     * Stores technician details, customer details, and issue information.
     *
     * @param techName    The name of the assigned technician.
     * @param custName    The name of the customer.

     * @param custContact The customer's contact number.
     * @param issue       The issue description.

     */
    private void addJobToFirestore(String techName, String custName,  String custContact, String issue) {
        Map<String, Object> job = new HashMap<>();
        job.put("AssignedTech", techName);
        job.put("CustomerName", custName);
        job.put("CustomerContact", custContact);
        job.put("IssueDetails", issue);
        job.put("CreatedBy", userName);
        job.put("CreatedAt", new java.util.Date());

        db.collection("ManagmentJobs").add(job)
                .addOnSuccessListener(documentReference -> {
                    writeInAppManagementJobNotifications(documentReference.getId(), custName, techName, userName);
                    Toast.makeText(this, "Job Added Successfully", Toast.LENGTH_SHORT).show();
                    clearInputFields();
                    returnToJobsActivity(); // Return to jobs first, then open WhatsApp
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to add job", Toast.LENGTH_SHORT).show());
    }

    /**
     * In-app notifications for Management jobs:
     * - Always notify the assigned technician (if different from creator)
     */
    private void writeInAppManagementJobNotifications(String jobId, String customerName, String assignedTech, String createdBy) {
        try {
            String creator = (createdBy != null && !createdBy.trim().isEmpty()) ? createdBy.trim() : "";
            String creatorLower = creator.toLowerCase(java.util.Locale.getDefault());
            String tech = assignedTech != null ? assignedTech.trim() : "";
            String techLower = tech.toLowerCase(java.util.Locale.getDefault());

            Map<String, Object> data = new HashMap<>();
            data.put("managementJobId", jobId);
            data.put("assignedTech", tech);
            data.put("customerName", customerName);
            data.put("createdBy", creator);
            data.put("type", "management");

            if (!techLower.isEmpty() && (creatorLower.isEmpty() || !techLower.equals(creatorLower))) {
                NotificationUtils.writeInAppNotification(
                        tech,
                        "management_assign_" + jobId,
                        "🗂️ New Management Job",
                        "Management job for " + customerName + " assigned to you",
                        "management",
                        data
                );
            }
        } catch (Exception ignored) {
        }
    }
    /**
     * Returns to the JobsActivity after successfully adding a job.
     * Passes the username back to maintain user session.
     * After navigation, triggers a WhatsApp notification to the technician.
     */
    private void returnToJobsActivity() {
        Intent intent = new Intent(AddManagmentJobsActivity.this, JobsActivity.class);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();

    }

    /**
     * Formats Irish mobile numbers to international format (+353).
     * Only applies to valid Irish mobile prefixes (087, 086, 085, etc.).
     *
     * @param number The mobile number to be formatted.
     * @return The formatted mobile number with the international prefix.
     */
    private String formatIrishMobile(String number) {
        if (number.startsWith("087") || number.startsWith("086") || number.startsWith("085") ||
                number.startsWith("089") || number.startsWith("083") || number.startsWith("088")) {
            return "+353" + number.substring(1);
        }
        return number;
    }

    /**
     * Clears all input fields after a job is successfully added.
     * Resets technician details, customer details, and issue description fields.
     */
    private void clearInputFields() {
        if (techNameSpinner != null) techNameSpinner.setSelection(0);
        customerName.setText("");
        customerContact.setText("");
        issueDetails.setText("");
    }
}
