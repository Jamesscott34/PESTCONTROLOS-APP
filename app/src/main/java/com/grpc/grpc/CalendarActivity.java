package com.grpc.grpc;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Calendar;

public class CalendarActivity extends AppCompatActivity {

    CalendarView calendarView;
    TextView textViewSelectedDate;
    Button buttonAddEvent;
    Button buttonback;
    ListView listViewEvents;

    ArrayList<String> eventList;
    ArrayAdapter<String> adapter;
    ReportDatabaseHelper dbHelper;
    String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        calendarView = findViewById(R.id.calendarView);
        textViewSelectedDate = findViewById(R.id.textViewSelectedDate);
        buttonAddEvent = findViewById(R.id.buttonAddEvent);
        buttonback = findViewById(R.id.buttonback);
        listViewEvents = findViewById(R.id.listViewEvents);

        dbHelper = new ReportDatabaseHelper(this);
        eventList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, eventList);
        listViewEvents.setAdapter(adapter);

        Calendar calendar = Calendar.getInstance();
        selectedDate = calendar.get(Calendar.YEAR) + "-" + (calendar.get(Calendar.MONTH) + 1) + "-" + calendar.get(Calendar.DAY_OF_MONTH);
        textViewSelectedDate.setText("Selected Date: " + selectedDate);

        loadEventsForDate(selectedDate);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = dayOfMonth + "-" + (month + 1) + "-" + year;
            textViewSelectedDate.setText("Selected Date: " + selectedDate);
            loadEventsForDate(selectedDate);
        });

        buttonAddEvent.setOnClickListener(view -> {
            showAddEventDialog();
        });

        buttonback.setOnClickListener(view -> {
            Intent intent = new Intent(CalendarActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });


    }

    private void showAddEventDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Event");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

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

        builder.setView(layout);

        builder.setPositiveButton("Add Event", (dialog, which) -> {
            String eventName = inputEventName.getText().toString();
            String address = inputAddress.getText().toString();
            String issue = inputIssue.getText().toString();
            String time = inputTime.getText().toString();

            if (!eventName.isEmpty() && !time.isEmpty()) {
                String eventDetails = "Event: " + eventName + "\nAddress: " + address + "\nIssue: " + issue + "\nTime: " + time;
                dbHelper.insertEvent(selectedDate, eventDetails);
                loadEventsForDate(selectedDate);
            } else {
                Toast.makeText(this, "Event Name and Time are required!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }



    private void loadEventsForDate(String date) {
        eventList.clear();
        eventList.addAll(dbHelper.getEventsByDate(date));
        adapter.notifyDataSetChanged();
    }

}
