package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button reportButton, reportViewButton, calendarButton, contractsButton, quotesButton;
    private String userEmail;
    private TextView welcomeTextView; // TextView to display the welcome message

    @SuppressLint("MissingInflatedId")
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
        welcomeTextView.setText("Welcome, " + userName + "!"); // Set the welcome message

        // Initialize Buttons
        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        calendarButton = findViewById(R.id.buttonOpenCalendar);
        contractsButton = findViewById(R.id.ContractsButton);
        quotesButton = findViewById(R.id.GeneralQuotesButton);

        // Button Listeners
        reportButton.setOnClickListener(view -> {
            // Navigate to Create Report screen
            Intent intent = new Intent(MainActivity.this, ReportSelectionActivity.class);
            startActivity(intent);
        });

        reportViewButton.setOnClickListener(view -> {
            // Navigate to View Reports screen
            Intent intent = new Intent(MainActivity.this, PDFSelectionActivity.class);
            startActivity(intent);
        });

        calendarButton.setOnClickListener(view -> {
            // Navigate to Calendar screen
            Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
            startActivity(intent);
        });

        contractsButton.setOnClickListener(view -> {
            // Navigate to Contracts screen
            Intent intent = new Intent(MainActivity.this, ContractsActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass the extracted name to ContractsActivity
            startActivity(intent);
        });


        quotesButton.setOnClickListener(view -> {
            // Navigate to General Quotes screen
            Intent intent = new Intent(MainActivity.this, QuotesActivity.class);
            startActivity(intent);
        });
    }

    // Helper to extract the first part of the email as the user's name
    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String namePart = email.split("@")[0];
            return namePart.substring(0, 1).toUpperCase() + namePart.substring(1); // Capitalize first letter
        }
        return "User";
    }
}
