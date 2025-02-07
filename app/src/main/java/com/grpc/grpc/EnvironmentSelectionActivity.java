package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class EnvironmentSelectionActivity extends AppCompatActivity {

    private Button NonToxButton,ToxicButton;
    private String userEmail, userName;
    private TextView welcomeTextView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_era);

        userName = getIntent().getStringExtra("USER_NAME");

        // Initialize the welcome TextView
        welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        ToxicButton = findViewById(R.id.ToxicButton);
        NonToxButton = findViewById(R.id.NonToxButton);

        // Set Button Click Listeners
        if (ToxicButton != null) {
            ToxicButton.setOnClickListener(view -> openActivity(ToxicERAActivity.class));
        }
        if (NonToxButton != null) {
            NonToxButton.setOnClickListener(view -> openActivity(NonToxERAActivity.class));


        }
    }

    // Helper method to start a new activity safely
    private void openActivity(Class<?> targetActivity) {
        Log.d("MainActivity", "Opening " + targetActivity.getSimpleName() + " with USER_NAME: " + userName);
        Intent intent = new Intent(EnvironmentSelectionActivity.this, targetActivity);
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
