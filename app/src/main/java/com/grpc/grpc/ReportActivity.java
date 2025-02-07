/**
 * ReportActivity.java
 *
 * This activity allows the user to create a company report by filling out multiple input fields.
 * The user can also select multiple images to include in the report and save the report data
 * to a local SQLite database using the `ReportDatabaseHelper`. Once saved, the report can also
 * be exported as a PDF using the `PDFReportGenerator`.
 *
 * Features:
 * - Input fields for report details such as name, address, date, visit type, and more.
 * - Image selection for including visual data in the report.
 * - Data persistence using SQLite database.
 * - PDF generation with optional images.
 * - Return navigation to the MainActivity.
 */

package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ReportActivity allows users to fill in a report form, select images, and save the data locally
 * in a SQLite database. It also supports generating a PDF with the report data.
 */
public class ReportActivity extends AppCompatActivity {

    // Input fields for report details
    private EditText nameInput, addressInput, dateInput, visitTypeInput,
            siteInspectionInput, recommendationsInput, followUpInput,
            prepInput, techInput;

    private String userName;

    // Buttons for actions
    private Button saveButton, backButton, selectImageButton;

    // List to hold the selected image URIs for the report
    private List<Uri> selectedImageUris = new ArrayList<>();

    /**
     * Initializes the activity, sets up the UI components, and defines button actions.
     *
     * @param savedInstanceState If the activity is re-initialized after being previously shut down,
     *                           this bundle contains the data it most recently supplied.
     */
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);


        // Retrieve the user's name passed from ContractsActivity
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize input fields
        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        dateInput = findViewById(R.id.dateInput);
        visitTypeInput = findViewById(R.id.visitTypeInput);
        siteInspectionInput = findViewById(R.id.siteInspectionInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        followUpInput = findViewById(R.id.followUpInput);
        prepInput = findViewById(R.id.prepInput);
        techInput = findViewById(R.id.techInput);

        // Initialize buttons
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        selectImageButton = findViewById(R.id.selectImageButton);

        // Set current date in the date field
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        dateInput.setText(sdf.format(new Date()));

        // Initialize the database helper
        ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Set up button actions
        selectImageButton.setOnClickListener(view -> openImageSelector());
        saveButton.setOnClickListener(view -> {
            saveReport(db); // Keep the save functionality
            clearFields();
            goBackToPreviousActivity(); // Close the current activity
        });
        backButton.setOnClickListener(view -> {
            goBackToPreviousActivity();
        });
    }

    // Function to navigate back to the previous activity
    private void goBackToPreviousActivity() {
        Intent intent = new Intent();
        if (userName != null && !userName.isEmpty()) {
            intent.putExtra("USER_NAME", userName); // Ensure username is passed
        } else {
            intent.putExtra("USER_NAME", "Unknown User"); // Fallback in case username is null
        }
        setResult(RESULT_OK, intent);
        finish();
    }


    // Function to clear all input fields
    private void clearFields() {
        nameInput.setText("");
        addressInput.setText("");
        dateInput.setText(new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(new Date())); // Reset to current date
        visitTypeInput.setText("");
        siteInspectionInput.setText("");
        recommendationsInput.setText("");
        followUpInput.setText("");
        prepInput.setText("");
        techInput.setText("");
        selectedImageUris.clear(); // Clear selected images
    }


    /**
     * Opens the Android system image selector for choosing multiple images.
     */
    private void openImageSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);  // Allow multiple image selection
        startActivityForResult(intent, 1);
    }

    /**
     * Handles the result from the image selection activity.
     * Stores selected image URIs in the list.
     *
     * @param requestCode The request code passed to startActivityForResult.
     * @param resultCode  The result code returned by the activity.
     * @param data        The intent data containing the selected images.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            // Handle multiple image selection
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                selectedImageUris.add(data.getData());
            }
            Toast.makeText(this, selectedImageUris.size() + " images selected!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Saves the report data into the SQLite database and generates a PDF report.
     *
     * @param db The writable SQLiteDatabase instance.
     */
    private void saveReport(SQLiteDatabase db) {
        // Collect input values for the report
        String reportName = nameInput.getText().toString();
        String content = "Premise Name: " + nameInput.getText().toString() +
                "\nAddress: " + addressInput.getText().toString() +
                "\nDate: " + dateInput.getText().toString() +
                "\nVisit Type: " + visitTypeInput.getText().toString() +
                "\nSite Inspection: " + siteInspectionInput.getText().toString() +
                "\nRecommendations: " + recommendationsInput.getText().toString() +
                "\nFollow-Up: " + followUpInput.getText().toString() +
                "\nPrep: " + prepInput.getText().toString() +
                "\nTech: " + techInput.getText().toString();

        // Store values in a ContentValues object for database insertion
        ContentValues values = new ContentValues();
        values.put("name", reportName);
        values.put("address", addressInput.getText().toString());
        values.put("date", dateInput.getText().toString());
        values.put("visit_type", visitTypeInput.getText().toString());
        values.put("site_inspection", siteInspectionInput.getText().toString());
        values.put("recommendations", recommendationsInput.getText().toString());
        values.put("follow_up", followUpInput.getText().toString());
        values.put("prep", prepInput.getText().toString());
        values.put("tech", techInput.getText().toString());

        // Insert the report data into the database
        long newRowId = db.insert("CompanyReports", null, values);
        if (newRowId != -1) {
            Toast.makeText(this, "Company Report Saved Successfully!", Toast.LENGTH_SHORT).show();

            // Generate a PDF report only if the OS version supports it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PDFReportGenerator.generatePDFReport(
                        "Company",
                        reportName,
                        content,
                        this,
                        !selectedImageUris.isEmpty() ? selectedImageUris : null
                );
            }
        } else {
            Toast.makeText(this, "Error Saving Report!", Toast.LENGTH_SHORT).show();
        }
    }
}
