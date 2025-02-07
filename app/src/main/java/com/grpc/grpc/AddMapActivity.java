package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddMapActivity extends AppCompatActivity {

    private String userName;

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

