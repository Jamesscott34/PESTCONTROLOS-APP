package com.grpc.grpc;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class OfflineLoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private Button loginButton;
    private Button offlineModeButton;
    private TextView statusText;
    private UserDatabaseHelper userDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_login);

        // Initialize database helper
        userDb = new UserDatabaseHelper(this);

        // Initialize UI components
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        offlineModeButton = findViewById(R.id.offlineModeButton);
        statusText = findViewById(R.id.statusText);

        // Check network status
        checkNetworkStatus();

        // Set up button listeners
        setupButtonListeners();
    }

    /**
     * Check if device is online
     */
    private boolean isOnline() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                boolean isConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
                Log.d("OfflineLoginActivity", "Network status: " + (isConnected ? "ONLINE" : "OFFLINE"));
                return isConnected;
            }
        } catch (Exception e) {
            Log.e("OfflineLoginActivity", "Error checking network status: " + e.getMessage());
        }
        // Default to online if we can't determine status
        Log.d("OfflineLoginActivity", "Defaulting to ONLINE mode");
        return true;
    }

    /**
     * Check network status and update UI
     */
    private void checkNetworkStatus() {
        if (isOnline()) {
            statusText.setText("Online Mode - Full functionality available");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            loginButton.setEnabled(true);
            offlineModeButton.setEnabled(true);
        } else {
            statusText.setText("Offline Mode - Same as online");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            loginButton.setEnabled(true);
            offlineModeButton.setEnabled(true);
        }
    }

    /**
     * Set up button click listeners
     */
    private void setupButtonListeners() {
        loginButton.setOnClickListener(v -> attemptLogin());

        offlineModeButton.setOnClickListener(v -> {
            // Show offline mode info
            Toast.makeText(this, "Offline mode: Same as online", Toast.LENGTH_LONG).show();
            
            // For offline mode, use default credentials
            String userName = "User";
            String email = "user@grpc.com";
            proceedToMainActivity(userName, email, true);
        });
    }

    /**
     * Attempt to login user
     */
    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Accept any valid email format and password length >= 3
        if (isValidEmail(email) && password.length() >= 3) {
            String userName = extractNameFromEmail(email);
            Log.d("OfflineLoginActivity", "Login successful for: " + email);
            proceedToMainActivity(userName, email, false);
        } else {
            Toast.makeText(this, "Please enter a valid email and password (min 3 characters)", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Validate email format
     */
    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /**
     * Extract name from email address
     */
    private String extractNameFromEmail(String email) {
        String[] parts = email.split("@");
        if (parts.length > 0) {
            String name = parts[0];
            return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
        }
        return "User";
    }

    /**
     * Proceed to main activity with user information
     */
    private void proceedToMainActivity(String userName, String userEmail, boolean isOfflineMode) {
        Intent intent = new Intent(OfflineLoginActivity.this, MainActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("USER_EMAIL", userEmail);
        intent.putExtra("OFFLINE_MODE", isOfflineMode);
        
        Log.d("OfflineLoginActivity", "Proceeding to MainActivity with user: " + userName + 
              ", offline mode: " + isOfflineMode);
        
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recheck network status when returning to login
        checkNetworkStatus();
    }
} 