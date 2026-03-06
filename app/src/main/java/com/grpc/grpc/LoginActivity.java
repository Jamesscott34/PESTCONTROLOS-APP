package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
 * Author: GRPC
 */


public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Offline flavor: no login – go straight to MainActivity with Create Report / View Reports only.
        if (BuildConfig.IS_OFFLINE) {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("USER_NAME", "Offline");
            intent.putExtra("OFFLINE_MODE", true);
            startActivity(intent);
            finish();
            return;
        }

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Safety: clear any prior cached session so shared devices never reuse old RBAC/StaffID.
        try { SessionManager.clear(this); } catch (Exception ignored) {}
        try { ActiveUserContext.clear(this); } catch (Exception ignored) {}
        try { StaffDirectory.clearCache(); } catch (Exception ignored) {}
        try { WorkViewLocalEventStore.clearAll(this); } catch (Exception ignored) {}
        try { WorkViewWidgetHelper.clearWidgetCache(this); } catch (Exception ignored) {}
        try { LocationSharing.clearLocalCache(this); } catch (Exception ignored) {}

        // Logo (prefer assets/logo.png, fallback to drawable)
        ImageView loginLogo = findViewById(R.id.loginLogo);
        BrandingAssets.trySetLogoFromAssets(loginLogo);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);

        // Login Button Action (Firebase)
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

                            // Ensure RBAC session is loaded for this Firebase user BEFORE showing UI.
                            SessionManager.ensureLoaded(LoginActivity.this, session -> runOnUiThread(() -> {
                                if (session == null) {
                                    Toast.makeText(LoginActivity.this, "Login succeeded, but profile could not be loaded. Please try again.", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Ensure users/{uid} profile exists and is merged with session fields (including canSeeContracts, canViewAllContracts).
                                UserRepository.ensureProfileForCurrentUser(LoginActivity.this, session, profile -> runOnUiThread(() -> {
                                    // When super_admin or admin first logs in, ensure the shared contracts collection exists by seeding a schema doc if empty.
                                    try {
                                        if (!BuildConfig.IS_OFFLINE && (session.isSuperAdmin || session.isAdmin)) {
                                            FirebaseFirestore.getInstance()
                                                    .collection(FirestorePaths.CONTRACTS)
                                                    .limit(1)
                                                    .get()
                                                    .addOnSuccessListener(snap -> {
                                                        if (snap == null || snap.isEmpty()) {
                                                            java.util.Map<String, Object> seed = new java.util.HashMap<>();
                                                            seed.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                                                            seed.put("schema", "contracts_v1");
                                                            FirebaseFirestore.getInstance()
                                                                    .collection(FirestorePaths.CONTRACTS)
                                                                    .document("_schema")
                                                                    .set(seed);
                                                        }
                                                    });
                                        }
                                    } catch (Exception ignored) {}

                                    // After profile is ensured, enforce viewProfile gating:
                                    // users/{uid}.viewProfile (default true) controls whether the user may proceed past login.
                                    if (!BuildConfig.IS_OFFLINE) {
                                        com.google.firebase.auth.FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
                                        if (authUser != null) {
                                            String uid = authUser.getUid();
                                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                                            db.collection(FirestorePaths.USERS)
                                                    .document(uid)
                                                    .get()
                                                    .addOnSuccessListener(snapshot -> {
                                                        Boolean viewProfile = snapshot.getBoolean("viewProfile");
                                                        // Seed missing field to true on first login without overriding explicit false.
                                                        if (viewProfile == null) {
                                                            java.util.Map<String, Object> update = new java.util.HashMap<>();
                                                            update.put("viewProfile", true);
                                                            snapshot.getReference().set(update, com.google.firebase.firestore.SetOptions.merge());
                                                            viewProfile = true;
                                                        }

                                                        if (Boolean.FALSE.equals(viewProfile)) {
                                                            new androidx.appcompat.app.AlertDialog.Builder(LoginActivity.this)
                                                                    .setTitle("Profile access issue")
                                                                    .setMessage("Please contact PestControlOS if you cannot access your profile.")
                                                                    .setPositiveButton("OK", (d, which) -> {
                                                                        try {
                                                                            FirebaseAuth.getInstance().signOut();
                                                                        } catch (Exception ignored) {}
                                                                    })
                                                                    .setCancelable(false)
                                                                    .show();
                                                            return;
                                                        }

                                                        // Allowed to proceed: open MainActivity as before.
                                                        openMainActivityWithUserExtras(email);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        // If we cannot read the flag, fall back to allowing login.
                                                        openMainActivityWithUserExtras(email);
                                                    });
                                        } else {
                                            // No auth user; fail safe by not proceeding.
                                            Toast.makeText(LoginActivity.this, "Login session expired. Please try again.", Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        // Offline flavor never reaches here in practice, but keep behavior consistent.
                                        openMainActivityWithUserExtras(email);
                                    }
                                }));
                            }));
                        } else {
                            Toast.makeText(LoginActivity.this, "Login Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Offline login: use app without Firebase (no account; custom PDF template etc.)
        Button offlineLoginButton = findViewById(R.id.offlineLoginButton);
        if (offlineLoginButton != null) {
            offlineLoginButton.setOnClickListener(view -> {
                Toast.makeText(LoginActivity.this, "Using app in offline mode", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USER_NAME", "Offline User");
                startActivity(intent);
                finish();
            });
        }
    }

    private void openMainActivityWithUserExtras(String email) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USER_EMAIL", email); // Pass email as extra
        // Critical: use ContractKey when set, else StaffID so contracts/workview use a stable key (never full name). Capitalize so "james" -> "James" to match Firestore collection names.
        String contractKey = SessionManager.getContractKey(LoginActivity.this);
        String staffId = SessionManager.getStaffId(LoginActivity.this);
        if (contractKey != null && !contractKey.trim().isEmpty()) {
            intent.putExtra("USER_NAME", StaffDirectory.capitalizeContractKey(contractKey.trim()));
        } else if (staffId != null && !staffId.trim().isEmpty()) {
            intent.putExtra("USER_NAME", staffId.trim());
        }
        startActivity(intent);
        finish();
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
