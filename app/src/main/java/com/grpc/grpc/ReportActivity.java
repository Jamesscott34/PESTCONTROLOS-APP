package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;


/**
 * Report Activity for managing and saving client reports.
 * Allows data entry, saving to SQLite, and generating a PDF report.
 */
public class ReportActivity extends AppCompatActivity {

    private EditText nameInput, addressInput, dateInput, visitTypeInput, siteInspectionInput, recommendationsInput, followUpInput, prepInput, techInput;
    private Button saveButton, backButton;
    private List<Uri> imageUris = new ArrayList<>();  // Stores image URIs for PDF generation
    private PDFReportGenerator pdfReportGenerator;
    private ReportDatabaseHelper dbHelper;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // Initialize UI elements
        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        dateInput = findViewById(R.id.dateInput);
        visitTypeInput = findViewById(R.id.visitTypeInput);
        siteInspectionInput = findViewById(R.id.siteInspectionInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        followUpInput = findViewById(R.id.followUpInput);
        prepInput = findViewById(R.id.prepInput);
        techInput = findViewById(R.id.techInput);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);

        // Initialize Database Helper and PDF Generator
        dbHelper = new ReportDatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Pre-fill date with the current date and time
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        dateInput.setText(sdf.format(new Date()));

        /**
         * Save Button Click Listener
         * Saves the report data into the database and generates a PDF report.
         */
        // Inside saveButton click listener
        saveButton.setOnClickListener(view -> {
            String reportName = nameInput.getText().toString();
            String content = generateReportContent();

            ContentValues values = new ContentValues();
            values.put("name", nameInput.getText().toString());
            values.put("address", addressInput.getText().toString());
            values.put("date", dateInput.getText().toString());
            values.put("visit_type", visitTypeInput.getText().toString());
            values.put("site_inspection", siteInspectionInput.getText().toString());
            values.put("recommendations", recommendationsInput.getText().toString());
            values.put("follow_up", followUpInput.getText().toString());
            values.put("prep", prepInput.getText().toString());
            values.put("tech", techInput.getText().toString());

            long newRowId = db.insert("CompanyReports", null, values);

            if (newRowId != -1) {
                Toast.makeText(this, "Report Saved Successfully!", Toast.LENGTH_SHORT).show();

                // Add the event to the system calendar
                addEventToCalendar(
                        nameInput.getText().toString(),
                        addressInput.getText().toString(),
                        siteInspectionInput.getText().toString(),
                        dateInput.getText().toString(),
                        "10:00"  // Default time if not provided
                );
            } else {
                Toast.makeText(this, "Error Saving Report!", Toast.LENGTH_SHORT).show();
            }
        });


        /**
         * Back Button Logic
         * Returns the user to the MainActivity.
         */
        backButton.setOnClickListener(view -> {
            Intent intent = new Intent(ReportActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Generates the report content in a structured format.
     *
     * @return A formatted string containing all the report details.
     */
    private String generateReportContent() {
        return "Premise Name: " + nameInput.getText().toString()
                + "\nAddress: " + addressInput.getText().toString()
                + "\nDate: " + dateInput.getText().toString()
                + "\nVisit Type: " + visitTypeInput.getText().toString()
                + "\nSite Inspection: " + siteInspectionInput.getText().toString()
                + "\nRecommendations: " + recommendationsInput.getText().toString()
                + "\nFollow-Up: " + followUpInput.getText().toString()
                + "\nPrep: " + prepInput.getText().toString()
                + "\nTech: " + techInput.getText().toString();
    }



    private void addEventToCalendar(String name, String address, String issue, String date, String time) {
        long startMillis = 0;
        Calendar beginTime = Calendar.getInstance();

        try {
            // Using SimpleDateFormat for date and time parsing
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
            Date parsedDate = sdf.parse(date + " " + time);

            // Set the parsed date to the Calendar instance
            beginTime.setTime(parsedDate);
            startMillis = beginTime.getTimeInMillis();
        } catch (Exception e) {
            Toast.makeText(this, "Error parsing date/time. Please use format dd-MM-yyyy HH:mm.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare the event data for insertion
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000); // Default duration: 1 hour
        values.put(CalendarContract.Events.TITLE, name);
        values.put(CalendarContract.Events.DESCRIPTION, "Issue: " + issue + "\nAddress: " + address);
        values.put(CalendarContract.Events.CALENDAR_ID, 1); // Calendar ID (default to primary calendar)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        // Insert the event into the system calendar
        try {
            Uri uri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            if (uri != null) {
                Toast.makeText(this, "Event Added to Calendar Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to Add Event to Calendar.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Calendar Permission Denied!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Granted!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Permission Denied! Cannot Add Event to Calendar.", Toast.LENGTH_SHORT).show();
        }
    }



}
