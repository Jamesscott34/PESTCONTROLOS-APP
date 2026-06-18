package com.grpc.grpc.main;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;

import com.grpc.grpc.core.ActiveUserContext;
import com.grpc.grpc.core.DailyContractPdfHelper;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.OfflineTrialHelper;
import com.grpc.grpc.core.RememberMeManager;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.email.ui.EmailComposeActivity;
import com.grpc.grpc.login.LoginActivity;

import com.grpc.grpc.contracts.ui.AddContractActivity;
import com.grpc.grpc.contracts.ui.ContractsActivity;
import com.grpc.grpc.contracts.ui.ViewContractActivity;
import com.grpc.grpc.jobs.ui.AddJobFromCalendarActivity;
import com.grpc.grpc.jobs.ui.AddJobsActivity;
import com.grpc.grpc.admin.ui.AdminDashboardActivity;
import com.grpc.grpc.admin.ui.EmployeeManagementActivity;
import com.grpc.grpc.billing.ui.InvoiceListActivity;
import com.grpc.grpc.jobs.ui.JobsActivity;
import com.grpc.grpc.leads.ui.LeadsSelectionActivity;
import com.grpc.grpc.location.ui.LocationFinderActivity;
import com.grpc.grpc.messaging.ui.MessagingConversationsActivity;
import com.grpc.grpc.messaging.ui.NotificationsActivity;
import com.grpc.grpc.reports.ui.PDFSelectionActivity;
import com.grpc.grpc.reports.ui.ReportActivity;
import com.grpc.grpc.reports.ui.ReportSelectionActivity;
import com.grpc.grpc.routes.ui.RouterActivity;
import com.grpc.grpc.search.ui.SearchActivity;
import com.grpc.grpc.serviceagreements.ui.ServiceAgreementActivity;
import com.grpc.grpc.workview.ui.WorkViewActivity;
import com.grpc.grpc.era.ui.EnvironmentSelectionActivity;
import com.grpc.grpc.files.ui.HelpReadmeActivity;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.messaging.NotificationUtils;
import com.grpc.grpc.quotations.ui.QuotesActivity;
import com.grpc.grpc.workview.data.WorkViewLocalEventStore;
import com.grpc.grpc.workview.data.WorkViewWidgetHelper;
import com.grpc.grpc.workview.data.WorkViewPopupReminderScheduler;
import com.grpc.grpc.converter.ui.ConverterActivity;

/**
 * ============================================================================
 * GRPest Control Application - Main Activity
 * ============================================================================
 * 
 * PROJECT OVERVIEW:
 * This is a comprehensive pest control management application for [Company 1] 
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
 * - Admin users: Full access to all features
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
 * Company: [Company 1]
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

public class MainActivity extends AppCompatActivity {

    // UI Components - Navigation buttons for all major features
    private Button reportButton, reportViewButton, contractsButton, quotesButton, logoutButton,
                   CommisionButton, ServiceAgreementButton, JobButton, EnviromentButton,
                   InstantMessage, MapsButton, InvoicesButton, WorkViewButton, HelpButton,
                   NotificationsButton, LocationFinderButton, SearchButton, EmailButton,
                   DashboardButton, EmployeeButton, RouteButton, ConverterButton;
    
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
        boolean offlineMode = BuildConfig.IS_OFFLINE || getIntent().getBooleanExtra("OFFLINE_MODE", false);

        // Offline flavor: no login; use fixed "Offline" user and skip Firestore/session.
        if (offlineMode) {
            userName = (intentUserName != null && !intentUserName.isEmpty()) ? intentUserName : "Offline";
            ActiveUserContext.setActiveUser(this, userName, "");
            Log.d("MainActivity", "Offline mode – username: " + userName);
        } else {
            // Internal identity MUST be ContractKey (derived from authenticated user), never a display name.
            if (intentUserName != null && !intentUserName.isEmpty()) {
                userName = intentUserName;
                Log.d("MainActivity", "Username from intent: " + userName);
            } else {
                userName = ActiveUserContext.getActiveUserName(this, "User");
                Log.d("MainActivity", "Using saved/default username: " + userName);
            }
        }

        // Central identity + RBAC (skip for offline – no Firestore).
        if (!offlineMode) {
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            String ck = SessionManager.getContractKey(MainActivity.this);
            if (ck != null && !ck.trim().isEmpty()) {
                userName = StaffDirectory.capitalizeContractKey(ck.trim());
            } else {
                // No ContractKey in Firestore: use StaffID so contracts/workview collections resolve (e.g. "001 Contracts").
                String sid = SessionManager.getStaffId(MainActivity.this);
                if (sid != null && !sid.trim().isEmpty()) {
                    userName = sid.trim();
                }
            }
            ActiveUserContext.setActiveUser(MainActivity.this, userName, userEmail != null ? userEmail : "");

            // Demo: record per-user first launch (for DemoRelease + DemoDaysNumber); then apply visibility if profile closed
            final SessionManager.Session loadedSession = session;
            runOnUiThread(() -> {
                if (loadedSession != null && loadedSession.staffId != null && !loadedSession.staffId.isEmpty()) {
                    DemoFirebaseExpiryHelper.recordDemoLaunchIfNeeded(MainActivity.this, loadedSession.staffId);
                }
                applyDemoExpiredVisibility();
            });

            // Load staff names/keys from Firestore (users) so dropdowns/WhatsApp use DB data
            StaffDirectory.refreshFromFirestore(MainActivity.this);

            // Location sharing keyed by authUid (session.staffId)
            String uid = session != null && session.staffId != null ? session.staffId : userName;
            LocationSharing.ensureScheduled(MainActivity.this, uid);
            WorkViewPopupReminderScheduler.scheduleUpcomingForUser(MainActivity.this, userName);
            DailyContractPdfHelper.scheduleDailyPdfIfNeeded(MainActivity.this, userName);
            applyInvoicesButtonVisibility(loadedSession);
            runOnUiThread(this::checkAndShowDailySummaryCard);
        }));
        }

        // Request location permission once so technicians can publish their last location.
        // If denied, the location system will simply have no data.
        maybeRequestLocationPermission();

        // Log online/offline mode
        if (offlineMode) Log.d("MainActivity", "Running in OFFLINE MODE - Create Report / View Reports only");
        else Log.d("MainActivity", "Running in ONLINE MODE - Full functionality");

        // Set up welcome message with user's name
        welcomeTextView = findViewById(R.id.welcomeTextView);

        // Logo: @drawable/logo in XML; flavor-specific res/drawable/logo.* overrides GRPC main (demo-style).
        if (welcomeTextView != null) {
            welcomeTextView.setText(getString(R.string.welcome_back_user, userName));
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                if (welcomeTextView == null) return;
                String name = SessionManager.getName(this);
                if (name != null && !name.trim().isEmpty()) {
                    welcomeTextView.setText(getString(R.string.welcome_back_user, name.trim()));
                    Log.d("MainActivity", "Welcome message set from profile Name: " + name);
                }
            }));
            Log.d("MainActivity", "Welcome message set for user: " + userName);
        } else {
            Log.e("MainActivity", "welcomeTextView is NULL! Check XML ID.");
        }
        
        // Log activity creation for debugging
        Log.d("MainActivity", "MainActivity created with user: " + userName);

        // Initialize all navigation buttons
        initializeButtons();

        // Temporary UI changes / moved features:
        // - General Quotes, Service Agreements, and ERA now live under Create Report section.
        if (quotesButton != null) quotesButton.setVisibility(View.GONE);
        if (ServiceAgreementButton != null) ServiceAgreementButton.setVisibility(View.GONE);
        if (EnviromentButton != null) EnviromentButton.setVisibility(View.GONE);
        
        // Set up click listeners for all buttons
        setupButtonClickListeners();

        // Offline user: show only Create Report, View Reports, and Logout
        applyOfflineUserVisibility();

        // Refresh Search (and other permission-based buttons) after session loads from Firestore so canSearch = true is not blocked
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> applySearchVisibilityFromSession(session)));

        // Offline trial: record first launch so we can redirect after OFFLINE_TRIAL_DAYS (demo + offline)
        OfflineTrialHelper.recordLaunchIfNeeded(this);

        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();

        // Offline flavor: no Firebase – no Firestore or Messaging access
        if (!BuildConfig.IS_OFFLINE) {
            db = FirebaseFirestore.getInstance();
            new Thread(() -> {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("all");
                if (userName != null && !userName.trim().isEmpty()) {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(userName.toLowerCase());
                }
            }).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyDemoExpiredVisibility();
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> applySearchVisibilityFromSession(session)));
        // Unread indicators: run messaging badge only when user can see Messaging (canMessage).
        if (SessionManager.canMessage(this)) {
            checkHomeUnreadIndicators();
        } else {
            // Still show notifications badge for everyone.
            if (!BuildConfig.IS_OFFLINE && !DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)
                    && userName != null && !userName.trim().isEmpty() && db != null) {
                checkUnreadNotifications();
            }
        }
    }

    /**
     * In-app indicator: highlight where to go for new Notifications / Messaging.
     * - Notifications: checks Firestore notification history (read=false)
     * - Messaging: checks latest chat messages vs per-conversation last-seen timestamp
     */
    private void checkHomeUnreadIndicators() {
        if (BuildConfig.IS_OFFLINE || (userName != null && (userName.equals("Offline") || userName.equals("Offline User")))) return;
        if (DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) return;
        if (userName == null || userName.trim().isEmpty() || db == null) return;
        checkUnreadNotifications();
        checkUnreadMessages();
    }

    private void checkUnreadNotifications() {
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            String staffId = session != null ? session.staffId : "";
            final String userKey = (staffId != null && !staffId.trim().isEmpty())
                    ? staffId.trim()
                    : NotificationUtils.resolveNotificationRecipientKey(userName);
            if (userKey == null || userKey.trim().isEmpty()) return;

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
        }));
    }

    private void checkUnreadMessages() {
        // IMPORTANT: Don't reset unread state on every resume, otherwise the prompt repeats.
        // We only clear the unread badge after we've confirmed there are NO unread conversations.
        if (InstantMessage != null) {
            InstantMessage.setText("Messaging");
        }

        java.util.ArrayList<String> convIds = new java.util.ArrayList<>();
        try {
            for (StaffDirectory.StaffProfile p : StaffDirectory.getCachedStaffProfiles()) {
                if (p == null) continue;
                String other = (p.contractKey != null && !p.contractKey.trim().isEmpty())
                        ? p.contractKey.trim()
                        : StaffDirectory.getUserNameKey(p.id);
                if (other == null || other.trim().isEmpty() || other.equalsIgnoreCase(userName)) continue;
                convIds.add(MessagingConversationsActivity.getConversationId(userName, other));
            }
        } catch (Exception ignored) {}
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
                                // Swipe right - admins: View Contract; techs: Work View only
                                if (SessionManager.isAdmin(MainActivity.this)) {
                                    Log.d("MainActivity", "Swipe RIGHT detected - opening ViewContractActivity with user: " + userName);
                                    Intent intent = new Intent(MainActivity.this, ViewContractActivity.class);
                                    intent.putExtra("USER_NAME", userName);
                                    startActivity(intent);
                                } else {
                                    Log.d("MainActivity", "Swipe RIGHT detected - opening WorkViewActivity (work view) with user: " + userName);
                                    Intent intent = new Intent(MainActivity.this, WorkViewActivity.class);
                                    intent.putExtra("USER_NAME", userName);
                                    startActivity(intent);
                                }
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
        MapsButton = findViewById(R.id.MapsButton);
        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        contractsButton = findViewById(R.id.ContractsButton);
        quotesButton = findViewById(R.id.GeneralQuotesButton);
        ServiceAgreementButton = findViewById(R.id.ServiceAgreementButton);
        CommisionButton = findViewById(R.id.CommisionButton);
        JobButton = findViewById(R.id.JobsButton);
        EnviromentButton = findViewById(R.id.EnviromentButton);
        logoutButton = findViewById(R.id.LogoutButton);
        InvoicesButton = findViewById(R.id.InvoicesButton);
        WorkViewButton = findViewById(R.id.WorkViewButton);
        HelpButton = findViewById(R.id.HelpButton);
        NotificationsButton = findViewById(R.id.NotificationsButton);
        EmailButton = findViewById(R.id.EmailButton);
        LocationFinderButton = findViewById(R.id.LocationFinderButton);
        SearchButton = findViewById(R.id.SearchButton);
        DashboardButton = findViewById(R.id.DashboardButton);
        EmployeeButton = findViewById(R.id.EmployeeButton);
        RouteButton = findViewById(R.id.RouteButton);
        ConverterButton = findViewById(R.id.ConverterButton);
    }

    /**
     * Set up click listeners for all navigation buttons
     * Each button opens a specific activity with user information passed along
     */
    private void setupButtonClickListeners() {
        // Instant messaging - conversation list (visible when canMessage=true)
        if (InstantMessage != null) {
            boolean showMessaging = SessionManager.canMessage(this);
            InstantMessage.setVisibility(showMessaging ? View.VISIBLE : View.GONE);
            if (showMessaging) {
                InstantMessage.setOnClickListener(view -> openActivity(MessagingConversationsActivity.class));
            }
        }

        // Maps - placeholder; visible when canMap=true, click shows "Feature soon to be implemented"
        if (MapsButton != null) {
            boolean showMaps = SessionManager.canMap(this);
            MapsButton.setVisibility(showMaps ? View.VISIBLE : View.GONE);
            if (showMaps) {
                MapsButton.setOnClickListener(v -> openActivity(com.grpc.grpc.maps.ui.MapsPlaceholderActivity.class));
            }
        }
        if (RouteButton != null) {
            boolean showRouter = SessionManager.canRoute(this);
            RouteButton.setVisibility(showRouter ? View.VISIBLE : View.GONE);
            if (showRouter) {
                RouteButton.setOnClickListener(v -> openActivity(RouterActivity.class));
            }
        }

        // Notification history - see what notifications were received
        if (NotificationsButton != null) {
            NotificationsButton.setOnClickListener(view -> openActivity(NotificationsActivity.class));
        }

        if (EmailButton != null) {
            EmailButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, EmailComposeActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Global search: visibility applied in applySearchVisibilityFromSession after session loads (so canSearch from users/{authUid} is correct)
        if (SearchButton != null) {
            SearchButton.setVisibility(View.GONE);
            SearchButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                intent.putExtra("USER_NAME", userName);
                startActivity(intent);
            });
        }

        // Admin Dashboard: admin and super_admin
        if (DashboardButton != null) {
            boolean canViewDashboard = SessionManager.isAdmin(this);
            DashboardButton.setVisibility(canViewDashboard ? View.VISIBLE : View.GONE);
            if (canViewDashboard) {
                DashboardButton.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, AdminDashboardActivity.class);
                    intent.putExtra("USER_NAME", userName);
                    startActivity(intent);
                });
            }
        }

        // Employee Management: super_admin only
        if (EmployeeButton != null) {
            boolean canManageEmployees = SessionManager.isSuperAdmin(this);
            EmployeeButton.setVisibility(canManageEmployees ? View.VISIBLE : View.GONE);
            if (canManageEmployees) {
                EmployeeButton.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, EmployeeManagementActivity.class);
                    intent.putExtra("USER_NAME", userName);
                    startActivity(intent);
                });
            }
        }

        // Billing / invoices (admin + super_admin; hidden offline / demo-restricted)
        if (InvoicesButton != null) {
            InvoicesButton.setVisibility(View.GONE);
            InvoicesButton.setOnClickListener(v -> {
                SessionManager.Session session = SessionManager.getCached(MainActivity.this);
                Intent intent = new Intent(MainActivity.this, InvoiceListActivity.class);
                intent.putExtra(InvoiceListActivity.EXTRA_CAN_CREATE, session != null && session.canInvoice);
                startActivity(intent);
            });
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

        // Commission / Leads: role-based access
        if (CommisionButton != null) {
            boolean canAccessCommission = SessionManager.canAccessCommissionLeads(this);
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

        // Location Finder: super admin only
        if (LocationFinderButton != null) {
            boolean canLocation = SessionManager.canUseLocationFinder(this);
            LocationFinderButton.setVisibility(canLocation ? View.VISIBLE : View.GONE);
            if (canLocation) {
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

                // Stop per-user background workers (use authUid same as ensureScheduled)
                try {
                    com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                    if (u != null) LocationSharing.cancelScheduled(MainActivity.this, u.getUid());
                } catch (Exception ignored) {}

                // Clear app session caches (prevents cross-user RBAC leaks on shared devices)
                try { SessionManager.clear(MainActivity.this); } catch (Exception ignored) {}
                try { ActiveUserContext.clear(MainActivity.this); } catch (Exception ignored) {}
                try { StaffDirectory.clearCache(); } catch (Exception ignored) {}
                try { WorkViewLocalEventStore.clearAll(MainActivity.this); } catch (Exception ignored) {}
                try { WorkViewWidgetHelper.clearWidgetCache(MainActivity.this); } catch (Exception ignored) {}
                try { LocationSharing.clearLocalCache(MainActivity.this); } catch (Exception ignored) {}
                try { RememberMeManager.disableForCurrentUser(MainActivity.this); } catch (Exception ignored) {}
                try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) {}

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
     * Apply Search button visibility from the loaded session (Firestore).
     * Ensures users with canSearch = true see the Search button even when session loads after onCreate.
     * Not restricted to super_admin only.
     */
    private void applySearchVisibilityFromSession(SessionManager.Session session) {
        if (BuildConfig.IS_OFFLINE || userName == null || userName.equals("Offline") || userName.equals("Offline User")) return;
        if (BuildConfig.IS_DEMO && DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) return;
        boolean canSearch = session != null && session.canSearch;
        if (SearchButton != null) {
            SearchButton.setVisibility(canSearch ? View.VISIBLE : View.GONE);
            if (canSearch) {
                SearchButton.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                    intent.putExtra("USER_NAME", userName);
                    startActivity(intent);
                });
            }
        }
        if (InstantMessage != null) {
            boolean canMessage = session != null && session.canMessage;
            InstantMessage.setVisibility(canMessage ? View.VISIBLE : View.GONE);
            if (canMessage) {
                InstantMessage.setOnClickListener(view -> openActivity(MessagingConversationsActivity.class));
            }
        }
        if (MapsButton != null) {
            boolean canMap = session != null && session.canMap;
            MapsButton.setVisibility(canMap ? View.VISIBLE : View.GONE);
            if (canMap) {
                MapsButton.setOnClickListener(v -> openActivity(com.grpc.grpc.maps.ui.MapsPlaceholderActivity.class));
            }
        }
        if (RouteButton != null) {
            boolean canRoute = session != null && session.canRoute;
            RouteButton.setVisibility(canRoute ? View.VISIBLE : View.GONE);
            if (canRoute) {
                RouteButton.setOnClickListener(v -> openActivity(RouterActivity.class));
            }
        }
        if (ConverterButton != null) {
            boolean canConvert = session != null && session.canConvert;
            ConverterButton.setVisibility(canConvert ? View.VISIBLE : View.GONE);
            if (canConvert) {
                ConverterButton.setOnClickListener(v -> openActivity(ConverterActivity.class));
            }
        }
        applyInvoicesButtonVisibility(session);
    }

    /**
     * Main screen Billing / Invoices: admin/super_admin, or users explicitly granted canInvoice.
     */
    private void applyInvoicesButtonVisibility(SessionManager.Session session) {
        if (InvoicesButton == null) return;
        if (BuildConfig.IS_OFFLINE || userName == null || userName.equals("Offline") || userName.equals("Offline User")) {
            InvoicesButton.setVisibility(View.GONE);
            return;
        }
        if (BuildConfig.IS_DEMO && DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
            InvoicesButton.setVisibility(View.GONE);
            return;
        }
        boolean show = session != null && (session.isAdmin || session.canInvoice);
        InvoicesButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * For offline flavor / offline user: show only Create Report, View Reports, and Exit; hide all other buttons.
     */
    private void applyOfflineUserVisibility() {
        boolean isOffline = BuildConfig.IS_OFFLINE
                || (userName != null && (userName.equals("Offline") || userName.equals("Offline User")));
        if (!isOffline) return;

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
        if (MapsButton != null) MapsButton.setVisibility(View.GONE);
        if (RouteButton != null) RouteButton.setVisibility(View.GONE);
        if (ConverterButton != null) ConverterButton.setVisibility(View.GONE);
        if (DashboardButton != null) DashboardButton.setVisibility(View.GONE);
        if (EmployeeButton != null) EmployeeButton.setVisibility(View.GONE);
        if (InvoicesButton != null) InvoicesButton.setVisibility(View.GONE);
        if (HelpButton != null) HelpButton.setVisibility(View.GONE);
        // Keep visible: reportButton (Create Report), reportViewButton (View Reports), logoutButton (Exit)
        if (logoutButton != null) {
            logoutButton.setText(getString(R.string.exit_button_offline));
            logoutButton.setOnClickListener(v -> finish());
        }
    }

    /**
     * Demo only: after DEMO_FIREBASE_EXPIRY_DAYS, hide Firebase access for admin/tech; super_admin keeps full access.
     * Does nothing for grpc or offline.
     */
    private void applyDemoExpiredVisibility() {
        if (!BuildConfig.IS_DEMO || !DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) return;

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
        if (MapsButton != null) MapsButton.setVisibility(View.GONE);
        if (RouteButton != null) RouteButton.setVisibility(View.GONE);
        if (ConverterButton != null) ConverterButton.setVisibility(View.GONE);
        if (HelpButton != null) HelpButton.setVisibility(View.GONE);
        if (DashboardButton != null) DashboardButton.setVisibility(View.GONE);
        if (EmployeeButton != null) EmployeeButton.setVisibility(View.GONE);
        if (InvoicesButton != null) InvoicesButton.setVisibility(View.GONE);
        if (welcomeTextView != null) welcomeTextView.setText(getString(R.string.demo_expired_message));
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

    private void checkAndShowDailySummaryCard() {
        if (BuildConfig.IS_OFFLINE) return;

        String uid = SessionManager.getStaffId(this);
        if (uid == null || uid.isEmpty()) return;

        String todayKey = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String prefKey = "DAILY_SUMMARY_SHOWN_DATE_" + uid;
        android.content.SharedPreferences prefs = getSharedPreferences("GRPC", MODE_PRIVATE);

        if (todayKey.equals(prefs.getString(prefKey, ""))) return;

        prefs.edit().putString(prefKey, todayKey).apply();

        String contractKey = SessionManager.getContractKey(this);
        if (contractKey == null || contractKey.trim().isEmpty()) return;

        String collectionName = StaffDirectory.capitalizeContractKey(contractKey.trim()) + " Contracts";
        String contractKeyLower = contractKey.trim().toLowerCase(java.util.Locale.getDefault());

        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();

        final int[] counts = {0, 0, 0};
        final int[] pending = {2};

        db.collection(collectionName).get()
                .addOnSuccessListener(snap -> {
                    java.text.SimpleDateFormat fmt =
                            new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
                    java.util.Date today = new java.util.Date();
                    java.util.Calendar in7 = java.util.Calendar.getInstance();
                    in7.add(java.util.Calendar.DAY_OF_YEAR, 7);

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        String nextVisit = doc.getString("nextVisit");
                        if (nextVisit == null || nextVisit.isEmpty() || "N/A".equals(nextVisit)) continue;
                        try {
                            java.util.Date d = fmt.parse(nextVisit);
                            if (d == null) continue;
                            if (d.before(today)) counts[0]++;
                            else if (!d.after(in7.getTime())) counts[1]++;
                        } catch (Exception ignored) {
                        }
                    }
                    pending[0]--;
                    if (pending[0] <= 0) runOnUiThread(() -> showDailySummaryCard(counts));
                })
                .addOnFailureListener(e -> {
                    pending[0]--;
                    if (pending[0] <= 0) runOnUiThread(() -> showDailySummaryCard(counts));
                });

        db.collection("JobWork")
                .whereEqualTo("assignedTech", contractKeyLower)
                .whereEqualTo("completed", false)
                .get()
                .addOnSuccessListener(snap -> {
                    counts[2] = snap.size();
                    pending[0]--;
                    if (pending[0] <= 0) runOnUiThread(() -> showDailySummaryCard(counts));
                })
                .addOnFailureListener(e -> {
                    pending[0]--;
                    if (pending[0] <= 0) runOnUiThread(() -> showDailySummaryCard(counts));
                });
    }

    private android.widget.LinearLayout findMainDashboardLayout() {
        android.view.ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return null;
        android.view.View root = content.getChildAt(0);
        if (!(root instanceof android.widget.ScrollView)) return null;
        android.widget.ScrollView scrollView = (android.widget.ScrollView) root;
        if (scrollView.getChildCount() == 0) return null;
        android.view.View child = scrollView.getChildAt(0);
        return child instanceof android.widget.LinearLayout
                ? (android.widget.LinearLayout) child
                : null;
    }

    private void showDailySummaryCard(int[] counts) {
        if (counts[0] == 0 && counts[1] == 0 && counts[2] == 0) return;

        android.widget.LinearLayout root = findMainDashboardLayout();
        if (root == null) return;

        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setPadding(32, 24, 32, 24);
        card.setBackgroundResource(R.drawable.surface_frame);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(16, 16, 16, 8);
        card.setLayoutParams(lp);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("☀ Good morning — here's your day");
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        if (counts[0] > 0) {
            addSummaryLine(card,
                    "⚠ " + counts[0] + " overdue contract" + (counts[0] == 1 ? "" : "s"));
        }
        if (counts[1] > 0) {
            addSummaryLine(card,
                    "📅 " + counts[1] + " contract" + (counts[1] == 1 ? "" : "s") + " due within 7 days");
        }
        if (counts[2] > 0) {
            addSummaryLine(card,
                    "🔧 " + counts[2] + " open job" + (counts[2] == 1 ? "" : "s") + " assigned to you");
        }

        android.widget.Button dismiss = new android.widget.Button(this);
        dismiss.setText("Dismiss");
        dismiss.setOnClickListener(v -> root.removeView(card));
        card.addView(dismiss);

        root.addView(card, 0);
    }

    private void addSummaryLine(android.widget.LinearLayout parent, String text) {
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setPadding(0, 8, 0, 0);
        parent.addView(tv);
    }
}
