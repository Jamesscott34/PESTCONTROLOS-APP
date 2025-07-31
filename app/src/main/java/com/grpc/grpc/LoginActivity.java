package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/**
 * LoginActivity.java
 *
 * This activity handles user authentication using Firebase Authentication.
 * Users enter their email and password to log in, and upon successful authentication,
 * they are redirected to the main activity with their email passed along.
 *
 * Features:
 * - User authentication using Firebase
 * - Input validation for email and password fields
 * - Displays a welcome message upon successful login
 * - Extracts the user's name from their email for a personalized experience
 * - Redirects to the main activity upon successful authentication
 *
 * Author: James Scott
 */


public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Logo (prefer assets/logo.png, fallback to drawable)
        ImageView loginLogo = findViewById(R.id.loginLogo);
        BrandingAssets.trySetLogoFromAssets(loginLogo);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);

        // Login Button Action
        loginButton.setOnClickListener(view -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Firebase Authentication
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Extract the name from the email
                            String userName = extractNameFromEmail(email);

                            // Display a welcome message
                            Toast.makeText(LoginActivity.this, "Welcome " + userName + "!", Toast.LENGTH_LONG).show();

                            // Navigate to the main activity and pass the email
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            intent.putExtra("USER_EMAIL", email); // Pass email as extra
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(LoginActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    // Helper to extract the name from the email
    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String namePart = email.split("@")[0];
            return namePart.substring(0, 1).toUpperCase() + namePart.substring(1); // Capitalize first letter
        }
        return "User";
    }
}
