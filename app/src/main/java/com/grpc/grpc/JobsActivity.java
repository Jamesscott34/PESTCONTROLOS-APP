package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;

public class JobsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String userName;
    private Button buttonCreateJob, buttonViewJob;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobs_selection);

        db = FirebaseFirestore.getInstance();

        // Retrieve USER_NAME from intent
        if (getIntent().hasExtra("USER_NAME")) {
            userName = getIntent().getStringExtra("USER_NAME");
        }

        // Set the welcome message
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Ensure USER_NAME is valid
        if (userName == null || userName.trim().isEmpty()) {
            Toast.makeText(this, "Error: USER_NAME not received!", Toast.LENGTH_SHORT).show();
            userName = "Unknown User";  // Default value to prevent crashes
        }

        buttonCreateJob = findViewById(R.id.buttonCreateJobs);
        buttonViewJob = findViewById(R.id.buttonViewJobs);

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

    }
}
