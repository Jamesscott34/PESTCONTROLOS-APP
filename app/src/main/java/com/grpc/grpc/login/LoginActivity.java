package com.grpc.grpc.login;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.main.MainActivity;
import com.grpc.grpc.R;
import com.grpc.grpc.workview.data.WorkViewLocalEventStore;
import com.grpc.grpc.workview.data.WorkViewWidgetHelper;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.grpc.grpc.core.ActiveUserContext;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.RememberMeManager;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.core.UserRepository;

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
        RememberMeManager.handleExpiryIfNeeded(this);

        // Logo: @drawable/logo in XML; background stays theme default like GRPC (see layouts).
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);

        attemptAutoLogin();

        // Login Button Action (Firebase)
        loginButton.setOnClickListener(view -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, getString(R.string.login_error_empty), Toast.LENGTH_SHORT).show();
                return;
            }

            // Firebase Authentication
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Extract the name from the email
                            String userName = extractNameFromEmail(email);

                            // Display a welcome message
                            Toast.makeText(LoginActivity.this, getString(R.string.login_welcome_toast, userName), Toast.LENGTH_LONG).show();

                            // Ensure RBAC session is loaded for this Firebase user BEFORE showing UI.
                            SessionManager.ensureLoaded(LoginActivity.this, session -> runOnUiThread(() -> {
                                if (session == null) {
                                    Toast.makeText(LoginActivity.this, getString(R.string.login_profile_unavailable), Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Log session/profile at login for debugging (authUid, role, contractKey, permission flags).
                                try {
                                    com.google.firebase.auth.FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
                                    String authUid = authUser != null ? authUser.getUid() : "null";
                                    Log.d("LoginActivity", "Session loaded: authUid=" + authUid
                                            + " role=" + (session.roleNorm != null ? session.roleNorm : "")
                                            + " contractKey=" + (session.contractKey != null ? session.contractKey : "")
                                            + " staffId=" + (session.staffId != null ? session.staffId : "")
                                            + " canSearch=" + session.canSearch
                                            + " canSeeContracts=" + session.canSeeContracts
                                            + " canViewAllContracts=" + session.canViewAllContracts
                                            + " canMessage=" + session.canMessage
                                            + " canMap=" + session.canMap);
                                } catch (Exception ignored) {}

                                // Permission flags are already set on the profile (true/false by role). No block; proceed with login.

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
                                                                    .setTitle(getString(R.string.login_profile_access_title))
                                                                    .setMessage(getString(R.string.login_profile_access_message))
                                                                    .setPositiveButton("OK", (d, which) -> {
                                                                        try {
                                                                            FirebaseAuth.getInstance().signOut();
                                                                        } catch (Exception ignored) {}
                                                                    })
                                                                    .setCancelable(false)
                                                                    .show();
                                                            return;
                                                        }

                                                        // Always enable Remember Me on successful login. canRemember is patched to true so
                                                        // auto-login works on next app open. Explicit logout sets canRemember: false via
                                                        // RememberMeManager.disableForCurrentUser(), which re-login will restore.
                                                        java.util.Map<String, Object> rememberPatch = new java.util.HashMap<>();
                                                        rememberPatch.put("canRemember", true);
                                                        FirebaseFirestore.getInstance()
                                                                .collection(FirestorePaths.USERS)
                                                                .document(uid)
                                                                .set(rememberPatch, com.google.firebase.firestore.SetOptions.merge());
                                                        RememberMeManager.enable(LoginActivity.this, uid, email);

                                                        // Allowed to proceed: open MainActivity as before.
                                                        openMainActivityWithUserExtras(email);
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        // If we cannot read the flag, fall back to allowing login.
                                                        openMainActivityWithUserExtras(email);
                                                    });
                                        } else {
                                            // No auth user; fail safe by not proceeding.
                                            Toast.makeText(LoginActivity.this, getString(R.string.login_session_expired), Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        // Offline flavor never reaches here in practice, but keep behavior consistent.
                                        openMainActivityWithUserExtras(email);
                                    }
                                }));
                            }));
                        } else {
                            Toast.makeText(LoginActivity.this, getString(R.string.login_failed, task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Offline login: use app without Firebase (no account; custom PDF template etc.)
        Button offlineLoginButton = findViewById(R.id.offlineLoginButton);
        if (offlineLoginButton != null) {
            offlineLoginButton.setOnClickListener(view -> {
                Toast.makeText(LoginActivity.this, getString(R.string.login_offline_toast), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USER_NAME", "Offline User");
                startActivity(intent);
                finish();
            });
        }
    }

    private void attemptAutoLogin() {
        if (mAuth.getCurrentUser() == null || !RememberMeManager.isActive(this)) {
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        RememberMeManager.clear(LoginActivity.this);
                        return;
                    }
                    SessionManager.ensureLoaded(LoginActivity.this, session -> runOnUiThread(() -> {
                        String email = mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getEmail() != null
                                ? mAuth.getCurrentUser().getEmail()
                                : RememberMeManager.getRememberedEmail(LoginActivity.this);
                        openMainActivityWithUserExtras(email);
                    }));
                })
                .addOnFailureListener(e -> {
                    // Stay on login screen if profile cannot be read.
                });
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
                                                                                                                                                                                                                        