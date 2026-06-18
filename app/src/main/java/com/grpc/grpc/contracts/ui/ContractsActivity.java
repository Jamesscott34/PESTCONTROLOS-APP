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
 *    - Special handling for admin/oversight access
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
 * - Admin users: Access to multiple contract collections
 * - Administrators: Full contract management and reporting access
 * - All users: Contract viewing and basic management capabilities
 * 
 * Author: GRPC
 * Company: [Company 1]
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

package com.grpc.grpc.contracts.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.contracts.pdf.BehindsListPDFGenerator;
import com.grpc.grpc.contracts.pdf.DueListPDFGenerator;
import com.grpc.grpc.main.MainActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ContractsActivity extends AppCompatActivity {
    
    // ============================================================================
    // UI COMPONENTS - Navigation and action buttons
    // ============================================================================
    
    private Button addContractButton, viewContractButton, behindsListButton, dueListButton, exportContractsExcelButton, viewBehindsButton;
    
    // ============================================================================
    // DATA MANAGEMENT - User context and database operations
    // ============================================================================
    
    private String userName;
    private FirebaseFirestore db;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private GestureDetectorCompat gestureDetector;
    private static final String[] CONTRACT_EXCEL_FIXED_HEADERS = {
            "Name", "Address", "Email", "Contact", "Visits", "Last Visit", "Next Visit",
            "Assigned Tech", "Status"
    };
    private static final String[] CONTRACT_EXCEL_FIXED_KEYS = {
            "name", "address", "email", "contact", "visits", "lastVisit", "nextVisit",
            "assignedTech", "status"
    };

    /** Debounce flag to avoid double-opening ViewContractActivity on fast taps. */
    private boolean viewContractClickInProgress = false;
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
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

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
        exportContractsExcelButton = findViewById(R.id.ExportContractsExcelButton);
        viewBehindsButton = findViewById(R.id.ViewBehindsButton);
        if (exportContractsExcelButton != null) {
            exportContractsExcelButton.setVisibility(View.GONE);
        }
        if (viewBehindsButton != null) {
            // "View Behinds" has been moved to View Reports hub for a single, cleaner document-management entry point.
            viewBehindsButton.setVisibility(View.GONE);
        }
    }

    /**
     * Set up the welcome message TextView with the user's name
     */
    private void setupWelcomeMessage() {
        android.widget.TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                if (welcomeTextView == null) return;
                String name = SessionManager.getName(this);
                if (name != null && !name.trim().isEmpty()) {
                    welcomeTextView.setText("Welcome, " + name.trim() + "!");
                }
            }));
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

        // View Contract Button - Navigate to contract management (debounced)
        viewContractButton.setOnClickListener(v -> {
            if (viewContractClickInProgress) {
                return;
            }
            viewContractClickInProgress = true;
            v.postDelayed(() -> viewContractClickInProgress = false, 600);

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

        exportContractsExcelButton.setOnClickListener(v -> {
            exportContractsToExcel();
        });
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (exportContractsExcelButton != null) {
                exportContractsExcelButton.setVisibility(SessionManager.isAdmin(this) ? View.VISIBLE : View.GONE);
            }
        }));

        // View-behinds management moved to View Reports hub.
    }

    private void exportContractsToExcel() {
        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                String msg = task.getException() != null ? task.getException().getMessage() : "unknown error";
                Toast.makeText(this, "Failed to load contracts: " + msg, Toast.LENGTH_SHORT).show();
                return;
            }

            List<Map<String, Object>> contracts = new ArrayList<>();
            Set<String> years = new TreeSet<>();
            for (QueryDocumentSnapshot document : task.getResult()) {
                Map<String, Object> contract = document.getData();
                if (contract == null) {
                    continue;
                }
                Object assignedTechValue = contract.get("assignedTech");
                String assignedTech = assignedTechValue != null ? assignedTechValue.toString().trim() : "";
                if (assignedTech.isEmpty()) {
                    continue;
                }

                contracts.add(contract);
                for (String key : contract.keySet()) {
                    String suffix = getCounterYearSuffix(key);
                    if (suffix != null) {
                        years.add(suffix);
                    }
                }
            }

            if (contracts.isEmpty()) {
                Toast.makeText(this, "No contracts found in shared collection.", Toast.LENGTH_SHORT).show();
                return;
            }

            File externalFilesDir = getExternalFilesDir(null);
            if (externalFilesDir == null) {
                Toast.makeText(this, "Unable to access export folder", Toast.LENGTH_SHORT).show();
                return;
            }
            File exportDir = new File(externalFilesDir, "BEHINDS LIST");
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                Toast.makeText(this, "Unable to create export folder", Toast.LENGTH_SHORT).show();
                return;
            }

            String dateStamp = new SimpleDateFormat("dd-MM-yy", Locale.getDefault()).format(new Date());
            File file = new File(exportDir, "Contract_list_" + dateStamp + ".xlsx");

            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                List<String> headers = new ArrayList<>();
                for (String header : CONTRACT_EXCEL_FIXED_HEADERS) {
                    headers.add(header);
                }
                for (String year : years) {
                    headers.add("Routines 20" + year);
                    headers.add("Callouts 20" + year);
                }

                List<List<String>> rows = new ArrayList<>();
                for (Map<String, Object> contract : contracts) {
                    List<String> row = new ArrayList<>();
                    for (String key : CONTRACT_EXCEL_FIXED_KEYS) {
                        Object value = contract.get(key);
                        row.add(value != null ? String.valueOf(value) : "");
                    }
                    for (String year : years) {
                        row.add(String.valueOf(readCounterNumber(contract.get("Routines" + year))));
                        row.add(String.valueOf(readCounterNumber(contract.get("Callout" + year))));
                    }
                    rows.add(row);
                }
                writeXlsxWorkbook(zip, headers, rows);
            } catch (IOException e) {
                Toast.makeText(this, "Failed to export contracts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }

            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export Contracts Excel"));
        });
    }

    private String getCounterYearSuffix(String key) {
        if (key == null) {
            return null;
        }
        String suffix = null;
        if (key.startsWith("Routines") && key.length() == "Routines".length() + 2) {
            suffix = key.substring("Routines".length());
        } else if (key.startsWith("Callout") && key.length() == "Callout".length() + 2) {
            suffix = key.substring("Callout".length());
        }
        if (suffix != null && Character.isDigit(suffix.charAt(0)) && Character.isDigit(suffix.charAt(1))) {
            return suffix;
        }
        return null;
    }

    private int readCounterNumber(Object counterValue) {
        if (!(counterValue instanceof Map)) {
            return 0;
        }
        Object number = ((Map<?, ?>) counterValue).get("number");
        if (number instanceof Number) {
            return ((Number) number).intValue();
        }
        if (number instanceof String) {
            try {
                return Integer.parseInt(((String) number).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private void writeXlsxWorkbook(ZipOutputStream zip, List<String> headers, List<List<String>> rows) throws IOException {
        writeZipEntry(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                        + "</Types>");
        writeZipEntry(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");
        writeZipEntry(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"Contracts\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                        + "</workbook>");
        writeZipEntry(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                        + "</Relationships>");
        writeZipEntry(zip, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                        + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                        + "<borders count=\"1\"><border/></borders>"
                        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                        + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                        + "</styleSheet>");
        writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(headers, rows));
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String buildSheetXml(List<String> headers, List<List<String>> rows) {
        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sheet.append(buildColumnWidths(headers, rows));
        sheet.append("<sheetData>");
        appendXlsxRow(sheet, 1, headers);
        for (int i = 0; i < rows.size(); i++) {
            appendXlsxRow(sheet, i + 2, rows.get(i));
        }
        sheet.append("</sheetData></worksheet>");
        return sheet.toString();
    }

    private String buildColumnWidths(List<String> headers, List<List<String>> rows) {
        int columnCount = headers != null ? headers.size() : 0;
        int[] maxLengths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            maxLengths[i] = getCellDisplayLength(headers.get(i));
        }
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            for (int i = 0; i < columnCount && i < row.size(); i++) {
                maxLengths[i] = Math.max(maxLengths[i], getCellDisplayLength(row.get(i)));
            }
        }

        StringBuilder cols = new StringBuilder("<cols>");
        for (int i = 0; i < columnCount; i++) {
            double width = Math.min(Math.max(maxLengths[i] + 2, 10), 45);
            cols.append("<col min=\"").append(i + 1)
                    .append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(String.format(Locale.US, "%.1f", width))
                    .append("\" customWidth=\"1\"/>");
        }
        cols.append("</cols>");
        return cols.toString();
    }

    private int getCellDisplayLength(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String[] lines = value.split("\\r?\\n", -1);
        int max = 0;
        for (String line : lines) {
            max = Math.max(max, line.length());
        }
        return max;
    }

    private void appendXlsxRow(StringBuilder sheet, int rowIndex, List<String> values) {
        sheet.append("<row r=\"").append(rowIndex).append("\">");
        for (int i = 0; i < values.size(); i++) {
            String cellRef = getExcelColumnName(i + 1) + rowIndex;
            sheet.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t>")
                    .append(escapeExcelValue(values.get(i)))
                    .append("</t></is></c>");
        }
        sheet.append("</row>");
    }

    private String getExcelColumnName(int columnNumber) {
        StringBuilder name = new StringBuilder();
        int column = columnNumber;
        while (column > 0) {
            column--;
            name.insert(0, (char) ('A' + (column % 26)));
            column /= 26;
        }
        return name.toString();
    }

    private String escapeExcelValue(String value) {
        String excelValue = value != null ? value : "";
        return excelValue
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Generates a behinds list PDF containing all overdue contracts.
     * Loads contracts from Firebase, filters overdue ones, generates PDF, and shows options.
     */
    private void generateBehindsListPDF() {
        // Show loading message
        Toast.makeText(this, "Generating behinds list...", Toast.LENGTH_SHORT).show();

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (SessionManager.seesAllJobs(this)) {
                // Admin/oversight: use shared scalable contracts collection grouped by assignedTech.
                generateBehindsListForAdmin();
            } else {
                // Technician: load from shared contracts collection by assignedTech (contractKey, normalized to lowercase).
                String contractKey = SessionManager.getContractKey(this);
                if (contractKey == null || contractKey.trim().isEmpty()) {
                    String sid = SessionManager.getStaffId(this);
                    contractKey = sid != null ? sid.trim() : "";
                }
                if (contractKey == null || contractKey.trim().isEmpty()) {
                    Toast.makeText(this, "Could not resolve technician key for behinds list.", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String ownerLabel = contractKey.trim();
                final String keyFilter = ownerLabel.toLowerCase(Locale.getDefault());

                db.collection(FirestorePaths.CONTRACTS)
                        .whereEqualTo("assignedTech", keyFilter)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null) {
                                List<Map<String, Object>> contractsList = new ArrayList<>();
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    Map<String, Object> contract = document.getData();
                                    contract.put("documentId", document.getId());
                                    contract.put("owner", ownerLabel);
                                    contractsList.add(contract);
                                }
                                // Move heavy processing to background thread
                                new ProcessContractsAsyncTask().execute(contractsList, ownerLabel);
                            } else {
                                String msg = task.getException() != null ? task.getException().getMessage() : "unknown error";
                                Toast.makeText(this, "Failed to load contracts: " + msg, Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }));
    }

    /**
     * Generates separate behinds list PDFs per contract owner when user is admin/oversight.
     */
    private void generateBehindsListForAdmin() {
        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                String msg = task.getException() != null ? task.getException().getMessage() : "unknown error";
                Toast.makeText(this, "Failed to load contracts: " + msg, Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, List<Map<String, Object>>> technicianContracts = new HashMap<>();

            for (QueryDocumentSnapshot document : task.getResult()) {
                Map<String, Object> contract = document.getData();
                contract.put("documentId", document.getId());

                String assignedTech = contract.get("assignedTech") != null
                        ? contract.get("assignedTech").toString().trim()
                        : "";
                if (assignedTech.isEmpty()) {
                    continue;
                }
                String keyLower = assignedTech.toLowerCase(Locale.getDefault());
                String displayLabel = StaffDirectory.capitalizeContractKey(assignedTech);
                contract.put("owner", displayLabel);

                technicianContracts
                        .computeIfAbsent(keyLower, k -> new ArrayList<>())
                        .add(contract);
            }

            if (technicianContracts.isEmpty()) {
                Toast.makeText(this, "No contracts found in shared collection.", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : technicianContracts.entrySet()) {
                String keyLower = entry.getKey();
                List<Map<String, Object>> contracts = entry.getValue();
                String techLabel = StaffDirectory.capitalizeContractKey(keyLower);
                new ProcessContractsAsyncTask().execute(contracts, techLabel);
            }
        });
    }

    /**
     * Generates a due list PDF containing all contracts due within 7 days.
     * Loads contracts from Firebase, filters due ones, generates PDF, and shows options.
     */
    private void generateDueListPDF() {
        // Show loading message
        Toast.makeText(this, "Generating due list...", Toast.LENGTH_SHORT).show();

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (SessionManager.seesAllJobs(this)) {
                // Admin/oversight: use shared scalable contracts collection grouped by assignedTech.
                generateDueListForAdmin();
            } else {
                String contractKey = SessionManager.getContractKey(this);
                if (contractKey == null || contractKey.trim().isEmpty()) {
                    String sid = SessionManager.getStaffId(this);
                    contractKey = sid != null ? sid.trim() : "";
                }
                if (contractKey == null || contractKey.trim().isEmpty()) {
                    Toast.makeText(this, "Could not resolve technician key for due list.", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String ownerLabel = contractKey.trim();
                final String keyFilter = ownerLabel.toLowerCase(Locale.getDefault());

                db.collection(FirestorePaths.CONTRACTS)
                        .whereEqualTo("assignedTech", keyFilter)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null) {
                                List<Map<String, Object>> contractsList = new ArrayList<>();
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    Map<String, Object> contract = document.getData();
                                    contract.put("documentId", document.getId());
                                    contract.put("owner", ownerLabel);
                                    contractsList.add(contract);
                                }
                                // Move heavy processing to background thread
                                new ProcessDueContractsAsyncTask().execute(contractsList, ownerLabel);
                            } else {
                                String msg = task.getException() != null ? task.getException().getMessage() : "unknown error";
                                Toast.makeText(this, "Failed to load contracts: " + msg, Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }));
    }

    /**
     * Generates separate due list PDFs per contract owner when user is admin/oversight.
     */
    private void generateDueListForAdmin() {
        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                String msg = task.getException() != null ? task.getException().getMessage() : "unknown error";
                Toast.makeText(this, "Failed to load contracts: " + msg, Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, List<Map<String, Object>>> technicianContracts = new HashMap<>();

            for (QueryDocumentSnapshot document : task.getResult()) {
                Map<String, Object> contract = document.getData();
                contract.put("documentId", document.getId());

                String assignedTech = contract.get("assignedTech") != null
                        ? contract.get("assignedTech").toString().trim()
                        : "";
                if (assignedTech.isEmpty()) {
                    continue;
                }
                String keyLower = assignedTech.toLowerCase(Locale.getDefault());
                String displayLabel = StaffDirectory.capitalizeContractKey(assignedTech);
                contract.put("owner", displayLabel);

                technicianContracts
                        .computeIfAbsent(keyLower, k -> new ArrayList<>())
                        .add(contract);
            }

            if (technicianContracts.isEmpty()) {
                Toast.makeText(this, "No contracts found in shared collection.", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : technicianContracts.entrySet()) {
                String keyLower = entry.getKey();
                List<Map<String, Object>> contracts = entry.getValue();
                String techLabel = StaffDirectory.capitalizeContractKey(keyLower);
                new ProcessDueContractsAsyncTask().execute(contracts, techLabel);
            }
        });
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
                    BuildConfig.APPLICATION_ID + ".fileprovider",
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
                    BuildConfig.APPLICATION_ID + ".fileprovider",
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

        // Safely parse visits – treat non-numeric values (e.g. "N/A") as 0
        int visits = 0;
        Object visitsObj = contract.get("visits");
        if (visitsObj != null) {
            String visitsStr = visitsObj.toString().trim();
            if (!visitsStr.isEmpty() && !"N/A".equalsIgnoreCase(visitsStr)) {
                try {
                    visits = Integer.parseInt(visitsStr);
                } catch (NumberFormatException ignored) {
                    visits = 0;
                }
            }
        }

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

