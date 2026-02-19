/**
 * AddFollowUpActivity.java
 * 
 * Activity for creating follow-up events from the calendar view.
 * Allows users to add follow-ups to existing jobs and contracts
 * with date and time selection.
 * 
 * Author: GRPC
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 */

package com.grpc.grpc;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AddFollowUpActivity extends AppCompatActivity {

    private TextView selectedDateText, selectedTimeText;
    private Button selectDateButton, selectTimeButton, saveFollowUpButton, cancelButton;
    private FirebaseFirestore db;
    private String userName;
    private String originalEventId;
    private String eventType;
    private String eventName;
    private long selectedDate;
    private String selectedTime = "09:00";
    private Calendar calendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_follow_up);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Get data from intent
        userName = getIntent().getStringExtra("USER_NAME");
        originalEventId = getIntent().getStringExtra("ORIGINAL_EVENT_ID");
        eventType = getIntent().getStringExtra("EVENT_TYPE");
        eventName = getIntent().getStringExtra("EVENT_NAME");
        selectedDate = getIntent().getLongExtra("SELECTED_DATE", System.currentTimeMillis());

        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize calendar
        calendar = Calendar.getInstance();
        calendar.setTimeInMillis(selectedDate);

        initializeUIComponents();
        setupButtonListeners();
        updateDisplay();
    }

    /**
     * Initialize UI components
     */
    private void initializeUIComponents() {
        selectedDateText = findViewById(R.id.selectedDateText);
        selectedTimeText = findViewById(R.id.selectedTimeText);
        selectDateButton = findViewById(R.id.selectDateButton);
        selectTimeButton = findViewById(R.id.selectTimeButton);
        saveFollowUpButton = findViewById(R.id.saveFollowUpButton);
        cancelButton = findViewById(R.id.cancelButton);
    }

    /**
     * Set up button click listeners
     */
    private void setupButtonListeners() {
        selectDateButton.setOnClickListener(v -> showDatePicker());
        selectTimeButton.setOnClickListener(v -> showTimePicker());
        saveFollowUpButton.setOnClickListener(v -> saveFollowUp());
        cancelButton.setOnClickListener(v -> finish());
    }

    /**
     * Show date picker dialog
     */
    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                calendar.set(Calendar.YEAR, year);
                calendar.set(Calendar.MONTH, month);
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                selectedDate = calendar.getTimeInMillis();
                updateDisplay();
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * Show time picker dialog
     */
    private void showTimePicker() {
        String[] timeSlots = {
            "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
            "12:00", "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30",
            "16:00", "16:30", "17:00", "17:30"
        };

        new AlertDialog.Builder(this)
            .setTitle("Select Time")
            .setItems(timeSlots, (dialog, which) -> {
                selectedTime = timeSlots[which];
                updateDisplay();
            })
            .show();
    }

    /**
     * Update the display with selected date and time
     */
    private void updateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault());
        selectedDateText.setText("Date: " + dateFormat.format(new Date(selectedDate)));
        selectedTimeText.setText("Time: " + selectedTime);
    }

    /**
     * Save the follow-up event
     */
    private void saveFollowUp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = sdf.format(new Date(selectedDate));
        
        Map<String, Object> followUpEvent = new HashMap<>();
        followUpEvent.put("userName", userName);
        followUpEvent.put("eventType", "followup");
        followUpEvent.put("eventId", originalEventId);
        followUpEvent.put("eventName", eventName + " - Follow-up");
        followUpEvent.put("date", dateString);
        followUpEvent.put("time", selectedTime);
        followUpEvent.put("status", "scheduled");
        followUpEvent.put("createdAt", new Date());

        // Save to user-specific work view collection
        String userCollection = userName.toLowerCase() + "_workview";
        db.collection(userCollection)
          .add(followUpEvent)
          .addOnSuccessListener(documentReference -> {
              Toast.makeText(this, "Follow-up scheduled successfully!", Toast.LENGTH_SHORT).show();
              finish();
          })
          .addOnFailureListener(e -> {
              Toast.makeText(this, "Error scheduling follow-up: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          });
    }
} 