package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ContractsActivity extends AppCompatActivity {

    private Button addContractButton, viewContractButton;
    private String userName; // To hold the logged-in user's name

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contract_selection);

        // Retrieve the name passed from the previous activity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            userName = "Unknown"; // Fallback in case name is missing
        }

        // Display a welcome toast message with the name
        Toast.makeText(this, "Welcome to Contracts, " + userName + "!", Toast.LENGTH_SHORT).show();

        // Initialize buttons
        addContractButton = findViewById(R.id.AddContractButton);
        viewContractButton = findViewById(R.id.ViewContractButton);

        // Add Contract Button Listener
        addContractButton.setOnClickListener(view -> {
            // Navigate to Add Contract screen and pass the name
            Intent intent = new Intent(ContractsActivity.this, AddContractActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass name to AddContractActivity
            startActivity(intent);
        });

        // View Contract Button Listener
        viewContractButton.setOnClickListener(view -> {
            // Navigate to View Contract screen and pass the name
            Intent intent = new Intent(ContractsActivity.this, ViewContractActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass name to ViewContractActivity
            startActivity(intent);
        });
    }
}
