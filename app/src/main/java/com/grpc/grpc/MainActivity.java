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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
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
 * - Admin users (001, 002, 004): Full access to all features
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
 * Author: GRPC
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

public class MainActivity extends AppCompatActivity {

    // UI Components - Navigation buttons for all major features
    private Button reportButton, reportViewButton, contractsButton, quotesButton, logoutButton,
                   CommisionButton, ServiceAgreementButton, JobButton, EnviromentButton,
                   InstantMessage, WebsiteButton, WorkViewButton, ChatButton, HelpButton,
                   NotificationsButton, LocationFinderButton, SearchButton;
    
    // User information extracted from login
    private String userEmail, userName;
    
    // Welcome message display
    private TextView welcomeTextView;
    private GestureDetectorCompat gestureDetector;

    // Firestore (for in-app unread indicators)
    private FirebaseFirestore db;

    // Permission request code for notification access
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
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
        
        // Performance optimizations
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );



        // Request notification permission for Android 13+ devices
        // This is required for push notifications to work properly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
        }

        // Extract user information from intent
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        String intentUserName = getIntent().getStringExtra("USER_NAME");

        // Set user name from intent extras or extract from email
        if (intentUserName != null && !intentUserName.isEmpty()) {
            // Username passed from swipe navigation
            userName = intentUserName;
            Log.d("MainActivity", "Username from intent: " + userName);
        } else if (userEmail != null && !userEmail.isEmpty()) {
            // Extract name from email address (e.g. user from user@domain)
            userName = extractNameFromEmail(userEmail);
            Log.d("MainActivity", "Username extracted from email: " + userName);
        } else {
            // Default fallback - try saved preference (single source of truth; swipe never loses context)
            userName = ActiveUserContext.getActiveUserName(this, "User");
            Log.d("MainActivity", "Using saved/default username: " + userName);
        }
        // Persist logged-in identity for other screens
        ActiveUserContext.setActiveUser(this, userName, userEmail != null ? userEmail : "");

        // Load staff names and numbers from Firestore (users) so reports/WhatsApp use DB data
        StaffDirectory.refreshFromFirestore(this);

        // Location sharing: schedule background workers (best effort)
        LocationSharing.ensureScheduled(this, userName);

        // WorkView popup reminders (local only): schedule for this user
        WorkViewPopupReminderScheduler.scheduleUpcomingForUser(this, userName);

        // First login every 24h: auto-generate behinds + due PDFs for this user, then in-app notify
        DailyContractPdfHelper.scheduleDailyPdfIfNeeded(this, userName);

        // Request location permission once so technicians can publish their last location.
        // If denied, the location system will simply have no data.
        maybeRequestLocationPermission();

        // Log online mode status
        Log.d("MainActivity", "Running in ONLINE MODE - Full functionality");

        // Set up welcome message with user's name
        welcomeTextView = findViewById(R.id.welcomeTextView);

        // Logo next to welcome text (prefer assets/logo.png, fallback to drawable)
        ImageView welcomeLogo = findViewById(R.id.welcomeLogo);
        BrandingAssets.trySetLogoFromAssets(welcomeLogo);
        
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
            Log.d("MainActivity", "Welcome message set for user: " + userName);
        } else {
            Log.e("MainActivity", "welcomeTextView is NULL! Check XML ID.");
        }
        
        // Log activity creation for debugging
        Log.d("MainActivity", "MainActivity created with user: " + userName);

        // Initialize all navigation buttons
        initializeButtons();
        
        // Set up click listeners for all buttons
        setupButtonClickListeners();

        // Offline user: show only Create Report, View Reports, and Logout
        applyOfflineUserVisibility();
        
        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();

        db = FirebaseFirestore.getInstance();
        
        // Initialize Firebase operations on background thread
        new Thread(() -> {
            // IMPORTANT: Do NOT use the "all" topic (it causes everyone to receive broadcasts).
            // Unsubscribe to clean up older installs that previously subscribed.
            FirebaseMessaging.getInstance().unsubscribeFromTopic("all");
            // In-app only notifications: unsubscribe from personal topic too (no push notifications)
            if (userName != null && !userName.trim().isEmpty()) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(userName.toLowerCase());
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check whenever user returns to home screen
        checkHomeUnreadIndicators();
    }

    /**
     * In-app indicator: highlight where to go for new Notifications / Messaging.
     * - Notifications: checks Firestore notification history (read=false)
     * - Messaging: checks latest chat messages vs per-conversation last-seen timestamp
     */
    private void checkHomeUnreadIndicators() {
        if (userName == null || userName.trim().isEmpty() || db == null) return;
        checkUnreadNotifications();
        checkUnreadMessages();
    }

    private void checkUnreadNotifications() {
        final String userKey = userName.trim().toLowerCase();
        db.collection("notifications")
                .document(userKey)
                .collection("items")
                .whereEqualTo("read", false)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean hasUnread = snapshot != null && !snapshot.isEmpty();
                    updateHomeButtonBadge(NotificationsButton, "Notifications", hasUnread,
                            "HAS_UNREAD_NOTIF", "You have new notifications — tap Notifications");
                })
                .addOnFailureListener(e -> {
                    // Fail silently; don't block home screen
                });
    }

    private void checkUnreadMessages() {
        // IMPORTANT: Don't reset unread state on every resume, otherwise the prompt repeats.
        // We only clear the unread badge after we've confirmed there are NO unread conversations.
        if (InstantMessage != null) {
            InstantMessage.setText("Messaging");
        }

        java.util.ArrayList<String> convIds = new java.util.ArrayList<>();
        for (String id : StaffDirectory.ORDERED_USER_IDS) {
            String other = StaffDirectory.getUserNameKey(id);
            if (other == null || other.equalsIgnoreCase(userName)) continue;
            convIds.add(MessagingConversationsActivity.getConversationId(userName, other));
        }
        convIds.add("group");

        final boolean[] anyUnread = {false};
        final int[] pending = {convIds.size()};

        for (String convId : convIds) {
            checkConversationHasUnread(convId, hasUnread -> {
                if (hasUnread) {
                    anyUnread[0] = true;
                    updateHomeButtonBadge(InstantMessage, "Messaging", true,
                            "HAS_UNREAD_MSG", "You have new messages — tap Messaging");
                }

                pending[0]--;
                if (pending[0] <= 0 && !anyUnread[0]) {
                    // No unread anywhere: clear badge + stored state so next NEW triggers prompt once.
                    updateHomeButtonBadge(InstantMessage, "Messaging", false, "HAS_UNREAD_MSG", null);
                }
            });
        }
    }

    private interface UnreadResultCallback {
        void onResult(boolean hasUnread);
    }

    private void checkConversationHasUnread(String conversationId, UnreadResultCallback callback) {
        final String prefKey = "CHAT_LAST_SEEN_" + conversationId;
        final long lastSeen = getSharedPreferences("GRPC", MODE_PRIVATE).getLong(prefKey, 0L);

        db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        if (callback != null) callback.onResult(false);
                        return;
                    }

                    DocumentSnapshot doc = snapshot.getDocuments().get(0);
                    java.util.Date ts = doc.getDate("timestamp");
                    String sender = doc.getString("sender");
                    long latest = ts != null ? ts.getTime() : 0L;
                    boolean fromOtherPerson = sender != null && !sender.equalsIgnoreCase(userName);
                    boolean hasUnread = fromOtherPerson && latest > lastSeen;

                    if (callback != null) callback.onResult(hasUnread);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onResult(false);
                });
    }

    /**
     * Adds "(NEW)" to button text, and optionally shows a one-time toast prompt.
     * We store the last unread state in SharedPreferences to avoid spamming prompts.
     */
    private void updateHomeButtonBadge(Button button, String baseText, boolean hasUnread,
                                       String statePrefKey, String promptIfNew) {
        if (button == null) return;

        button.setText(hasUnread ? (baseText + " (NEW)") : baseText);

        boolean prev = getSharedPreferences("GRPC", MODE_PRIVATE).getBoolean(statePrefKey, false);
        if (!prev && hasUnread && promptIfNew != null) {
            Toast.makeText(this, promptIfNew, Toast.LENGTH_LONG).show();
        }
        getSharedPreferences("GRPC", MODE_PRIVATE).edit().putBoolean(statePrefKey, hasUnread).apply();
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
        HelpButton = findViewById(R.id.HelpButton);
        NotificationsButton = findViewById(R.id.NotificationsButton);
        LocationFinderButton = findViewById(R.id.LocationFinderButton);
        SearchButton = findViewById(R.id.SearchButton);
        ChatButton = findViewById(R.id.ChatButton);
    }

    /**
     * Set up click listeners for all navigation buttons
     * Each button opens a specific activity with user information passed along
     */
    private void setupButtonClickListeners() {
        // Instant messaging - conversation list (all users + group)
        if (InstantMessage != null) {
            InstantMessage.setOnClickListener(view -> {
                openActivity(MessagingConversationsActivity.class);
            });
        }

        // Notification history - see what notifications were received
        if (NotificationsButton != null) {
            NotificationsButton.setOnClickListener(view -> openActivity(NotificationsActivity.class));
        }

        // Global search: user 001 only
        if (SearchButton != null) {
            String userId = StaffDirectory.getUserId(userName);
            boolean isSearchUser = StaffDirectory.isJamesUserId(userId);
            SearchButton.setVisibility(isSearchUser ? View.VISIBLE : View.GONE);
            if (isSearchUser) {
                SearchButton.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                    intent.putExtra("USER_NAME", userName);
                    startActivity(intent);
                });
            }
        }

        // Company website access
        if (WebsiteButton != null) {
            WebsiteButton.setOnClickListener(view -> openWebsite());
        }

        // Report generation and management
        if (reportButton != null) {
            reportButton.setOnClickListener(view -> {
                openActivity(ReportSelectionActivity.class);
            });
        }

        // View and manage stored reports
        if (reportViewButton != null) {
            reportViewButton.setOnClickListener(view -> {
                openActivity(PDFSelectionActivity.class);
            });
        }

        // Contract management and customer tracking
        if (contractsButton != null) {
            contractsButton.setOnClickListener(view -> {
                openActivity(ContractsActivity.class);
            });
        }

        // Quotation generation and management
        if (quotesButton != null) {
            quotesButton.setOnClickListener(view -> openActivity(QuotesActivity.class));
        }

        // Commission / Leads: 001, 002, 003, 004
        if (CommisionButton != null) {
            String userId = StaffDirectory.getUserId(userName);
            boolean canAccessCommission = StaffDirectory.canAccessCommissionLeadsUserId(userId);
            CommisionButton.setVisibility(canAccessCommission ? View.VISIBLE : View.GONE);
            if (canAccessCommission) {
                CommisionButton.setOnClickListener(view -> openActivity(LeadsSelectionActivity.class));
            }
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
                openActivity(WorkViewActivity.class);
            });
        }

        // Location Finder: user 001 only
        if (LocationFinderButton != null) {
            String userId = StaffDirectory.getUserId(userName);
            boolean isLocationUser = StaffDirectory.isJamesUserId(userId);
            LocationFinderButton.setVisibility(isLocationUser ? View.VISIBLE : View.GONE);
            if (isLocationUser) {
                LocationFinderButton.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, LocationFinderActivity.class);
                    intent.putExtra("USER_NAME", userName);
                    startActivity(intent);
                });
            }
        }

        // Help / README screen
        if (HelpButton != null) {
            HelpButton.setOnClickListener(view -> openActivity(HelpReadmeActivity.class));
        }

        // AI Chat Assistant
        if (ChatButton != null) {
            ChatButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Secure logout - clears activity stack, clears saved user, and returns to login
        if (logoutButton != null) {
            logoutButton.setOnClickListener(view -> {
                // Clean up push topic subscriptions to prevent cross-user notifications on shared devices
                try {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic("all");
                    if (userName != null && !userName.trim().isEmpty()) {
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(userName.toLowerCase());
                    }
                } catch (Exception ignored) {}

                // Stop per-user background workers
                LocationSharing.cancelScheduled(MainActivity.this, userName);

                getSharedPreferences("GRPC", MODE_PRIVATE).edit().remove("USER_NAME").apply();
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void maybeRequestLocationPermission() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                    || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    /**
     * For offline user, show only Create Report, View Reports, and Logout; hide all other buttons.
     */
    private void applyOfflineUserVisibility() {
        if (userName == null || !userName.equals("Offline User")) return;
        if (NotificationsButton != null) NotificationsButton.setVisibility(View.GONE);
        if (SearchButton != null) SearchButton.setVisibility(View.GONE);
        if (WorkViewButton != null) WorkViewButton.setVisibility(View.GONE);
        if (LocationFinderButton != null) LocationFinderButton.setVisibility(View.GONE);
        if (contractsButton != null) contractsButton.setVisibility(View.GONE);
        if (quotesButton != null) quotesButton.setVisibility(View.GONE);
        if (ServiceAgreementButton != null) ServiceAgreementButton.setVisibility(View.GONE);
        if (CommisionButton != null) CommisionButton.setVisibility(View.GONE);
        if (JobButton != null) JobButton.setVisibility(View.GONE);
        if (EnviromentButton != null) EnviromentButton.setVisibility(View.GONE);
        if (InstantMessage != null) InstantMessage.setVisibility(View.GONE);
        if (WebsiteButton != null) WebsiteButton.setVisibility(View.GONE);
        if (HelpButton != null) HelpButton.setVisibility(View.GONE);
        if (ChatButton != null) ChatButton.setVisibility(View.GONE);
        // Keep visible: reportButton (Create Report), reportViewButton (View Reports), logoutButton (Logout)
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
     * Converts email format to display name (e.g. user@domain -> User)
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
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            // No-op: location sharing is best-effort; workers will check permission.
        }
    }
}
