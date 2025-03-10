package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * AddMapActivity.java
 *
 * This activity handles the display of a map-related screen with a personalized
 * welcome message. It retrieves the username from the intent and displays it on
 * the screen. If the username is missing or invalid, a default value is assigned
 * to prevent crashes.
 *
 * Features:
 * - Retrieves username from intent
 * - Displays a welcome message
 * - Handles missing or empty username input gracefully
 *
 * Author: James Scott
 */

public class AddMapActivity extends AppCompatActivity {

    private String userName;

    /**
     * Initializes the activity, retrieves the username from intent, and sets up
     * the UI with a welcome message.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_map);

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

    }
}

