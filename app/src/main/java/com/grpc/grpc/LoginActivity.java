package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/**
 * ============================================================================
 * GRPest Control Application - Login Activity
 * ============================================================================
 * 
 * AUTHENTICATION OVERVIEW:
 * This activity serves as the entry point for user authentication in the
 * GRPest Control application. It provides secure login functionality using
 * Firebase Authentication, ensuring only authorized users can access the
 * pest control management system.
 * 
 * SECURITY FEATURES:
 * - Firebase Authentication for secure user verification
 * - Email and password validation
 * - Secure session management
 * - User role-based access control
 * 
 * USER FLOW:
 * 1. User enters email and password
 * 2. Input validation ensures required fields are completed
 * 3. Firebase Authentication verifies credentials
 * 4. Upon success, user name is extracted from email
 * 5. Welcome message is displayed
 * 6. User is redirected to MainActivity with email passed as extra
 * 
 * SUPPORTED USER ROLES:
 * - Admin Users (James, Ian, Kristine): Full system access
 * - Technicians: Job assignment and report generation
 * - Sales Staff: Lead management and commission tracking
 * 
 * TECHNICAL DETAILS:
 * - Uses Firebase Auth for secure authentication
 * - Extracts user-friendly name from email address
 * - Passes user information to subsequent activities
 * - Handles authentication errors gracefully
 * 
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

public class LoginActivity extends AppCompatActivity {

    // UI Components for user input and interaction
    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    
    // Firebase Authentication instance for secure login
    private FirebaseAuth mAuth;

    /**
     * Main entry point of the login activity
     * Initializes the user interface and sets up authentication
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Authentication for secure user verification
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI components by finding them in the layout
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);

        // Set up login button click listener for user authentication
        setupLoginButton();
    }

    /**
     * Sets up the login button with authentication logic
     * Handles user input validation and Firebase authentication
     */
    private void setupLoginButton() {
        loginButton.setOnClickListener(view -> {
            // Get user input and trim whitespace
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            // Validate that both email and password are provided
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Perform Firebase Authentication with provided credentials
            authenticateUser(email, password);
        });
    }

    /**
     * Authenticates user credentials using Firebase Authentication
     * Handles success and failure scenarios appropriately
     * 
     * @param email The user's email address
     * @param password The user's password
     */
    private void authenticateUser(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Authentication successful - proceed to main application
                        handleSuccessfulLogin(email);
                    } else {
                        // Authentication failed - show error message
                        handleFailedLogin(task.getException().getMessage());
                    }
                });
    }

    /**
     * Handles successful user authentication
     * Extracts user name, displays welcome message, and navigates to main activity
     * 
     * @param email The authenticated user's email address
     */
    private void handleSuccessfulLogin(String email) {
        // Extract a user-friendly name from the email address
        String userName = extractNameFromEmail(email);

        // Display personalized welcome message
        Toast.makeText(LoginActivity.this, "Welcome " + userName + "!", Toast.LENGTH_LONG).show();

        // Navigate to the main activity and pass the email for user identification
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USER_EMAIL", email); // Pass email as extra for user context
        startActivity(intent);
        finish(); // Close login activity to prevent back navigation
    }

    /**
     * Handles failed authentication attempts
     * Displays appropriate error message to the user
     * 
     * @param errorMessage The error message from Firebase Authentication
     */
    private void handleFailedLogin(String errorMessage) {
        Toast.makeText(LoginActivity.this, "Login Failed: " + errorMessage, Toast.LENGTH_SHORT).show();
    }

    /**
     * Extracts a user-friendly name from an email address
     * Converts email format to display name (e.g., "james@grpc.com" -> "James")
     * 
     * @param email The email address to extract name from
     * @return The extracted name with first letter capitalized, or "User" if invalid
     */
    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String namePart = email.split("@")[0];
            return namePart.substring(0, 1).toUpperCase() + namePart.substring(1); // Capitalize first letter
        }
        return "User";
    }
}
