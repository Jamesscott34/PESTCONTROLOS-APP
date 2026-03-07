package com.grpc.grpc.leads.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * LeadsSelectionActivity.java
 *
 * This activity serves as the main hub for managing sales leads. Users can either generate
 * new leads or view existing lead records. The username is retrieved from the intent and
 * passed to subsequent activities to maintain user session.
 *
 * Features:
 * - Displays a welcome message with the user's name
 * - Allows navigation to the lead generation screen
 * - Allows navigation to the lead viewing screen
 * - Ensures USER_NAME is always passed to the next activity
 *
 * Author: GRPC
 */


public class LeadsSelectionActivity extends AppCompatActivity {

    private Button GenerateLeadsButton, ViewLeadButton;
    private String userName;
    private TextView welcomeTextView;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leads_selection);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.trim().isEmpty()) {
            userName = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_NAME", "User");
        }

        // Initialize the welcome TextView
        welcomeTextView = findViewById(R.id.welcomeTextView);
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

        GenerateLeadsButton = findViewById(R.id.GenerateLeadsButton);
        ViewLeadButton = findViewById(R.id.ViewLeadButton);

        GenerateLeadsButton.setOnClickListener(view -> {
            Intent intent = new Intent(LeadsSelectionActivity.this, GenerateLeadsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        ViewLeadButton.setOnClickListener(view -> {
            Intent intent = new Intent(LeadsSelectionActivity.this, ViewLeadsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });


    }
}
