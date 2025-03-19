package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.messaging.FirebaseMessaging;

/**
 * MainActivity.java
 *
 * This is the central hub of the GRPest Control application, providing access to all key features.
 * Users can navigate through various functionalities including report generation, contract management,
 * job assignments, service agreements, environmental risk assessments, messaging, and lead tracking.
 *
 * Features:
 * - **User Authentication & Personalization**:
 *   - Retrieves the user's email and extracts their name for a personalized welcome message.
 * - **Report Management**:
 *   - Create, view, and manage pest control reports.
 *   - Supports different report types, including rodent control, bird control, and general quotations.
 *   - Stores reports locally and in Firebase for easy access.
 * - **Contracts & Jobs**:
 *   - View, add, and manage service contracts.
 *   - Track contract status, update last visit dates, and generate contract-based reports.
 *   - Manage job assignments, including technician allocation, job status tracking, and WhatsApp notifications.
 * - **Quotations & Service Agreements**:
 *   - Generate professional quotations for pest control services with automatic VAT calculations.
 *   - Create and store structured service agreements for customers.
 * - **Leads & Commission Tracking**:
 *   - Generate and manage sales leads.
 *   - Assign contracts and jobs, track invoice payments, and manage commission calculations.
 * - **Environmental Risk Assessments (ERA)**:
 *   - Create Toxic and Non-Toxic ERA reports with structured data and digital signatures.
 * - **Messaging & Notifications**:
 *   - Provides instant messaging between staff members.
 *   - Supports job notifications via WhatsApp.
 * - **Cloud Integration**:
 *   - Uses Firebase Firestore for contract, job, lead, and report management.
 *   - Stores reports and agreements in Firebase Storage for remote access.
 * - **Navigation & External Links**:
 *   - Allows users to open Google Maps for job locations.
 *   - Directs users to the company website for additional information.
 * - **User Management & Security**:
 *   - Supports admin users (James, Ian, Kristine) with special permissions for contract and lead management.
 *   - Ensures secure login with Firebase Authentication.
 *   - Provides a logout button to securely exit the application.
 *
 * Author: James Scott
 */


public class MainActivity extends AppCompatActivity {

    private Button reportButton, reportViewButton, contractsButton, quotesButton, logoutButton, CommisionButton, ServiceAgreementButton, JobButton, EnviromentButton, InstantMessage, WebsiteButton;
    private String userEmail, userName;
    private TextView welcomeTextView;


    // You can use any request code value
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }





        userEmail = getIntent().getStringExtra("USER_EMAIL");

        if (userEmail == null || userEmail.isEmpty()) {
            userName = "User";
        } else {
            userName = extractNameFromEmail(userEmail);
        }

        // Subscribe to topics AFTER userName is ready
        FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "✅ Subscribed to topic: all");
                    } else {
                        Log.e("FCM", "❌ Failed to subscribe", task.getException());
                    }
                });

        // Subscribe to personal topic to allow exclusion from their own push notifications
        FirebaseMessaging.getInstance().subscribeToTopic(userName.toLowerCase())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "✅ Subscribed to personal topic: " + userName.toLowerCase());
                    } else {
                        Log.e("FCM", "❌ Failed to subscribe to personal topic", task.getException());
                    }
                });

        welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        } else {
            Log.e("MainActivity", "welcomeTextView is NULL! Check XML ID.");
        }



        // Initialize Buttons
        InstantMessage = findViewById(R.id.InstantMessage);
        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        contractsButton = findViewById(R.id.ContractsButton);
        quotesButton = findViewById(R.id.GeneralQuotesButton);
        ServiceAgreementButton = findViewById(R.id.ServiceAgreementButton);
        CommisionButton = findViewById(R.id.CommisionButton);
        JobButton = findViewById(R.id.JobsButton);
        EnviromentButton = findViewById(R.id.EnviromentButton);
        logoutButton = findViewById(R.id.LogoutButton);
        WebsiteButton = findViewById(R.id.WebsiteButton);

        if (InstantMessage != null) {
            InstantMessage.setOnClickListener(view -> openActivity(MessagingActivity.class));
        }

        if (WebsiteButton != null) {
            WebsiteButton.setOnClickListener(view -> openWebsite());
        }

        if (reportButton != null) {
            reportButton.setOnClickListener(view -> openActivity(ReportSelectionActivity.class));
        }

        if (reportViewButton != null) {
            reportViewButton.setOnClickListener(view -> openActivity(PDFSelectionActivity.class));
        }

        if (contractsButton != null) {
            contractsButton.setOnClickListener(view -> openActivity(ContractsActivity.class));
        }

        if (quotesButton != null) {
            quotesButton.setOnClickListener(view -> openActivity(QuotesActivity.class));
        }

        if (CommisionButton != null) {
            CommisionButton.setOnClickListener(view -> openActivity(LeadsSelectionActivity.class));
        }

        if (EnviromentButton != null) {
            EnviromentButton.setOnClickListener(view -> openActivity(EnvironmentSelectionActivity.class));
        }

        if (ServiceAgreementButton != null) {
            ServiceAgreementButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, ServiceAgreementActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        if (JobButton != null) {
            JobButton.setOnClickListener(view -> openActivity(JobsActivity.class));
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void openActivity(Class<?> targetActivity) {
        Log.d("MainActivity", "Opening " + targetActivity.getSimpleName() + " with USER_NAME: " + userName);
        Intent intent = new Intent(MainActivity.this, targetActivity);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
    }

    private void openWebsite() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://grpcstaff.com"));
        startActivity(browserIntent);
    }

    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String namePart = email.split("@")[0];
            return namePart.substring(0, 1).toUpperCase() + namePart.substring(1);
        }
        return "User";
    }

    // Optional: Handle result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permissions", "✅ Notification permission granted!");
            } else {
                Log.w("Permissions", "❌ Notification permission denied.");
            }
        }
    }
}
