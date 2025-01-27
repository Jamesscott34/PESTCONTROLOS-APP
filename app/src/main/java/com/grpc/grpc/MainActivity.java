package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button reportButton, reportViewButton, calendarButton, contractsButton, quotesButton, logoutButton, CommisionButton;
    private String userEmail;
    private TextView welcomeTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Retrieve the user's email from the intent
        userEmail = getIntent().getStringExtra("USER_EMAIL");

        // Extract the name from the email
        String userName = extractNameFromEmail(userEmail);

        // Initialize the welcome TextView
        welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Initialize Buttons
        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        calendarButton = findViewById(R.id.buttonOpenCalendar);
        contractsButton = findViewById(R.id.ContractsButton);
        quotesButton = findViewById(R.id.GeneralQuotesButton);
        CommisionButton = findViewById(R.id.CommisionButton);
        logoutButton = findViewById(R.id.LogoutButton); // Logout button

        // Button Listeners
        reportButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ReportSelectionActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        reportViewButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, PDFSelectionActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        calendarButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        contractsButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ContractsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        quotesButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, QuotesActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        CommisionButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, LeadsSelectionActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(view -> {
            // Navigate back to LoginActivity and clear session data
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear activity stack
            startActivity(intent);
            finish(); // Close the current activity
        });
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
