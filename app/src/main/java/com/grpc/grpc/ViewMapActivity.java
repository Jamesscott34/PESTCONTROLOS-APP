package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ViewMapActivity extends AppCompatActivity {

    private String userName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_map);

        // Retrieve USER_NAME from intent
        userName = getIntent().getStringExtra("USER_NAME");

        // Ensure USER_NAME is valid
        if (userName == null || userName.trim().isEmpty()) {
            userName = "Unknown User";  // Default value to prevent crashes
            Toast.makeText(this, "Error: USER_NAME not received!", Toast.LENGTH_SHORT).show();
        }

        // Set the welcome message
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        welcomeTextView.setText("Welcome, " + userName + "!");

        // Buttons and their click listeners

        Button btnStencilMap = findViewById(R.id.btnStencilMap);
        Button btnViewSavedMaps = findViewById(R.id.btnViewMaps);


        btnStencilMap.setOnClickListener(v -> openActivity(StencilMapActivity.class));
        btnViewSavedMaps.setOnClickListener(v -> openActivity(SavedMapsActivity.class));

    }

    private void openActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.putExtra("USER_NAME", userName); // Pass USER_NAME to the next activity
        startActivity(intent);
    }
}
