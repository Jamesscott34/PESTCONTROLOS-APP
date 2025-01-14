/**
 * MainActivity.java
 *
 * This is the main entry point for the GRPC Android application.
 * The app serves as a reporting tool, allowing users to create, view, and manage reports,
 * as well as access a calendar for scheduling purposes.
 *
 * Key Features:
 * - Create new reports using a dedicated activity.
 * - View existing reports stored in the local database.
 * - Access a calendar for scheduling and date-related tasks.
 *
 * Each button in the main activity navigates the user to a specific feature screen.
 */

package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity handles the primary navigation of the application.
 * It allows the user to navigate to three main sections:
 * - Report Creation
 * - Report Viewing
 * - Calendar Management
 */
public class MainActivity extends AppCompatActivity {

    // UI components for navigation
    private Button reportButton;          // Button to navigate to report creation screen
    private Button reportViewButton;      // Button to navigate to report viewing screen
    private Button buttonOpenCalendar;    // Button to navigate to the calendar activity

    /**
     * Called when the activity is first created.
     * Initializes the UI and sets up button click listeners for navigation.
     *
     * @param savedInstanceState If the activity is being re-initialized after being previously shut down,
     *                           this bundle contains the data it most recently supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Set the layout file associated with this activity

        // Initialize UI components and link them to layout elements
        reportButton = findViewById(R.id.ReportButton);
        reportViewButton = findViewById(R.id.ReportViewButton);
        buttonOpenCalendar = findViewById(R.id.buttonOpenCalendar);

        // Set click listeners for each button, directing the user to the appropriate activity
        reportButton.setOnClickListener(view ->
                startActivity(new Intent(this, ReportActivity.class)));  // Launch report creation screen

        reportViewButton.setOnClickListener(view ->
                startActivity(new Intent(this, ReportViewActivity.class)));  // Launch report viewing screen

        buttonOpenCalendar.setOnClickListener(view ->
                startActivity(new Intent(this, CalendarActivity.class)));  // Launch calendar screen
    }
}
