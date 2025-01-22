package com.grpc.grpc;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.*;

public class CalendarActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private TextView textViewSelectedDate;
    private Button buttonAddEvent, buttonBack;
    private ListView listViewEvents;

    private ArrayList<String> eventList;
    private ArrayAdapter<String> adapter;
    private ReportDatabaseHelper dbHelper;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        initializeViews();
        setupListeners();
        setupEventList();

        Calendar calendar = Calendar.getInstance();
        selectedDate = formatDate(calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));
        textViewSelectedDate.setText("Selected Date: " + selectedDate);
        loadEventsForDate(selectedDate);
    }

    private void initializeViews() {
        calendarView = findViewById(R.id.calendarView);
        textViewSelectedDate = findViewById(R.id.textViewSelectedDate);
        buttonAddEvent = findViewById(R.id.buttonAddEvent);
        buttonBack = findViewById(R.id.buttonback);
        listViewEvents = findViewById(R.id.listViewEvents);

        dbHelper = new ReportDatabaseHelper(this);
        eventList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, eventList);
        listViewEvents.setAdapter(adapter);
    }

    private void setupListeners() {
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = formatDate(dayOfMonth, month + 1, year);
            textViewSelectedDate.setText("Selected Date: " + selectedDate);
            loadEventsForDate(selectedDate);
        });

        calendarView.setOnLongClickListener(v -> {
            openDateDetailsActivity(selectedDate);
            return true;
        });

        buttonAddEvent.setOnClickListener(view -> showAddEventDialog());

        buttonBack.setOnClickListener(view -> {
            startActivity(new Intent(CalendarActivity.this, MainActivity.class));
            finish();
        });

        listViewEvents.setOnItemClickListener((parent, view, position, id) -> openAddressInMaps(eventList.get(position)));

        listViewEvents.setOnItemLongClickListener((parent, view, position, id) -> {
            String selectedItem = eventList.get(position);
            String eventName = extractEventName(selectedItem);
            showOptionsDialog(eventName);
            return true;
        });
    }

    private void setupEventList() {
        eventList.clear();
        adapter.notifyDataSetChanged();
    }

    private void showAddEventDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Event");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText inputEventName = createEditText("Enter Name");
        EditText inputAddress = createEditText("Enter Address");
        EditText inputIssue = createEditText("Enter Issue");
        EditText inputTime = createEditText("Enter Time (HH:mm)");
        Spinner visitTypeSpinner = createSpinner(new String[]{"Job", "Contract"});
        EditText inputVisitCount = createEditText("Enter Number of Visits", InputType.TYPE_CLASS_NUMBER);

        layout.addView(inputEventName);
        layout.addView(inputAddress);
        layout.addView(inputIssue);
        layout.addView(inputTime);
        layout.addView(visitTypeSpinner);
        layout.addView(inputVisitCount);

        builder.setView(layout);

        builder.setPositiveButton("Add Event", (dialog, which) -> {
            try {
                String eventName = inputEventName.getText().toString();
                String address = inputAddress.getText().toString();
                String issue = inputIssue.getText().toString();
                String time = inputTime.getText().toString();
                String visitType = visitTypeSpinner.getSelectedItem().toString();
                int visitCount = Integer.parseInt(inputVisitCount.getText().toString());

                saveEvent(eventName, address, issue, time, visitType, visitCount);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number of visits", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private EditText createEditText(String hint) {
        return createEditText(hint, InputType.TYPE_CLASS_TEXT);
    }

    private EditText createEditText(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        return editText;
    }

    private Spinner createSpinner(String[] options) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void saveEvent(String eventName, String address, String issue, String time, String visitType, int visitCount) {
        ArrayList<String> visitDates = generateVisitSchedule(selectedDate, visitType, visitCount);

        dbHelper.insertEvent(selectedDate, formatEventDetails(eventName, address, issue, time, visitType, visitCount));

        for (int i = 1; i < visitDates.size(); i++) {
            dbHelper.insertEvent(visitDates.get(i), formatEventDetails(eventName, address, issue, time, visitType, 0));
        }

        loadEventsForDate(selectedDate);
        Toast.makeText(this, "Event added on " + selectedDate, Toast.LENGTH_SHORT).show();
    }

    private String formatEventDetails(String name, String address, String issue, String time, String visitType, int visits) {
        return "Name: " + name + "\nAddress: " + address + "\nIssue: " + issue + "\nTime: " + time + "\nVisit Type: " + visitType + "\nVisits: " + visits;
    }

    private ArrayList<String> generateVisitSchedule(String startDate, String visitType, int visitCount) {
        ArrayList<String> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();

        try {
            calendar.setTime(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(startDate));
        } catch (Exception e) {
            e.printStackTrace();
        }

        int weeksBetweenVisits = determineWeeksBetweenVisits(visitType, visitCount);

        for (int i = 0; i < visitCount; i++) {
            dates.add(formatDate(calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR)));
            calendar.add(Calendar.WEEK_OF_YEAR, weeksBetweenVisits);
        }

        return dates;
    }

    private int determineWeeksBetweenVisits(String visitType, int visitCount) {
        if ("Job".equals(visitType)) return 1;

        switch (visitCount) {
            case 12: return 4;
            case 8: return 6;
            case 6: return 8;
            case 4: return 12;
            default: return 1;
        }
    }

    private void loadEventsForDate(String date) {
        eventList.clear();
        eventList.addAll(dbHelper.getEventsByDate(date));
        adapter.notifyDataSetChanged();
    }

    private void openAddressInMaps(String eventDetails) {
        if (eventDetails.contains("Address:")) {
            String address = eventDetails.substring(eventDetails.indexOf("Address:") + 8).split("\n")[0];
            Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        }
    }



    private void showOptionsDialog(String eventName) {
        AlertDialog.Builder optionsDialog = new AlertDialog.Builder(this);
        optionsDialog.setTitle("Options");
        optionsDialog.setItems(new CharSequence[]{"View Dates", "Delete"}, (dialog, which) -> {
            if (which == 0) {
                ArrayList<String> dates = dbHelper.getDatesByName(eventName);
                if (dates.isEmpty()) {
                    Toast.makeText(this, "No dates found for " + eventName, Toast.LENGTH_SHORT).show();
                } else {
                    showDatesDialog(eventName, dates);
                }
            } else if (which == 1) {
                dbHelper.deleteEventsByName(eventName);
                loadEventsForDate(selectedDate);
                Toast.makeText(this, "All visits deleted for " + eventName, Toast.LENGTH_SHORT).show();
            }
        });
        optionsDialog.show();
    }

    private void showDatesDialog(String eventName, ArrayList<String> dates) {
        Collections.sort(dates, (d1, d2) -> {
            try {
                return new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(d1).compareTo(
                        new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(d2));
            } catch (Exception e) {
                return 0;
            }
        });

        StringBuilder dateList = new StringBuilder("Dates for " + eventName + ":\n");
        for (String date : dates) {
            dateList.append(date).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Dates for " + eventName)
                .setMessage(dateList.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void openDateDetailsActivity(String selectedDate) {
        Intent intent = new Intent(CalendarActivity.this, DateDetailActivity.class);
        intent.putExtra("selectedDate", selectedDate);
        startActivity(intent);
    }

    private String formatDate(int day, int month, int year) {
        return day + "-" + month + "-" + year;
    }

    private String extractEventName(String eventDetails) {
        return eventDetails.split("\n")[0].replace("Name: ", "");
    }
}
