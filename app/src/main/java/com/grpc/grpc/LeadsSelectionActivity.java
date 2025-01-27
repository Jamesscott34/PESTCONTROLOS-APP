package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class LeadsSelectionActivity extends AppCompatActivity {

    private Button GenerateLeadsButton, ViewLeadButton;
    private String userName;
    private TextView welcomeTextView;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leads_selection);



        userName = getIntent().getStringExtra("USER_NAME");

        // Initialize the welcome TextView
        welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

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
