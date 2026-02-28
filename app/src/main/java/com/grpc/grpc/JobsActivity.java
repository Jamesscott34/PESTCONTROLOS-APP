package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;

/**
* JobsActivity.java
*
* This activity serves as the main hub for job management. Users can either create new jobs
* or view existing job records. The username is retrieved from the intent and passed to
* subsequent activities to maintain user session.
*
* Features:
* - Displays a welcome message with the user's name
* - Allows navigation to the job creation screen
* - Allows navigation to the job viewing screen
* - Ensures USER_NAME is always passed to the next activity
*
* Author: GRPC
*/


public class JobsActivity extends AppCompatActivity {

private FirebaseFirestore db;
private String userName;
private Button buttonCreateJob, buttonViewJob, buttonCreateManagmentJob, buttonViewManagmentJob;

@SuppressLint("MissingInflatedId")
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_jobs_selection);
    if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

    db = FirebaseFirestore.getInstance();

    // Retrieve USER_NAME from intent
    if (getIntent().hasExtra("USER_NAME")) {
        userName = getIntent().getStringExtra("USER_NAME");
    }

    // Set the welcome message
    TextView welcomeTextView = findViewById(R.id.welcomeTextView);
    if (welcomeTextView != null) {
        welcomeTextView.setText("Welcome, " + userName + "!");
    }
    SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
        if (welcomeTextView == null) return;
        String name = SessionManager.getName(this);
        if (name != null && !name.trim().isEmpty()) {
            welcomeTextView.setText("Welcome, " + name.trim() + "!");
        }
    }));

    // Ensure USER_NAME is valid
    if (userName == null || userName.trim().isEmpty()) {
        Toast.makeText(this, "Error: USER_NAME not received!", Toast.LENGTH_SHORT).show();
        userName = "Unknown User";  // Default value to prevent crashes
    }

    buttonCreateJob = findViewById(R.id.buttonCreateJobs);
    buttonViewJob = findViewById(R.id.buttonViewJobs);
    buttonCreateManagmentJob = findViewById(R.id.buttonCreateManagmentJob);
    buttonViewManagmentJob = findViewById(R.id.buttonViewManagmentJob);

    // Pass USER_NAME when opening AddJobsActivity
    buttonCreateJob.setOnClickListener(view -> {
        Intent intent = new Intent(JobsActivity.this, AddJobsActivity.class);
        intent.putExtra("USER_NAME", userName); // Ensure USER_NAME is passed
        startActivity(intent);
    });

    // Pass USER_NAME when opening ViewJobActivity
    buttonViewJob.setOnClickListener(view -> {
        Intent intent = new Intent(JobsActivity.this, ViewJobActivity.class);
        intent.putExtra("USER_NAME", userName); // Ensure USER_NAME is passed
        startActivity(intent);
    });

    buttonCreateManagmentJob.setOnClickListener(view -> {
        Intent intent = new Intent(JobsActivity.this, AddManagmentJobsActivity.class);
        intent.putExtra("USER_NAME", userName); // Ensure USER_NAME is passed
        startActivity(intent);
    });

    buttonViewManagmentJob.setOnClickListener(view -> {
        Intent intent = new Intent(JobsActivity.this, ViewManagmentJobActivity.class);
        intent.putExtra("USER_NAME", userName); // Ensure USER_NAME is passed
        startActivity(intent);
    });

    }

}

