package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button reportButton, reportViewButton, calendarButton, contractsButton, quotesButton, logoutButton, CommisionButton, ServiceAgreementButton, JobButton, CustomerMapButton, EnviromentButton;
    private String userEmail, userName;
    private TextView welcomeTextView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve the user's email from the intent
        userEmail = getIntent().getStringExtra("USER_EMAIL");

        if (userEmail == null || userEmail.isEmpty()) {
            userName = "User";  // Fallback name
        } else {
            userName = extractNameFromEmail(userEmail);
        }

        // Initialize the welcome TextView
        welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        } else {
            Log.e("MainActivity", "welcomeTextView is NULL! Check XML ID.");
        }

        // Initialize Buttons
        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        calendarButton = findViewById(R.id.buttonOpenCalendar);
        contractsButton = findViewById(R.id.ContractsButton);
        quotesButton = findViewById(R.id.GeneralQuotesButton);
        ServiceAgreementButton = findViewById(R.id.ServiceAgreementButton);
        CommisionButton = findViewById(R.id.CommisionButton);
        JobButton = findViewById(R.id.JobsButton);
        CustomerMapButton = findViewById(R.id.CustomerMapButton);
        EnviromentButton = findViewById(R.id.EnviromentButton);
        logoutButton = findViewById(R.id.LogoutButton); // Logout button

        // Set Button Click Listeners
        if (reportButton != null) {
            reportButton.setOnClickListener(view -> openActivity(ReportSelectionActivity.class));
        }

        if (reportViewButton != null) {
            reportViewButton.setOnClickListener(view -> openActivity(PDFSelectionActivity.class));
        }

        if (calendarButton != null) {
            calendarButton.setOnClickListener(view -> openActivity(CalendarActivity.class));
        }

        if (contractsButton != null) {
            contractsButton.setOnClickListener(view -> openActivity(ContractsActivity.class));
        }

        if (quotesButton != null) {
            quotesButton.setOnClickListener(view -> openActivity(QuotesActivity.class));
        }

        if (CommisionButton != null) {
            CommisionButton.setOnClickListener(view -> openActivity(LeadsSelectionActivity.class));
        }
        if (EnviromentButton != null) {
            EnviromentButton.setOnClickListener(view -> openActivity(EnvironmentSelectionActivity.class));
        }

        if (ServiceAgreementButton != null) {
            ServiceAgreementButton.setOnClickListener(view -> {

                Intent intent = new Intent(MainActivity.this, ServiceAgreementActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }


        if (JobButton != null) {
            JobButton.setOnClickListener(view -> openActivity(JobsActivity.class));
        }

        if (CustomerMapButton != null) {
            CustomerMapButton.setOnClickListener(view -> openActivity(CustomerMapActivity.class));
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(view -> {
                // Navigate back to LoginActivity and clear session data
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear activity stack
                startActivity(intent);
                finish(); // Close the current activity
            });
        }
    }

    // Helper method to start a new activity safely
    private void openActivity(Class<?> targetActivity) {
        Log.d("MainActivity", "Opening " + targetActivity.getSimpleName() + " with USER_NAME: " + userName);
        Intent intent = new Intent(MainActivity.this, targetActivity);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
    }

    // Helper to extract the first part of the email as the user's name
    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String namePart = email.split("@")[0];
            return namePart.substring(0, 1).toUpperCase() + namePart.substring(1);
        }
        return "User";
    }
}
