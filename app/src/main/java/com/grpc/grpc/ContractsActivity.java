/**
 * ============================================================================
 * GRPest Control Application - Contract Management Hub
 * ============================================================================
 * 
 * BUSINESS OVERVIEW:
 * This activity serves as the central hub for all contract management operations
 * within the GRPest Control application. It provides technicians and administrators
 * with comprehensive tools to manage pest control contracts, track overdue services,
 * and generate detailed reports for business operations and client communication.
 * 
 * CORE FUNCTIONALITIES:
 * 
 * 1. CONTRACT MANAGEMENT
 *    - Add new pest control contracts with detailed specifications
 *    - View and manage existing contracts across all technicians
 *    - Contract status tracking and update capabilities
 *    - Client information and service history management
 * 
 * 2. BEHINDS LIST GENERATION
 *    - Automated identification of overdue contracts
 *    - PDF report generation for overdue services
 *    - Special handling for user 004 (dual collection access)
 *    - Professional formatting with company branding
 * 
 * 3. CONTRACT VIEWING & SEARCH
 *    - Comprehensive contract listing and filtering
 *    - Search functionality across contract databases
 *    - Contract details and service history access
 *    - Report generation for individual contracts
 * 
 * 4. PDF MANAGEMENT
 *    - Behind list PDF storage and organization
 *    - PDF viewing, sharing, and download capabilities
 *    - File management and cleanup operations
 *    - Integration with device sharing features
 * 
 * 5. USER ROLE MANAGEMENT
 *    - Role-based access control for different users
 *    - Special permissions for administrative users
 *    - Technician-specific contract collections
 *    - Cross-collection access for management users
 * 
 * TECHNICAL FEATURES:
 * - Firebase Firestore integration for contract data
 * - PDF generation using iText7 library
 * - File management and storage operations
 * - Real-time data synchronization
 * - Cross-platform sharing capabilities
 * 
 * USER ROLES & PERMISSIONS:
 * - Technicians: Manage their own contracts and generate behinds lists
 * - User 004: Access to multiple contract collections
 * - Administrators: Full contract management and reporting access
 * - All users: Contract viewing and basic management capabilities
 * 
 * Author: GRPC
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ContractsActivity extends AppCompatActivity {
    
    // ============================================================================
    // UI COMPONENTS - Navigation and action buttons
    // ============================================================================
    
    private Button addContractButton, viewContractButton, behindsListButton, dueListButton, viewBehindsButton;
    
    // ============================================================================
    // DATA MANAGEMENT - User context and database operations
    // ============================================================================
    
    private String userName;
    private FirebaseFirestore db;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    /**
     * Main entry point of the contract management hub
     * Initializes the user interface, sets up Firebase connection,
     * and configures all navigation and action buttons
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contract_selection);

        // ============================================================================
        // FIREBASE INITIALIZATION
        // ============================================================================
        
        // Initialize Firebase Firestore for contract data management
        db = FirebaseFirestore.getInstance();

        // ============================================================================
        // USER AUTHENTICATION & VALIDATION
        // ============================================================================
        
        // Retrieve and validate user information from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) {
            userName = "Unknown"; // Default username for error handling
        }

        // Display welcome message to confirm user context
        Toast.makeText(this, "Welcome to Contracts, " + userName + "!", Toast.LENGTH_SHORT).show();

        // ============================================================================
        // UI COMPONENT INITIALIZATION
        // ============================================================================
        
        // Initialize all navigation and action buttons
        initializeButtons();
        
        // Set up welcome message with user's name
        setupWelcomeMessage();
        
        // Set up click listeners for all button actions
        setupButtonClickListeners();
        
        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();
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
                                Intent intent = new Intent(ContractsActivity.this, MainActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("ContractsActivity", "Error in swipe detection: " + e.getMessage());
                }
                return false;
            }
        });
    }

    /**
     * Handle touch events for swipe gestures
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Initialize all UI button components by finding them in the layout
     * and setting up their basic properties
     */
    private void initializeButtons() {
        addContractButton = findViewById(R.id.AddContractButton);
        viewContractButton = findViewById(R.id.ViewContractButton);
        behindsListButton = findViewById(R.id.BehindsListButton);
        dueListButton = findViewById(R.id.DueListButton);
        viewBehindsButton = findViewById(R.id.ViewBehindsButton);
    }

    /**
     * Set up the welcome message TextView with the user's name
     */
    private void setupWelcomeMessage() {
        android.widget.TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        }
    }

    /**
     * Set up click listeners for all navigation and action buttons
     * Each button opens specific activities or performs specialized functions
     */
    private void setupButtonClickListeners() {
        // Add Contract Button - Navigate to contract creation
        addContractButton.setOnClickListener(v -> {
            Intent intent = new Intent(ContractsActivity.this, AddContractActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        // View Contract Button - Navigate to contract management
        viewContractButton.setOnClickListener(v -> {
            Intent intent = new Intent(ContractsActivity.this, ViewContractActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        // Behinds List Button - Generate overdue contracts report
        behindsListButton.setOnClickListener(v -> {
            generateBehindsListPDF();
        });

        // Due List Button - Generate due contracts report
        dueListButton.setOnClickListener(v -> {
            generateDueListPDF();
        });

        // View Behinds Button - Navigate to behinds list management
        viewBehindsButton.setOnClickListener(v -> {
            Intent intent = new Intent(ContractsActivity.this, BehindsListViewActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });
    }

    /**
     * Generates a behinds list PDF containing all overdue contracts.
     * Loads contracts from Firebase, filters overdue ones, generates PDF, and shows options.
     */
    private void generateBehindsListPDF() {
        // Show loading message
        Toast.makeText(this, "Generating behinds list...", Toast.LENGTH_SHORT).show();

        if ("004".equals(StaffDirectory.getUserId(userName))) {
            generateBehindsListForKristine();
        } else {
            String tableName = userName + " Contracts";

            db.collection(tableName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Map<String, Object>> contractsList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        contract.put("owner", userName);
                        contractsList.add(contract);
                    }
                    // Move heavy processing to background thread
                    new ProcessContractsAsyncTask().execute(contractsList, userName);
                } else {
                    Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Generates separate behinds list PDFs per contract technician when user is 004.
     */
    private void generateBehindsListForKristine() {
        String[] contractCollections = new String[StaffDirectory.CONTRACT_TECHNICIAN_IDS.length];
        for (int i = 0; i < StaffDirectory.CONTRACT_TECHNICIAN_IDS.length; i++)
            contractCollections[i] = StaffDirectory.getContractsCollectionName(StaffDirectory.CONTRACT_TECHNICIAN_IDS[i]);
        Map<String, List<Map<String, Object>>> technicianContracts = new HashMap<>();
        int[] loadedCount = {0};

        for (String collectionName : contractCollections) {
            db.collection(collectionName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Map<String, Object>> techContracts = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        contract.put("owner", collectionName.replace(" Contracts", ""));
                        techContracts.add(contract);
                    }

                    String technician = collectionName.replace(" Contracts", "");
                    technicianContracts.put(technician, techContracts);
                } else {
                    Toast.makeText(this, "Failed to load " + collectionName, Toast.LENGTH_SHORT).show();
                }

                loadedCount[0]++;
                if (loadedCount[0] == contractCollections.length) {
                    // Generate PDFs for each technician using AsyncTask
                    for (String tech : technicianContracts.keySet()) {
                        List<Map<String, Object>> contracts = technicianContracts.get(tech);
                        new ProcessContractsAsyncTask().execute(contracts, tech);
                    }
                }
            });
        }
    }

    /**
     * Generates a due list PDF containing all contracts due within 7 days.
     * Loads contracts from Firebase, filters due ones, generates PDF, and shows options.
     */
    private void generateDueListPDF() {
        // Show loading message
        Toast.makeText(this, "Generating due list...", Toast.LENGTH_SHORT).show();

        if ("004".equals(StaffDirectory.getUserId(userName))) {
            generateDueListForKristine();
        } else {
            String tableName = userName + " Contracts";

            db.collection(tableName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Map<String, Object>> contractsList = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        contract.put("owner", userName);
                        contractsList.add(contract);
                    }
                    // Move heavy processing to background thread
                    new ProcessDueContractsAsyncTask().execute(contractsList, userName);
                } else {
                    Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Generates separate due list PDFs per contract technician when user is 004.
     */
    private void generateDueListForKristine() {
        String[] contractCollections = new String[StaffDirectory.CONTRACT_TECHNICIAN_IDS.length];
        for (int i = 0; i < StaffDirectory.CONTRACT_TECHNICIAN_IDS.length; i++)
            contractCollections[i] = StaffDirectory.getContractsCollectionName(StaffDirectory.CONTRACT_TECHNICIAN_IDS[i]);
        Map<String, List<Map<String, Object>>> technicianContracts = new HashMap<>();
        int[] loadedCount = {0};

        for (String collectionName : contractCollections) {
            db.collection(collectionName).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Map<String, Object>> techContracts = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        contract.put("owner", collectionName.replace(" Contracts", ""));
                        techContracts.add(contract);
                    }

                    String technician = collectionName.replace(" Contracts", "");
                    technicianContracts.put(technician, techContracts);
                } else {
                    Toast.makeText(this, "Failed to load " + collectionName, Toast.LENGTH_SHORT).show();
                }

                loadedCount[0]++;
                if (loadedCount[0] == contractCollections.length) {
                    // Generate PDFs for each technician using AsyncTask
                    for (String tech : technicianContracts.keySet()) {
                        List<Map<String, Object>> contracts = technicianContracts.get(tech);
                        new ProcessDueContractsAsyncTask().execute(contracts, tech);
                    }
                }
            });
        }
    }

    /**
     * Processes the loaded contracts to filter overdue ones and generate PDF.
     *
     * @param contractsList List of all contracts
     * @param technician The technician name for the PDF
     */
    private void processContractsForBehindsList(List<Map<String, Object>> contractsList, String technician) {
        List<Map<String, Object>> overdueContracts = new ArrayList<>();

        for (Map<String, Object> contract : contractsList) {
            String nextVisit = calculateNextVisit(contract);
            if (isPastDue(nextVisit)) {
                overdueContracts.add(contract);
            }
        }

        if (overdueContracts.isEmpty()) {
            Toast.makeText(this, "No overdue contracts found for " + technician + "!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate PDF
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            File pdfFile = BehindsListPDFGenerator.generateBehindsListPDF(technician, overdueContracts, this);
            if (pdfFile != null) {
                showPdfOptions(pdfFile, technician);
            } else {
                Toast.makeText(this, "Failed to generate PDF for " + technician + "!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "PDF generation requires Android 13 or higher", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows options to view or share the generated PDF.
     *
     * @param pdfFile The generated PDF file
     * @param technician The technician name
     */
    private void showPdfOptions(File pdfFile, String technician) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Behinds List PDF Generated")
                .setMessage("PDF has been created successfully for " + technician + ". What would you like to do?")
                .setPositiveButton("View PDF", (dialog, which) -> {
                    viewPDF(pdfFile);
                })
                .setNegativeButton("Share PDF", (dialog, which) -> {
                    sharePDF(pdfFile);
                })
                .setNeutralButton("Close", null)
                .show();
    }

    /**
     * Shows options to view or share the generated due list PDF.
     *
     * @param pdfFile The generated PDF file
     * @param technician The technician name
     */
    private void showDuePdfOptions(File pdfFile, String technician) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Due List PDF Generated")
                .setMessage("Due list PDF has been created successfully for " + technician + ". What would you like to do?")
                .setPositiveButton("View PDF", (dialog, which) -> {
                    viewPDF(pdfFile);
                })
                .setNegativeButton("Share PDF", (dialog, which) -> {
                    sharePDF(pdfFile);
                })
                .setNeutralButton("Close", null)
                .show();
    }

    /**
     * Opens the PDF file using a PDF viewer app.
     *
     * @param file The PDF file to view
     */
    private void viewPDF(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    file
            );

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(viewIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No application found to view this PDF.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shares the PDF file using an Intent.
     *
     * @param file The PDF file to share
     */
    private void sharePDF(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Behinds List"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the PDF.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Calculates the next visit date based on contract data.
     * This method replicates the logic from ViewContractActivity.
     *
     * @param contract The contract data map
     * @return The calculated next visit date as a string
     */
    private String calculateNextVisit(Map<String, Object> contract) {
        SimpleDateFormat shortYearFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        int visits = contract.get("visits") != null ? Integer.parseInt(contract.get("visits").toString()) : 0;

        if ("N/A".equals(lastVisit) || visits == 0) {
            return "N/A";
        }

        Calendar calendar = Calendar.getInstance();
        try {
            calendar.setTime(dateFormat.parse(lastVisit));
        } catch (Exception e) {
            return "Invalid Date";
        }

        // Adjust the next visit date based on the number of visits
        switch (visits) {
            case 8:
                calendar.add(Calendar.WEEK_OF_YEAR, 6);
                break;
            case 12:
                calendar.add(Calendar.WEEK_OF_YEAR, 4);
                break;
            case 6:
                calendar.add(Calendar.WEEK_OF_YEAR, 8);
                break;
            case 4:
                calendar.add(Calendar.WEEK_OF_YEAR, 12);
                break;
            default:
                return "N/A";
        }

        return shortYearFormat.format(calendar.getTime());
    }

    /**
     * Checks if a contract is past due based on the next visit date.
     *
     * @param nextVisit The next visit date string
     * @return True if the contract is past due, false otherwise
     */
    private boolean isPastDue(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return true; // Consider past due if missing
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            return nextVisitDate.before(currentDate); // True if next visit date has passed
        } catch (Exception e) {
            return true; // Assume past due if parsing fails
        }
    }

    /**
     * Checks if a contract is due within 7 days.
     *
     * @param nextVisit The next visit date string
     * @return True if the contract is due within 7 days, false otherwise
     */
    private boolean isDueSoon(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return false; // Not due soon if no date
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();
            
            // Calculate 7 days from now
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(currentDate);
            calendar.add(Calendar.DAY_OF_YEAR, 7);
            Date sevenDaysFromNow = calendar.getTime();

            // Check if next visit is within the next 7 days but not past due
            return !nextVisitDate.before(currentDate) && !nextVisitDate.after(sevenDaysFromNow);
        } catch (Exception e) {
            return false; // Not due soon if parsing fails
        }
    }

    /**
     * AsyncTask to handle heavy contract processing and PDF generation on background thread
     */
    private class ProcessContractsAsyncTask extends AsyncTask<Object, Void, File> {
        private String technician;
        private List<Map<String, Object>> overdueContracts;

        @Override
        protected void onPreExecute() {
            // Show progress dialog or loading indicator
            Toast.makeText(ContractsActivity.this, "Processing contracts for " + technician + "...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected File doInBackground(Object... params) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contractsList = (List<Map<String, Object>>) params[0];
                technician = (String) params[1];

                // Process contracts on background thread
                overdueContracts = new ArrayList<>();
                for (Map<String, Object> contract : contractsList) {
                    String nextVisit = calculateNextVisit(contract);
                    if (isPastDue(nextVisit)) {
                        overdueContracts.add(contract);
                    }
                }

                if (overdueContracts.isEmpty()) {
                    return null;
                }

                // Generate PDF on background thread
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return BehindsListPDFGenerator.generateBehindsListPDF(technician, overdueContracts, ContractsActivity.this);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(File pdfFile) {
            if (overdueContracts.isEmpty()) {
                Toast.makeText(ContractsActivity.this, "No overdue contracts found for " + technician + "!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (pdfFile != null) {
                showPdfOptions(pdfFile, technician);
            } else {
                Toast.makeText(ContractsActivity.this, "Failed to generate PDF for " + technician + "!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * AsyncTask to handle heavy due contract processing and PDF generation on background thread
     */
    private class ProcessDueContractsAsyncTask extends AsyncTask<Object, Void, File> {
        private String technician;
        private List<Map<String, Object>> dueContracts;

        @Override
        protected void onPreExecute() {
            // Show progress dialog or loading indicator
            Toast.makeText(ContractsActivity.this, "Processing due contracts for " + technician + "...", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected File doInBackground(Object... params) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contractsList = (List<Map<String, Object>>) params[0];
                technician = (String) params[1];

                // Process contracts on background thread
                dueContracts = new ArrayList<>();
                for (Map<String, Object> contract : contractsList) {
                    String nextVisit = calculateNextVisit(contract);
                    if (isDueSoon(nextVisit)) {
                        dueContracts.add(contract);
                    }
                }

                if (dueContracts.isEmpty()) {
                    return null;
                }

                // Generate PDF on background thread
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return DueListPDFGenerator.generateDueListPDF(technician, dueContracts, ContractsActivity.this);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(File pdfFile) {
            if (dueContracts.isEmpty()) {
                Toast.makeText(ContractsActivity.this, "No due contracts found for " + technician + "!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (pdfFile != null) {
                showDuePdfOptions(pdfFile, technician);
            } else {
                Toast.makeText(ContractsActivity.this, "Failed to generate due PDF for " + technician + "!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
