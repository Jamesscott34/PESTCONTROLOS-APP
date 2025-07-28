package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.messaging.FirebaseMessaging;

/**
 * ============================================================================
 * GRPest Control Application - Main Activity
 * ============================================================================
 * 
 * PROJECT OVERVIEW:
 * This is a comprehensive pest control management application for Good Riddance 
 * Pest Control (GRPC). The app serves as a complete business management solution
 * for pest control operations, allowing technicians and administrators to manage
 * all aspects of the business from a single mobile application.
 * 
 * CORE FUNCTIONALITIES:
 * 
 * 1. REPORT MANAGEMENT SYSTEM
 *    - Generate detailed pest control reports for various services
 *    - Support for rodent control, bird control, and general pest treatments
 *    - PDF generation with professional formatting and company branding
 *    - Local and cloud storage for easy access and sharing
 * 
 * 2. CONTRACT & CUSTOMER MANAGEMENT
 *    - Track service contracts with automatic renewal reminders
 *    - Manage customer information and service history
 *    - Generate "behinds list" reports for overdue contracts
 *    - View contract-specific reports and documentation
 * 
 * 3. JOB ASSIGNMENT & TRACKING
 *    - Assign jobs to technicians with location tracking
 *    - Real-time job status updates and completion tracking
 *    - WhatsApp integration for instant job notifications
 *    - Route optimization and scheduling capabilities
 * 
 * 4. QUOTATION & SERVICE AGREEMENTS
 *    - Professional quotation generation with automatic VAT calculations
 *    - Service agreement templates with digital signatures
 *    - Customer approval tracking and contract management
 * 
 * 5. SALES & COMMISSION TRACKING
 *    - Lead generation and management system
 *    - Commission calculation and tracking for sales staff
 *    - Invoice management and payment tracking
 *    - Performance analytics and reporting
 * 
 * 6. ENVIRONMENTAL COMPLIANCE
 *    - Toxic and Non-Toxic Environmental Risk Assessments (ERA)
 *    - Regulatory compliance documentation
 *    - Safety procedure tracking and certification
 * 
 * 7. COMMUNICATION & NOTIFICATIONS
 *    - Internal messaging system for staff communication
 *    - Push notifications for job assignments and updates
 *    - WhatsApp integration for customer communications
 * 
 * 8. CLOUD INTEGRATION & DATA MANAGEMENT
 *    - Firebase Firestore for real-time data synchronization
 *    - Firebase Storage for document and report storage
 *    - Multi-user access with role-based permissions
 *    - Offline capability with local data caching
 * 
 * TECHNICAL ARCHITECTURE:
 * - Android Native Application (Java)
 * - Firebase Backend (Firestore + Storage + Authentication)
 * - PDF Generation using iText7 library
 * - Real-time data synchronization
 * - Push notification system
 * - External API integrations (WhatsApp, Maps)
 * 
 * USER ROLES & PERMISSIONS:
 * - Admin Users (James, Ian, Kristine): Full access to all features
 * - Technicians: Job assignment, report generation, customer management
 * - Sales Staff: Lead management, quotation generation, commission tracking
 * 
 * SECURITY FEATURES:
 * - Firebase Authentication for secure login
 * - Role-based access control
 * - Encrypted data transmission
 * - Secure document storage
 * 
 * EXTERNAL INTEGRATIONS:
 * - Google Maps for location services
 * - WhatsApp Business API for communications
 * - Company website integration
 * - Email and SMS notifications
 * 
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

public class MainActivity extends AppCompatActivity {

    // UI Components - Navigation buttons for all major features
    private Button reportButton, reportViewButton, contractsButton, quotesButton, logoutButton, 
                   CommisionButton, ServiceAgreementButton, JobButton, EnviromentButton, 
                   InstantMessage, WebsiteButton, WorkViewButton;
    
    // User information extracted from login
    private String userEmail, userName;
    private boolean isOfflineMode = false;
    
    // Welcome message display
    private TextView welcomeTextView;
    private TextView offlineModeIndicator;
    private GestureDetectorCompat gestureDetector;

    // Permission request code for notification access
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1;
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    /**
     * Main entry point of the application
     * Sets up the user interface, handles user authentication,
     * and initializes all navigation buttons and features
     */
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        // Request notification permission for Android 13+ devices
        // This is required for push notifications to work properly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }

        // Extract user information from intent
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        String intentUserName = getIntent().getStringExtra("USER_NAME");
        isOfflineMode = getIntent().getBooleanExtra("OFFLINE_MODE", false);

        // Set user name from intent extras or extract from email
        if (intentUserName != null && !intentUserName.isEmpty()) {
            // Username passed from swipe navigation
            userName = intentUserName;
            Log.d("MainActivity", "Username from intent: " + userName);
        } else if (userEmail != null && !userEmail.isEmpty()) {
            // Extract name from email address (e.g., "james@grpc.com" -> "James")
            userName = extractNameFromEmail(userEmail);
            Log.d("MainActivity", "Username extracted from email: " + userName);
        } else {
            // Default fallback
            userName = "User";
            Log.d("MainActivity", "Using default username: " + userName);
        }

        // Log offline mode status
        if (isOfflineMode) {
            Log.d("MainActivity", "Running in OFFLINE MODE - Limited functionality");
        } else {
            Log.d("MainActivity", "Running in ONLINE MODE - Full functionality");
        }

        // Subscribe to Firebase Cloud Messaging topics for push notifications
        // "all" topic receives general notifications
        FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "✅ Subscribed to topic: all");
                    } else {
                        Log.e("FCM", "❌ Failed to subscribe", task.getException());
                    }
                });

        // Subscribe to personal topic to allow exclusion from their own push notifications
        // This prevents users from receiving notifications they sent themselves
        FirebaseMessaging.getInstance().subscribeToTopic(userName.toLowerCase())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "✅ Subscribed to personal topic: " + userName.toLowerCase());
                    } else {
                        Log.e("FCM", "❌ Failed to subscribe to personal topic", task.getException());
                    }
                });

        // Set up welcome message with user's name
        welcomeTextView = findViewById(R.id.welcomeTextView);
        offlineModeIndicator = findViewById(R.id.offlineModeIndicator);
        
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
            Log.d("MainActivity", "Welcome message set for user: " + userName);
        } else {
            Log.e("MainActivity", "welcomeTextView is NULL! Check XML ID.");
        }

        // Show offline mode indicator if in offline mode
        if (isOfflineMode && offlineModeIndicator != null) {
            offlineModeIndicator.setText("🔄 OFFLINE MODE - Limited functionality available");
            offlineModeIndicator.setVisibility(View.VISIBLE);
            offlineModeIndicator.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
        }
        
        // Log activity creation for debugging
        Log.d("MainActivity", "MainActivity created with user: " + userName);

        // Initialize all navigation buttons
        initializeButtons();
        
        // Set up click listeners for all buttons
        setupButtonClickListeners();
        
        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();
    }

    /**
     * Initialize gesture detector for swipe navigation
     */
    private void initializeGestureDetector() {
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    
                    Log.d("MainActivity", "Swipe detected - diffX: " + diffX + ", diffY: " + diffY + ", velocityX: " + velocityX);
                    
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Swipe right - open ViewContractActivity
                                Log.d("MainActivity", "Swipe RIGHT detected - opening ViewContractActivity with user: " + userName);
                                Intent intent = new Intent(MainActivity.this, ViewContractActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            } else {
                                // Swipe left - open ReportActivity (previous in sequence)
                                Log.d("MainActivity", "Swipe LEFT detected - opening ReportActivity with user: " + userName);
                                Intent intent = new Intent(MainActivity.this, ReportActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            }
                        } else {
                            Log.d("MainActivity", "Swipe threshold not met - diffX: " + Math.abs(diffX) + ", velocityX: " + Math.abs(velocityX));
                        }
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error in swipe detection: " + e.getMessage());
                }
                return false;
            }
        });
    }

    /**
     * Handle touch events for swipe gestures
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Initialize all UI button components by finding them in the layout
     */
    private void initializeButtons() {
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
        WorkViewButton = findViewById(R.id.WorkViewButton);
    }

    /**
     * Set up click listeners for all navigation buttons
     * Each button opens a specific activity with user information passed along
     */
    private void setupButtonClickListeners() {
        // Instant messaging system for staff communication
        if (InstantMessage != null) {
            InstantMessage.setOnClickListener(view -> {
                if (isOfflineMode) {
                    Toast.makeText(this, "Messaging requires online connection", Toast.LENGTH_SHORT).show();
                } else {
                    openActivity(MessagingActivity.class);
                }
            });
        }

        // Company website access
        if (WebsiteButton != null) {
            WebsiteButton.setOnClickListener(view -> openWebsite());
        }

        // Report generation and management
        if (reportButton != null) {
            reportButton.setOnClickListener(view -> {
                if (isOfflineMode) {
                    // In offline mode, go directly to report creation
                    openActivity(ReportActivity.class);
                } else {
                    // In online mode, go to report selection
                    openActivity(ReportSelectionActivity.class);
                }
            });
        }

        // View and manage stored reports
        if (reportViewButton != null) {
            reportViewButton.setOnClickListener(view -> {
                if (isOfflineMode) {
                    Toast.makeText(this, "Report viewing requires online connection", Toast.LENGTH_SHORT).show();
                } else {
                    openActivity(PDFSelectionActivity.class);
                }
            });
        }

        // Contract management and customer tracking
        if (contractsButton != null) {
            contractsButton.setOnClickListener(view -> {
                if (isOfflineMode) {
                    Toast.makeText(this, "Contract management requires online connection", Toast.LENGTH_SHORT).show();
                } else {
                    openActivity(ContractsActivity.class);
                }
            });
        }

        // Quotation generation and management
        if (quotesButton != null) {
            quotesButton.setOnClickListener(view -> openActivity(QuotesActivity.class));
        }

        // Sales leads and commission tracking
        if (CommisionButton != null) {
            CommisionButton.setOnClickListener(view -> openActivity(LeadsSelectionActivity.class));
        }

        // Environmental Risk Assessments (ERA)
        if (EnviromentButton != null) {
            EnviromentButton.setOnClickListener(view -> openActivity(EnvironmentSelectionActivity.class));
        }

        // Service agreement creation and management
        if (ServiceAgreementButton != null) {
            ServiceAgreementButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, ServiceAgreementActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Job assignment and tracking
        if (JobButton != null) {
            JobButton.setOnClickListener(view -> openActivity(JobsActivity.class));
        }

        // Work View Calendar and scheduling
        if (WorkViewButton != null) {
            WorkViewButton.setOnClickListener(view -> {
                if (isOfflineMode) {
                    Toast.makeText(this, "Work schedule requires online connection", Toast.LENGTH_SHORT).show();
                } else {
                    openActivity(WorkViewActivity.class);
                }
            });
        }

        // Secure logout - clears activity stack and returns to login
        if (logoutButton != null) {
            logoutButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    /**
     * Generic method to open activities with user information
     * Passes the current user's name to the target activity
     * 
     * @param targetActivity The activity class to open
     */
    private void openActivity(Class<?> targetActivity) {
        Log.d("MainActivity", "Opening " + targetActivity.getSimpleName() + " with USER_NAME: " + userName);
        Intent intent = new Intent(MainActivity.this, targetActivity);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
    }

    /**
     * Opens the company website in the device's default browser
     * Provides quick access to company information and resources
     */
    private void openWebsite() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://grpcstaff.com"));
        startActivity(browserIntent);
    }

    /**
     * Extracts a user-friendly name from an email address
     * Converts email format to display name (e.g., "james@grpc.com" -> "James")
     * 
     * @param email The email address to extract name from
     * @return The extracted name with first letter capitalized
     */
    private String extractNameFromEmail(String email) {
        if (email != null && email.contains("@")) {
            String namePart = email.split("@")[0];
            return namePart.substring(0, 1).toUpperCase() + namePart.substring(1);
        }
        return "User";
    }

    /**
     * Handles the result of permission requests
     * Specifically handles notification permission for push notifications
     * 
     * @param requestCode The request code for the permission request
     * @param permissions Array of requested permissions
     * @param grantResults Array of permission grant results
     */
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
