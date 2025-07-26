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
 *    - Special handling for Kristine user (dual collection access)
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
 * - Kristine: Access to both Ian and James contract collections
 * - Administrators: Full contract management and reporting access
 * - All users: Contract viewing and basic management capabilities
 * 
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

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
    
    private Button addContractButton, viewContractButton, behindsListButton, viewBehindsButton;
    
    // ============================================================================
    // DATA MANAGEMENT - User context and database operations
    // ============================================================================
    
    private String userName;
    private FirebaseFirestore db;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

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
    }

    /**
     * Initialize all UI button components by finding them in the layout
     * and setting up their basic properties
     */
    private void initializeButtons() {
        addContractButton = findViewById(R.id.AddContractButton);
        viewContractButton = findViewById(R.id.ViewContractButton);
        behindsListButton = findViewById(R.id.BehindsListButton);
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

        if ("Kristine".equalsIgnoreCase(userName)) {
            // For Kristine, generate separate PDFs for Ian and James
            generateBehindsListForKristine();
        } else {
            // Default load for Ian or James
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
                    processContractsForBehindsList(contractsList, userName);
                } else {
                    Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Generates separate behinds list PDFs for Ian and James when Kristine is the user.
     */
    private void generateBehindsListForKristine() {
        String[] contractCollections = {"Ian Contracts", "James Contracts"};
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
                    // Generate PDFs for each technician
                    for (String tech : technicianContracts.keySet()) {
                        List<Map<String, Object>> contracts = technicianContracts.get(tech);
                        processContractsForBehindsList(contracts, tech);
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
}
