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
 *    - Time slots from 08:00 to 17:30
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
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

package com.grpc.grpc;

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

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.app.DatePickerDialog;
import android.widget.EditText;
import android.widget.ListView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;

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

    // Time slots for daily view
    private static final String[] TIME_SLOTS = {
        "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
        "12:00", "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30",
        "16:00", "16:30", "17:00", "17:30"
    };

    /**
     * Main entry point of the work view activity
     * Initializes the calendar interface, sets up event management,
     * and configures notification system
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_view);

        // ============================================================================
        // FIREBASE INITIALIZATION
        // ============================================================================
        
        db = FirebaseFirestore.getInstance();

        // ============================================================================
        // USER AUTHENTICATION & VALIDATION
        // ============================================================================
        
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
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
        dailyViewButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(android.R.color.holo_blue_dark)));
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
                                // Swipe left - open ViewContractActivity (previous in sequence)
                                Log.d("WorkViewActivity", "Swipe LEFT detected - opening ViewContractActivity with user: " + userName);
                                Intent intent = new Intent(WorkViewActivity.this, ViewContractActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
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
        }
    }

    /**
     * Get the user-specific collection name for Firebase
     * Creates separate collections for each user (e.g., "james_workview", "ian_workview")
     */
    private String getUserWorkViewCollection() {
        return userName.toLowerCase() + "_workview";
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
        dailyViewButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(android.R.color.holo_blue_dark)));
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

        String userCollection = userName.toLowerCase() + "_workview";
        
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
        if ("kristine".equalsIgnoreCase(userName)) {
            // Kristine can access all users' contracts
            showKristineContractSelectionDialog();
        } else {
            // Regular users can only access their own contracts
            showUserContractSelectionDialog(userName);
        }
    }

    /**
     * Show contract selection dialog for regular users
     */
    private void showUserContractSelectionDialog(String user) {
        String collectionName = user + " Contracts";
        
        db.collection(collectionName).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
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
                    Toast.makeText(this, "No contracts found in " + collectionName + ". Please add contracts first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Create custom dialog with search functionality
                showContractSelectionDialogWithSearch(contractNames, contractIds, contractAddresses, collectionName);
            } else {
                Toast.makeText(this, "Error loading contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
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

        // Handle item click with better error handling
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                String selectedContract = adapter.getItem(position);
                if (selectedContract != null) {
                    // Find the original index in the contractIds list
                    int originalIndex = contractNames.indexOf(selectedContract);
                    if (originalIndex >= 0 && originalIndex < contractIds.size()) {
                        String selectedContractId = contractIds.get(originalIndex);
                        String contractName = adapter.getContractName(position);
                        showTimeSelectionDialog("contract", selectedContractId, contractName);
                        builder.create().dismiss();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error selecting contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Focus on search bar for immediate typing
        searchBar.requestFocus();
    }

    /**
     * Show contract selection dialog for Kristine (can access all users' contracts)
     */
    private void showKristineContractSelectionDialog() {
        // First, let Kristine select which user's contracts to view
        String[] users = {"Ian", "James", "Kristine"};
        
        new AlertDialog.Builder(this)
            .setTitle("Select User's Contracts")
            .setItems(users, (dialog, which) -> {
                String selectedUser = users[which];
                showUserContractSelectionDialog(selectedUser);
            })
            .show();
    }

    /**
     * Show time selection dialog with user selection for Kristine
     */
    private void showTimeSelectionDialog(String eventType, String eventId, String eventName) {
        if ("kristine".equalsIgnoreCase(userName)) {
            // Kristine can select which user's work view to add the event to
            showKristineUserSelectionDialog(eventType, eventId, eventName);
        } else {
            // Regular users add to their own work view
            showTimeSlotsDialog(eventType, eventId, eventName, userName);
        }
    }

    /**
     * Show user selection dialog for Kristine
     */
    private void showKristineUserSelectionDialog(String eventType, String eventId, String eventName) {
        String[] users = {"Ian", "James", "Kristine"};
        
        new AlertDialog.Builder(this)
            .setTitle("Select User's Work View")
            .setItems(users, (dialog, which) -> {
                String selectedUser = users[which];
                showTimeSlotsDialog(eventType, eventId, eventName, selectedUser);
            })
            .show();
    }

    /**
     * Show time slots dialog
     */
    private void showTimeSlotsDialog(String eventType, String eventId, String eventName, String targetUser) {
        new AlertDialog.Builder(this)
            .setTitle("Select Time for " + targetUser + "'s Work View")
            .setItems(TIME_SLOTS, (dialog, which) -> {
                String selectedTime = TIME_SLOTS[which];
                createWorkEvent(eventType, eventId, eventName, selectedTime, targetUser);
            })
            .show();
    }

    /**
     * Show dialog for adding job events
     */
    private void showAddJobDialog() {
        if ("kristine".equalsIgnoreCase(userName)) {
            // Kristine can select which user's work view to add the job to
            String[] users = {"Ian", "James", "Kristine"};
            
            new AlertDialog.Builder(this)
                .setTitle("Select User's Work View for Job")
                .setItems(users, (dialog, which) -> {
                    String selectedUser = users[which];
                    openAddJobActivity(selectedUser);
                })
                .show();
        } else {
            // Regular users add jobs to their own work view
            openAddJobActivity(userName);
        }
    }

    /**
     * Open AddJobFromCalendarActivity with target user
     */
    private void openAddJobActivity(String targetUser) {
        Intent intent = new Intent(this, AddJobFromCalendarActivity.class);
        intent.putExtra("USER_NAME", targetUser);
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
     * Create a new work event with address information and target user
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
            // For contracts, get address from contract data
            String collectionName = targetUser + " Contracts";
            db.collection(collectionName).document(eventId)
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
     * Save event to Firebase with user-specific collection
     */
    private void saveEventToFirebase(Map<String, Object> event) {
        // Add createdBy field for notification system
        event.put("createdBy", userName);
        
        db.collection(getUserWorkViewCollection())
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
              workEvent.setStatus((String) event.get("status"));
              workEvent.setAddress((String) event.get("address"));
              workEvent.setCreatedBy((String) event.get("createdBy"));
              
              // Schedule notification for the event
              scheduleEventNotification(workEvent);
              
              loadEventsForDate(selectedDate);
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error adding event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Handle event click for actions like mark done, add follow-up, create report
     */
    private void onEventClicked(WorkEvent event) {
        if ("job".equals(event.getEventType())) {
            // Job actions: Follow-up, Create Report, Finished
            String[] jobOptions = {"Follow-up", "Create Report", "Finished"};
            
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
                            finishJob(event);
                            break;
                    }
                })
                .show();
        } else if ("contract".equals(event.getEventType())) {
            // Contract actions: Follow-up, Complete, Create Report
            String[] contractOptions = {"Follow-up", "Complete", "Create Report"};
            
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
                    }
                })
                .show();
        } else {
            // Follow-up actions: Create Report, Mark Done
            String[] followUpOptions = {"Create Report", "Mark Done"};
            
            new AlertDialog.Builder(this)
                .setTitle("Follow-up Actions")
                .setItems(followUpOptions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            createReportFromEvent(event);
                            break;
                        case 1:
                            markEventDone(event);
                            break;
                    }
                })
                .show();
        }
    }

    /**
     * Mark an event as completed
     */
    private void markEventDone(WorkEvent event) {
        markEventComplete(event);
    }

    /**
     * Mark an event as complete with 24-hour deletion timer
     */
    private void markEventComplete(WorkEvent event) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", new Date());
        
        db.collection(getUserWorkViewCollection()).document(event.getId())
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              // Update contract last visit if it's a contract event
              if ("contract".equals(event.getEventType())) {
                  updateContractLastVisit(event.getEventId(), event.getUserName());
              }
              Toast.makeText(this, "Event marked as completed! Will be deleted in 24 hours.", Toast.LENGTH_SHORT).show();
              loadEventsForDate(selectedDate);
              
              // Schedule deletion after 24 hours
              scheduleEventDeletion(event.getId(), 24 * 60 * 60 * 1000); // 24 hours in milliseconds
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error updating event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Mark an event as scheduled (revert from completed)
     */
    private void markEventScheduled(WorkEvent event) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "scheduled");
        updates.put("completedAt", null);
        
        db.collection(getUserWorkViewCollection()).document(event.getId())
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              Toast.makeText(this, "Event marked as scheduled!", Toast.LENGTH_SHORT).show();
              loadEventsForDate(selectedDate);
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error updating event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }

    /**
     * Schedule event deletion after specified delay
     */
    private void scheduleEventDeletion(String eventId, long delayMillis) {
        new android.os.Handler().postDelayed(() -> {
            // Delete the event after the delay
            db.collection(getUserWorkViewCollection()).document(eventId)
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
        try {
            // Parse event time to get notification time (30 minutes before)
            String eventTime = event.getTime();
            String eventDate = event.getDate();
            
            // Parse the event date and time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            
            Date eventDateTime = dateFormat.parse(eventDate);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(eventDateTime);
            
            // Parse time (HH:mm format)
            String[] timeParts = eventTime.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            
            // Calculate notification time (30 minutes before)
            calendar.add(Calendar.MINUTE, -30);
            Date notificationTime = calendar.getTime();
            
            // Check if notification time is in the future
            if (notificationTime.after(new Date())) {
                long delayMillis = notificationTime.getTime() - new Date().getTime();
                
                new android.os.Handler().postDelayed(() -> {
                    // Send notification
                    sendEventNotification(event);
                }, delayMillis);
                
                Toast.makeText(this, "Notification scheduled for " + event.getEventName() + " at " + eventTime, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error scheduling notification: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
     * Finish a job - delete from database and remove from calendar
     */
    private void finishJob(WorkEvent event) {
        new AlertDialog.Builder(this)
            .setTitle("Finish Job")
            .setMessage("Are you sure you want to finish this job? This will delete it from the database.")
            .setPositiveButton("Yes", (dialog, which) -> {
                // Delete from jobs collection
                db.collection("jobs").document(event.getEventId())
                  .delete()
                  .addOnSuccessListener(aVoid -> {
                      // Delete from work view collection
                      db.collection(getUserWorkViewCollection()).document(event.getId())
                        .delete()
                        .addOnSuccessListener(aVoid2 -> {
                            Toast.makeText(this, "Job finished and removed!", Toast.LENGTH_SHORT).show();
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
     * Update contract last visit and remove from calendar
     */
    private void updateContractLastVisitAndRemove(WorkEvent event) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        String today = sdf.format(new Date());
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", today);
        
        db.collection("contracts").document(event.getEventId())
          .update(updates)
          .addOnSuccessListener(aVoid -> {
              // Remove from work view collection
              db.collection(getUserWorkViewCollection()).document(event.getId())
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
        for (String timeSlot : TIME_SLOTS) {
            // Create time slot view
            LinearLayout timeSlotView = new LinearLayout(this);
            timeSlotView.setOrientation(LinearLayout.VERTICAL);
            timeSlotView.setPadding(16, 16, 16, 16);
            timeSlotView.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
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
            timeLabel.setText(timeSlot);
            timeLabel.setTextSize(18);
            timeLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            timeLabel.setTextColor(android.graphics.Color.parseColor("#2196F3"));
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
            noEventsText.setTextColor(android.graphics.Color.GRAY);
            noEventsText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            eventsContainer.addView(noEventsText);

            // Add click listener to add event
            timeSlotView.setOnClickListener(v -> {
                showAddEventDialogForTime(timeSlot);
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
                noEventsText.setTextColor(android.graphics.Color.GRAY);
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
            for (int i = 0; i < timeSlotsLayout.getChildCount(); i++) {
                LinearLayout timeSlotView = (LinearLayout) timeSlotsLayout.getChildAt(i);
                TextView timeLabel = (TextView) timeSlotView.getChildAt(0);
                
                if (timeLabel.getText().equals(eventTime)) {
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
        
        new AlertDialog.Builder(this)
            .setTitle("Add Event at " + timeSlot)
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
     * Show add contract dialog for specific time
     */
    private void showAddContractDialogForTime(String timeSlot) {
        if ("kristine".equalsIgnoreCase(userName)) {
            showKristineContractSelectionDialogForTime(timeSlot);
        } else {
            showUserContractSelectionDialogForTime(userName, timeSlot);
        }
    }

    /**
     * Show contract selection dialog for regular users with time
     */
    private void showUserContractSelectionDialogForTime(String user, String timeSlot) {
        String collectionName = user + " Contracts";
        
        db.collection(collectionName).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
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
                    Toast.makeText(this, "No contracts found in " + collectionName + ". Please add contracts first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Use the improved contract selection dialog
                showContractSelectionDialogWithSearchForTime(contractNames, contractIds, contractAddresses, collectionName, timeSlot, user);
            } else {
                Toast.makeText(this, "Error loading contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Show contract selection dialog for Kristine with time
     */
    private void showKristineContractSelectionDialogForTime(String timeSlot) {
        String[] users = {"Ian", "James", "Kristine"};
        
        new AlertDialog.Builder(this)
            .setTitle("Select User's Contracts for " + timeSlot)
            .setItems(users, (dialog, which) -> {
                String selectedUser = users[which];
                showUserContractSelectionDialogForTime(selectedUser, timeSlot);
            })
            .show();
    }

    /**
     * Show contract selection dialog with search functionality for specific time
     */
    private void showContractSelectionDialogWithSearchForTime(List<String> contractNames, List<String> contractIds, 
                                                           List<String> contractAddresses, String collectionName, String timeSlot, String user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Contract for " + timeSlot + " from " + collectionName);

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

        // Handle item click with better error handling
        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                String selectedContract = adapter.getItem(position);
                if (selectedContract != null) {
                    // Find the original index in the contractIds list
                    int originalIndex = contractNames.indexOf(selectedContract);
                    if (originalIndex >= 0 && originalIndex < contractIds.size()) {
                        String selectedContractId = contractIds.get(originalIndex);
                        String contractName = adapter.getContractName(position);
                        createWorkEvent("contract", selectedContractId, contractName, timeSlot, user);
                        builder.create().dismiss();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error selecting contract: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        builder.setView(dialogView);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        
        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Focus on search bar for immediate typing
        searchBar.requestFocus();
    }

    /**
     * Show add job dialog for specific time
     */
    private void showAddJobDialogForTime(String timeSlot) {
        if ("kristine".equalsIgnoreCase(userName)) {
            String[] users = {"Ian", "James", "Kristine"};
            
            new AlertDialog.Builder(this)
                .setTitle("Select User's Work View for " + timeSlot)
                .setItems(users, (dialog, which) -> {
                    String selectedUser = users[which];
                    openAddJobActivityForTime(selectedUser, timeSlot);
                })
                .show();
        } else {
            openAddJobActivityForTime(userName, timeSlot);
        }
    }

    /**
     * Open AddJobFromCalendarActivity with target user and time
     */
    private void openAddJobActivityForTime(String targetUser, String timeSlot) {
        Intent intent = new Intent(this, AddJobFromCalendarActivity.class);
        intent.putExtra("USER_NAME", targetUser);
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
     * Create notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "work_events",
                "Work Events",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for upcoming work events");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Send notification for upcoming events with route option
     */
    private void sendEventNotification(WorkEvent event) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "work_events")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming Work Event")
            .setContentText(event.getEventName() + " at " + event.getTime())
            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                .bigText(event.getEventName() + " at " + event.getTime() + "\n📍 " + (event.getAddress() != null ? event.getAddress() : "No address")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true);

        // Create intent for opening the app
        Intent intent = new Intent(this, WorkViewActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Create intent for opening maps with route
        Intent mapIntent = new Intent(Intent.ACTION_VIEW);
        if (event.getAddress() != null && !event.getAddress().isEmpty() && !event.getAddress().equals("N/A")) {
            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(event.getAddress()));
            mapIntent.setData(gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
        }
        android.app.PendingIntent mapPendingIntent = android.app.PendingIntent.getActivity(this, 1, mapIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);
        builder.addAction(android.R.drawable.ic_dialog_map, "Route", mapPendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(event.getId().hashCode(), builder.build());
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
        
        // Checkbox click listener
        completeCheckBox.setOnClickListener(v -> {
            if (completeCheckBox.isChecked()) {
                markEventComplete(event);
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
        eventNameView.setText(event.getEventName());
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

        eventView.addView(completeCheckBox);
        eventView.addView(eventTypeView);
        eventView.addView(eventDetails);
        return eventView;
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
     * Update contract last visit date with proper format
     */
    private void updateContractLastVisit(String contractId, String userName) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        String today = sdf.format(new Date());
        
        String collectionName = userName + " Contracts";
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", today);
        
        db.collection(collectionName).document(contractId)
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
                // Delete from Firebase
                String userCollection = userName.toLowerCase() + "_workview";
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