/**
 * ============================================================================
 * GRPest Control Application - Report Creation Activity
 * ============================================================================
 * 
 * BUSINESS OVERVIEW:
 * This activity serves as the primary interface for creating detailed pest control
 * reports. It allows technicians to document their site visits, inspections,
 * recommendations, and follow-up actions in a structured format that can be
 * saved locally and exported as professional PDF documents.
 * 
 * CORE FUNCTIONALITIES:
 * 
 * 1. REPORT DATA ENTRY
 *    - Company name and address information
 *    - Visit date and type classification
 *    - Site inspection findings and observations
 *    - Treatment recommendations and procedures
 *    - Follow-up requirements and scheduling
 *    - Preparation notes and technician details
 * 
 * 2. IMAGE DOCUMENTATION
 *    - Multiple image selection for visual evidence
 *    - Integration with device camera and gallery
 *    - Image preview and management
 *    - Automatic inclusion in generated PDFs
 * 
 * 3. DATA PERSISTENCE
 *    - Local SQLite database storage
 *    - Report history and retrieval
 *    - Data validation and error handling
 *    - Backup and recovery capabilities
 * 
 * 4. PDF GENERATION
 *    - Professional PDF report creation
 *    - Company branding and formatting
 *    - Image integration and layout
 *    - Watermark and footer application
 * 
 * 5. USER EXPERIENCE
 *    - Keyboard handling and input optimization
 *    - Form validation and error messages
 *    - Progress indicators and confirmation
 *    - Navigation and workflow management
 * 
 * TECHNICAL FEATURES:
 * - SQLite database integration for data persistence
 * - PDF generation using iText7 library
 * - Image handling and compression
 * - Theme-aware UI components
 * - Keyboard optimization for mobile devices
 * 
 * USER ROLES & PERMISSIONS:
 * - Technicians: Full report creation and editing
 * - Administrators: Report review and management
 * - All users: Report viewing and PDF export
 * 
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
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
import android.app.AlertDialog;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    // ============================================================================
    // UI COMPONENTS - Input fields for comprehensive report data
    // ============================================================================
    
    // Core company and visit information
    private EditText nameInput, addressInput, dateInput, visitTypeInput;
    
    // Detailed report information
    private EditText siteInspectionInput, recommendationsInput, followUpInput,
            prepInput, techInput;

    // User context and session management
    private String userName;

    // ============================================================================
    // ACTION BUTTONS - User interface controls
    // ============================================================================
    
    private Button saveButton, backButton, selectImageButton;

    // ============================================================================
    // DATA MANAGEMENT - Image and content storage
    // ============================================================================
    
    // List to hold selected image URIs for visual documentation
    private List<Uri> selectedImageUris = new ArrayList<>();

    /**
     * Main entry point of the report creation activity
     * Initializes the user interface, sets up data handling,
     * and configures all input fields and action buttons
     */
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // ============================================================================
        // USER AUTHENTICATION & VALIDATION
        // ============================================================================
        
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ============================================================================
        // EVENT DATA HANDLING - Pre-fill form if coming from WorkView
        // ============================================================================
        
        String eventName = getIntent().getStringExtra("EVENT_NAME");
        String eventType = getIntent().getStringExtra("EVENT_TYPE");
        String eventAddress = getIntent().getStringExtra("EVENT_ADDRESS");
        String eventIssue = getIntent().getStringExtra("EVENT_ISSUE");

        // ============================================================================
        // UI COMPONENT INITIALIZATION
        // ============================================================================
        
        initializeInputFields();
        initializeButtons();
        setupKeyboardHandling();
        setCurrentDate();
        
        // Set up welcome message with user's name
        setupWelcomeMessage();

        // ============================================================================
        // PRE-FILL FORM DATA FROM EVENT
        // ============================================================================
        
        if (eventName != null && !eventName.isEmpty()) {
            nameInput.setText(eventName);
        }
        
        if (eventAddress != null && !eventAddress.isEmpty()) {
            addressInput.setText(eventAddress);
        }
        
        if (eventType != null && !eventType.isEmpty()) {
            String visitType = eventType.equals("contract") ? "Contract Visit" : "Job Visit";
            visitTypeInput.setText(visitType);
        }
        
        if (eventIssue != null && !eventIssue.isEmpty()) {
            // Add issue to site inspection or recommendations
            String currentInspection = siteInspectionInput.getText().toString();
            if (currentInspection.isEmpty()) {
                siteInspectionInput.setText("Issue: " + eventIssue);
            } else {
                siteInspectionInput.setText(currentInspection + "\n\nIssue: " + eventIssue);
            }
        }

        // ============================================================================
        // SAVE BUTTON FUNCTIONALITY
        // ============================================================================
        
        // Save button - stores report data and generates PDF
        saveButton.setOnClickListener(v -> {
            ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            saveReport(db);
        });
    }

    /**
     * Initialize all input fields by finding them in the layout
     * and setting up their basic properties
     */
    private void initializeInputFields() {
        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        dateInput = findViewById(R.id.dateInput);
        visitTypeInput = findViewById(R.id.visitTypeInput);
        siteInspectionInput = findViewById(R.id.siteInspectionInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        followUpInput = findViewById(R.id.followUpInput);
        prepInput = findViewById(R.id.prepInput);
        techInput = findViewById(R.id.techInput);
    }

    /**
     * Set up welcome message with user's name
     */
    private void setupWelcomeMessage() {
        android.widget.TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        }
    }

    /**
     * Initialize action buttons and set up their click listeners
     * for save, back navigation, and image selection
     */
    private void initializeButtons() {
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        selectImageButton = findViewById(R.id.selectImageButton);

        // Save button - stores report data and generates PDF
        saveButton.setOnClickListener(v -> {
            ReportDatabaseHelper dbHelper = new ReportDatabaseHelper(this);
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            saveReport(db);
        });
        
        // Back button - returns to previous screen
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ReportActivity.this, MainActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });
        
        // Image selection button - opens image picker
        selectImageButton.setOnClickListener(v -> openImageSelector());
    }

    /**
     * Set up keyboard handling to ensure optimal user experience
     * on mobile devices with virtual keyboards
     */
    private void setupKeyboardHandling() {
        // Add focus change listeners to all input fields
        EditText[] allInputs = {nameInput, addressInput, dateInput, visitTypeInput,
                               siteInspectionInput, recommendationsInput, followUpInput,
                               prepInput, techInput};
        
        for (EditText input : allInputs) {
            if (input != null) {
                input.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        // Ensure the input field is visible when focused
                        input.requestFocus();
                    }
                });
            }
        }
    }

    /**
     * Set the current date as the default value for the date input field
     * Provides convenience for users creating reports
     */
    private void setCurrentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        if (dateInput != null) {
            dateInput.setText(currentDate);
        }
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String currentTime = timeSdf.format(new Date());
        if (dateInput != null) { // Assuming dateInput is used for both date and time
            dateInput.setText(currentDate + " " + currentTime);
        }
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
            
            // Show Firebase folder selection popup after successful save
            showFirebaseFolderSelectionPopup();
        } else {
            Toast.makeText(this, "Error Saving Report!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows Firebase folder selection popup for uploading the saved report
     */
    private void showFirebaseFolderSelectionPopup() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();

        storageRef.listAll().addOnSuccessListener(listResult -> {
            List<String> folderList = new ArrayList<>();
            for (StorageReference prefix : listResult.getPrefixes()) {
                String folderName = prefix.getName();
                // Only show Reports folders (Reports25, Reports26, etc.)
                if (folderName.matches("Reports\\d+")) {
                    folderList.add(folderName);
                }
            }

            if (folderList.isEmpty()) {
                Toast.makeText(this, "No Reports folders found.", Toast.LENGTH_SHORT).show();
                clearInputFields();
                return;
            }

            // Convert list to array for AlertDialog
            String[] foldersArray = folderList.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Reports Folder");
            builder.setMessage("Choose where to save your report:");
            builder.setItems(foldersArray, (dialog, which) -> {
                String selectedFolder = foldersArray[which];
                showSubFolderSelectionDialog(selectedFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                clearInputFields();
                dialog.dismiss();
            });
            builder.show();

        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load folders: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Shows subfolder selection dialog after selecting a parent folder
     *
     * @param parentFolder The selected parent folder
     */
    private void showSubFolderSelectionDialog(String parentFolder) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference parentFolderRef = storage.getReference().child(parentFolder);

        parentFolderRef.listAll().addOnSuccessListener(listResult -> {
            List<String> subFolderList = new ArrayList<>();
            for (StorageReference prefix : listResult.getPrefixes()) {
                subFolderList.add(prefix.getName());
            }

            if (subFolderList.isEmpty()) {
                Toast.makeText(this, "No subfolders found. Uploading directly to " + parentFolder, Toast.LENGTH_SHORT).show();
                uploadReportToFirebase(parentFolder);
                return;
            }

            // Show subfolder selection dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Subfolder in " + parentFolder);
            builder.setItems(subFolderList.toArray(new String[0]), (dialog, which) -> {
                String selectedSubFolder = subFolderList.get(which);
                uploadReportToFirebase(parentFolder + "/" + selectedSubFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                clearInputFields();
                dialog.dismiss();
            });
            builder.show();

        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load subfolders: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Uploads the generated report to Firebase Storage
     *
     * @param folderPath The path in Firebase Storage where the report should be saved
     */
    private void uploadReportToFirebase(String folderPath) {
        // Find the generated PDF file
        File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
        if (!reportsFolder.exists()) {
            Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
            clearInputFields();
            return;
        }

        // Look for the most recent PDF file (the one we just generated)
        File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No PDF report found to upload!", Toast.LENGTH_SHORT).show();
            clearInputFields();
            return;
        }

        // Get the most recent file
        File latestFile = files[files.length - 1];
        for (File file : files) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }

        // Upload to Firebase
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageReference = storage.getReference();
        StorageReference fileRef = storageReference.child(folderPath + "/" + latestFile.getName());

        UploadTask uploadTask = fileRef.putFile(Uri.fromFile(latestFile));
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            Toast.makeText(this, "Report uploaded successfully to " + folderPath, Toast.LENGTH_SHORT).show();
            clearInputFields();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            clearInputFields();
        });
    }

    /**
     * Clears all input fields after successful save and upload
     */
    private void clearInputFields() {
        nameInput.setText("");
        addressInput.setText("");
        dateInput.setText("");
        visitTypeInput.setText("");
        siteInspectionInput.setText("");
        recommendationsInput.setText("");
        followUpInput.setText("");
        prepInput.setText("");
        techInput.setText("");
        selectedImageUris.clear();
        
        // Set current date as default
        setCurrentDate();
    }
}
