/**
 * AddJobFromCalendarActivity.java
 *
 * Activity for creating new jobs from the WorkView calendar.
 * Saves to JobWork collection (same as AddJobsActivity) so jobs appear in View Jobs.
 * AssignedTech is pre-set from the target user; no technician name input needed.
 *
 * Author: GRPC
 * Company: [Company 1]
 * Version: 1.0
 * Last Updated: 2024
 */

package com.grpc.grpc.jobs.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.messaging.NotificationUtils;
import com.grpc.grpc.core.*;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddJobFromCalendarActivity extends AppCompatActivity {

    private EditText customerNameInput, customerEmailInput, customerContactInput, addressInput, issueDetailsInput;
    private TextView assignedToLabel;
    private Button saveJobButton, cancelButton;
    private FirebaseFirestore db;
    private String assignedTech;  // Target user (login key)
    private String createdBy;      // Logged-in user (for push notifications)
    private long selectedDate;
    private String selectedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_job_from_calendar);

        db = FirebaseFirestore.getInstance();

        assignedTech = getIntent().getStringExtra("USER_NAME");
        createdBy = getIntent().getStringExtra("CREATED_BY");
        selectedDate = getIntent().getLongExtra("SELECTED_DATE", System.currentTimeMillis());
        selectedTime = getIntent().getStringExtra("SELECTED_TIME");

        if (assignedTech == null || assignedTech.isEmpty()) {
            Toast.makeText(this, "Error: Assigned technician not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (selectedTime == null || selectedTime.isEmpty()) {
            selectedTime = "09:00";
        }

        initializeUIComponents();
        setupButtonListeners();
    }

    private void initializeUIComponents() {
        assignedToLabel = findViewById(R.id.assignedToLabel);
        customerNameInput = findViewById(R.id.customerNameInput);
        customerEmailInput = findViewById(R.id.customerEmailInput);
        customerContactInput = findViewById(R.id.customerContactInput);
        addressInput = findViewById(R.id.addressInput);
        issueDetailsInput = findViewById(R.id.issueDetailsInput);
        saveJobButton = findViewById(R.id.saveJobButton);
        cancelButton = findViewById(R.id.cancelButton);

        assignedToLabel.setText("Assigned to: " + assignedTech);
    }

    private void setupButtonListeners() {
        saveJobButton.setOnClickListener(v -> saveJob());
        cancelButton.setOnClickListener(v -> finish());
    }

    /**
     * Save job to shared \"jobwork\" collection (same format as AddJobsActivity) and create work event.
     * Jobs appear in View Jobs section for the assigned technician.
     */
    private void saveJob() {
        String customerName = customerNameInput.getText().toString().trim();
        String customerEmail = customerEmailInput.getText().toString().trim();
        String customerContact = formatIrishMobile(customerContactInput.getText().toString().trim());
        String address = addressInput.getText().toString().trim();
        String issueDetails = issueDetailsInput.getText().toString().trim();

        if (TextUtils.isEmpty(customerName)) {
            Toast.makeText(this, "Please enter customer name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(customerContact)) {
            Toast.makeText(this, "Please enter customer contact", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(issueDetails)) {
            Toast.makeText(this, "Please enter issue details", Toast.LENGTH_SHORT).show();
            return;
        }

        final String emailToUse = customerEmail.isEmpty() ? "N/A" : customerEmail;
        final String addressToUse = address.isEmpty() ? "N/A" : address;
        final String assignedTechKey = assignedTech != null ? assignedTech.trim().toLowerCase(Locale.getDefault()) : "";

        Map<String, Object> job = new HashMap<>();
        job.put("AssignedTech", assignedTech);
        job.put("AssignedTechKey", assignedTechKey);
        job.put("CustomerName", customerName);
        job.put("CustomerEmail", emailToUse);
        job.put("CustomerContact", customerContact);
        job.put("IssueDetails", issueDetails);
        job.put("Address", addressToUse);
        job.put("CreatedBy", createdBy != null ? createdBy : assignedTech);
        job.put("CreatedAt", new Date());
        job.put("JobType", "Service");

        db.collection(FirestorePaths.JOBWORK)
          .add(job)
          .addOnSuccessListener(documentReference -> {
              String jobId = documentReference.getId();
              writeInAppJobNotifications(jobId, customerName, assignedTech, assignedTechKey, createdBy);
              createWorkEventForJob(jobId, customerName, addressToUse, issueDetails);
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error saving job: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * In-app notification history (NOT system push):
     * - Always notify the assigned technician (if different from creator)
     * - Admin fan-out is handled centrally by NotificationUtils
     */
    private void writeInAppJobNotifications(String jobId, String customerName, String assignedTechDisplay, String assignedTechKey, String createdBy) {
        try {
            String creator = (createdBy != null && !createdBy.trim().isEmpty()) ? createdBy.trim() : "";
            String creatorLower = creator.toLowerCase(Locale.getDefault());
            String techDisplay = assignedTechDisplay != null ? assignedTechDisplay.trim() : "";
            String techKey = assignedTechKey != null ? assignedTechKey.trim() : "";
            String techLower = techKey.toLowerCase(Locale.getDefault());

            // Shared payload for deep links
            Map<String, Object> data = new HashMap<>();
            data.put("jobId", jobId);
            data.put("assignedTech", techDisplay);

            // 1) Assigned tech notification
            if (!techLower.isEmpty() && (creatorLower.isEmpty() || !techLower.equals(creatorLower))) {
                NotificationUtils.writeInAppNotification(
                        techKey,
                        "jobwork_assign_" + jobId,
                        "🚐 New Job Assignment",
                        "Service job for " + customerName + " assigned to you",
                        "jobwork",
                        data
                );
            }
        } catch (Exception ignored) {
            // Never block job creation on notification write
        }
    }

    /**
     * Create work event in assigned tech's _workview collection.
     */
    private void createWorkEventForJob(String jobId, String jobName, String address, String issue) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(new Date(selectedDate));

        Map<String, Object> event = new HashMap<>();
        event.put("userName", assignedTech);
        event.put("eventType", "job");
        event.put("eventId", jobId);
        event.put("eventName", jobName);
        event.put("address", address);
        event.put("issue", issue);
        event.put("date", dateString);
        event.put("time", selectedTime);
        event.put("status", "scheduled");
        event.put("createdAt", new Date());
        event.put("createdBy", createdBy != null ? createdBy : assignedTech);

        String userCollection = assignedTech.toLowerCase(Locale.getDefault()) + "_workview";
        db.collection(userCollection)
          .add(event)
          .addOnSuccessListener(documentReference -> {
              Toast.makeText(this, "Job created and added to " + assignedTech + "'s calendar and View Jobs!", Toast.LENGTH_SHORT).show();
              finish();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Job saved but error adding to calendar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
              finish();
          });
    }

    private String formatIrishMobile(String number) {
        if (number == null) return "";
        number = number.trim();
        if (number.startsWith("087") || number.startsWith("086") || number.startsWith("085") ||
                number.startsWith("089") || number.startsWith("083") || number.startsWith("088")) {
            return "+353" + number.substring(1);
        }
        return number;
    }
}
