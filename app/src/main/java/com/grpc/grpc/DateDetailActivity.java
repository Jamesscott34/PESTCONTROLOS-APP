package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class DateDetailActivity extends AppCompatActivity {

    private TextView textViewDateDetails;
    private ListView listViewDateDetails;
    private Button buttonReturn;
    private ArrayAdapter<String> adapter;
    private ArrayList<String> eventDetailsList;
    private ReportDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_date_details);

        // Initialize views
        initializeViews();

        // Initialize database helper and data list
        dbHelper = new ReportDatabaseHelper(this);
        eventDetailsList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, eventDetailsList);
        listViewDateDetails.setAdapter(adapter);

        // Get the selected date from the Intent
        String selectedDate = getIntent().getStringExtra("selectedDate");
        if (selectedDate != null) {
            textViewDateDetails.setText("Details for " + selectedDate);
            loadEventsForDate(selectedDate);
        } else {
            textViewDateDetails.setText("No date selected.");
        }

        // Return button functionality
        buttonReturn.setOnClickListener(v -> finish());
    }

    private void initializeViews() {
        textViewDateDetails = findViewById(R.id.textViewDateDetails);
        listViewDateDetails = findViewById(R.id.listViewDateDetails);
        buttonReturn = findViewById(R.id.buttonReturn);
    }

    private void loadEventsForDate(String date) {
        eventDetailsList.clear();

        // Fetch events from the database
        ArrayList<String> events = dbHelper.getEventsByDate(date);
        if (events.isEmpty()) {
            Toast.makeText(this, "No events found for this date.", Toast.LENGTH_SHORT).show();
        } else {
            eventDetailsList.addAll(events);
        }

        adapter.notifyDataSetChanged();
    }
}