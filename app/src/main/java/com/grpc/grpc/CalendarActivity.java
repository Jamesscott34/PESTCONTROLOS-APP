/**
 * CalendarActivity.java
 *
 * This activity provides a calendar-based interface for managing events.
 * Users can select a date, view existing events, and add new events with
 * details including event name, address, issue, and time. Events are stored
 * in a local SQLite database using the `ReportDatabaseHelper` class.
 *
 * Features:
 * - Calendar view for date selection.
 * - Event list display based on the selected date.
 * - Adding events with custom details.
 * - Return to the main screen from the calendar view.
 */

package com.grpc.grpc;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * CalendarActivity manages the calendar-based user interface for event management.
 * Users can select a date, view associated events, and add new events.
 */
public class CalendarActivity extends AppCompatActivity {

    // UI components for interacting with the calendar and events
    private CalendarView calendarView;        // Displays the calendar for date selection
    private TextView textViewSelectedDate;    // Shows the currently selected date
    private Button buttonAddEvent;            // Button to add a new event
    private Button buttonback;                // Button to return to the main activity
    private ListView listViewEvents;          // Displays the list of events for the selected date

    // Data and utilities
    private ArrayList<String> eventList;      // Holds the list of events for the selected date
    private ArrayAdapter<String> adapter;     // Adapter to populate the ListView with events
    private ReportDatabaseHelper dbHelper;    // Database helper for storing and retrieving events
    private String selectedDate;              // Currently selected date in the calendar

    /**
     * Called when the activity is created. Initializes the UI components and
     * sets up event listeners for date selection, adding events, and navigation.
     *
     * @param savedInstanceState If the activity is being re-initialized after being previously shut down,
     *                           this bundle contains the data it most recently supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar); // Set the XML layout for this activity

        // Initialize UI components
        calendarView = findViewById(R.id.calendarView);
        textViewSelectedDate = findViewById(R.id.textViewSelectedDate);
        buttonAddEvent = findViewById(R.id.buttonAddEvent);
        buttonback = findViewById(R.id.buttonback);
        listViewEvents = findViewById(R.id.listViewEvents);

        // Initialize database helper and list components
        dbHelper = new ReportDatabaseHelper(this);
        eventList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, eventList);
        listViewEvents.setAdapter(adapter);

        // Set the initial selected date to today
        Calendar calendar = Calendar.getInstance();
        selectedDate = calendar.get(Calendar.DAY_OF_MONTH) + "-" +
                (calendar.get(Calendar.MONTH) + 1) + "-" +
                calendar.get(Calendar.YEAR);
        textViewSelectedDate.setText("Selected Date: " + selectedDate);

        // Load events for today's date from the database
        loadEventsForDate(selectedDate);

        // Set a listener for date selection changes in the CalendarView
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = dayOfMonth + "-" + (month + 1) + "-" + year;
            textViewSelectedDate.setText("Selected Date: " + selectedDate);
            loadEventsForDate(selectedDate);
        });

        // Add event button click listener to open the event dialog
        buttonAddEvent.setOnClickListener(view -> {
            showAddEventDialog();
        });

        // Return to the main activity when the back button is clicked
        buttonback.setOnClickListener(view -> {
            Intent intent = new Intent(CalendarActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Displays a dialog for the user to add a new event with details such as
     * event name, address, issue, and time. The event is stored in the database
     * if the user provides a name and time.
     */
    private void showAddEventDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Event");

        // Create a vertical layout for input fields
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Create input fields for event details
        final EditText inputEventName = new EditText(this);
        inputEventName.setHint("Enter Name");
        layout.addView(inputEventName);

        final EditText inputAddress = new EditText(this);
        inputAddress.setHint("Enter Address");
        layout.addView(inputAddress);

        final EditText inputIssue = new EditText(this);
        inputIssue.setHint("Enter Issue");
        layout.addView(inputIssue);

        final EditText inputTime = new EditText(this);
        inputTime.setHint("Enter Time (HH:mm)");
        layout.addView(inputTime);

        // Attach the input layout to the dialog
        builder.setView(layout);

        // Define actions for the positive button (Add Event)
        builder.setPositiveButton("Add Event", (dialog, which) -> {
            String eventName = inputEventName.getText().toString();
            String address = inputAddress.getText().toString();
            String issue = inputIssue.getText().toString();
            String time = inputTime.getText().toString();

            // Validate input before saving the event
            if (!eventName.isEmpty() && !time.isEmpty()) {
                String eventDetails = "Event: " + eventName +
                        "\nAddress: " + address +
                        "\nIssue: " + issue +
                        "\nTime: " + time;
                // Save event details into the database
                dbHelper.insertEvent(selectedDate, eventDetails);
                loadEventsForDate(selectedDate);
            } else {
                Toast.makeText(this, "Event Name and Time are required!", Toast.LENGTH_SHORT).show();
            }
        });

        // Define actions for the negative button (Cancel)
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Loads and displays all events for the specified date from the database.
     * Clears the existing event list and repopulates it with the new data.
     *
     * @param date The date for which events should be loaded.
     */
    private void loadEventsForDate(String date) {
        eventList.clear(); // Clear existing events
        eventList.addAll(dbHelper.getEventsByDate(date)); // Fetch and add new events
        adapter.notifyDataSetChanged(); // Update the ListView with the new data
    }
}
