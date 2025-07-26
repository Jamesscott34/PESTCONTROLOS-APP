/**
 * AddJobFromCalendarActivity.java
 * 
 * Activity for creating new jobs from the calendar view.
 * Allows users to add job details including name, address, and issue.
 * 
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 */

package com.grpc.grpc;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddJobFromCalendarActivity extends AppCompatActivity {

    private EditText jobNameInput, jobAddressInput, jobIssueInput;
    private Button saveJobButton, cancelButton;
    private FirebaseFirestore db;
    private String userName;
    private long selectedDate;
    private String selectedTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_job_from_calendar);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Get user information
        userName = getIntent().getStringExtra("USER_NAME");
        selectedDate = getIntent().getLongExtra("SELECTED_DATE", System.currentTimeMillis());
        selectedTime = getIntent().getStringExtra("SELECTED_TIME");

        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Use default time if none provided
        if (selectedTime == null || selectedTime.isEmpty()) {
            selectedTime = "09:00";
        }

        initializeUIComponents();
        setupButtonListeners();
    }

    /**
     * Initialize UI components
     */
    private void initializeUIComponents() {
        jobNameInput = findViewById(R.id.jobNameInput);
        jobAddressInput = findViewById(R.id.jobAddressInput);
        jobIssueInput = findViewById(R.id.jobIssueInput);
        saveJobButton = findViewById(R.id.saveJobButton);
        cancelButton = findViewById(R.id.cancelButton);
    }

    /**
     * Set up button click listeners
     */
    private void setupButtonListeners() {
        saveJobButton.setOnClickListener(v -> saveJob());
        cancelButton.setOnClickListener(v -> finish());
    }

    /**
     * Save the job to Firebase and create calendar event
     */
    private void saveJob() {
        String jobName = jobNameInput.getText().toString().trim();
        String jobAddress = jobAddressInput.getText().toString().trim();
        String jobIssue = jobIssueInput.getText().toString().trim();

        if (jobName.isEmpty()) {
            Toast.makeText(this, "Please enter a job name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (jobAddress.isEmpty()) {
            Toast.makeText(this, "Please enter an address", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create job document
        Map<String, Object> job = new HashMap<>();
        job.put("jobName", jobName);
        job.put("address", jobAddress);
        job.put("issue", jobIssue);
        job.put("userName", userName);
        job.put("createdAt", new Date());

        // Save job to jobs collection
        db.collection("jobs")
          .add(job)
          .addOnSuccessListener(documentReference -> {
              String jobId = documentReference.getId();
              
              // Create work event for the job
              createWorkEventForJob(jobId, jobName, jobAddress, jobIssue);
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error saving job: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Create a work event for the newly created job
     */
    private void createWorkEventForJob(String jobId, String jobName, String jobAddress, String jobIssue) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(new Date(selectedDate));

        Map<String, Object> event = new HashMap<>();
        event.put("userName", userName); // Use the target user (could be different from logged-in user)
        event.put("eventType", "job");
        event.put("eventId", jobId);
        event.put("eventName", jobName);
        event.put("address", jobAddress);
        event.put("issue", jobIssue);
        event.put("date", dateString);
        event.put("time", selectedTime); // Use the selected time
        event.put("status", "scheduled");
        event.put("createdAt", new Date());

        // Save to target user's work view collection
        String userCollection = userName.toLowerCase() + "_workview";
        db.collection(userCollection)
          .add(event)
          .addOnSuccessListener(documentReference -> {
              Toast.makeText(this, "Job created and added to " + userName + "'s calendar!", Toast.LENGTH_SHORT).show();
              finish();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error adding to calendar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }
} 