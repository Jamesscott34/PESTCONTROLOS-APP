package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * ContractsActivity.java
 *
 * This activity serves as the main contract management screen, allowing users
 * to either add new contracts or view existing ones. It retrieves the user's
 * name from the intent and passes it to the respective contract-related activities.
 *
 * Features:
 * - Displays a welcome message to the user
 * - Provides navigation options for adding or viewing contracts
 * - Passes the username to subsequent activities for contract management
 *
 * Author: James Scott
 */

public class ContractsActivity extends AppCompatActivity {
    /**
     * Initializes the activity, retrieves the username from intent, and sets up UI elements.
     * Handles button click events for navigating to the contract management screens.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */
    private Button addContractButton, viewContractButton;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contract_selection);

        // Retrieve the user's name from the intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) {
            userName = "Unknown"; // Default username
        }

        // Display a welcome message
        Toast.makeText(this, "Welcome to Contracts, " + userName + "!", Toast.LENGTH_SHORT).show();

        // Initialize buttons
        addContractButton = findViewById(R.id.AddContractButton);
        viewContractButton = findViewById(R.id.ViewContractButton);

        // Add Contract Button Listener
        addContractButton.setOnClickListener(v -> {
            Intent intent = new Intent(ContractsActivity.this, AddContractActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass the username to AddContractActivity
            startActivity(intent);
        });

        // View Contract Button Listener
        viewContractButton.setOnClickListener(v -> {
            Intent intent = new Intent(ContractsActivity.this, ViewContractActivity.class);
            intent.putExtra("USER_NAME", userName); // Pass the username to ViewContractActivity
            startActivity(intent);
        });
    }
}
