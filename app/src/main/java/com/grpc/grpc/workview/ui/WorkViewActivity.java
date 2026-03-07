/**
 * ============================================================================
 * GRPest Control Application - Work View & Calendar Activity
 * ============================================================================
 * 
 * BUSINESS OVERVIEW:
 * This activity provides a comprehensive work scheduling and calendar management
 * system for GRPest Control technicians. It allows users to view their work
 * schedule in daily or weekly formats, add new appointments, manage contracts
 * and jobs, and receive notifications for upcoming events.
 * 
 * CORE FUNCTIONALITIES:
 * 
 * 1. CALENDAR VIEW
 *    - Daily and weekly calendar views
 *    - Time slots from 08:30 to 17:30 (1-hour slots)
 *    - Multiple events per time slot support
 *    - Visual event indicators and status
 * 
 * 2. EVENT MANAGEMENT
 *    - Add new contracts or jobs to calendar
 *    - Select existing contracts from user's list
 *    - Job creation with name, address, and issue details
 *    - Follow-up scheduling for existing jobs
 * 
 * 3. CONTRACT INTEGRATION
 *    - Link calendar events to existing contracts
 *    - Update contract last visit dates
 *    - Mark contracts as completed
 *    - Generate reports from calendar events
 * 
 * 4. NOTIFICATION SYSTEM
 *    - Push notifications for upcoming events
 *    - Real-time alerts when user is logged in
 *    - Customizable notification timing
 * 
 * 5. REPORT GENERATION
 *    - Create reports from calendar events
 *    - Use generic report template
 *    - Save reports to standard folder structure
 *    - Link reports to contracts and jobs
 * 
 * TECHNICAL FEATURES:
 * - Custom calendar implementation
 * - Firebase integration for data persistence
 * - Local notification system
 * - Report generation integration
 * - Contract management integration
 * 
 * USER ROLES & PERMISSIONS:
 * - Technicians: Full calendar management and event creation
 * - Administrators: Oversight of all scheduled work
 * - All users: View their assigned work schedule
 * 
 * Author: GRPC
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

package com.grpc.grpc.workview.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.reports.ui.AddFollowUpActivity;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.generalreports.ui.GeneralReportActivity;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.messaging.NotificationUtils;
import com.grpc.grpc.contracts.ui.ContractSelectionAdapter;
import com.grpc.grpc.contracts.ui.ViewContractActivity;
import com.grpc.grpc.core.*;
import com.grpc.grpc.main.MainActivity;
import com.grpc.grpc.jobs.ui.AddJobFromCalendarActivity;
import com.grpc.grpc.reports.ui.ReportActivity;
import com.grpc.grpc.workview.model.WorkEvent;
import com.grpc.grpc.workview.data.WorkViewLocalEventStore;
import com.grpc.grpc.workview.data.WorkViewWidgetHelper;
import com.grpc.grpc.workview.data.WorkViewPopupReminderScheduler;
import com.grpc.grpc.workview.worker.WorkViewPopupReminderWorker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import android.app.DatePickerDialog;
import android.widget.EditText;
import android.widget.ListView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;

import java.util.concurrent.TimeUnit;

public class WorkViewActivity extends AppCompatActivity {

    // UI Components
    private CalendarView calendarView;
    private RecyclerView eventsRecyclerView;
    private Button dailyViewButton;
    private Button calendarViewButton;
    private Button addEventButton;
    private TextView viewTypeText;
    private TextView welcomeTextView;
    private LinearLayout eventsContainer;

    // Data
    private FirebaseFirestore db;
    private String userName;
    private Date selectedDate;
    private boolean isDailyView = true;
    private WorkEventAdapter eventsAdapter;
    private List<WorkEvent> eventsList = new ArrayList<>();
    private GestureDetectorCompat gestureDetector;

    // Swipe gesture constants
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    // Time slots for daily view (1-hour slots, 08:30 -> 16:30 (ends 17:30))
    private static final String[] SLOT_START_TIMES = {
            "08:30", "09:30", "10:30", "11:30", "12:30", "13:30", "14:30", "15:30", "16:30"
    };

    private static final String[] SLOT_DISPLAY_RANGES = {
            "08:30 - 09:30",
            "09:30 - 10:30",
            "10:30 - 11:30",
            "11:30 - 12:30",
            "12:30 - 13:30",
            "13:30 - 14:30",
            "14:30 - 15:30",
            "15:30 - 16:30",
            "16:30 - 17:30"
    };

    // Dynamic staff list is fetched from Firestore when needed (no hardcoded staff IDs/names).
    private static final String[] TECHNICIAN_IDS = new String[0];

    /** Prevents double-tap on Mark as Done (idempotent save). */
    private final Set<String> markDoneInProgress = new HashSet<>();

    private boolean canManageOtherWorkViews() {
        SessionManager.ensureLoaded(this, null);
        return SessionManager.seesAllJobs(this);
    }

    /**
     * Main entry point of the work view activity
     * Initializes the calendar interface, sets up event management,
     * and configures notification system
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_view);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        // ============================================================================
        // FIREBASE INITIALIZATION
        // ============================================================================
        
        db = FirebaseFirestore.getInstance();

        // ============================================================================
        // USER AUTHENTICATION & VALIDATION (single source of truth; persist so swipe never loses context)
        // ============================================================================
        
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            userName = ActiveUserContext.getActiveUserName(this);
        }
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "User not set. Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Normalize ContractKey to capital so "james" -> "James" and contract collection "James Contracts" loads
        if (!userName.matches("\\d{3}") && !userName.contains(" ")) {
            userName = StaffDirectory.capitalizeContractKey(userName);
        }
        ActiveUserContext.setActiveUserName(this, userName);
        Log.d("WorkViewActivity", "WorkViewActivity created with user: " + userName);

        // ============================================================================
        // DATE INITIALIZATION
        // ============================================================================
        
        // Initialize selectedDate to today's date
        selectedDate = new Date();

        // ============================================================================
        // UI COMPONENT INITIALIZATION
        // ============================================================================
        
        initializeUIComponents();
        setupCalendarView();
        setupEventRecyclerView();
        setupButtonListeners();
        setupWelcomeMessage();
        
        // Set daily view as default and highlight the button
        isDailyView = true;
        dailyViewButton.setEnabled(false);
        calendarViewButton.setEnabled(true);
        dailyViewButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(dailyViewButton, com.google.android.material.R.attr.colorPrimary)));
        calendarViewButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(android.R.color.darker_gray)));
        viewTypeText.setText("Daily View");
        
        // Load events for current date
        loadEventsForDate(selectedDate);
        
        // Set up notification channel
        createNotificationChannel();
        
        // Schedule notifications for existing events (run in background)
        new android.os.Handler().postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                try {
                    scheduleNotificationsForExistingEvents();
                } catch (Exception e) {
                    Log.e("WorkViewActivity", "Error scheduling notifications: " + e.getMessage());
                }
            }
        }, 1000); // Delay by 1 second to let activity fully load
        
        // Schedule daily missed events popup at 18:00 (run in background)
        new android.os.Handler().postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                try {
                    scheduleDailyMissedEventsPopup();
                } catch (Exception e) {
                    Log.e("WorkViewActivity", "Error scheduling missed events popup: " + e.getMessage());
                }
            }
        }, 2000); // Delay by 2 seconds
        
        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up any pending operations
        Log.d("WorkViewActivity", "WorkViewActivity destroyed");
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
                    
                    Log.d("WorkViewActivity", "Swipe detected - diffX: " + diffX + ", diffY: " + diffY + ", velocityX: " + velocityX);
                    
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Swipe right - open GeneralReportActivity
                                Log.d("WorkViewActivity", "Swipe RIGHT detected - opening GeneralReportActivity with user: " + userName);
                                Intent intent = new Intent(WorkViewActivity.this, GeneralReportActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            } else {
                                // Swipe left - admins: View Contract; techs: Main (no view contract)
                                if (SessionManager.isAdmin(WorkViewActivity.this)) {
                                    Log.d("WorkViewActivity", "Swipe LEFT detected - opening ViewContractActivity with user: " + userName);
                                    Intent intent = new Intent(WorkViewActivity.this, ViewContractActivity.class);
                                    intent.putExtra("USER_NAME", userName);
                                    startActivity(intent);
                                } else {
                                    Log.d("WorkViewActivity", "Swipe LEFT detected - opening MainActivity with user: " + userName);
                                    Intent intent = new Intent(WorkViewActivity.this, MainActivity.class);
                                    intent.putExtra("USER_NAME", userName);
                                    startActivity(intent);
                                }
                                finish(); // Destroy this activity
                                return true;
                            }
                        } else {
                            Log.d("WorkViewActivity", "Swipe threshold not met - diffX: " + Math.abs(diffX) + ", velocityX: " + Math.abs(velocityX));
                        }
                    }
                } catch (Exception e) {
                    Log.e("WorkViewActivity", "Error in swipe detection: " + e.getMessage());
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
     * Initialize all UI components
     */
    private void initializeUIComponents() {
        calendarView = findViewById(R.id.calendarView);
        dailyViewButton = findViewById(R.id.dailyViewButton);
        calendarViewButton = findViewById(R.id.calendarViewButton);
        addEventButton = findViewById(R.id.addEventButton);
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView);
        viewTypeText = findViewById(R.id.viewTypeText);
        eventsContainer = findViewById(R.id.eventsContainer);
    }

    /**
     * Set up welcome message with user's name
     */
    private void setupWelcomeMessage() {
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Work View - " + userName);
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                if (welcomeTextView == null) return;
                String name = SessionManager.getName(this);
                if (name != null && !name.trim().isEmpty()) {
                    welcomeTextView.setText("Work View - " + name.trim());
                }
            }));
        }
    }

    /**
     * Get the user-specific WorkView collection name for Firebase.
     * Contract/WorkView convention: use ContractKey (not full name). Collection name is
     * lowercase(ContractKey) + "_workview" (e.g. "user_a_workview"). Contracts use
     * getContractsCollectionNameFromAnyKey (e.g. "User_A Contracts" with capital C).
     */
    private String getUserWorkViewCollection() {
        return getCollectionForUser(userName);
    }

    /** WorkView collection = lowercase(ContractKey) + "_workview". */
    private String getCollectionForUser(String user) {
        return user != null ? user.trim().toLowerCase(Locale.getDefault()) + "_workview" : "_workview";
    }

    private String getCollectionForEvent(WorkEvent event) {
        if (event == null || event.getUserName() == null || event.getUserName().trim().isEmpty()) {
            return getUserWorkViewCollection();
        }
        return getCollectionForUser(event.getUserName());
    }

    /**
     * Set up calendar view with date selection
     */
    private void setupCalendarView() {
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            // Create a Calendar instance and set the selected date
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            selectedDate = calendar.getTime();
            
            // Switch back to daily view with the selected date
            switchToDailyView();
        });
    }

    /**
     * Set up RecyclerView for displaying events
     */
    private void setupEventRecyclerView() {
        eventsAdapter = new WorkEventAdapter(eventsList, this::onEventClicked);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventsRecyclerView.setAdapter(eventsAdapter);
    }

    /**
     * Set up button click listeners
     */
    private void setupButtonListeners() {
        // Daily/Weekly view toggle buttons
        dailyViewButton.setOnClickListener(v -> switchToDailyView());
        calendarViewButton.setOnClickListener(v -> switchToCalendarView());
        
        // Add event button
        addEventButton.setOnClickListener(v -> showAddEventDialog());
        
        // Back button
        Button backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    /**
     * Switch to daily view mode
     */
    private void switchToDailyView() {
        isDailyView = true;
        
        // Update view type text with selected date
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());
        viewTypeText.setText("Daily View - " + sdf.format(selectedDate));
        
        dailyViewButton.setEnabled(false);
        calendarViewButton.setEnabled(true);
        // Update button appearance
        dailyViewButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                MaterialColors.getColor(dailyViewButton, com.google.android.material.R.attr.colorPrimary)));
        calendarViewButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(android.R.color.darker_gray)));
        
        // Show time slots container, hide calendar and events recycler
        findViewById(R.id.calendarView).setVisibility(android.view.View.GONE);
        findViewById(R.id.timeSlotsContainer).setVisibility(android.view.View.VISIBLE);
        findViewById(R.id.eventsRecyclerView).setVisibility(android.view.View.GONE);
        
        loadEventsForDate(selectedDate);
    }

    /**
     * Switch to calendar view mode
     */
    private void switchToCalendarView() {
        // Show date picker dialog directly
        showDatePickerDialog();
    }

    /**
     * Show date picker dialog as fallback
     */
    private void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(selectedDate);
        
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                Calendar selectedCalendar = Calendar.getInstance();
                selectedCalendar.set(year, month, dayOfMonth);
                selectedDate = selectedCalendar.getTime();
                
                // Switch back to daily view with selected date
                switchToDailyView();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        
        datePickerDialog.setTitle("Select Date");
        datePickerDialog.show();
    }

    /**
     * Load events for a specific date
     */
    private void loadEventsForDate(Date date) {
        selectedDate = date;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(date);

        // Admin/oversight users see a combined calendar for all technicians
        if (canManageOtherWorkViews()) {
            loadEventsForDateAllUsers(dateString);
            return;
        }

        String userCollection = getUserWorkViewCollection();
        
        db.collection(userCollection)
          .whereEqualTo("date", dateString)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              List<WorkEvent> events = new ArrayList<>();
              
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  WorkEvent event = document.toObject(WorkEvent.class);
                  if (event != null) {
                      event.setId(document.getId());
                      events.add(event);
                  }
              }
              
              // Sort events by time
              events.sort((e1, e2) -> e1.getTime().compareTo(e2.getTime()));

              // Persist today's jobs for the home screen widget (still shows after logout)
              String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
              if (today.equals(dateString)) {
                  WorkViewWidgetHelper.saveTodayJobsCache(getApplicationContext(), dateString, events);
                  WorkViewWidgetProvider.refreshAllWidgets(getApplicationContext());
              }

              if (isDailyView) {
                  // Create time slot views for daily view
                  createTimeSlotViews();
                  updateTimeSlotsWithEvents(events);
              } else {
                  // Update RecyclerView for weekly view
                  eventsAdapter.updateEvents(events);
              }
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error loading events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Load events for a specific date across all technicians (combined view)
     */
    private void loadEventsForDateAllUsers(String dateString) {
        StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
            List<StaffDirectory.OwnerOption> opts = options != null ? options : new ArrayList<>();
            if (opts.isEmpty()) {
                // Fallback: show only the current user's calendar (avoids stale/hardcoded identity).
                opts = new ArrayList<>();
                String me = SessionManager.getContractKey(this);
                if (me == null || me.trim().isEmpty()) me = userName;
                if (me == null) me = "";
                me = me.trim();
                if (!me.isEmpty()) {
                    opts.add(new StaffDirectory.OwnerOption("", me, me));
                }
            }

            List<WorkEvent> allEvents = new ArrayList<>();
            final int expected = opts.size();
            final int[] completed = {0};

            for (StaffDirectory.OwnerOption o : opts) {
                String tech = o != null ? o.ownerKey : "";
                if (tech == null) tech = "";
                final String techFinal = tech;
                String collection = getCollectionForUser(techFinal);
                db.collection(collection)
                        .whereEqualTo("date", dateString)
                        .get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                WorkEvent event = document.toObject(WorkEvent.class);
                                if (event != null) {
                                    event.setId(document.getId());
                                    if (event.getUserName() == null || event.getUserName().trim().isEmpty()) {
                                        event.setUserName(techFinal);
                                    }
                                    allEvents.add(event);
                                }
                            }
                            completed[0]++;
                            if (completed[0] >= expected) {
                                allEvents.sort((e1, e2) -> e1.getTime().compareTo(e2.getTime()));
                                if (isDailyView) {
                                    createTimeSlotViews();
                                    updateTimeSlotsWithEvents(allEvents);
                                } else {
                                    eventsAdapter.updateEvents(allEvents);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e("WorkViewActivity", "Error loading events for " + techFinal + ": " + e.getMessage());
                            completed[0]++;
                            if (completed[0] >= expected) {
                                if (isDailyView) {
                                    createTimeSlotViews();
                                    updateTimeSlotsWithEvents(allEvents);
                                } else {
                                    eventsAdapter.updateEvents(allEvents);
                                }
                            }
                        });
            }
        }));
    }

    /**
     * Load events for the week containing the specified date
     */
    private void loadEventsForWeek(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String startDate = sdf.format(calendar.getTime());
        
        calendar.add(Calendar.DAY_OF_WEEK, 6);
        String endDate = sdf.format(calendar.getTime());
        
        db.collection(getUserWorkViewCollection())
          .whereEqualTo("userName", userName)
          .whereGreaterThanOrEqualTo("date", startDate)
          .whereLessThanOrEqualTo("date", endDate)
          .get()
          .addOnSuccessListener(queryDocumentSnapshots -> {
              eventsList.clear();
              for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                  WorkEvent event = document.toObject(WorkEvent.class);
                  if (event != null) {
                      event.setId(document.getId());
                      eventsList.add(event);
                  }
              }
              eventsAdapter.notifyDataSetChanged();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error loading events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Show dialog for adding new events
     */
    private void showAddEventDialog() {
        String[] options = {"Add Contract", "Add Job", "Add Follow-up"};
        
        new AlertDialog.Builder(this)
            .setTitle("Add New Event")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showAddContractDialog();
                        break;
                    case 1:
                        showAddJobDialog();
                        break;
                    case 2:
                        showAddFollowUpDialog();
                        break;
                }
            })
            .show();
    }

    /**
     * Show dialog for adding contract events
     */
    private void showAddContractDialog() {
        if (canManageOtherWorkViews()) {
            // Admin/oversight users can access all technicians' calendars
            showAdminContractSelectionDialog();
        } else {
            // Regular users can only access their own contracts
            showUserContractSelectionDialog(userName);
        }
    }

    /**
     * Show contract selection dialog for regular users.
     * Loads from shared contracts collection by assignedTech (contractKey). Scalable for any number of techs.
     */
    private void showUserContractSelectionDialog(String user) {
        // When admin chose a user (e.g. Dean), filter by that user's contracts; otherwise use session contractKey (tech).
        String contractKey = (user != null && !user.trim().isEmpty()) ? user.trim() : (SessionManager.getContractKey(this) != null ? SessionManager.getContractKey(this).trim() : "");
        if (contractKey.isEmpty()) {
            Toast.makeText(this, "Could not resolve technician. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String filterKey = contractKey.trim().toLowerCase(java.util.Locale.getDefault());

        // Debug: log contract selection query for WorkView (no time slot).
        try {
            com.google.firebase.auth.FirebaseUser authUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String authUid = authUser != null ? authUser.getUid() : "null";
            SessionManager.Session session = SessionManager.getCached(this);
            String role = session != null ? session.roleNorm : "unknown";
            String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
            Log.d("WorkViewActivity", "Contract selection query (calendar) where assignedTech=" + filterKey
                    + " (requestedUser=" + user
                    + ", authUid=" + authUid
                    + ", role=" + role
                    + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "") + ")");
        } catch (Exception e) {
            Log.w("WorkViewActivity", "Failed to log contract selection (calendar) context: " + e.getMessage());
        }

        db.collection(FirestorePaths.CONTRACTS)
                .whereEqualTo("assignedTech", filterKey)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> contractNames = new ArrayList<>();
                        List<String> contractIds = new ArrayList<>();
                        List<String> contractAddresses = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> contract = document.getData();
                            String contractName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
                            String contractAddress = contract.get("address") != null ? contract.get("address").toString() : "N/A";
                            if (!contractName.equals("N/A")) {
                                contractNames.add(contractName + "\n📍 " + contractAddress);
                                contractIds.add(document.getId());
                                contractAddresses.add(contractAddress);
                            }
                        }
                        if (contractNames.isEmpty()) {
                            Toast.makeText(this, "No contracts found for you. Please add contracts first.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showContractSelectionDialogWithSearch(contractNames, contractIds, contractAddresses, FirestorePaths.CONTRACTS);
                    } else {
                        Toast.makeText(this, "Error loading contracts: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show contract selection dialog with search functionality
     */
    private void showContractSelectionDialogWithSearch(List<String> contractNames, List<String> contractIds, 
                                                    List<String> contractAddresses, String collectionName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Contract from " + collectionName);

        // Inflate custom layout
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_contract_selection, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        ListView listView = dialogView.findViewById(R.id.contractsListView);

        // Create custom adapter with better filtering
        ContractSelectionAdapter adapter = new ContractSelectionAdapter(this, contractNames);
        listView.setAdapter(adapter);

        // Enhanced live search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Live search - filter as user types
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        // Create and show the dialog - must create before setting click listener so we can dismiss it
        AlertDialog dialog = builder.create();
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                String selectedContract = adapter.getItem(position);
                if (selectedContract != null) {
                    // Find the original index in the contractIds list
                    int originalIndex = contractNames.indexOf(selectedContract);
                    if (originalIndex >= 0 && originalIndex < contractIds.size()) {
                        String selectedContractId = contractIds.get(originalIndex);
                        String contractName = adapter.getContractName(position);
                        dialog.dismiss();
                        showTimeSelectionDialog("contract", selectedContractId, contractName);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error selecting contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
        
        // Focus on search bar for immediate typing
        searchBar.requestFocus();
    }

    /**
     * Show contract selection dialog for oversight users (admin/super_admin).
     * Uses assignable users (contractKey) so it scales with any number of techs.
     */
    private void showAdminContractSelectionDialog() {
        UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
            List<UserRepository.AssignableUser> opts = users != null ? users : new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<String> contractKeys = new ArrayList<>();
            for (UserRepository.AssignableUser u : opts) {
                if (u == null || u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                labels.add(u.contractKey.trim());
                contractKeys.add(u.contractKey.trim());
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "No technicians with contracts found.", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] displayLabels = labels.toArray(new String[0]);
            final String[] ownerContractKeys = contractKeys.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Select User's Contracts")
                    .setItems(displayLabels, (dialog, which) -> showUserContractSelectionDialog(ownerContractKeys[which]))
                    .show();
        }));
    }

    /**
     * Show time selection dialog with user selection for admins
     */
    private void showTimeSelectionDialog(String eventType, String eventId, String eventName) {
        if (canManageOtherWorkViews()) {
            // Oversight users can select which user's work view to add the event to
            showAdminUserSelectionDialog(eventType, eventId, eventName);
        } else {
            // Regular users add to their own work view
            showTimeSlotsDialog(eventType, eventId, eventName, userName);
        }
    }

    /**
     * Show user selection dialog for oversight users (contractKey labels; scales with any number of techs).
     */
    private void showAdminUserSelectionDialog(String eventType, String eventId, String eventName) {
        UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
            List<UserRepository.AssignableUser> opts = users != null ? users : new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (UserRepository.AssignableUser u : opts) {
                if (u == null || u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                labels.add(u.contractKey.trim());
                keys.add(u.contractKey.trim());
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "No technicians found.", Toast.LENGTH_SHORT).show();
                return;
            }
            final String[] ownerKeys = keys.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Select User's Work View")
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> showTimeSlotsDialog(eventType, eventId, eventName, ownerKeys[which]))
                    .show();
        }));
    }

    /**
     * Show time slots dialog (with optional \"Custom time...\" entry).
     */
    private void showTimeSlotsDialog(String eventType, String eventId, String eventName, String targetUser) {
        CharSequence[] options = new CharSequence[SLOT_DISPLAY_RANGES.length + 1];
        System.arraycopy(SLOT_DISPLAY_RANGES, 0, options, 0, SLOT_DISPLAY_RANGES.length);
        options[SLOT_DISPLAY_RANGES.length] = "Custom time...";

        new AlertDialog.Builder(this)
            .setTitle("Select Time for " + targetUser + "'s Work View")
            .setItems(options, (dialog, which) -> {
                if (which >= 0 && which < SLOT_DISPLAY_RANGES.length) {
                    String selectedTime = SLOT_START_TIMES[which];
                    createWorkEvent(eventType, eventId, eventName, selectedTime, targetUser);
                } else {
                    showCustomTimeDialog(eventType, eventId, eventName, targetUser);
                }
            })
            .show();
    }

    /**
     * Show dialog for adding job events.
     * First asks New or Old. New = create new job. Old = pick from existing View Jobs list.
     */
    private void showAddJobDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Add Job")
            .setItems(new String[]{"New", "Old"}, (dialog, which) -> {
                if (which == 0) {
                    showAddNewJobDialog();
                } else {
                    showAddExistingJobDialog(null);
                }
            })
            .show();
    }

    /** Show user selection then open AddJobFromCalendarActivity (New job). */
    private void showAddNewJobDialog() {
        if (canManageOtherWorkViews()) {
            StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
                List<StaffDirectory.OwnerOption> opts = options != null ? options : new ArrayList<>();
                String[] displayLabels = new String[opts.size()];
                String[] ownerKeys = new String[opts.size()];
                for (int i = 0; i < opts.size(); i++) {
                    StaffDirectory.OwnerOption o = opts.get(i);
                    displayLabels[i] = o != null ? o.display : "";
                    ownerKeys[i] = o != null ? o.ownerKey : "";
                }
                new AlertDialog.Builder(this)
                        .setTitle("Assign Job To")
                        .setItems(displayLabels, (d, which) -> openAddJobActivity(ownerKeys[which]))
                        .show();
            }));
        } else {
            openAddJobActivity(userName);
        }
    }

    /** Load jobs from shared jobwork collection and show selection (role-gated). */
    private void showAddExistingJobDialog(String timeSlot) {
        String targetUser = userName;
        if (canManageOtherWorkViews()) {
            StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
                List<StaffDirectory.OwnerOption> opts = options != null ? options : new ArrayList<>();
                String[] displayLabels = new String[opts.size()];
                String[] ownerKeys = new String[opts.size()];
                for (int i = 0; i < opts.size(); i++) {
                    StaffDirectory.OwnerOption o = opts.get(i);
                    displayLabels[i] = o != null ? o.display : "";
                    ownerKeys[i] = o != null ? o.ownerKey : "";
                }
                new AlertDialog.Builder(this)
                        .setTitle("Whose jobs to add?")
                        .setItems(displayLabels, (d, which) -> loadJobsAndShowSelection(ownerKeys[which], timeSlot))
                        .show();
            }));
        } else {
            loadJobsAndShowSelection(targetUser, timeSlot);
        }
    }

    private void loadJobsAndShowSelection(String forUser, String timeSlot) {
        com.google.firebase.firestore.Query query = db.collection(FirestorePaths.JOBWORK);
        query = query.whereEqualTo("AssignedTech", forUser);
        query.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Toast.makeText(this, "Error loading jobs: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            List<Map<String, Object>> jobs = new ArrayList<>();
            List<String> displayNames = new ArrayList<>();
            List<String> jobIds = new ArrayList<>();
            for (QueryDocumentSnapshot doc : task.getResult()) {
                Map<String, Object> job = doc.getData();
                job.put("documentId", doc.getId());
                jobs.add(job);
                String customer = job.get("CustomerName") != null ? job.get("CustomerName").toString() : "N/A";
                String address = job.get("Address") != null ? job.get("Address").toString() : "";
                displayNames.add(customer + "\n📍 " + address);
                jobIds.add(doc.getId());
            }
            if (jobs.isEmpty()) {
                Toast.makeText(this, "No jobs found for " + forUser + ". Add jobs via View Jobs first.", Toast.LENGTH_SHORT).show();
                return;
            }
            showJobSelectionDialogWithSearch(displayNames, jobIds, jobs, forUser, timeSlot);
        });
    }

    private void showJobSelectionDialogWithSearch(List<String> displayNames, List<String> jobIds,
            List<Map<String, Object>> jobs, String targetUser, String timeSlot) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Job from View Jobs");

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_contract_selection, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        ListView listView = dialogView.findViewById(R.id.contractsListView);
        searchBar.setHint("Type to search jobs...");

        ContractSelectionAdapter adapter = new ContractSelectionAdapter(this, displayNames);
        listView.setAdapter(adapter);

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        
        AlertDialog dialog = builder.create();
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = adapter.getItem(position);
            if (selected != null) {
                int idx = displayNames.indexOf(selected);
                if (idx >= 0 && idx < jobIds.size()) {
                    String jobId = jobIds.get(idx);
                    Map<String, Object> job = jobs.get(idx);
                    String jobName = job.get("CustomerName") != null ? job.get("CustomerName").toString() : "Job";
                    dialog.dismiss();
                    if (timeSlot != null) {
                        createWorkEvent("job", jobId, jobName, timeSlot, targetUser);
                    } else {
                        showTimeSlotsForJob(jobId, jobName, targetUser);
                    }
                }
            }
        });
        
        dialog.show();
        searchBar.requestFocus();
    }

    private void showTimeSlotsForJob(String jobId, String jobName, String targetUser) {
        CharSequence[] options = new CharSequence[SLOT_DISPLAY_RANGES.length + 1];
        System.arraycopy(SLOT_DISPLAY_RANGES, 0, options, 0, SLOT_DISPLAY_RANGES.length);
        options[SLOT_DISPLAY_RANGES.length] = "Custom time...";

        new AlertDialog.Builder(this)
            .setTitle("Select Time for " + targetUser + "'s Work View")
            .setItems(options, (dialog, which) -> {
                if (which >= 0 && which < SLOT_DISPLAY_RANGES.length) {
                    String time = SLOT_START_TIMES[which];
                    createWorkEvent("job", jobId, jobName, time, targetUser);
                } else {
                    showCustomTimeDialog("job", jobId, jobName, targetUser);
                }
            })
            .show();
    }

    /**
     * Open AddJobFromCalendarActivity with target user (AssignedTech) and creator for push notifications
     */
    private void openAddJobActivity(String targetUser) {
        Intent intent = new Intent(this, AddJobFromCalendarActivity.class);
        intent.putExtra("USER_NAME", targetUser);
        intent.putExtra("CREATED_BY", userName);
        intent.putExtra("SELECTED_DATE", selectedDate.getTime());
        startActivity(intent);
    }

    /**
     * Show dialog for adding follow-up events
     */
    private void showAddFollowUpDialog() {
        // Show follow-up creation dialog
        Intent intent = new Intent(this, AddFollowUpActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("SELECTED_DATE", selectedDate.getTime());
        startActivity(intent);
    }

    /**
     * Create a new work event with address information
     */
    private void createWorkEvent(String eventType, String eventId, String eventName, String time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(selectedDate);
        
        Map<String, Object> event = new HashMap<>();
        event.put("userName", userName);
        event.put("eventType", eventType);
        event.put("eventId", eventId);
        event.put("eventName", eventName);
        event.put("date", dateString);
        event.put("time", time);
        event.put("status", "scheduled");
        event.put("createdAt", new Date());
        
        // Add address information based on event type
        if ("contract".equals(eventType)) {
            // For contracts, get address from contract data
            db.collection("contracts").document(eventId)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      String address = documentSnapshot.getString("address");
                      if (address != null) {
                          event.put("address", address);
                      }
                  }
                  // Save the event with address
                  saveEventToFirebase(event);
              })
              .addOnFailureListener(e -> {
                  // Save event without address if contract lookup fails
                  saveEventToFirebase(event);
              });
        } else {
            // For jobs and follow-ups, address will be added when job is created
            saveEventToFirebase(event);
        }
    }

    /**
     * Create a new work event with address information and target user (1-hour slot).
     */
    private void createWorkEvent(String eventType, String eventId, String eventName, String time, String targetUser) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(selectedDate);
        
        Map<String, Object> event = new HashMap<>();
        event.put("userName", targetUser); // Set the userName to the target user
        event.put("eventType", eventType);
        event.put("eventId", eventId);
        event.put("eventName", eventName);
        event.put("date", dateString);
        event.put("time", time);
        event.put("status", "scheduled");
        event.put("createdAt", new Date());
        
        // Add address information based on event type
        if ("contract".equals(eventType)) {
            // Contracts live in shared collection (scalable for any number of techs)
            db.collection(FirestorePaths.CONTRACTS).document(eventId)
              .get()
              .addOnSuccessListener(documentSnapshot -> {
                  if (documentSnapshot.exists()) {
                      String address = documentSnapshot.getString("address");
                      if (address != null) {
                          event.put("address", address);
                      }
                  }
                  // Save the event with address
                  saveEventToFirebase(event);
              })
              .addOnFailureListener(e -> {
                  // Save event without address if contract lookup fails
                  saveEventToFirebase(event);
              });
        } else {
            // For jobs and follow-ups, address will be added when job is created
            saveEventToFirebase(event);
        }
    }

    /**
     * Create a new work event with explicit start/end time for target user.
     * Adds endTime field but keeps all other behaviour identical.
     */
    private void createWorkEventWithEndTime(String eventType, String eventId, String eventName,
                                            String startTime, String endTime, String targetUser) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(selectedDate);

        Map<String, Object> event = new HashMap<>();
        event.put("userName", targetUser);
        event.put("eventType", eventType);
        event.put("eventId", eventId);
        event.put("eventName", eventName);
        event.put("date", dateString);
        event.put("time", startTime);
        event.put("endTime", endTime != null ? endTime : "");
        event.put("status", "scheduled");
        event.put("createdAt", new Date());

        if ("contract".equals(eventType)) {
            db.collection(FirestorePaths.CONTRACTS).document(eventId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String address = documentSnapshot.getString("address");
                            if (address != null) {
                                event.put("address", address);
                            }
                        }
                        saveEventToFirebase(event);
                    })
                    .addOnFailureListener(e -> saveEventToFirebase(event));
        } else {
            saveEventToFirebase(event);
        }
    }

    /**
     * Save event to Firebase with user-specific collection
     */
    private void saveEventToFirebase(Map<String, Object> event) {
        // Add createdBy field for notification system
        event.put("createdBy", userName);

        String targetUser = (String) event.get("userName");
        String collectionName = (targetUser != null && !targetUser.trim().isEmpty())
                ? getCollectionForUser(targetUser)
                : getUserWorkViewCollection();

        db.collection(collectionName)
          .add(event)
          .addOnSuccessListener(documentReference -> {
              Toast.makeText(this, "Event added successfully!", Toast.LENGTH_SHORT).show();
              
              // Create WorkEvent object for notification scheduling
              WorkEvent workEvent = new WorkEvent();
              workEvent.setId(documentReference.getId());
              workEvent.setUserName((String) event.get("userName"));
              workEvent.setEventType((String) event.get("eventType"));
              workEvent.setEventId((String) event.get("eventId"));
              workEvent.setEventName((String) event.get("eventName"));
              workEvent.setDate((String) event.get("date"));
              workEvent.setTime((String) event.get("time"));
              workEvent.setEndTime((String) event.get("endTime"));
              workEvent.setStatus((String) event.get("status"));
              workEvent.setAddress((String) event.get("address"));
              workEvent.setCreatedBy((String) event.get("createdBy"));
              
              // Schedule notification for the event
              scheduleEventNotification(workEvent);

              // In-app notification when oversight users add to someone else's work view
              writeInAppWorkViewUpdateIfNeeded(documentReference.getId(), event);
              
              loadEventsForDate(selectedDate);
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error adding event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * In-app notification history (NOT system push).
     * When oversight users add an event to another user's work view, notify that user.
     */
    private void writeInAppWorkViewUpdateIfNeeded(String workViewDocId, Map<String, Object> event) {
        try {
            if (!canManageOtherWorkViews()) return;
            if (event == null) return;

            String targetUser = asString(event.get("userName"));
            if (targetUser == null || targetUser.trim().isEmpty()) return;
            if (targetUser.equalsIgnoreCase(userName)) return;

            String eventName = asString(event.get("eventName"));
            String eventType = asString(event.get("eventType"));
            String eventDate = asString(event.get("date"));
            String eventTime = asString(event.get("time"));
            String address = asString(event.get("address"));

            Map<String, Object> data = new HashMap<>();
            data.put("eventId", workViewDocId);
            data.put("targetUser", targetUser);
            data.put("eventName", eventName);
            data.put("eventType", eventType);
            data.put("eventDate", eventDate);
            data.put("eventTime", eventTime);
            data.put("eventAddress", address);
            data.put("createdBy", userName);
            data.put("type", "workview_update");

            String title = "📅 Work View Updated";
            String body = (eventName != null ? eventName : "Event")
                    + " (" + (eventType != null ? eventType : "event") + ") added for "
                    + (eventDate != null ? eventDate : "")
                    + " at " + (eventTime != null ? eventTime : "");

            NotificationUtils.writeInAppNotification(
                    targetUser,
                    "workview_update_" + workViewDocId,
                    title,
                    body,
                    "workview_update",
                    data
            );
        } catch (Exception ignored) {
        }
    }

    private String asString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    /**
     * Handle event click for actions like mark done, add follow-up, create report
     */
    private void onEventClicked(WorkEvent event) {
        if ("job".equals(event.getEventType())) {
            // Job actions: Follow-up, Create Report, Edit Time, Finished
            String[] jobOptions = {"Follow-up", "Create Report", "Edit Time", "Finished"};
            
            new AlertDialog.Builder(this)
                .setTitle("Job Actions")
                .setItems(jobOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            addFollowUpToJob(event);
                            break;
                        case 1:
                            createReportFromJob(event);
                            break;
                        case 2:
                            showEditTimeDialog(event);
                            break;
                        case 3:
                            finishJob(event);
                            break;
                    }
                })
                .show();
        } else if ("contract".equals(event.getEventType())) {
            // Contract actions: Follow-up, Complete, Create Report, Edit Time
            String[] contractOptions = {"Follow-up", "Complete", "Create Report", "Edit Time"};
            
            new AlertDialog.Builder(this)
                .setTitle("Contract Actions")
                .setItems(contractOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            addFollowUpToContract(event);
                            break;
                        case 1:
                            completeContract(event);
                            break;
                        case 2:
                            createReportFromContract(event);
                            break;
                        case 3:
                            showEditTimeDialog(event);
                            break;
                    }
                })
                .show();
        } else {
            // Follow-up actions: Create Report, Edit Time, Mark Done
            String[] followUpOptions = {"Create Report", "Edit Time", "Mark Done"};
            
            new AlertDialog.Builder(this)
                .setTitle("Follow-up Actions")
                .setItems(followUpOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            createReportFromEvent(event);
                            break;
                        case 1:
                            showEditTimeDialog(event);
                            break;
                        case 2:
                            markEventDone(event);
                            break;
                    }
                })
                .show();
        }
    }

    /**
     * Mark an event as completed - shows confirmation dialog first
     */
    private void markEventDone(WorkEvent event) {
        showMarkDoneConfirmationDialog(event, null, null);
    }

    /**
     * Allow editing of an event's time (and optional end time) for jobs, contracts, and follow-ups.
     * Keeps underlying scheduling based on the start time only.
     */
    private void showEditTimeDialog(WorkEvent event) {
        if (event == null || event.getId() == null || event.getId().trim().isEmpty()) return;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText startInput = new EditText(this);
        startInput.setHint("Start time (e.g. 08:30 or 0830)");
        startInput.setText(event.getTime() != null ? event.getTime() : "");
        layout.addView(startInput);

        EditText endInput = new EditText(this);
        endInput.setHint("End time (optional, e.g. 15:00 or 1500)");
        // Default end time: use existing endTime if set, otherwise +60 minutes from start.
        String currentEnd = event.getEndTime();
        if (currentEnd == null || currentEnd.trim().isEmpty()) {
            int startMin = parseMinutes(event.getTime());
            if (startMin >= 0) {
                int endMin = startMin + 60;
                int endH = (endMin / 60) % 24;
                int endM = endMin % 60;
                currentEnd = String.format(Locale.getDefault(), "%02d:%02d", endH, endM);
            }
        }
        if (currentEnd != null && !currentEnd.trim().isEmpty()) {
            endInput.setText(currentEnd);
        }
        layout.addView(endInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit Time")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String rawStart = startInput.getText().toString().trim();
                    String rawEnd = endInput.getText().toString().trim();

                    String normStart = normalizeTimeInput(rawStart);
                    if (normStart == null) {
                        Toast.makeText(this, "Invalid start time. Use 08:30 or 0830.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    final String finalNormStart = normStart;
                    final String finalNormEnd;
                    if (!rawEnd.isEmpty()) {
                        String normEnd = normalizeTimeInput(rawEnd);
                        if (normEnd == null) {
                            Toast.makeText(this, "Invalid end time. Use 15:00 or 1500.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        int startMin = parseMinutes(normStart);
                        int endMin = parseMinutes(normEnd);
                        if (startMin < 0 || endMin < 0 || endMin <= startMin) {
                            Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        finalNormEnd = normEnd;
                    } else {
                        finalNormEnd = "";
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("time", finalNormStart);
                    updates.put("endTime", finalNormEnd);

                    db.collection(getCollectionForEvent(event)).document(event.getId())
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                event.setTime(finalNormStart);
                                event.setEndTime(finalNormEnd);
                                scheduleEventNotification(event);
                                Toast.makeText(this, "Time updated.", Toast.LENGTH_SHORT).show();
                                loadEventsForDate(selectedDate);
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Error updating time: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Show confirmation dialog before marking event as done.
     * onStartSave is called when user taps Yes (e.g. disable checkbox to prevent double tap).
     */
    private void showMarkDoneConfirmationDialog(WorkEvent event, Runnable onCancel, Runnable onStartSave) {
        new AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage("Are you sure a routine or visit was done?")
            .setPositiveButton("Yes", (dialog, which) -> {
                if (onStartSave != null) onStartSave.run();
                markEventComplete(event);
            })
            .setNegativeButton("No", (d, which) -> {
                if (onCancel != null) onCancel.run();
            })
            .show();
    }

    /**
     * Calculate next visit date from lastVisit (dd/MM/yy) and visits count.
     * Same logic as ViewContractActivity: 4/6/8/12 visits -> add 12/8/6/4 weeks.
     */
    private String calculateNextVisitFromLastVisit(String lastVisit, String visitsStr) {
        if (lastVisit == null || lastVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(lastVisit)) return "N/A";
        int visits;
        try {
            visits = Integer.parseInt(visitsStr != null ? visitsStr.trim() : "0");
        } catch (NumberFormatException e) {
            return "N/A";
        }
        if (visits == 0) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(sdf.parse(lastVisit));
        } catch (Exception e) {
            return "N/A";
        }
        switch (visits) {
            case 8: cal.add(Calendar.WEEK_OF_YEAR, 6); break;
            case 12: cal.add(Calendar.WEEK_OF_YEAR, 4); break;
            case 6: cal.add(Calendar.WEEK_OF_YEAR, 8); break;
            case 4: cal.add(Calendar.WEEK_OF_YEAR, 12); break;
            default: return "N/A";
        }
        return sdf.format(cal.getTime());
    }

    /**
     * Mark an event as complete: update work view, update contract by ID if contract,
     * then open Create Report with pre-filled details. Prevents double-tap; on failure do not open report.
     */
    private void markEventComplete(WorkEvent event) {
        if (event == null || event.getId() == null) return;
        if (markDoneInProgress.contains(event.getId())) return;
        markDoneInProgress.add(event.getId());

        cancelInAppReminder(event);
        String collectionName = getCollectionForEvent(event);
        boolean isContract = "contract".equals(event.getEventType());

        if (isContract) {
            // Debug: log mark-as-done context for contract events.
            try {
                com.google.firebase.auth.FirebaseUser authUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                String authUid = authUser != null ? authUser.getUid() : "null";
                SessionManager.Session session = SessionManager.getCached(this);
                String role = session != null ? session.roleNorm : "unknown";
                String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
                Log.d("WorkViewActivity", "MarkEventComplete for contract: eventId=" + event.getId()
                        + ", contractId=" + event.getEventId()
                        + ", workCollection=" + collectionName
                        + ", contractCollection=" + FirestorePaths.CONTRACTS
                        + ", authUid=" + authUid
                        + ", role=" + role
                        + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "") + ")");
            } catch (Exception e) {
                Log.w("WorkViewActivity", "Failed to log markEventComplete contract context: " + e.getMessage());
            }

            // Update contract in shared contracts collection by contract ID; use batch with work view update. Scalable for any number of techs.
            String contractCollection = FirestorePaths.CONTRACTS;
            db.collection(contractCollection).document(event.getEventId()).get()
                .addOnSuccessListener(contractSnap -> {
                    if (!contractSnap.exists()) {
                        markDoneInProgress.remove(event.getId());
                        Toast.makeText(this, "Contract not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String visits = contractSnap.get("visits") != null ? contractSnap.get("visits").toString() : "0";
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                    sdf.setTimeZone(TimeZone.getTimeZone("Europe/Dublin"));
                    String today = sdf.format(new Date());
                    String nextVisit = calculateNextVisitFromLastVisit(today, visits);

                    WriteBatch batch = db.batch();
                    Map<String, Object> workUpdates = new HashMap<>();
                    workUpdates.put("status", "completed");
                    workUpdates.put("completedAt", new Date());
                    batch.update(db.collection(collectionName).document(event.getId()), workUpdates);

                    Map<String, Object> contractUpdates = new HashMap<>();
                    contractUpdates.put("lastVisit", today);
                    contractUpdates.put("nextVisit", nextVisit);
                    batch.update(db.collection(contractCollection).document(event.getEventId()), contractUpdates);

                    batch.commit()
                        .addOnSuccessListener(aVoid -> {
                            markDoneInProgress.remove(event.getId());
                            Toast.makeText(this, "Event marked as completed! Will be deleted in 24 hours.", Toast.LENGTH_SHORT).show();
                            loadEventsForDate(selectedDate);
                            scheduleEventDeletion(event, 24 * 60 * 60 * 1000);
                            openCreateReportFromEvent(event, today);
                        })
                        .addOnFailureListener(e -> {
                            markDoneInProgress.remove(event.getId());
                            Toast.makeText(this, "Error updating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                })
                .addOnFailureListener(e -> {
                    markDoneInProgress.remove(event.getId());
                    Toast.makeText(this, "Error loading contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            return;
        }

        // Job or follow-up: update work view only, then open Create Report on success.
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", new Date());
        db.collection(collectionName).document(event.getId())
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                markDoneInProgress.remove(event.getId());
                Toast.makeText(this, "Event marked as completed! Will be deleted in 24 hours.", Toast.LENGTH_SHORT).show();
                loadEventsForDate(selectedDate);
                scheduleEventDeletion(event, 24 * 60 * 60 * 1000);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("Europe/Dublin"));
                openCreateReportFromEvent(event, sdf.format(new Date()));
            })
            .addOnFailureListener(e -> {
                markDoneInProgress.remove(event.getId());
                Toast.makeText(this, "Error updating event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    /**
     * Open Create Report (ReportActivity) with pre-filled customer name, address, date, visit type.
     */
    private void openCreateReportFromEvent(WorkEvent event, String reportDateDdMmYy) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("EVENT_NAME", event.getEventName());
        intent.putExtra("EVENT_TYPE", event.getEventType() != null ? event.getEventType() : "");
        intent.putExtra("EVENT_ADDRESS", event.getAddress() != null ? event.getAddress() : "");
        intent.putExtra("EVENT_ISSUE", event.getIssue() != null ? event.getIssue() : "");
        intent.putExtra("COMPANY_NAME", event.getEventName() != null ? event.getEventName() : "N/A");
        intent.putExtra("ADDRESS", event.getAddress() != null ? event.getAddress() : "N/A");
        // ReportActivity uses REPORT_DATE for date field (dd/MM/yyyy preferred for display)
        try {
            SimpleDateFormat in = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            intent.putExtra("REPORT_DATE", out.format(in.parse(reportDateDdMmYy)));
        } catch (Exception e) {
            intent.putExtra("REPORT_DATE", new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));
        }
        startActivity(intent);
    }

    /**
     * Mark an event as scheduled (revert from completed)
     */
    private void markEventScheduled(WorkEvent event) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "scheduled");
        updates.put("completedAt", null);
        
        db.collection(getCollectionForEvent(event)).document(event.getId())
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              Toast.makeText(this, "Event marked as scheduled!", Toast.LENGTH_SHORT).show();
              loadEventsForDate(selectedDate);
              scheduleEventNotification(event);
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error updating event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Schedule event deletion after specified delay
     */
    private void scheduleEventDeletion(WorkEvent event, long delayMillis) {
        new android.os.Handler().postDelayed(() -> {
            // Delete the event after the delay
            db.collection(getCollectionForEvent(event)).document(event.getId())
              .delete()
              .addOnSuccessListener(aVoid -> {
                  Toast.makeText(this, "Completed event automatically deleted.", Toast.LENGTH_SHORT).show();
                  loadEventsForDate(selectedDate);
              })
              .addOnFailureListener(e -> {
                  // Event might have been manually deleted, ignore error
              });
        }, delayMillis);
    }

    /**
     * Schedule notification for upcoming events (30 minutes before)
     */
    private void scheduleEventNotification(WorkEvent event) {
        // In-app only reminders: schedule WorkManager to write a Firestore notification record.
        if (event == null) return;
        if (!"scheduled".equalsIgnoreCase(event.getStatus())) return;
        if (event.getId() == null || event.getId().trim().isEmpty()) return;
        // Only jobs/contracts should generate reminders
        if (!"job".equalsIgnoreCase(event.getEventType()) && !"contract".equalsIgnoreCase(event.getEventType())) return;

        try {
            // Keep an offline cache so popup reminders can fire/schedule without Firestore.
            try {
                WorkViewLocalEventStore.upsert(getApplicationContext(), userName, getUserWorkViewCollection(), event);
            } catch (Exception ignored) {}

            // Parse event date/time (stored as yyyy-MM-dd and HH:mm)
            String date = event.getDate();
            String time = event.getTime();
            if (date == null || time == null) return;

            String[] d = date.split("-");
            String[] t = time.split(":");
            if (d.length != 3 || t.length != 2) return;

            int year = Integer.parseInt(d[0]);
            int month = Integer.parseInt(d[1]) - 1;
            int day = Integer.parseInt(d[2]);
            int hour = Integer.parseInt(t[0]);
            int minute = Integer.parseInt(t[1]);

            Calendar eventCal = Calendar.getInstance();
            eventCal.set(Calendar.YEAR, year);
            eventCal.set(Calendar.MONTH, month);
            eventCal.set(Calendar.DAY_OF_MONTH, day);
            eventCal.set(Calendar.HOUR_OF_DAY, hour);
            eventCal.set(Calendar.MINUTE, minute);
            eventCal.set(Calendar.SECOND, 0);
            eventCal.set(Calendar.MILLISECOND, 0);

            // Reminder is 30 minutes before event time
            eventCal.add(Calendar.MINUTE, -30);

            long delayMs = eventCal.getTimeInMillis() - System.currentTimeMillis();
            if (delayMs <= 0) {
                return; // don't schedule past reminders
            }

            String targetUser = event.getUserName() != null ? event.getUserName() : userName;
            String collection = getCollectionForEvent(event);

            Data input = new Data.Builder()
                    .putString(InAppReminderWorker.KEY_USER_NAME, targetUser)
                    .putString(InAppReminderWorker.KEY_COLLECTION, collection)
                    .putString(InAppReminderWorker.KEY_EVENT_DOC_ID, event.getId())
                    .putString(InAppReminderWorker.KEY_EXPECTED_DATE, event.getDate())
                    .putString(InAppReminderWorker.KEY_EXPECTED_TIME, event.getTime())
                    .putString(InAppReminderWorker.KEY_EVENT_NAME, event.getEventName())
                    .putString(InAppReminderWorker.KEY_EVENT_TYPE, event.getEventType())
                    .putString(InAppReminderWorker.KEY_EVENT_ADDRESS, event.getAddress())
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(InAppReminderWorker.class)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .build();

            WorkManager.getInstance(getApplicationContext())
                    .enqueueUniqueWork(getReminderWorkName(event), ExistingWorkPolicy.REPLACE, request);

            // Local popup reminder: only schedule for the currently logged-in user on this device.
            if (targetUser != null && targetUser.equalsIgnoreCase(userName)) {
                WorkViewPopupReminderScheduler.scheduleForEvent(
                        getApplicationContext(),
                        userName,
                        getUserWorkViewCollection(),
                        event
                );
            }
        } catch (Exception e) {
            Log.e("WorkViewActivity", "Failed to schedule in-app reminder: " + e.getMessage());
        }
    }

    private void cancelInAppReminder(WorkEvent event) {
        if (event == null) return;
        try {
            WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(getReminderWorkName(event));
            if (event.getId() != null) {
                WorkViewPopupReminderScheduler.cancelForEvent(getApplicationContext(), event.getId());
                try {
                    WorkViewLocalEventStore.remove(getApplicationContext(), userName, event.getId());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
        }
    }

    private String getReminderWorkName(WorkEvent event) {
        return "inapp_reminder_" + (event != null && event.getId() != null ? event.getId() : "unknown");
    }

    /**
     * Schedule notifications for all existing events
     */
    private void scheduleNotificationsForExistingEvents() {
        try {
            // Get today's date and next 7 days
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = sdf.format(new Date());
            
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, 7);
            String nextWeek = sdf.format(calendar.getTime());
            
            db.collection(getUserWorkViewCollection())
              .whereGreaterThanOrEqualTo("date", today)
              .whereLessThanOrEqualTo("date", nextWeek)
              .whereEqualTo("status", "scheduled")
              .get()
              .addOnSuccessListener(queryDocumentSnapshots -> {
                  try {
                      for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                          WorkEvent event = document.toObject(WorkEvent.class);
                          if (event != null) {
                              event.setId(document.getId());
                              scheduleEventNotification(event);
                          }
                      }
                  } catch (Exception e) {
                      Log.e("WorkViewActivity", "Error processing events for notifications: " + e.getMessage());
                  }
              })
              .addOnFailureListener(e -> {
                  Log.e("WorkViewActivity", "Error loading events for notifications: " + e.getMessage());
              });
        } catch (Exception e) {
            Log.e("WorkViewActivity", "Error in scheduleNotificationsForExistingEvents: " + e.getMessage());
        }
    }

    /**
     * Schedule daily missed events popup at 18:00
     */
    private void scheduleDailyMissedEventsPopup() {
        try {
            // Calculate time until 18:00 today
            Calendar calendar = Calendar.getInstance();
            Calendar targetTime = Calendar.getInstance();
            targetTime.set(Calendar.HOUR_OF_DAY, 18);
            targetTime.set(Calendar.MINUTE, 0);
            targetTime.set(Calendar.SECOND, 0);
            targetTime.set(Calendar.MILLISECOND, 0);
            
            // If it's already past 18:00, schedule for tomorrow
            if (calendar.getTimeInMillis() >= targetTime.getTimeInMillis()) {
                targetTime.add(Calendar.DAY_OF_YEAR, 1);
            }
            
            long delayMillis = targetTime.getTimeInMillis() - calendar.getTimeInMillis();
            
            new android.os.Handler().postDelayed(() -> {
                try {
                    showMissedEventsPopup();
                    // Schedule the next day's popup
                    scheduleDailyMissedEventsPopup();
                } catch (Exception e) {
                    Log.e("WorkViewActivity", "Error in missed events popup: " + e.getMessage());
                }
            }, delayMillis);
            
        } catch (Exception e) {
            Log.e("WorkViewActivity", "Error scheduling missed events popup: " + e.getMessage());
        }
    }

    /**
     * Show popup with all missed events for today
     */
    private void showMissedEventsPopup() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = sdf.format(new Date());
            
            db.collection(getUserWorkViewCollection())
              .whereEqualTo("date", today)
              .whereEqualTo("status", "scheduled")
              .get()
              .addOnSuccessListener(queryDocumentSnapshots -> {
                  try {
                      List<WorkEvent> missedEvents = new ArrayList<>();
                      
                      for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                          WorkEvent event = document.toObject(WorkEvent.class);
                          if (event != null) {
                              event.setId(document.getId());
                              
                              // Check if event time has passed
                              try {
                                  String eventTime = event.getTime();
                                  String[] timeParts = eventTime.split(":");
                                  int eventHour = Integer.parseInt(timeParts[0]);
                                  int eventMinute = Integer.parseInt(timeParts[1]);
                                  
                                  Calendar now = Calendar.getInstance();
                                  Calendar eventTimeCal = Calendar.getInstance();
                                  eventTimeCal.set(Calendar.HOUR_OF_DAY, eventHour);
                                  eventTimeCal.set(Calendar.MINUTE, eventMinute);
                                  eventTimeCal.set(Calendar.SECOND, 0);
                                  eventTimeCal.set(Calendar.MILLISECOND, 0);
                                  
                                  if (now.getTimeInMillis() > eventTimeCal.getTimeInMillis()) {
                                      missedEvents.add(event);
                                  }
                              } catch (Exception e) {
                                  // Skip events with invalid time format
                                  Log.d("WorkViewActivity", "Skipping event with invalid time format: " + e.getMessage());
                              }
                          }
                      }
              
              if (!missedEvents.isEmpty()) {
                  showMissedEventsDialog(missedEvents);
              }
          } catch (Exception e) {
              Log.e("WorkViewActivity", "Error processing missed events: " + e.getMessage());
          }
      })
      .addOnFailureListener(e -> {
          Log.e("WorkViewActivity", "Error loading missed events: " + e.getMessage());
      });
    } catch (Exception e) {
        Log.e("WorkViewActivity", "Error in showMissedEventsPopup: " + e.getMessage());
    }
}

    /**
     * Show dialog with missed events and options
     */
    private void showMissedEventsDialog(List<WorkEvent> missedEvents) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Missed Events - " + new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));
        
        // Create message with missed events
        StringBuilder message = new StringBuilder();
        message.append("You have ").append(missedEvents.size()).append(" missed event(s) today:\n\n");
        
        for (WorkEvent event : missedEvents) {
            message.append("⏰ ").append(event.getTime()).append(" - ").append(event.getEventName());
            if (event.getAddress() != null && !event.getAddress().isEmpty() && !event.getAddress().equals("N/A")) {
                message.append("\n📍 ").append(event.getAddress());
            }
            message.append("\n\n");
        }
        
        builder.setMessage(message.toString());
        
        // Add action buttons
        builder.setPositiveButton("Reschedule All", (dialog, which) -> {
            rescheduleMissedEvents(missedEvents);
        });
        
        builder.setNegativeButton("Mark Complete", (dialog, which) -> {
            markMissedEventsComplete(missedEvents);
        });
        
        builder.setNeutralButton("Close", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.setCancelable(false);
        builder.show();
    }

    /**
     * Reschedule all missed events for tomorrow
     */
    private void rescheduleMissedEvents(List<WorkEvent> missedEvents) {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String tomorrowDate = sdf.format(tomorrow.getTime());
        
        final int[] rescheduledCount = {0};
        for (WorkEvent event : missedEvents) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("date", tomorrowDate);
            
            db.collection(getUserWorkViewCollection()).document(event.getId())
              .update(updates)
              .addOnSuccessListener(aVoid -> {
                  // Reschedule reminder for the new date
                  event.setDate(tomorrowDate);
                  scheduleEventNotification(event);
                  rescheduledCount[0]++;
                  if (rescheduledCount[0] == missedEvents.size()) {
                      Toast.makeText(this, "All missed events rescheduled for tomorrow!", Toast.LENGTH_SHORT).show();
                      loadEventsForDate(selectedDate);
                  }
              })
              .addOnFailureListener(e -> {
                  Toast.makeText(this, "Error rescheduling event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
              });
        }
    }

    /**
     * Mark all missed events as complete
     */
    private void markMissedEventsComplete(List<WorkEvent> missedEvents) {
        final int[] completedCount = {0};
        for (WorkEvent event : missedEvents) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "completed");
            updates.put("completedAt", new Date());
            
            db.collection(getUserWorkViewCollection()).document(event.getId())
              .update(updates)
              .addOnSuccessListener(aVoid -> {
                  cancelInAppReminder(event);
                  completedCount[0]++;
                  if (completedCount[0] == missedEvents.size()) {
                      Toast.makeText(this, "All missed events marked as complete!", Toast.LENGTH_SHORT).show();
                      loadEventsForDate(selectedDate);
                  }
              })
              .addOnFailureListener(e -> {
                  Toast.makeText(this, "Error marking event complete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
              });
        }
    }



    /**
     * Add follow-up to an existing event
     */
    private void addFollowUpToEvent(WorkEvent event) {
        Intent intent = new Intent(this, AddFollowUpActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("ORIGINAL_EVENT_ID", event.getId());
        intent.putExtra("EVENT_TYPE", event.getEventType());
        intent.putExtra("EVENT_NAME", event.getEventName());
        startActivity(intent);
    }

    /**
     * Create report from an event
     */
    private void createReportFromEvent(WorkEvent event) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("EVENT_NAME", event.getEventName());
        intent.putExtra("EVENT_TYPE", event.getEventType());
        startActivity(intent);
    }

    /**
     * Add follow-up to a job event
     */
    private void addFollowUpToJob(WorkEvent event) {
        showTimeSelectionDialog("followup", event.getEventId(), event.getEventName() + " - Follow-up");
    }

    /**
     * Add follow-up to a contract event
     */
    private void addFollowUpToContract(WorkEvent event) {
        showTimeSelectionDialog("followup", event.getEventId(), event.getEventName() + " - Follow-up");
    }

    /**
     * Finish a job - delete from JobWork and remove from calendar
     */
    private void finishJob(WorkEvent event) {
        new AlertDialog.Builder(this)
            .setTitle("Finish Job")
            .setMessage("Are you sure you want to finish this job? This will delete it from the database.")
            .setPositiveButton("Yes", (dialog, which) -> {
                cancelInAppReminder(event);
                // Delete from jobwork collection (eventId is the jobwork document ID)
                String userCollection = getCollectionForEvent(event);
                db.collection(FirestorePaths.JOBWORK).document(event.getEventId())
                  .delete()
                  .addOnSuccessListener(aVoid -> {
                      // Delete from work view collection
                      db.collection(userCollection).document(event.getId())
                        .delete()
                        .addOnSuccessListener(aVoid2 -> {
                            Toast.makeText(this, "Job finished and removed from View Jobs!", Toast.LENGTH_SHORT).show();
                            loadEventsForDate(selectedDate);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Error removing from calendar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                  })
                  .addOnFailureListener(e -> {
                      Toast.makeText(this, "Error finishing job: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                  });
            })
            .setNegativeButton("No", null)
            .show();
    }

    /**
     * Complete a contract - show options for create report or update visit
     */
    private void completeContract(WorkEvent event) {
        String[] completeOptions = {"Create Report", "Update Last Visit"};
        
        new AlertDialog.Builder(this)
            .setTitle("Complete Contract")
            .setItems(completeOptions, (dialog, which) -> {
                switch (which) {
                    case 0:
                        createReportFromContract(event);
                        break;
                    case 1:
                        updateContractLastVisitAndRemove(event);
                        break;
                }
            })
            .show();
    }

    /**
     * Update contract last visit and remove from calendar.
     * Updates by contract ID in the shared contracts collection. Scalable for any number of techs.
     */
    private void updateContractLastVisitAndRemove(WorkEvent event) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Dublin"));
        String today = sdf.format(new Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", today);

        db.collection(FirestorePaths.CONTRACTS).document(event.getEventId())
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              // Notify technician + admins about contract visit update from calendar.
              try {
                  if (!BuildConfig.IS_OFFLINE) {
                      Map<String, Object> data = new HashMap<>();
                      data.put("contractId", event.getEventId());
                      data.put("eventId", event.getId());
                      data.put("lastVisit", today);
                      data.put("eventName", event.getEventName());
                      String docId = "contract_visit_calendar_" + event.getEventId() + "_" + System.currentTimeMillis();
                      String recipient = event.getUserName() != null ? event.getUserName() : userName;
                      NotificationUtils.writeInAppNotification(
                              recipient,
                              docId,
                              "Contract visit updated from calendar",
                              "Visit marked complete for " + (event.getEventName() != null ? event.getEventName() : "contract") + ".",
                              "contract_update",
                              data
                      );
                  }
              } catch (Exception ignored) { }

              cancelInAppReminder(event);
              db.collection(getCollectionForEvent(event)).document(event.getId())
                .delete()
                .addOnSuccessListener(aVoid2 -> {
                    Toast.makeText(this, "Contract visit updated and removed from calendar!", Toast.LENGTH_SHORT).show();
                    loadEventsForDate(selectedDate);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error removing from calendar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error updating contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Create report from job event
     */
    private void createReportFromJob(WorkEvent event) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("EVENT_NAME", event.getEventName());
        intent.putExtra("EVENT_TYPE", "job");
        intent.putExtra("EVENT_ADDRESS", event.getAddress());
        intent.putExtra("EVENT_ISSUE", event.getIssue());
        startActivity(intent);
    }

    /**
     * Create report from contract event
     */
    private void createReportFromContract(WorkEvent event) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("EVENT_NAME", event.getEventName());
        intent.putExtra("EVENT_TYPE", "contract");
        intent.putExtra("EVENT_ADDRESS", event.getAddress());
        startActivity(intent);
    }

    /**
     * Create time slot views for daily view
     */
    private void createTimeSlotViews() {
        android.view.View timeSlotsContainer = findViewById(R.id.timeSlotsContainer);
        ScrollView scrollView = (ScrollView) timeSlotsContainer;
        LinearLayout timeSlotsLayout = (LinearLayout) scrollView.getChildAt(0);
        timeSlotsLayout.removeAllViews();

        // Create time slots more efficiently
        for (int idx = 0; idx < SLOT_START_TIMES.length; idx++) {
            String timeSlot = SLOT_START_TIMES[idx];
            String displayRange = SLOT_DISPLAY_RANGES[idx];
            // Create time slot view
            LinearLayout timeSlotView = new LinearLayout(this);
            timeSlotView.setOrientation(LinearLayout.VERTICAL);
            timeSlotView.setPadding(16, 16, 16, 16);
            timeSlotView.setBackgroundResource(R.drawable.surface_frame);
            timeSlotView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            
            // Add margin between time slots
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) timeSlotView.getLayoutParams();
            params.setMargins(8, 8, 8, 8);
            timeSlotView.setLayoutParams(params);

            // Time label
            TextView timeLabel = new TextView(this);
            timeLabel.setText(displayRange);
            timeLabel.setTag(timeSlot); // store start time for matching
            timeLabel.setTextSize(18);
            timeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            timeLabel.setTextColor(MaterialColors.getColor(timeLabel, com.google.android.material.R.attr.colorPrimary));
            timeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            // Events container
            LinearLayout eventsContainer = new LinearLayout(this);
            eventsContainer.setOrientation(LinearLayout.VERTICAL);
            eventsContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            // Default "No events" text
            TextView noEventsText = new TextView(this);
            noEventsText.setText("No events");
            noEventsText.setTextSize(14);
            noEventsText.setTextColor(MaterialColors.getColor(noEventsText, com.google.android.material.R.attr.colorOnSurface));
            noEventsText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            eventsContainer.addView(noEventsText);

            // Add click listener to add event
            timeSlotView.setOnClickListener(v -> {
                showAddEventDialogForTime(timeSlot);
            });

            // Allow dropping dragged events onto this slot to move time
            timeSlotView.setOnDragListener(new android.view.View.OnDragListener() {
                @Override
                public boolean onDrag(android.view.View v, android.view.DragEvent eventDrag) {
                    int action = eventDrag.getAction();
                    switch (action) {
                        case android.view.DragEvent.ACTION_DRAG_STARTED:
                            // Accept all drags; we'll validate type on drop
                            return true;
                        case android.view.DragEvent.ACTION_DRAG_ENTERED:
                            v.setAlpha(0.9f);
                            return true;
                        case android.view.DragEvent.ACTION_DRAG_EXITED:
                            v.setAlpha(1.0f);
                            return true;
                        case android.view.DragEvent.ACTION_DROP:
                            v.setAlpha(1.0f);
                            Object local = eventDrag.getLocalState();
                            if (!(local instanceof WorkEvent)) return true;
                            WorkEvent ev = (WorkEvent) local;
                            if (ev.getId() == null || ev.getId().trim().isEmpty()) return true;

                            // Preserve duration if endTime is set; otherwise 1-hour slot
                            String oldStart = ev.getTime();
                            String oldEnd = ev.getEndTime();
                            String newStart = timeSlot;
                            String newEnd = "";
                            if (oldEnd != null && !oldEnd.trim().isEmpty()) {
                                int oldStartMin = parseMinutes(oldStart);
                                int oldEndMin = parseMinutes(oldEnd);
                                int dur = (oldStartMin >= 0 && oldEndMin > oldStartMin) ? (oldEndMin - oldStartMin) : 60;
                                int newStartMin = parseMinutes(newStart);
                                int newEndMin = newStartMin + dur;
                                int endH = (newEndMin / 60) % 24;
                                int endM = newEndMin % 60;
                                newEnd = String.format(java.util.Locale.getDefault(), "%02d:%02d", endH, endM);
                            }

                            final String normStart = newStart;
                            final String normEnd = newEnd;

                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("time", normStart);
                            updates.put("endTime", normEnd);

                            db.collection(getCollectionForEvent(ev)).document(ev.getId())
                                    .update(updates)
                                    .addOnSuccessListener(aVoid -> {
                                        ev.setTime(normStart);
                                        ev.setEndTime(normEnd);
                                        scheduleEventNotification(ev);
                                        android.widget.Toast.makeText(WorkViewActivity.this, "Time updated.", android.widget.Toast.LENGTH_SHORT).show();
                                        loadEventsForDate(selectedDate);
                                    })
                                    .addOnFailureListener(e -> android.widget.Toast.makeText(WorkViewActivity.this, "Error updating time: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                            return true;
                        case android.view.DragEvent.ACTION_DRAG_ENDED:
                            v.setAlpha(1.0f);
                            return true;
                    }
                    return false;
                }
            });

            // Add views to time slot
            timeSlotView.addView(timeLabel);
            timeSlotView.addView(eventsContainer);
            timeSlotsLayout.addView(timeSlotView);
        }
    }

    /**
     * Update time slots with events for daily view
     */
    private void updateTimeSlotsWithEvents(List<WorkEvent> events) {
        android.view.View timeSlotsContainer = findViewById(R.id.timeSlotsContainer);
        ScrollView scrollView = (ScrollView) timeSlotsContainer;
        LinearLayout timeSlotsLayout = (LinearLayout) scrollView.getChildAt(0);
        
        // Clear existing event labels
        for (int i = 0; i < timeSlotsLayout.getChildCount(); i++) {
            LinearLayout timeSlotView = (LinearLayout) timeSlotsLayout.getChildAt(i);
            if (timeSlotView.getChildCount() > 1) {
                LinearLayout eventsContainer = (LinearLayout) timeSlotView.getChildAt(1);
                eventsContainer.removeAllViews();
                
                // Add default "No events" text
                TextView noEventsText = new TextView(this);
                noEventsText.setText("No events");
                noEventsText.setTextSize(14);
                noEventsText.setTextColor(MaterialColors.getColor(noEventsText, com.google.android.material.R.attr.colorOnSurface));
                noEventsText.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                eventsContainer.addView(noEventsText);
            }
        }
        
        // Update with actual events
        for (WorkEvent event : events) {
            String eventTime = event.getTime();
            String slotStart = resolveSlotStartForEventTime(eventTime);
            for (int i = 0; i < timeSlotsLayout.getChildCount(); i++) {
                LinearLayout timeSlotView = (LinearLayout) timeSlotsLayout.getChildAt(i);
                TextView timeLabel = (TextView) timeSlotView.getChildAt(0);
                
                Object tag = timeLabel.getTag();
                String slotTag = tag != null ? String.valueOf(tag) : null;
                if (slotTag != null && slotTag.equals(slotStart)) {
                    LinearLayout eventsContainer = (LinearLayout) timeSlotView.getChildAt(1);
                    
                    // Remove "No events" text if it exists
                    if (eventsContainer.getChildCount() > 0) {
                        android.view.View firstChild = eventsContainer.getChildAt(0);
                        if (firstChild instanceof TextView) {
                            TextView firstChildText = (TextView) firstChild;
                            if (firstChildText.getText().equals("No events")) {
                                eventsContainer.removeAllViews();
                            }
                        }
                    }
                    
                    // Add clickable event view
                    LinearLayout eventView = createClickableEventView(event);
                    eventsContainer.addView(eventView);
                    break;
                }
            }
        }
    }

    /**
     * Show add event dialog for a specific time slot
     */
    private void showAddEventDialogForTime(String timeSlot) {
        String[] options = {"Add Contract", "Add Job", "Add Follow-up"};
        String titleTime = formatSlotRange(timeSlot);
        
        new AlertDialog.Builder(this)
            .setTitle("Add Event at " + titleTime)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showAddContractDialogForTime(timeSlot);
                        break;
                    case 1:
                        showAddJobDialogForTime(timeSlot);
                        break;
                    case 2:
                        showAddFollowUpDialogForTime(timeSlot);
                        break;
                }
            })
            .show();
    }

    /**
     * \"Custom time...\" flow: specify start/end once when creating an event.
     */
    private void showCustomTimeDialog(String eventType, String eventId, String eventName, String targetUser) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText startInput = new EditText(this);
        startInput.setHint("Start time (e.g. 08:30 or 0830)");
        layout.addView(startInput);

        EditText endInput = new EditText(this);
        endInput.setHint("End time (optional, e.g. 15:00 or 1500)");
        layout.addView(endInput);

        new AlertDialog.Builder(this)
                .setTitle("Custom Time")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String rawStart = startInput.getText().toString().trim();
                    String rawEnd = endInput.getText().toString().trim();

                    String normStart = normalizeTimeInput(rawStart);
                    if (normStart == null) {
                        Toast.makeText(this, "Invalid start time. Use 08:30 or 0830.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String normEnd = "";
                    if (!rawEnd.isEmpty()) {
                        normEnd = normalizeTimeInput(rawEnd);
                        if (normEnd == null) {
                            Toast.makeText(this, "Invalid end time. Use 15:00 or 1500.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        int startMin = parseMinutes(normStart);
                        int endMin = parseMinutes(normEnd);
                        if (startMin < 0 || endMin < 0 || endMin <= startMin) {
                            Toast.makeText(this, "End time must be after start time.", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }

                    createWorkEventWithEndTime(eventType, eventId, eventName, normStart, normEnd, targetUser);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Show add contract dialog for specific time
     */
    private void showAddContractDialogForTime(String timeSlot) {
        if (canManageOtherWorkViews()) {
            showAdminContractSelectionDialogForTime(timeSlot);
        } else {
            showUserContractSelectionDialogForTime(userName, timeSlot);
        }
    }

    /**
     * Show contract selection dialog for regular users with time.
     * Loads from shared contracts by assignedTech (contractKey). Scalable for any number of techs.
     */
    private void showUserContractSelectionDialogForTime(String user, String timeSlot) {
        // When admin chose a user, filter by that user's contracts; otherwise use session contractKey (tech).
        String contractKey = (user != null && !user.trim().isEmpty()) ? user.trim() : (SessionManager.getContractKey(this) != null ? SessionManager.getContractKey(this).trim() : "");
        if (contractKey.isEmpty()) {
            Toast.makeText(this, "Could not resolve technician.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String filterKey = contractKey.trim().toLowerCase(java.util.Locale.getDefault());

        // Debug: log contract selection query for WorkView (with time slot).
        try {
            com.google.firebase.auth.FirebaseUser authUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String authUid = authUser != null ? authUser.getUid() : "null";
            SessionManager.Session session = SessionManager.getCached(this);
            String role = session != null ? session.roleNorm : "unknown";
            String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
            Log.d("WorkViewActivity", "Contract selection query (calendar+time) where assignedTech=" + filterKey
                    + " (requestedUser=" + user
                    + ", timeSlot=" + timeSlot
                    + ", authUid=" + authUid
                    + ", role=" + role
                    + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "") + ")");
        } catch (Exception e) {
            Log.w("WorkViewActivity", "Failed to log contract selection (calendar+time) context: " + e.getMessage());
        }

        db.collection(FirestorePaths.CONTRACTS)
                .whereEqualTo("assignedTech", filterKey)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> contractNames = new ArrayList<>();
                        List<String> contractIds = new ArrayList<>();
                        List<String> contractAddresses = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> contract = document.getData();
                            String contractName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
                            String contractAddress = contract.get("address") != null ? contract.get("address").toString() : "N/A";
                            if (!contractName.equals("N/A")) {
                                contractNames.add(contractName + "\n📍 " + contractAddress);
                                contractIds.add(document.getId());
                                contractAddresses.add(contractAddress);
                            }
                        }
                        if (contractNames.isEmpty()) {
                            Toast.makeText(this, "No contracts found. Please add contracts first.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showContractSelectionDialogWithSearchForTime(contractNames, contractIds, contractAddresses, FirestorePaths.CONTRACTS, timeSlot, user);
                    } else {
                        Toast.makeText(this, "Error loading contracts: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show contract selection dialog for oversight users with time (contractKey list; scales with any number of techs).
     */
    private void showAdminContractSelectionDialogForTime(String timeSlot) {
        UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
            List<UserRepository.AssignableUser> opts = users != null ? users : new ArrayList<>();
            List<String> labels = new ArrayList<>();
            List<String> keys = new ArrayList<>();
            for (UserRepository.AssignableUser u : opts) {
                if (u == null || u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                labels.add(u.contractKey.trim());
                keys.add(u.contractKey.trim());
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "No technicians with contracts found.", Toast.LENGTH_SHORT).show();
                return;
            }
            final String[] ownerContractKeys = keys.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Select User's Contracts for " + formatSlotRange(timeSlot))
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> showUserContractSelectionDialogForTime(ownerContractKeys[which], timeSlot))
                    .show();
        }));
    }

    /**
     * Show contract selection dialog with search functionality for specific time
     */
    private void showContractSelectionDialogWithSearchForTime(List<String> contractNames, List<String> contractIds, 
                                                           List<String> contractAddresses, String collectionName, String timeSlot, String user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Contract for " + formatSlotRange(timeSlot) + " from " + collectionName);

        // Inflate custom layout
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_contract_selection, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        ListView listView = dialogView.findViewById(R.id.contractsListView);

        // Create custom adapter with better filtering
        ContractSelectionAdapter adapter = new ContractSelectionAdapter(this, contractNames);
        listView.setAdapter(adapter);

        // Enhanced live search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Live search - filter as user types
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                String selectedContract = adapter.getItem(position);
                if (selectedContract != null) {
                    // Find the original index in the contractIds list
                    int originalIndex = contractNames.indexOf(selectedContract);
                    if (originalIndex >= 0 && originalIndex < contractIds.size()) {
                        String selectedContractId = contractIds.get(originalIndex);
                        String contractName = adapter.getContractName(position);
                        dialog.dismiss();
                        createWorkEvent("contract", selectedContractId, contractName, timeSlot, user);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error selecting contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
        
        // Focus on search bar for immediate typing
        searchBar.requestFocus();
    }

    /**
     * Show add job dialog for specific time.
     * First asks New or Old. New = create new job. Old = pick from existing View Jobs list.
     */
    private void showAddJobDialogForTime(String timeSlot) {
        new AlertDialog.Builder(this)
            .setTitle("Add Job at " + formatSlotRange(timeSlot))
            .setItems(new String[]{"New", "Old"}, (dialog, which) -> {
                if (which == 0) {
                    if (canManageOtherWorkViews()) {
                        StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
                            List<StaffDirectory.OwnerOption> opts = options != null ? options : new ArrayList<>();
                            String[] displayLabels = new String[opts.size()];
                            String[] ownerKeys = new String[opts.size()];
                            for (int i = 0; i < opts.size(); i++) {
                                StaffDirectory.OwnerOption o = opts.get(i);
                                displayLabels[i] = o != null ? o.display : "";
                                ownerKeys[i] = o != null ? o.ownerKey : "";
                            }
                            new AlertDialog.Builder(this)
                                    .setTitle("Assign Job To (" + formatSlotRange(timeSlot) + ")")
                                    .setItems(displayLabels, (d, w) -> openAddJobActivityForTime(ownerKeys[w], timeSlot))
                                    .show();
                        }));
                    } else {
                        openAddJobActivityForTime(userName, timeSlot);
                    }
                } else {
                    showAddExistingJobDialog(timeSlot);
                }
            })
            .show();
    }

    /**
     * Open AddJobFromCalendarActivity with target user and time
     */
    private void openAddJobActivityForTime(String targetUser, String timeSlot) {
        Intent intent = new Intent(this, AddJobFromCalendarActivity.class);
        intent.putExtra("USER_NAME", targetUser);
        intent.putExtra("CREATED_BY", userName);
        intent.putExtra("SELECTED_DATE", selectedDate.getTime());
        intent.putExtra("SELECTED_TIME", timeSlot);
        startActivity(intent);
    }

    /**
     * Show add follow-up dialog for specific time
     */
    private void showAddFollowUpDialogForTime(String timeSlot) {
        Intent intent = new Intent(this, AddFollowUpActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("SELECTED_DATE", selectedDate.getTime());
        intent.putExtra("SELECTED_TIME", timeSlot);
        startActivity(intent);
    }

    /**
     * Resolve which 1-hour slot a stored event time belongs to.
     * This keeps older events (e.g., "09:00") visible by mapping them into the closest slot window.
     */
    private String resolveSlotStartForEventTime(String eventTime) {
        if (eventTime == null || eventTime.trim().isEmpty()) return null;
        int eventMin = parseMinutes(eventTime);
        if (eventMin < 0) return null;

        // First, check if it matches a slot start exactly.
        for (String start : SLOT_START_TIMES) {
            if (eventTime.equals(start)) return start;
        }

        // Otherwise, place it into the slot window [start, start+60).
        for (String start : SLOT_START_TIMES) {
            int startMin = parseMinutes(start);
            if (startMin >= 0 && eventMin >= startMin && eventMin < (startMin + 60)) {
                return start;
            }
        }

        // If earlier than first slot, put into first.
        int first = parseMinutes(SLOT_START_TIMES[0]);
        if (first >= 0 && eventMin < first) return SLOT_START_TIMES[0];

        // If later than last slot end, put into last.
        return SLOT_START_TIMES[SLOT_START_TIMES.length - 1];
    }

    private int parseMinutes(String hhmm) {
        try {
            String[] p = hhmm.trim().split(":");
            if (p.length != 2) return -1;
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return -1;
            return (h * 60) + m;
        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * Normalizes time input such as \"8:30\", \"08:30\", or \"0830\" into HH:mm (24h) format.
     */
    private String normalizeTimeInput(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;

        // If it's already in H:mm or HH:mm form.
        if (t.matches("^\\d{1,2}:\\d{2}$")) {
            String[] p = t.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return String.format(Locale.getDefault(), "%02d:%02d", h, m);
        }

        // If it's a 3 or 4 digit number like 930 or 0930.
        if (t.matches("^\\d{3,4}$")) {
            if (t.length() == 3) t = "0" + t;
            String hStr = t.substring(0, 2);
            String mStr = t.substring(2, 4);
            int h = Integer.parseInt(hStr);
            int m = Integer.parseInt(mStr);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return String.format(Locale.getDefault(), "%02d:%02d", h, m);
        }

        return null;
    }

    private String formatSlotRange(String startTime) {
        int startMin = parseMinutes(startTime);
        if (startMin < 0) return startTime != null ? startTime : "";
        int endMin = startMin + 60;
        int endH = (endMin / 60) % 24;
        int endM = endMin % 60;
        return String.format(Locale.getDefault(), "%s - %02d:%02d", startTime, endH, endM);
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        // In-app only notifications: Android notification channels disabled.
    }

    /**
     * Send notification for upcoming events with route option
     */
    private void sendEventNotification(WorkEvent event) {
        // In-app only notifications: Android system notifications disabled.
    }

    /**
     * Create a clickable event view with checkbox
     */
    private LinearLayout createClickableEventView(WorkEvent event) {
        LinearLayout eventView = new LinearLayout(this);
        eventView.setOrientation(LinearLayout.HORIZONTAL);
        eventView.setPadding(8, 8, 8, 8);
        eventView.setBackgroundResource(android.R.drawable.list_selector_background);
        eventView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Checkbox for marking complete
        android.widget.CheckBox completeCheckBox = new android.widget.CheckBox(this);
        completeCheckBox.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        
        // Set checkbox state based on event status
        completeCheckBox.setChecked("completed".equals(event.getStatus()));
        
        // Checkbox click listener - show confirmation before marking done; disable on Yes to prevent double tap
        completeCheckBox.setOnClickListener(v -> {
            if (completeCheckBox.isChecked()) {
                showMarkDoneConfirmationDialog(event, () -> completeCheckBox.setChecked(false), () -> completeCheckBox.setEnabled(false));
            } else {
                // Uncheck - revert to scheduled
                markEventScheduled(event);
            }
        });

        // Event icon/type indicator
        TextView eventTypeView = new TextView(this);
        eventTypeView.setText(getEventTypeIcon(event.getEventType()));
        eventTypeView.setTextSize(16);
        eventTypeView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Event details
        LinearLayout eventDetails = new LinearLayout(this);
        eventDetails.setOrientation(LinearLayout.VERTICAL);
        eventDetails.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView eventNameView = new TextView(this);
        String displayName = event.getEventName();
        if (canManageOtherWorkViews()
                && event.getUserName() != null
                && !event.getUserName().trim().isEmpty()) {
            displayName = displayName + " (" + event.getUserName() + ")";
        }
        eventNameView.setText(displayName);
        eventNameView.setTextSize(14);
        eventNameView.setTypeface(null, android.graphics.Typeface.BOLD);
        eventNameView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView eventTypeLabel = new TextView(this);
        eventTypeLabel.setText(event.getEventType());
        eventTypeLabel.setTextSize(12);
        eventTypeLabel.setTextColor(android.graphics.Color.GRAY);
        eventTypeLabel.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        eventDetails.addView(eventNameView);
        eventDetails.addView(eventTypeLabel);

        // Add click listener for options dialog
        eventView.setOnClickListener(v -> {
            showEventOptionsDialog(event);
        });

        // Long-press to drag-and-drop between time slots (change start/end time)
        eventView.setOnLongClickListener(v -> {
            android.view.View.DragShadowBuilder shadow = new android.view.View.DragShadowBuilder(v);
            v.startDragAndDrop(null, shadow, event, 0);
            return true;
        });

        eventView.addView(completeCheckBox);
        eventView.addView(eventTypeView);
        eventView.addView(eventDetails);

        // Color-code by technician for combined view
        eventNameView.setTextColor(getColorForUser(event.getUserName()));

        return eventView;
    }

    /**
     * Colour mapping per user for calendar events.
     */
    private int getColorForUser(String user) {
        if (user == null) {
            return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0);
        }
        final int[] palette = new int[] {
                android.graphics.Color.parseColor("#FB8C00"), // Orange
                android.graphics.Color.parseColor("#1E88E5"), // Blue
                android.graphics.Color.parseColor("#43A047"), // Green
                android.graphics.Color.parseColor("#8E24AA"), // Purple
                android.graphics.Color.parseColor("#F4511E"), // Deep Orange
                android.graphics.Color.parseColor("#3949AB")  // Indigo
        };
        String key = user.trim().toLowerCase(Locale.getDefault());
        if (key.isEmpty()) return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0);
        int idx = Math.abs(key.hashCode()) % palette.length;
        return palette[idx];
    }

    /**
     * Get event type icon
     */
    private String getEventTypeIcon(String eventType) {
        switch (eventType.toLowerCase()) {
            case "contract":
                return "📋";
            case "job":
                return "🔧";
            case "followup":
                return "🔄";
            default:
                return "📅";
        }
    }

    /**
     * Show event options dialog
     */
    private void showEventOptionsDialog(WorkEvent event) {
        String[] options = {"Update", "Create Report", "Mark Complete", "Add Follow-up", "Directions", "Delete"};
        
        new AlertDialog.Builder(this)
            .setTitle("Event Options: " + event.getEventName())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        // Update event
                        updateEvent(event);
                        break;
                    case 1:
                        // Create report
                        createReportFromEvent(event);
                        break;
                    case 2:
                        // Mark complete
                        markEventDone(event);
                        break;
                    case 3:
                        // Add follow-up
                        addFollowUpToEvent(event);
                        break;
                    case 4:
                        // Open directions
                        openInMaps(event.getAddress());
                        break;
                    case 5:
                        // Delete event
                        deleteEvent(event);
                        break;
                }
            })
            .show();
    }

    /**
     * Update event - for contracts, updates last visit date
     */
    private void updateEvent(WorkEvent event) {
        if ("contract".equals(event.getEventType())) {
            // Update contract last visit date
            updateContractLastVisit(event.getEventId(), event.getUserName());
            Toast.makeText(this, "Contract last visit updated!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Update event: " + event.getEventName(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update contract last visit date with proper format. Uses shared contracts collection (scalable for any number of techs).
     */
    private void updateContractLastVisit(String contractId, String userName) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        String today = sdf.format(new Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", today);

        db.collection(FirestorePaths.CONTRACTS).document(contractId)
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              Toast.makeText(this, "Contract last visit updated to " + today, Toast.LENGTH_SHORT).show();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error updating contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Delete event
     */
    private void deleteEvent(WorkEvent event) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '" + event.getEventName() + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                cancelInAppReminder(event);
                // Delete from Firebase using the correct technician's collection
                String userCollection = getCollectionForEvent(event);
                db.collection(userCollection).document(event.getId()).delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Event deleted successfully", Toast.LENGTH_SHORT).show();
                        loadEventsForDate(selectedDate);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to delete event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Open Google Maps with the event address
     */
    private void openInMaps(String address) {
        if (address != null && !address.isEmpty() && !address.equals("N/A")) {
            // Record this as the user's last "map opened" location
            try {
                FirebaseFirestore.getInstance()
                        .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                        .document(LocationSharing.userKey(userName))
                        .set(new java.util.HashMap<String, Object>() {{
                            put("userName", userName);
                            put("lastMapQuery", address);
                            put("lastMapClientTimestampMs", System.currentTimeMillis());
                            put("lastMapAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                            put("source", "map_open");
                        }}, com.google.firebase.firestore.SetOptions.merge());
            } catch (Exception ignored) {}

            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            
            // Check if Google Maps is available
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to any available map app
                mapIntent.setPackage(null);
                startActivity(mapIntent);
            }
        } else {
            Toast.makeText(this, "No address available to open in Maps.", Toast.LENGTH_SHORT).show();
        }
    }
} 
