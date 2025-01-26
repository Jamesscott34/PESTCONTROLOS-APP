package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ContractsActivity extends AppCompatActivity {

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
