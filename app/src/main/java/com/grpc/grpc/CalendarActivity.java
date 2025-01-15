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
        selectedDate = formatDate(calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR));
        textViewSelectedDate.setText("Selected Date: " + selectedDate);

        loadEventsForDate(selectedDate);

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = formatDate(dayOfMonth, month + 1, year);
            textViewSelectedDate.setText("Selected Date: " + selectedDate);
            loadEventsForDate(selectedDate);
        });

        buttonAddEvent.setOnClickListener(view -> showAddEventDialog());

        buttonback.setOnClickListener(view -> {
            Intent intent = new Intent(CalendarActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        listViewEvents.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = eventList.get(position);
            if (selectedItem.contains("Address:")) {
                String address = selectedItem.substring(selectedItem.indexOf("Address:") + 8).split("\n")[0];
                Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                startActivity(mapIntent);
            }
        });

        listViewEvents.setOnItemLongClickListener((parent, view, position, id) -> {
            String selectedItem = eventList.get(position);
            String eventName = selectedItem.split("\n")[0].replace("Name: ", "");

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
            return true;
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

        final Spinner visitTypeSpinner = new Spinner(this);
        ArrayAdapter<String> visitTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Job", "Contract"});
        visitTypeSpinner.setAdapter(visitTypeAdapter);
        layout.addView(visitTypeSpinner);

        final EditText inputVisitCount = new EditText(this);
        inputVisitCount.setHint("Enter Number of Visits");
        inputVisitCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(inputVisitCount);

        builder.setView(layout);

        builder.setPositiveButton("Add Event", (dialog, which) -> {
            String eventName = inputEventName.getText().toString();
            String address = inputAddress.getText().toString();
            String issue = inputIssue.getText().toString();
            String time = inputTime.getText().toString();
            String visitType = visitTypeSpinner.getSelectedItem().toString();

            int visitCount;
            try {
                visitCount = Integer.parseInt(inputVisitCount.getText().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number of visits", Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<String> visitDates = generateVisitSchedule(selectedDate, visitType, visitCount);

            // Save the main event
            dbHelper.insertEvent(selectedDate, "Name: " + eventName + "\nAddress: " + address + "\nIssue: " + issue + "\nTime: " + time + "\nVisit Type: " + visitType + "\nVisits: " + visitCount);

            // Save additional visit dates (skip the first visit date, already saved)
            for (int i = 1; i < visitDates.size(); i++) {
                String visitDate = visitDates.get(i);
                dbHelper.insertEvent(visitDate, "Name: " + eventName + "\nAddress: " + address + "\nIssue: " + issue + "\nTime: " + time + "\nVisit Type: " + visitType);
            }

            // Refresh the event list for the selected date
            loadEventsForDate(selectedDate);

            // Notify the user
            Toast.makeText(this, "Event added on " + selectedDate, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private ArrayList<String> generateVisitSchedule(String startDate, String visitType, int visitCount) {
        ArrayList<String> dates = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            calendar.setTime(sdf.parse(startDate));
        } catch (Exception e) {
            e.printStackTrace();
        }

        int weeksBetweenVisits;

        switch (visitType) {
            case "Job":
                weeksBetweenVisits = 1; // Weekly visits
                break;
            case "Contract":
                // Set weeks between visits based on the number of visits
                if (visitCount == 12) {
                    weeksBetweenVisits = 4;
                } else if (visitCount == 8) {
                    weeksBetweenVisits = 6;
                } else if (visitCount == 6) {
                    weeksBetweenVisits = 8;
                } else if (visitCount == 4) {
                    weeksBetweenVisits = 12;
                } else {
                    weeksBetweenVisits = 1; // Default to weekly if unspecified
                }
                break;
            default:
                weeksBetweenVisits = 1; // Default to weekly
        }

        // Add visits
        for (int i = 0; i < visitCount; i++) {
            dates.add(formatDate(calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR)));
            calendar.add(Calendar.WEEK_OF_YEAR, weeksBetweenVisits); // Increment by the defined interval
        }

        return dates;
    }


    private void loadEventsForDate(String date) {
        eventList.clear();
        eventList.addAll(dbHelper.getEventsByDate(date));
        eventList.sort(null);
        adapter.notifyDataSetChanged();
    }

    private String formatDate(int day, int month, int year) {
        return day + "-" + month + "-" + year;
    }
    private void showDatesDialog(String eventName, ArrayList<String> dates) {
        Collections.sort(dates, (d1, d2) -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                return sdf.parse(d1).compareTo(sdf.parse(d2));
            } catch (Exception e) {
                return 0;
            }
        });

        StringBuilder dateList = new StringBuilder("Dates for " + eventName + ":\n");
        for (String date : dates) {
            dateList.append(date).append("\n");
        }

        AlertDialog.Builder dateDialog = new AlertDialog.Builder(this);
        dateDialog.setTitle("Dates for " + eventName);
        dateDialog.setMessage(dateList.toString());
        dateDialog.setPositiveButton("OK", null);
        dateDialog.show();
    }
}
