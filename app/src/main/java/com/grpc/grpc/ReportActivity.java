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
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

// ✅ AI and Voice Recognition Imports
import okhttp3.*;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.IOException;
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
    private DictateEditText nameInput, addressInput, visitTypeInput;
    private EditText dateInput;
    
    // Detailed report information
    private DictateEditText siteInspectionInput, recommendationsInput, followUpInput,
            prepInput, techInput;

    // User context and session management
    private String userName;
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    // ============================================================================
    // ACTION BUTTONS - User interface controls
    // ============================================================================
    
    private Button saveButton, backButton, selectImageButton, aiPolishButton, readBackButton;

    // ============================================================================
    // DATA MANAGEMENT - Image and content storage
    // ============================================================================
    
    // List to hold selected image URIs for visual documentation
    private List<Uri> selectedImageUris = new ArrayList<>();

    // ============================================================================
    // AI AND VOICE RECOGNITION COMPONENTS
    // ============================================================================
    
    // AI API Configuration
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String AI_MODEL = "mistralai/mistral-nemo";
    private String openRouterApiKey = "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"; // Pre-configured API key
    
    // Voice Recognition Components
    private SpeechRecognizer speechRecognizer;
    private static final int PERMISSION_REQUEST_CODE = 123;
    private boolean isListening = false;
    
    // Text-to-Speech Components
    private TextToSpeech textToSpeech;
    
    // Interactive Voice System
    private String pendingField = ""; // Tracks which field we're waiting for
    private boolean isInteractiveMode = false; // Whether we're in question-answer mode
    private Handler autoProgressHandler = new Handler(); // For automatic field progression
    private int currentFieldIndex = 0; // Tracks current field in auto-progression
    private static final String[] AUTO_PROGRESS_FIELDS = {
        "property name", "address", "visit type", "site inspection", 
        "recommendations", "prep steps", "follow up", "technician name"
    };

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
        Log.d("ReportActivity", "Username loaded from intent: " + userName);
        Log.d("ReportActivity", "ReportActivity created with user: " + userName);

        // ============================================================================
        // EVENT DATA HANDLING - Pre-fill form if coming from WorkView
        // ============================================================================
        
        String eventName = getIntent().getStringExtra("EVENT_NAME");
        String eventType = getIntent().getStringExtra("EVENT_TYPE");
        String eventAddress = getIntent().getStringExtra("EVENT_ADDRESS");
        String eventIssue = getIntent().getStringExtra("EVENT_ISSUE");

        // ============================================================================
        // CONTRACT DATA HANDLING - Pre-fill form if coming from ViewContractActivity
        // ============================================================================
        
        String contractCompanyName = getIntent().getStringExtra("COMPANY_NAME");
        String contractAddress = getIntent().getStringExtra("ADDRESS");
        String reportDate = getIntent().getStringExtra("REPORT_DATE"); // Get date from intent

        // ============================================================================
        // UI COMPONENT INITIALIZATION
        // ============================================================================
        
        initializeInputFields();
        initializeButtons();
        setupKeyboardHandling();
        setCurrentDate();
        
        // Set up welcome message with user's name
        setupWelcomeMessage();
        
        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();

        // ============================================================================
        // INITIALIZE AI AND VOICE FEATURES
        // ============================================================================
        
        initializeAIAndVoiceFeatures();

        // ============================================================================
        // PRE-FILL FORM DATA FROM EVENT
        // ============================================================================
        
        if (eventName != null && !eventName.isEmpty()) {
            nameInput.getEditText().setText(eventName);
        }
        
        if (eventAddress != null && !eventAddress.isEmpty()) {
            addressInput.getEditText().setText(eventAddress);
        }
        
        if (eventType != null && !eventType.isEmpty()) {
            String visitType = eventType.equals("contract") ? "Contract Visit" : "Job Visit";
            visitTypeInput.getEditText().setText(visitType);
        }
        
        if (eventIssue != null && !eventIssue.isEmpty()) {
            // Add issue to site inspection or recommendations
            String currentInspection = siteInspectionInput.getEditText().getText().toString();
            if (currentInspection.isEmpty()) {
                siteInspectionInput.getEditText().setText("Issue: " + eventIssue);
            } else {
                siteInspectionInput.getEditText().setText(currentInspection + "\n\nIssue: " + eventIssue);
            }
        }

        // ============================================================================
        // PRE-FILL FORM DATA FROM CONTRACT
        // ============================================================================
        
        if (contractCompanyName != null && !contractCompanyName.isEmpty() && !contractCompanyName.equals("N/A")) {
            nameInput.getEditText().setText(contractCompanyName);
        }
        
        if (contractAddress != null && !contractAddress.isEmpty() && !contractAddress.equals("N/A")) {
            addressInput.getEditText().setText(contractAddress);
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
     * Initialize gesture detector for swipe navigation
     */
    private void initializeGestureDetector() {
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Swipe right - open MainActivity
                                Log.d("ReportActivity", "Swipe RIGHT detected - opening MainActivity with user: " + userName);
                                Intent intent = new Intent(ReportActivity.this, MainActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            } else {
                                // Swipe left - open GeneralReportActivity (previous in sequence)
                                Log.d("ReportActivity", "Swipe LEFT detected - opening GeneralReportActivity with user: " + userName);
                                Intent intent = new Intent(ReportActivity.this, GeneralReportActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("ReportActivity", "Error in swipe detection: " + e.getMessage());
                }
                return false;
            }
        });
    }

    /**
     * Handle touch events for swipe gestures
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
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
        
        // Configure each DictateEditText with proper hints and settings
        nameInput.setHint("Enter Property Name");
        nameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        
        addressInput.setHint("Enter Address");
        addressInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        addressInput.setMinLines(3);
        addressInput.setGravity(android.view.Gravity.TOP);
        
        dateInput.setHint("Enter Date and Time (e.g., 26/07/25 16:25)");
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        
        visitTypeInput.setHint("Visit Type");
        visitTypeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        
        siteInspectionInput.setHint("Enter Site Inspection");
        siteInspectionInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        siteInspectionInput.setMinLines(3);
        siteInspectionInput.setGravity(android.view.Gravity.TOP);
        
        recommendationsInput.setHint("Enter Recommendations");
        recommendationsInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        recommendationsInput.setMinLines(3);
        recommendationsInput.setGravity(android.view.Gravity.TOP);
        
        prepInput.setHint("Enter Prep Steps");
        prepInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        prepInput.setMinLines(3);
        prepInput.setGravity(android.view.Gravity.TOP);
        
        followUpInput.setHint("Enter Follow Up Details");
        followUpInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        
        techInput.setHint("Enter Technician Name");
        techInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
    }

    /**
     * Set up welcome message with user's name
     */
    private void setupWelcomeMessage() {
        android.widget.TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
            Log.d("ReportActivity", "Welcome message set for user: " + userName);
        } else {
            Log.e("ReportActivity", "welcomeTextView is NULL! Check XML ID.");
        }
    }

    /**
     * Initialize action buttons and set up their click listeners
     * for save, back navigation, image selection, AI polish, and voice dictation
     */
    private void initializeButtons() {
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        aiPolishButton = findViewById(R.id.aiPolishButton);
        readBackButton = findViewById(R.id.readBackButton);

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
        
        // AI Polish button - enhances report content using AI
        aiPolishButton.setOnClickListener(v -> {
            if (openRouterApiKey.isEmpty()) {
                requestOpenRouterApiKey();
            } else {
                polishWithAI();
            }
        });
        

        
        // Read Back button - reads the entire report
        readBackButton.setOnClickListener(v -> readReportBack());
    }

    /**
     * Set up keyboard handling to ensure optimal user experience
     * on mobile devices with virtual keyboards
     */
    private void setupKeyboardHandling() {
        // Add focus change listeners to all DictateEditText fields
        DictateEditText[] dictateInputs = {nameInput, addressInput, visitTypeInput,
                               siteInspectionInput, recommendationsInput, followUpInput,
                               prepInput, techInput};
        
        for (DictateEditText input : dictateInputs) {
            if (input != null) {
                input.getEditText().setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        // Ensure the input field is visible when focused
                        input.getEditText().requestFocus();
                    }
                });
            }
        }
        
        // Handle regular EditText (dateInput) separately
        if (dateInput != null) {
            dateInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    // Ensure the input field is visible when focused
                    dateInput.requestFocus();
                }
            });
        }
    }

    /**
     * Set the current date as the default value for the date input field
     * Provides convenience for users creating reports
     */
    private void setCurrentDate() {
        // Check if a date was passed from intent (from GeneralReportActivity)
        String reportDate = getIntent().getStringExtra("REPORT_DATE");
        
        if (reportDate != null && !reportDate.isEmpty()) {
            // Use the passed date
            if (dateInput != null) {
                dateInput.setText(reportDate);
            }
        } else {
            // Use current date and time
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
        String reportName = nameInput.getEditText().getText().toString();
        String content = "Premise Name: " + nameInput.getEditText().getText().toString() +
                "\nAddress: " + addressInput.getEditText().getText().toString() +
                "\nDate: " + dateInput.getText().toString() +
                "\nVisit Type: " + visitTypeInput.getEditText().getText().toString() +
                "\nSite Inspection: " + siteInspectionInput.getEditText().getText().toString() +
                "\nRecommendations: " + recommendationsInput.getEditText().getText().toString() +
                "\nFollow-Up: " + followUpInput.getEditText().getText().toString() +
                "\nPrep: " + prepInput.getEditText().getText().toString() +
                "\nTech: " + techInput.getEditText().getText().toString();

        // Store values in a ContentValues object for database insertion
        ContentValues values = new ContentValues();
        values.put("name", reportName);
        values.put("address", addressInput.getEditText().getText().toString());
        values.put("date", dateInput.getText().toString());
        values.put("visit_type", visitTypeInput.getEditText().getText().toString());
        values.put("site_inspection", siteInspectionInput.getEditText().getText().toString());
        values.put("recommendations", recommendationsInput.getEditText().getText().toString());
        values.put("follow_up", followUpInput.getEditText().getText().toString());
        values.put("prep", prepInput.getEditText().getText().toString());
        values.put("tech", techInput.getEditText().getText().toString());

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
                        !selectedImageUris.isEmpty() ? selectedImageUris : null,
                        dateInput.getText().toString() // Pass the date from the input field
                );
            }
            
            // Clear fields after successful save
            clearInputFields();
            
            // Show options dialog after successful save
            showReportOptionsDialog();
        } else {
            Toast.makeText(this, "Error Saving Report!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows a dialog with options to share report, upload to Firebase, or cancel
     */
    private void showReportOptionsDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Report Saved Successfully!")
                    .setMessage("What would you like to do with your report?")
                    .setPositiveButton("Share Report", (dialog, which) -> {
                        shareReport();
                    })
                    .setNegativeButton("Upload to Firebase", (dialog, which) -> {
                        showFirebaseFolderSelectionPopup();
                    })
                    .setNeutralButton("Cancel", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .setCancelable(false);
            
            AlertDialog dialog = builder.create();
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error showing dialog: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    /**
     * Shares the generated report using an Intent
     */
    private void shareReport() {
        try {
            // Find the generated PDF file
            File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
            if (!reportsFolder.exists()) {
                Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Look for the most recent PDF file (the one we just generated)
            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files == null || files.length == 0) {
                Toast.makeText(this, "No PDF report found to share!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get the most recent file
            File latestFile = files[files.length - 1];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }

            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    latestFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Report"));
            
            // Show the options dialog again after sharing
            showReportOptionsDialog();
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the report.", Toast.LENGTH_SHORT).show();
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
                if (!folderName.equals("backup")) { // Exclude the backup folder
                    folderList.add(folderName);
                }
            }

            if (folderList.isEmpty()) {
                Toast.makeText(this, "No available folders to select.", Toast.LENGTH_SHORT).show();
                // Show the options dialog again if no folders found
                showReportOptionsDialog();
                return;
            }

            // Convert list to array for AlertDialog
            String[] foldersArray = folderList.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select a Parent Folder");
            builder.setItems(foldersArray, (dialog, which) -> {
                String selectedFolder = foldersArray[which];
                showSubFolderSelectionDialog(selectedFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                // Show the options dialog again if user cancels
                showReportOptionsDialog();
                dialog.dismiss();
            });
            builder.show();

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load folders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Show the options dialog again if loading folders fails
            showReportOptionsDialog();
        });
    }

    /**
     * Shows subfolder selection dialog after selecting a parent folder
     *
     * @param parentFolder The selected parent folder
     */
    private void showSubFolderSelectionDialog(String parentFolder) {
        Toast.makeText(this, "Loading subfolders for: " + parentFolder, Toast.LENGTH_SHORT).show();
        
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
            builder.setTitle("Select a Subfolder");
            builder.setItems(subFolderList.toArray(new String[0]), (dialog, which) -> {
                String selectedSubFolder = subFolderList.get(which);
                uploadReportToFirebase(parentFolder + "/" + selectedSubFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                // Show the options dialog again if user cancels
                showReportOptionsDialog();
                dialog.dismiss();
            });
            builder.show();

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load subfolders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Show the options dialog again if loading subfolders fails
            showReportOptionsDialog();
        });
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
            // Show the options dialog again if report folder not found
            showReportOptionsDialog();
            return;
        }

        // Look for the most recent PDF file (the one we just generated)
        File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No PDF report found to upload!", Toast.LENGTH_SHORT).show();
            // Show the options dialog again if no PDF found
            showReportOptionsDialog();
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
            // Show the options dialog again after successful upload
            showReportOptionsDialog();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            // Show the options dialog again after failed upload
            showReportOptionsDialog();
        });
    }

    /**
     * Clears all input fields after successful save and upload
     */
    private void clearInputFields() {
        nameInput.getEditText().setText("");
        addressInput.getEditText().setText("");
        dateInput.setText("");
        visitTypeInput.getEditText().setText("");
        siteInspectionInput.getEditText().setText("");
        recommendationsInput.getEditText().setText("");
        followUpInput.getEditText().setText("");
        prepInput.getEditText().setText("");
        techInput.getEditText().setText("");
        selectedImageUris.clear();
        
        // Set current date as default
        setCurrentDate();
    }

    // ============================================================================
    // AI POLISHING FEATURES
    // ============================================================================

    /**
     * Initialize AI and voice recognition features
     */
    private void initializeAIAndVoiceFeatures() {
        // Initialize speech recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            setupSpeechRecognizer();
        } else {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
        }
        
        // Initialize Text-to-Speech
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Request OpenRouter API key from user
     */
    private void requestOpenRouterApiKey() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🤖 AI Polish Setup")
                .setMessage("To use AI polishing, please enter your OpenRouter API key:")
                .setView(createApiKeyInputView())
                .setPositiveButton("Save", (dialog, which) -> {
                    // API key will be saved in the EditText's text
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(false);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Create input view for API key
     */
    private android.view.View createApiKeyInputView() {
        EditText apiKeyInput = new EditText(this);
        apiKeyInput.setHint("Enter your OpenRouter API key");
        apiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setPadding(50, 20, 50, 20);
        
        // Set up text change listener to save API key
        apiKeyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                openRouterApiKey = s.toString().trim();
            }
        });
        
        return apiKeyInput;
    }

    /**
     * Polish the report content using AI
     */
    private void polishWithAI() {
        String siteInspection = siteInspectionInput.getEditText().getText().toString().trim();
        String recommendations = recommendationsInput.getEditText().getText().toString().trim();
        
        if (siteInspection.isEmpty() && recommendations.isEmpty()) {
            Toast.makeText(this, "Please enter some content in Site Inspection or Recommendations first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (openRouterApiKey.isEmpty()) {
            Toast.makeText(this, "Please set your OpenRouter API key first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show loading indicator
        Toast.makeText(this, "🤖 AI is polishing your report...", Toast.LENGTH_SHORT).show();
        aiPolishButton.setEnabled(false);
        aiPolishButton.setText("🤖 Polishing...");
        
        // Create the prompt for AI
        String prompt = "Rewrite the following text to make it sound more professional, slightly longer, and more fluid. " +
                "Ensure the grammar is correct throughout. Keep the original meaning and all key information intact, " +
                "but improve the sentence structure, vocabulary, and tone to reflect the voice of a confident, experienced professional. " +
                "Expand naturally where appropriate without adding unrelated content. " +
                "Return the result as plain text only — do not include quotation marks, asterisks, or colons in the output formatting.\n\n";
        
        if (!siteInspection.isEmpty()) {
            prompt += "Site Inspection: " + siteInspection + "\n\n";
        } else {
            prompt += "Site Inspection: No site inspection details noted\n\n";
        }
        
        if (!recommendations.isEmpty()) {
            prompt += "Recommendations: " + recommendations + "\n\n";
        } else {
            prompt += "Recommendations: No recommendations noted\n\n";
        }
        
        prompt += "Please provide the enhanced content in this exact format:\n\n" +
                "Site Inspection: [professional content with improved grammar]\n\n" +
                "Recommendations: [professional recommendations with improved grammar]\n\n" +
                "Do not include any other text, labels, or formatting symbols. Return plain text only.";
        
        // Make API call in background thread
        final String finalPrompt = prompt;
        new Thread(() -> {
            try {
                String response = callOpenRouterAPI(finalPrompt);
                updateUIWithAIPolish(response);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "AI polishing failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    aiPolishButton.setEnabled(true);
                    aiPolishButton.setText("🤖 AI Polish Report");
                });
            }
        }).start();
    }

    /**
     * Call OpenRouter API with the given prompt
     */
    private String callOpenRouterAPI(String prompt) throws IOException {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", AI_MODEL);
            
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 4000);
            requestBody.put("temperature", 0.8);

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json; charset=utf-8")
            );

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(OPENROUTER_API_URL)
                    .addHeader("Authorization", "Bearer " + openRouterApiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://grpc-app.com")
                    .addHeader("X-Title", "GRPest Control App")
                    .addHeader("User-Agent", "GRPest-Control-App/1.0")
                    .post(body)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                String responseBody = response.body().string();
                
                if (!response.isSuccessful()) {
                    Log.e("ReportActivity", "API Error Response: " + responseBody);
                    throw new IOException("API call failed: " + response.code() + " " + response.message() + "\nResponse: " + responseBody);
                }
                
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    return messageObj.getString("content");
                } else {
                    throw new IOException("No response content from AI");
                }
            }
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error: " + e.getMessage());
        }
    }

    /**
     * Update UI with AI polished content
     */
    private void updateUIWithAIPolish(String aiResponse) {
        runOnUiThread(() -> {
            try {
                // Parse the AI response to extract Site Inspection and Recommendations
                String[] sections = aiResponse.split("Site Inspection:|Recommendations:", -1);
                
                if (sections.length >= 3) {
                    // Extract site inspection (section 1) - clean content only
                    String siteInspection = sections[1].trim();
                    if (!siteInspection.isEmpty()) {
                        // Remove any remaining "Site Inspection:" or "Recommendations:" labels
                        siteInspection = siteInspection.replaceAll("(?i)Site Inspection:", "").trim();
                        siteInspection = siteInspection.replaceAll("(?i)Recommendations:", "").trim();
                        // Remove formatting symbols
                        siteInspection = siteInspection.replaceAll("\\*\\*", "").trim();
                        siteInspection = siteInspection.replaceAll("\"", "").trim();
                        // Set clean content without formatting
                        siteInspectionInput.getEditText().setText(siteInspection);
                    }
                    
                    // Extract recommendations (section 2) - clean content only
                    String recommendations = sections[2].trim();
                    if (!recommendations.isEmpty()) {
                        // Remove any remaining "Site Inspection:" or "Recommendations:" labels
                        recommendations = recommendations.replaceAll("(?i)Site Inspection:", "").trim();
                        recommendations = recommendations.replaceAll("(?i)Recommendations:", "").trim();
                        // Remove formatting symbols
                        recommendations = recommendations.replaceAll("\\*\\*", "").trim();
                        recommendations = recommendations.replaceAll("\"", "").trim();
                        // Set clean content without formatting
                        recommendationsInput.getEditText().setText(recommendations);
                    }
                    
                    Toast.makeText(this, "✅ AI polishing completed!", Toast.LENGTH_SHORT).show();
                } else {
                    // If parsing fails, try to split by common separators
                    String[] altSections = aiResponse.split("(?i)recommendations:", -1);
                    if (altSections.length >= 2) {
                        String siteInspection = altSections[0].replaceAll("(?i)site inspection:", "").trim();
                        String recommendations = altSections[1].trim();
                        
                        // Remove formatting symbols
                        siteInspection = siteInspection.replaceAll("\\*\\*", "").trim();
                        siteInspection = siteInspection.replaceAll("\"", "").trim();
                        recommendations = recommendations.replaceAll("\\*\\*", "").trim();
                        recommendations = recommendations.replaceAll("\"", "").trim();
                        
                        if (!siteInspection.isEmpty()) {
                            siteInspectionInput.getEditText().setText(siteInspection);
                        }
                        if (!recommendations.isEmpty()) {
                            recommendationsInput.getEditText().setText(recommendations);
                        }
                        Toast.makeText(this, "✅ AI polishing completed!", Toast.LENGTH_SHORT).show();
                    } else {
                        // Last resort - just use the whole response
                        String cleanResponse = aiResponse.trim();
                        // Remove formatting symbols from the whole response
                        cleanResponse = cleanResponse.replaceAll("\\*\\*", "").trim();
                        cleanResponse = cleanResponse.replaceAll("\"", "").trim();
                        siteInspectionInput.getEditText().setText(cleanResponse);
                        Toast.makeText(this, "✅ AI polishing completed!", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error parsing AI response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                aiPolishButton.setEnabled(true);
                aiPolishButton.setText("🤖 AI Polish Report");
            }
        });
    }

    // ============================================================================
    // VOICE RECOGNITION FEATURES
    // ============================================================================

    /**
     * Check if voice permission is granted
     */
    private boolean checkVoicePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request voice permission
     */
    private void requestVoicePermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                PERMISSION_REQUEST_CODE);
    }

    /**
     * Handle permission result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition();
            } else {
                Toast.makeText(this, "Voice permission is required for dictation", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Setup speech recognizer
     */
    private void setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    isListening = true;
                    Toast.makeText(ReportActivity.this, "🎙️ Listening... Speak now!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                runOnUiThread(() -> {
                    // Processing state handled by individual field dictate buttons
                });
            }

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    isListening = false;
                    String errorMessage = "Voice recognition error: ";
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                            errorMessage += "No speech detected. Try again.";
                            // Speak the commands again if no speech detected
                            if (textToSpeech != null) {
                                textToSpeech.speak("No speech detected. Please try again.", TextToSpeech.QUEUE_FLUSH, null, "NO_SPEECH");
                            }
                            break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            errorMessage += "Speech timeout after 15 seconds";
                            // Auto-progress to next field if in interactive mode
                            if (isInteractiveMode) {
                                progressToNextField();
                            } else {
                                // Speak timeout message
                                if (textToSpeech != null) {
                                    textToSpeech.speak("Timeout. Please try again.", TextToSpeech.QUEUE_FLUSH, null, "TIMEOUT");
                                }
                            }
                            break;
                        default:
                            // Silently handle other errors without showing error messages
                            return;
                    }
                    // Only show toast for specific errors, not for unknown errors
                    if (!errorMessage.equals("Voice recognition error: ")) {
                        Toast.makeText(ReportActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0).toLowerCase();
                    processVoiceCommand(spokenText);
                }
                
                runOnUiThread(() -> {
                    isListening = false;
                });
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    /**
     * Start voice recognition
     */
    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...");
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000); // 15 second timeout
            speechRecognizer.startListening(intent);
            
            // Set up auto-progression after 15 seconds if no voice heard
            autoProgressHandler.postDelayed(() -> {
                if (isListening && isInteractiveMode) {
                    // No voice heard, move to next field
                    progressToNextField();
                }
            }, 15000); // 15 second timeout
        }
    }

    /**
     * Process voice command and fill appropriate field
     */
    private void processVoiceCommand(String spokenText) {
        runOnUiThread(() -> {
            String fieldName = "";
            String content = "";
            
            // Convert to lowercase for better matching
            String lowerText = spokenText.toLowerCase();
            
            // Check if we're in interactive mode (waiting for a response)
            if (isInteractiveMode && !pendingField.isEmpty()) {
                // We're waiting for content for a specific field
                fillFieldWithContent(pendingField, spokenText);
                
                // Always move to next field after successful input
                if (currentFieldIndex < AUTO_PROGRESS_FIELDS.length) {
                    currentFieldIndex++;
                    new Handler().postDelayed(() -> {
                        progressToNextField();
                    }, 1000); // 1 second delay between fields
                } else {
                    // Auto-progression completed
                    isInteractiveMode = false;
                    pendingField = "";
                    currentFieldIndex = 0;
                }
                return;
            }
            
            // Parse commands using improved string matching
            if (lowerText.contains("enter property name") || lowerText.contains("property name")) {
                content = extractContentAfterCommand(spokenText, "enter property name", "property name");
                if (!content.isEmpty()) {
                    // Direct command with content
                    nameInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Property Name filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(1); // Start from address
                } else {
                    // Interactive mode - ask for property name
                    askForFieldContent("property name");
                }
            } else if (lowerText.contains("enter address") || lowerText.contains("address")) {
                content = extractContentAfterCommand(spokenText, "enter address", "address");
                if (!content.isEmpty()) {
                    addressInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Address filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(2); // Start from visit type
                } else {
                    askForFieldContent("address");
                }
            } else if (lowerText.contains("visit type") || lowerText.contains("visit")) {
                content = extractContentAfterCommand(spokenText, "visit type", "visit");
                if (!content.isEmpty()) {
                    visitTypeInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Visit Type filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(3); // Start from site inspection
                } else {
                    askForFieldContent("visit type");
                }
            } else if (lowerText.contains("site inspection") || lowerText.contains("inspection")) {
                content = extractContentAfterCommand(spokenText, "site inspection", "inspection");
                if (!content.isEmpty()) {
                    siteInspectionInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Site Inspection filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(4); // Start from recommendations
                } else {
                    askForFieldContent("site inspection");
                }
            } else if (lowerText.contains("recommendations") || lowerText.contains("recommend")) {
                content = extractContentAfterCommand(spokenText, "recommendations", "recommend");
                if (!content.isEmpty()) {
                    recommendationsInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Recommendations filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(5); // Start from prep steps
                } else {
                    askForFieldContent("recommendations");
                }
            } else if (lowerText.contains("prep steps") || lowerText.contains("prep")) {
                content = extractContentAfterCommand(spokenText, "prep steps", "prep");
                if (!content.isEmpty()) {
                    prepInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Prep Steps filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(6); // Start from follow up
                } else {
                    askForFieldContent("prep steps");
                }
            } else if (lowerText.contains("follow up") || lowerText.contains("follow")) {
                content = extractContentAfterCommand(spokenText, "follow up", "follow");
                if (!content.isEmpty()) {
                    followUpInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Follow Up filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progress to next field
                    startAutoProgressionFromField(7); // Start from technician name
                } else {
                    askForFieldContent("follow up");
                }
            } else if (lowerText.contains("technician name") || lowerText.contains("technician")) {
                content = extractContentAfterCommand(spokenText, "technician name", "technician");
                if (!content.isEmpty()) {
                    techInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Technician Name filled: " + content, Toast.LENGTH_SHORT).show();
                    // Auto-progression completed
                    Toast.makeText(this, "✅ All fields completed!", Toast.LENGTH_SHORT).show();
                    if (textToSpeech != null) {
                        textToSpeech.speak("All fields have been completed.", TextToSpeech.QUEUE_FLUSH, null, "ALL_COMPLETE");
                    }
                } else {
                    askForFieldContent("technician name");
                }
            } else if (lowerText.contains("polish report") || lowerText.contains("ai polish")) {
                // Trigger AI polish via voice command
                Toast.makeText(this, "🤖 Starting AI polish...", Toast.LENGTH_SHORT).show();
                polishWithAI();
                return;
            } else if (lowerText.contains("read back") || lowerText.contains("read report")) {
                // Trigger read back via voice command
                Toast.makeText(this, "📖 Reading report back...", Toast.LENGTH_SHORT).show();
                readReportBack();
                return;
            } else if (lowerText.contains("auto fill") || lowerText.contains("auto fill all")) {
                // Start auto-progression through all fields
                startAutoProgression();
                return;
            } else {
                Toast.makeText(this, "❌ Command not recognized. Try: 'Enter property name [content]', 'Polish report', 'Read back', or 'Auto fill all'", Toast.LENGTH_LONG).show();
                return;
            }
        });
    }

    /**
     * Extract content after a command
     */
    private String extractContentAfterCommand(String spokenText, String... commands) {
        for (String command : commands) {
            int index = spokenText.indexOf(command);
            if (index != -1) {
                String afterCommand = spokenText.substring(index + command.length()).trim();
                // Remove common filler words and clean up
                afterCommand = afterCommand.replaceAll("^(is|are|was|were|the|a|an)\\s+", "");
                afterCommand = afterCommand.replaceAll("\\s+", " ").trim();
                return afterCommand;
            }
        }
        return "";
    }

    /**
     * Ask for field content in interactive mode
     */
    private void askForFieldContent(String fieldName) {
        pendingField = fieldName;
        isInteractiveMode = true;
        
        // Speak the question
        String question = "What is the " + fieldName + "?";
        textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, "FIELD_QUESTION");
        
        // Show toast
        Toast.makeText(this, "🎙️ " + question, Toast.LENGTH_SHORT).show();
        
        // Start listening for the answer
        startVoiceRecognition();
    }

    /**
     * Fill field with content from interactive mode
     */
    private void fillFieldWithContent(String fieldName, String content) {
        switch (fieldName.toLowerCase()) {
            case "property name":
                nameInput.getEditText().setText(content);
                break;
            case "address":
                addressInput.getEditText().setText(content);
                break;
            case "visit type":
                visitTypeInput.getEditText().setText(content);
                break;
            case "site inspection":
                siteInspectionInput.getEditText().setText(content);
                break;
            case "recommendations":
                recommendationsInput.getEditText().setText(content);
                break;
            case "prep steps":
                prepInput.getEditText().setText(content);
                break;
            case "follow up":
                followUpInput.getEditText().setText(content);
                break;
            case "technician name":
                techInput.getEditText().setText(content);
                break;
        }
        
        // Confirm with speech and toast
        String confirmation = fieldName + " filled with " + content;
        textToSpeech.speak(confirmation, TextToSpeech.QUEUE_FLUSH, null, "FIELD_CONFIRMATION");
        Toast.makeText(this, "✅ " + confirmation, Toast.LENGTH_SHORT).show();
    }

    /**
     * Start auto-progression through all fields
     */
    private void startAutoProgression() {
        currentFieldIndex = 0;
        isInteractiveMode = true;
        Toast.makeText(this, "🔄 Starting auto-fill mode. Will progress through all fields.", Toast.LENGTH_SHORT).show();
        progressToNextField();
    }

    /**
     * Progress to next field in auto-progression
     */
    private void progressToNextField() {
        if (currentFieldIndex >= AUTO_PROGRESS_FIELDS.length) {
            // All fields completed
            isInteractiveMode = false;
            currentFieldIndex = 0;
            Toast.makeText(this, "✅ Auto-fill completed!", Toast.LENGTH_SHORT).show();
            if (textToSpeech != null) {
                textToSpeech.speak("Auto-fill completed. All fields have been processed.", TextToSpeech.QUEUE_FLUSH, null, "AUTO_COMPLETE");
            }
            return;
        }

        String currentField = AUTO_PROGRESS_FIELDS[currentFieldIndex];
        pendingField = currentField;
        
        // Speak the question
        String question = "What is the " + currentField + "?";
        if (textToSpeech != null) {
            textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, "AUTO_QUESTION");
        }
        
        Toast.makeText(this, "🎙️ " + question, Toast.LENGTH_SHORT).show();
        
        // Start listening for the answer
        startVoiceRecognition();
    }

    /**
     * Handle auto-progression when no voice is heard
     */
    private void handleAutoProgressionTimeout() {
        if (isInteractiveMode && currentFieldIndex < AUTO_PROGRESS_FIELDS.length) {
            // Set a default value and move to next field
            String currentField = AUTO_PROGRESS_FIELDS[currentFieldIndex];
            fillFieldWithContent(currentField, "Not specified");
            currentFieldIndex++;
            
            // Progress to next field after a short delay
            new Handler().postDelayed(() -> {
                progressToNextField();
            }, 2000); // 2 second delay between fields
        }
    }

    /**
     * Start auto-progression from a specific field index
     */
    private void startAutoProgressionFromField(int fieldIndex) {
        currentFieldIndex = fieldIndex;
        isInteractiveMode = true;
        
        // Progress to the next field after a short delay
        new Handler().postDelayed(() -> {
            progressToNextField();
        }, 1000); // 1 second delay
    }

    /**
     * Read back the entire report using Text-to-Speech
     */
    private void readReportBack() {
        // Initialize Text-to-Speech if not already done
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    // Call the actual reading logic after initialization
                    readReportContent();
                } else {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        // If TextToSpeech is already initialized, read the content directly
        readReportContent();
    }
    
    /**
     * Actually read the report content using Text-to-Speech
     */
    private void readReportContent() {
        // Build the report text
        StringBuilder reportText = new StringBuilder();
        reportText.append("Property Name: ").append(nameInput.getEditText().getText().toString()).append(". ");
        reportText.append("Address: ").append(addressInput.getEditText().getText().toString()).append(". ");
        reportText.append("Date: ").append(dateInput.getText().toString()).append(". ");
        reportText.append("Visit Type: ").append(visitTypeInput.getEditText().getText().toString()).append(". ");
        reportText.append("Site Inspection: ").append(siteInspectionInput.getEditText().getText().toString()).append(". ");
        reportText.append("Recommendations: ").append(recommendationsInput.getEditText().getText().toString()).append(". ");
        reportText.append("Prep Steps: ").append(prepInput.getEditText().getText().toString()).append(". ");
        reportText.append("Follow Up: ").append(followUpInput.getEditText().getText().toString()).append(". ");
        reportText.append("Technician Name: ").append(techInput.getEditText().getText().toString()).append(". ");
        
        // Speak the report
        textToSpeech.speak(reportText.toString(), TextToSpeech.QUEUE_FLUSH, null, "REPORT_READBACK");
        Toast.makeText(this, "📖 Reading report back...", Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle keyboard and UI updates when activity resumes
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Ensure proper keyboard handling when activity resumes
    }
    
    /**
     * Clean up resources when activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
    }
}
