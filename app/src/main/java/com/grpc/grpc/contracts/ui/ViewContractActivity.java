package com.grpc.grpc.contracts.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.billing.ui.CreateInvoiceActivity;
import com.grpc.grpc.core.*;
import com.grpc.grpc.contracts.pdf.ContractListPDFGenerator;
import com.grpc.grpc.email.ui.EmailComposeActivity;
import com.grpc.grpc.main.MainActivity;
import com.grpc.grpc.maps.ui.MapsPlaceholderActivity;
import com.grpc.grpc.era.ui.BirdProofingERAActivity;
import com.grpc.grpc.reports.ui.ActionFormActivity;
import com.grpc.grpc.location.LocationSharing;
import com.grpc.grpc.messaging.NotificationUtils;
import com.grpc.grpc.reports.ui.ReportActivity;
import com.grpc.grpc.safety.ui.SafetyStatementActivity;
import com.grpc.grpc.jobs.rodent.RodentRoutineActivity;
import com.grpc.grpc.jobs.rodent.RodentActivityRoutine;
import com.grpc.grpc.jobs.rodent.RodentCallOutActivity;
import com.grpc.grpc.jobs.rodent.RodentCallOutExternalActivity;
import com.grpc.grpc.search.ui.SearchActivity;
import com.grpc.grpc.workview.ui.WorkViewActivity;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.content.FileProvider;
import androidx.core.view.GestureDetectorCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

/**
 * ============================================================================
 * GRPest Control Application - Contract Management Activity
 * ============================================================================
 * 
 * BUSINESS OVERVIEW:
 * This activity serves as the central hub for contract management in the
 * GRPest Control application. It provides comprehensive functionality for
 * viewing, managing, and tracking service contracts with customers.
 * 
 * CORE FUNCTIONALITIES:
 * 
 * 1. CONTRACT DISPLAY & SEARCH
 *    - Loads contracts from Firebase Firestore based on user permissions
 *    - Real-time search functionality to filter contracts by name
 *    - Color-coded contract status (behind, due, up-to-date)
 *    - Dynamic contract list with detailed information
 * 
 * 2. CONTRACT STATUS TRACKING
 *    - Automatic calculation of next visit dates
 *    - Overdue contract identification and highlighting
 *    - Visit date tracking and updates
 *    - Contract completion status management
 * 
 * 3. ADMINISTRATIVE FEATURES
 *    - Contract editing and deletion (admin users only)
 *    - Contract transfer between technicians
 *    - Bulk contract management operations
 *    - Contract statistics and reporting
 * 
 * 4. REPORT GENERATION
 *    - Routine pest control reports
 *    - Call-out service reports
 *    - Initial setup reports
 *    - Contract-specific report viewing
 * 
 * 5. INTEGRATION FEATURES
 *    - Google Maps integration for location navigation
 *    - WhatsApp integration for customer communication
 *    - Firebase Storage for report management
 *    - Real-time data synchronization
 * 
 * 6. BEHINDS LIST MANAGEMENT
 *    - Automatic overdue contract detection
 *    - PDF generation for behinds list reports
 *    - Special handling for admin/oversight access
 *    - Contract-specific report viewing
 * 
 * USER ROLES & PERMISSIONS:
 * - Admin users: Full contract management
 * - Technicians: View and update their assigned contracts
 * - Sales Staff: View contract information and generate reports
 * 
 * TECHNICAL FEATURES:
 * - Firebase Firestore for real-time data
 * - Dynamic UI generation for contract display
 * - Date calculation and validation
 * - PDF generation and storage
 * - External app integration (Maps, WhatsApp)
 * 
 * Author: GRPC
 * Company: [Company 1]
 * Version: 1.0
 * Last Updated: 2024
 * ============================================================================
 */

public class ViewContractActivity extends AppCompatActivity {

    // UI Components for contract management interface
    private EditText searchBar;
    private LinearLayout contractsContainer;
    private LinearLayout technicianSelectorLayout;
    private Spinner technicianSpinner;
    private Button backButton;
    private Button exportPdfButton;

    /** Technician spinner values (UIDs + ALL). */
    private String[] technicianIdsForSpinner;
    private static final String TECH_ID_ALL = "ALL";
    /** Dynamic staff list for dropdowns (UID-based). */
    private List<UserRepository.AssignableUser> staffOptionsForSpinner = new ArrayList<>();
    /** All contracts loaded for the selected technician (for search filtering). */
    private List<Map<String, Object>> allLoadedContracts = new ArrayList<>();

    /** Sort order: 0=Name A-Z, 1=Name Z-A, 2=Next visit soonest, 3=Next visit latest, 4=Address A-Z. */
    private int sortOrder = 0;
    private Spinner sortSpinner;
    
    // Firebase Firestore instance for data management
    private FirebaseFirestore db;
    
    // Current user information for permissions and data filtering
    private String userName;
    private String userId; // StaffID (3 digits) when available
    // In-memory list of contracts currently displayed (for export to PDF)
    private List<Map<String, Object>> currentDisplayedContracts = new ArrayList<>();
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;
    
    // Date formatting utility for consistent date display
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    /** Optional override used by global search to pick a technician (contractKey e.g. "ian"). */
    public static final String EXTRA_TECHNICIAN_OVERRIDE = "TECHNICIAN_OVERRIDE";
    /** Optional: open this contract by document ID when coming from search. */
    public static final String EXTRA_OPEN_CONTRACT_ID = "OPEN_CONTRACT_ID";

    /** True after we have scrolled to the contract opened from search (avoid repeated scroll). */
    private boolean hasScrolledToOpenContract = false;

    /** Contract document IDs for which the current user has "Remind me" enabled (12h notifications). */
    private Set<String> contractIdsWithReminder = new HashSet<>();

    /**
     * Main entry point of the contract management activity
     * Initializes the user interface and loads contract data
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_contract);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        // Initialize Firebase with authentication
        FirebaseHelper.initialize();
        FirebaseHelper.ensureAuthentication();
        db = FirebaseHelper.getFirestore();
        
        // Test Firebase connection
        FirebaseHelper.testConnection();
        
        userName = getIntent().getStringExtra("USER_NAME");
        // Resolve to ContractKey or StaffID when missing so contract collection is stable (never full name).
        if (userName == null || userName.isEmpty()) {
            String ck = SessionManager.getContractKey(this);
            if (ck != null && !ck.trim().isEmpty()) {
                userName = StaffDirectory.capitalizeContractKey(ck.trim());
            } else {
                String sid = SessionManager.getStaffId(this);
                if (sid != null && !sid.trim().isEmpty()) userName = sid;
            }
        } else if (!userName.matches("\\d{3}") && !userName.contains(" ")) {
            userName = StaffDirectory.capitalizeContractKey(userName.trim());
        }
        userId = userName != null ? StaffDirectory.getUserId(userName) : null;
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Log.d("ViewContractActivity", "ViewContractActivity created with user: " + userName);

        // Initialize UI components
        initializeUIComponents();

        // Resolve RBAC + authoritative StaffID, then ensure users/{uid} has profile (so Firestore contract rules can read effectiveContractKeyLower()), then load contracts.
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            String staffId = SessionManager.getStaffId(this);
            if (staffId != null && !staffId.trim().isEmpty()) {
                userId = staffId.trim();
            }
            applyTechnicianSelectorState();
            // Ensure users/{uid} exists with StaffID + contractKey before querying contracts (required for tech read permission).
            UserRepository.ensureProfileForCurrentUser(this, session, profile -> runOnUiThread(() -> {
                applyTechnicianSelectorState();
                // For non-admin, load now; for admin, load is triggered from spinner callback when staff list is ready (so search→contract works).
                if (!canSelectTechnician()) loadInitialContractsWithOverrides();
            }));
        }));

        // Optional: pre-fill search from global Search screen (only when not opening a specific contract)
        String openContractId = getIntent().getStringExtra(EXTRA_OPEN_CONTRACT_ID);
        if ((openContractId == null || openContractId.trim().isEmpty()) && searchBar != null) {
            String initialQuery = getIntent().getStringExtra(SearchActivity.EXTRA_SEARCH_QUERY);
            if (initialQuery != null && !initialQuery.trim().isEmpty()) {
                searchBar.setText(initialQuery.trim());
                searchBar.setSelection(searchBar.getText().length());
            }
        }

        // Apply current (possibly cached) RBAC state immediately (spinner visibility); contracts load after ensureProfileForCurrentUser above.
        applyTechnicianSelectorState();
        
        // Set up navigation and search functionality
        setupNavigationAndSearch();
        
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
                                // Swipe right - open WorkViewActivity
                                Log.d("ViewContractActivity", "Swipe RIGHT detected - opening WorkViewActivity with user: " + userName);
                                Intent intent = new Intent(ViewContractActivity.this, WorkViewActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            } else {
                                // Swipe left - open MainActivity (previous in sequence)
                                Log.d("ViewContractActivity", "Swipe LEFT detected - opening MainActivity with user: " + userName);
                                Intent intent = new Intent(ViewContractActivity.this, MainActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("ViewContractActivity", "Error in swipe detection: " + e.getMessage());
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

    /** RBAC: admins can select which technician's contracts to view. */
    private boolean canSelectTechnician() {
        SessionManager.ensureLoaded(this, null);
        return SessionManager.isAdmin(this);
    }

    /**
     * Initialize all UI components by finding them in the layout
     */
    private void initializeUIComponents() {
        searchBar = findViewById(R.id.searchBar);
        contractsContainer = findViewById(R.id.contractsContainer);
        technicianSelectorLayout = findViewById(R.id.technicianSelectorLayout);
        technicianSpinner = findViewById(R.id.technicianSpinner);
        sortSpinner = findViewById(R.id.sortSpinner);
        backButton = findViewById(R.id.backButton);
        exportPdfButton = findViewById(R.id.exportPdfButton);
        if (technicianSelectorLayout != null) technicianSelectorLayout.setVisibility(android.view.View.GONE);
    }

    private void applyTechnicianSelectorState() {
        if (technicianSelectorLayout == null || technicianSpinner == null) return;

        boolean isAdmin = canSelectTechnician();
        technicianSelectorLayout.setVisibility(isAdmin ? android.view.View.VISIBLE : android.view.View.GONE);

        if (!isAdmin) {
            technicianIdsForSpinner = null;
            staffOptionsForSpinner = new ArrayList<>();
            return;
        }

        // Admins: dropdown should be driven by UID-based users collection.
        ArrayAdapter<String> loading = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Loading..."});
        loading.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        technicianSpinner.setAdapter(loading);

        UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
            staffOptionsForSpinner = users != null ? users : new ArrayList<>();
            java.util.List<String> displayList = new ArrayList<>();
            java.util.List<String> idList = new ArrayList<>();
            for (UserRepository.AssignableUser u : staffOptionsForSpinner) {
                if (u == null) continue;
                if (u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                displayList.add(u.contractKey.trim());
                idList.add(u.uid != null ? u.uid : "");
            }
            // Add "All" option at end.
            displayList.add("All");
            idList.add(TECH_ID_ALL);

            technicianIdsForSpinner = idList.toArray(new String[0]);
            String[] display = displayList.toArray(new String[0]);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, display);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            technicianSpinner.setAdapter(adapter);

            // Skip the first onItemSelected when we set selection programmatically to avoid double load (flicker).
            final boolean[] skipNextSpinnerSelection = { true };
            technicianSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                    if (skipNextSpinnerSelection[0]) {
                        skipNextSpinnerSelection[0] = false;
                        return;
                    }
                    if (technicianIdsForSpinner == null || position < 0 || position >= technicianIdsForSpinner.length) return;
                    loadContractsForSelection(technicianIdsForSpinner[position]);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            // If opened from search with a technician override (contractKey e.g. "ian"), select that technician and load.
            String techOverride = getIntent().getStringExtra(EXTRA_TECHNICIAN_OVERRIDE);
            if (techOverride != null && !techOverride.trim().isEmpty()) {
                String overrideKey = techOverride.trim();
                for (int i = 0; i < staffOptionsForSpinner.size() && i < technicianIdsForSpinner.length; i++) {
                    UserRepository.AssignableUser u = staffOptionsForSpinner.get(i);
                    if (u == null || u.uid == null) continue;
                    if (u.contractKey != null && u.contractKey.trim().equalsIgnoreCase(overrideKey)) {
                        technicianSpinner.setSelection(i);
                        loadContractsForSelection(technicianIdsForSpinner[i]);
                        return;
                    }
                }
                for (int i = 0; i < staffOptionsForSpinner.size() && i < technicianIdsForSpinner.length; i++) {
                    UserRepository.AssignableUser u = staffOptionsForSpinner.get(i);
                    if (u != null && u.name != null && u.name.equalsIgnoreCase(overrideKey)) {
                        technicianSpinner.setSelection(i);
                        loadContractsForSelection(technicianIdsForSpinner[i]);
                        return;
                    }
                }
            }

            // Default selection: current auth user when present; else "All". (After listener so first setSelection does not trigger load.)
            String myUid = null;
            try {
                com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (u != null) myUid = u.getUid();
            } catch (Exception ignored) {}

            int defaultIndex = displayList.size() - 1; // All
            if (myUid != null) {
                for (int i = 0; i < technicianIdsForSpinner.length; i++) {
                    if (technicianIdsForSpinner[i] != null && technicianIdsForSpinner[i].equals(myUid)) {
                        defaultIndex = i;
                        break;
                    }
                }
            }
            technicianSpinner.setSelection(defaultIndex);
            // Load contracts now that spinner is ready (ensures search→contract opens correct tech's list).
            loadInitialContractsWithOverrides();
        }));
    }

    private void loadInitialContractsWithOverrides() {
        // Optional: select a specific technician (used by global search results; override is contractKey e.g. "ian")
        String techOverride = getIntent().getStringExtra(EXTRA_TECHNICIAN_OVERRIDE);
        if (canSelectTechnician() && technicianSpinner != null && techOverride != null && !techOverride.trim().isEmpty()) {
            String overrideKey = techOverride.trim();
            String uidToSelect = null;
            if (staffOptionsForSpinner != null) {
                for (UserRepository.AssignableUser u : staffOptionsForSpinner) {
                    if (u == null || u.uid == null) continue;
                    if (u.contractKey != null && u.contractKey.trim().equalsIgnoreCase(overrideKey)) {
                        uidToSelect = u.uid.trim();
                        break;
                    }
                    if (u.name != null && u.name.equalsIgnoreCase(overrideKey)) {
                        uidToSelect = u.uid.trim();
                        break;
                    }
                }
            }
            if (uidToSelect != null && !uidToSelect.isEmpty()) {
                trySelectTechnicianInSpinnerById(uidToSelect);
                return;
            }
        }

        if (canSelectTechnician() && technicianIdsForSpinner != null && technicianSpinner != null) {
            int pos = technicianSpinner.getSelectedItemPosition();
            if (pos >= 0 && pos < technicianIdsForSpinner.length) {
                loadContractsForSelection(technicianIdsForSpinner[pos]);
                return;
            }
        }
        // Tech/non-admin: load own contracts by assignedTechUid.
        String myUid = null;
        try {
            com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) myUid = u.getUid();
        } catch (Exception ignored) {}
        if (myUid != null && !myUid.trim().isEmpty()) {
            loadContractsForSelection(myUid.trim());
        } else {
            // Fallback to legacy behaviour if UID is unavailable.
            String contractKey = SessionManager.getContractKey(this);
            if (contractKey != null && !contractKey.trim().isEmpty() && !contractKey.trim().matches("\\d{3}")) {
                loadContractsForSelection(contractKey.trim());
            } else {
                loadContractsForSelection(userName != null ? userName.trim() : userId);
            }
        }
    }

    private void loadContractsForSelection(String techIdOrAll) {
        if (TECH_ID_ALL.equals(techIdOrAll)) {
            loadAllTechniciansContractsForDisplay();
        } else {
            loadContractsForTechnicianById(techIdOrAll);
        }
    }

    private void trySelectTechnicianInSpinnerById(String techId) {
        if (technicianSpinner == null || technicianIdsForSpinner == null || techId == null) return;
        for (int i = 0; i < technicianIdsForSpinner.length; i++) {
            if (techId.equals(technicianIdsForSpinner[i])) {
                technicianSpinner.setSelection(i);
                loadContractsForSelection(techId);
                return;
            }
        }
    }

    /**
     * Set up navigation back to contracts overview and search functionality
     */
    private void setupNavigationAndSearch() {
        // Back button navigation to contracts overview
        backButton.setOnClickListener(view -> {
            Intent intent = new Intent(ViewContractActivity.this, ContractsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });

        // Export button to generate a PDF of the currently visible contracts
        if (exportPdfButton != null) {
            exportPdfButton.setOnClickListener(v -> exportContractsToPDF());
        }

        // Real-time search functionality for contract filtering
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContracts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Sort order spinner
        if (sortSpinner != null) {
            ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(this,
                    R.array.contract_sort_options, android.R.layout.simple_spinner_item);
            sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sortSpinner.setAdapter(sortAdapter);
            sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    sortOrder = position;
                    applySearchAndDisplay();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    /**
     * Load contracts for a single technician by UID.
     * Also merges in legacy per-tech collection contracts for backward compatibility.
     */
    private void loadContractsForTechnicianById(String techUidOrLegacyKey) {
        if (db == null) {
            allLoadedContracts = new ArrayList<>();
            applySearchAndDisplay();
            return;
        }

        final List<Map<String, Object>> merged = Collections.synchronizedList(new ArrayList<>());

        // Resolve contractKey for this technician when possible.
        String contractKey = null;
        if (staffOptionsForSpinner != null) {
            for (UserRepository.AssignableUser u : staffOptionsForSpinner) {
                if (u != null && u.uid != null && u.uid.equals(techUidOrLegacyKey)) {
                    if (u.contractKey != null && !u.contractKey.trim().isEmpty()) {
                        contractKey = u.contractKey.trim();
                    }
                    break;
                }
            }
        }
        if (contractKey == null || contractKey.trim().isEmpty()) {
            String ck = SessionManager.getContractKey(this);
            if (ck != null && !ck.trim().isEmpty()) {
                contractKey = ck.trim();
            }
        }
        final String finalContractKey = contractKey != null ? contractKey.trim() : "";
        final String finalContractKeyLower = finalContractKey.toLowerCase(Locale.getDefault());

        // We now query contracts by normalized assignedTech only (contractKey lowercased).
        if (finalContractKey.isEmpty()) {
            // No contractKey available; nothing to load for this technician.
            finishContractsLoadForOwner(merged);
            return;
        }
        final int[] remaining = {1};

        // Shared collection by normalized assignedTech (contractKey lowercased).
        try {
            com.google.firebase.auth.FirebaseUser authUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String authUid = authUser != null ? authUser.getUid() : "null";
            SessionManager.Session session = SessionManager.getCached(this);
            String role = session != null ? session.roleNorm : "unknown";
            String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
            Log.d("ViewContractActivity", "Query contracts where assignedTech=" + finalContractKeyLower
                    + " (techIdOrAll=" + techUidOrLegacyKey
                    + ", authUid=" + authUid
                    + ", role=" + role
                    + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "") + ")");
        } catch (Exception e) {
            Log.w("ViewContractActivity", "Failed to log contracts query context: " + e.getMessage());
        }

        db.collection(FirestorePaths.CONTRACTS)
                .whereEqualTo("assignedTech", finalContractKeyLower)
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (!task.isSuccessful() && task.getException() != null) {
                            Log.e("ViewContractActivity", "Contracts query (assignedTech) failed: " + task.getException().getMessage(), task.getException());
                        }
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Map<String, Object> contract = document.getData();
                                contract.put("documentId", document.getId());
                                String owner = contract.get("assignedTech") != null
                                        ? contract.get("assignedTech").toString()
                                        : finalContractKey;
                                contract.put("owner", owner);
                                merged.add(contract);
                            }
                        }
                    } catch (Exception e) {
                        Log.w("ViewContractActivity", "Error loading shared contracts by assignedTech=" + finalContractKeyLower + ": " + e.getMessage());
                    } finally {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            finishContractsLoadForOwner(merged);
                        }
                    }
                });
    }

    private void finishContractsLoadForOwner(List<Map<String, Object>> merged) {
        allLoadedContracts = new ArrayList<>(merged);
        Log.d("ViewContractActivity", "Loaded " + allLoadedContracts.size() + " merged contracts for technician");
        sendDailyBehindSummaryIfNeeded(SessionManager.getName(this), allLoadedContracts);
        loadContractRemindersThenDisplay();
    }

    /** Apply current sort order to the list (mutates list). */
    private void applySortOrder(List<Map<String, Object>> list) {
        if (list == null) return;
        switch (sortOrder) {
            case 1: // Name Z-A
                list.sort((c1, c2) -> {
                    String name1 = c1.get("name") != null ? c1.get("name").toString() : "";
                    String name2 = c2.get("name") != null ? c2.get("name").toString() : "";
                    return name2.compareToIgnoreCase(name1);
                });
                break;
            case 2: // Next visit soonest first
                list.sort((c1, c2) -> {
                    String n1 = calculateNextVisit(c1);
                    String n2 = calculateNextVisit(c2);
                    long t1 = parseNextVisitForSort(n1);
                    long t2 = parseNextVisitForSort(n2);
                    return Long.compare(t1, t2);
                });
                break;
            case 3: // Next visit latest first
                list.sort((c1, c2) -> {
                    String n1 = calculateNextVisit(c1);
                    String n2 = calculateNextVisit(c2);
                    long t1 = parseNextVisitForSort(n1);
                    long t2 = parseNextVisitForSort(n2);
                    return Long.compare(t2, t1);
                });
                break;
            case 4: // Address A-Z
                list.sort((c1, c2) -> {
                    String a1 = c1.get("address") != null ? c1.get("address").toString() : "";
                    String a2 = c2.get("address") != null ? c2.get("address").toString() : "";
                    return a1.compareToIgnoreCase(a2);
                });
                break;
            default: // 0 = Name A-Z
                list.sort((c1, c2) -> {
                    String name1 = c1.get("name") != null ? c1.get("name").toString() : "";
                    String name2 = c2.get("name") != null ? c2.get("name").toString() : "";
                    return name1.compareToIgnoreCase(name2);
                });
                break;
        }
    }

    /** Parse next visit string to timestamp for sorting (N/A = Long.MAX so sorts last). */
    private long parseNextVisitForSort(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit.trim())) return Long.MAX_VALUE;
        try {
            SimpleDateFormat f = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date d = f.parse(nextVisit.trim());
            return d != null ? d.getTime() : Long.MAX_VALUE;
        } catch (Exception e) { return Long.MAX_VALUE; }
    }

    /** Apply current search filter to allLoadedContracts, sort, and display. */
    private void applySearchAndDisplay() {
        String query = searchBar != null && searchBar.getText() != null ? searchBar.getText().toString().toLowerCase().trim() : "";
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> contract : allLoadedContracts) {
            String name = contract.get("name") != null ? contract.get("name").toString().toLowerCase() : "";
            String address = contract.get("address") != null ? contract.get("address").toString().toLowerCase() : "";
            String contact = contract.get("contact") != null ? contract.get("contact").toString().toLowerCase() : "";
            String email = contract.get("email") != null ? contract.get("email").toString().toLowerCase() : "";

            boolean match = query.isEmpty()
                    || name.contains(query)
                    || address.contains(query)
                    || contact.contains(query)
                    || email.contains(query);

            if (match) {
                filtered.add(contract);
            }
        }
        applySortOrder(filtered);
        handleContractsData(filtered);

        // If opened from search with a specific contract id, scroll to that contract (do not open dialog).
        String openContractId = getIntent().getStringExtra(EXTRA_OPEN_CONTRACT_ID);
        if (openContractId != null && !openContractId.trim().isEmpty() && !hasScrolledToOpenContract) {
            hasScrolledToOpenContract = true;
            final String targetId = openContractId.trim();
            contractsContainer.post(() -> scrollToContractWithId(targetId));
        }
    }

    /** Scroll contracts list so the contract with this documentId is visible (opened from search). */
    private void scrollToContractWithId(String documentId) {
        if (contractsContainer == null) return;
        for (int i = 0; i < contractsContainer.getChildCount(); i++) {
            android.view.View child = contractsContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag != null && documentId.equals(tag.toString())) {
                child.requestFocus();
                child.post(() -> {
                    if (contractsContainer.getParent() instanceof android.widget.ScrollView) {
                        android.widget.ScrollView sv = (android.widget.ScrollView) contractsContainer.getParent();
                        int y = child.getTop();
                        sv.smoothScrollTo(0, Math.max(0, y - sv.getHeight() / 3));
                    }
                });
                break;
            }
        }
    }

    /** Admin "All": loads contracts from shared collection only and merges by technician. */
    private void loadAllTechniciansContractsForDisplay() {
        if (db == null) {
            allLoadedContracts = new ArrayList<>();
            applySearchAndDisplay();
            return;
        }

        Log.d("ViewContractActivity", "Loading ALL contracts for admin view from shared collection");
        final List<Map<String, Object>> merged = Collections.synchronizedList(new ArrayList<>());

        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            try {
                if (task.isSuccessful() && task.getResult() != null) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Map<String, Object> contract = document.getData();
                        contract.put("documentId", document.getId());
                        String owner = contract.get("assignedTech") != null
                                ? contract.get("assignedTech").toString()
                                : "Technician";
                        contract.put("owner", owner);
                        merged.add(contract);
                    }
                }
            } catch (Exception e) {
                Log.w("ViewContractActivity", "Error loading shared contracts for admin view: " + e.getMessage());
            } finally {
                allLoadedContracts = new ArrayList<>(merged);
                loadContractRemindersThenDisplay();
            }
        });
    }

    /** Load current user's contract reminders from Firestore, then refresh the list. */
    private void loadContractRemindersThenDisplay() {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || db == null) {
            contractIdsWithReminder.clear();
            runOnUiThread(this::applySearchAndDisplay);
            return;
        }
        String uid = user.getUid();
        db.collection(FirestorePaths.CONTRACT_REMINDERS)
                .whereEqualTo("userId", uid)
                .get()
                .addOnCompleteListener(task -> {
                    contractIdsWithReminder.clear();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            String cid = doc.getString("contractId");
                            if (cid != null && !cid.trim().isEmpty()) contractIdsWithReminder.add(cid.trim());
                        }
                    }
                    runOnUiThread(this::applySearchAndDisplay);
                });
    }

    private String[] getAllStaffIdsForContracts() {
        try {
            if (staffOptionsForSpinner != null && !staffOptionsForSpinner.isEmpty()) {
                String[] ids = new String[staffOptionsForSpinner.size()];
                for (int i = 0; i < staffOptionsForSpinner.size(); i++) {
                    UserRepository.AssignableUser u = staffOptionsForSpinner.get(i);
                    ids[i] = u != null && u.contractKey != null ? u.contractKey : "";
                }
                return ids;
            }
        } catch (Exception ignored) {}
        try {
            java.util.List<StaffDirectory.StaffProfile> profiles = StaffDirectory.getCachedStaffProfiles();
            java.util.ArrayList<String> ids = new java.util.ArrayList<>();
            for (StaffDirectory.StaffProfile p : profiles) {
                if (p == null) continue;
                String ck = p.contractKey != null ? p.contractKey.trim() : "";
                if (!ck.isEmpty()) ids.add(ck);
            }
            return ids.toArray(new String[0]);
        } catch (Exception ignored) {}
        return new String[0];
    }

    private void loadContracts() {
        Log.d("ViewContractActivity", "Loading contracts for user id: " + userId);
        if (canSelectTechnician()) {
            int pos = technicianSpinner != null ? technicianSpinner.getSelectedItemPosition() : 0;
            if (technicianIdsForSpinner != null && pos >= 0 && pos < technicianIdsForSpinner.length)
                loadContractsForSelection(technicianIdsForSpinner[pos]);
            else if (userName != null && !userName.trim().isEmpty())
                loadContractsForSelection(userName.trim());
        } else {
            // Tech: load own contracts by UID when available; fall back to legacy collections.
            String myUid = null;
            try {
                com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (u != null) myUid = u.getUid();
            } catch (Exception ignored) {}
            if (myUid != null && !myUid.trim().isEmpty()) {
                loadContractsForSelection(myUid.trim());
            } else {
                String key = SessionManager.getContractKey(this);
                if (key == null || key.trim().isEmpty() || key.trim().matches("\\d{3}")) key = userName;
                if (key == null || key.trim().isEmpty()) key = userId;
                loadContractsForSelection(key);
            }
        }
    }


    private void sendDailyBehindSummaryIfNeeded(String technician, List<Map<String, Object>> contracts) {
        SharedPreferences prefs = getSharedPreferences("ContractReminders", MODE_PRIVATE);
        String key = "sent_" + (technician != null ? technician.toLowerCase() : "tech");
        String lastSentDate = prefs.getString(key, "");

        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        if (today.equals(lastSentDate)) return; // Already sent today ❌

        // Filter overdue contracts
        List<String> overdueSummaries = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            String nextVisit = calculateNextVisit(contract);
            if (isPastDue(nextVisit)) {
                String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
                String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
                overdueSummaries.add("🔹 " + name + "\n📍 " + address + "\n📅 Next Visit: " + nextVisit);
            }
        }

        // WhatsApp notification removed: no longer open WhatsApp when user opens View Contract.
        // Optionally an in-app notification could be added here instead.
    }

    private void launchWhatsAppMessage(String phoneNumber, String message) {
        try {
            String formatted = phoneNumber.replaceFirst("^0", "353"); // Irish number formatting
            String url = "https://wa.me/" + formatted + "?text=" + Uri.encode(message);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch WhatsApp", Toast.LENGTH_SHORT).show();
        }
    }



    private void handleContractsData(List<Map<String, Object>> contractsList) {
        int totalContracts = contractsList.size();
        int behindContracts = 0;
        int dueContracts = 0;
        int upToDateContracts = 0;

        for (Map<String, Object> contract : contractsList) {
            String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
            String nextVisit = calculateNextVisit(contract);

            if ("N/A".equals(lastVisit) || isPastDue(nextVisit)) {
                behindContracts++; // Mark as behind if no last visit or next visit is overdue
            } else if (isDueSoon(nextVisit)) {
                dueContracts++; // Mark as due if it's within 7 days
            } else {
                upToDateContracts++; // Otherwise, it's up to date
            }
        }

        // Update UI with new statistics
        updateStatistics(totalContracts, behindContracts, dueContracts, upToDateContracts);

        // Sort is applied in applySortOrder before calling handleContractsData; no sort here.

        // Keep track of what is currently displayed (for PDF export)
        currentDisplayedContracts = new ArrayList<>(contractsList);

        // Clear and add updated contract views
        contractsContainer.removeAllViews();
        for (Map<String, Object> contract : contractsList) {
            Object docIdObj = contract.get("documentId");
            String documentId = docIdObj != null ? docIdObj.toString() : "";
            if (documentId.isEmpty()) continue;
            addContractToView(contract, documentId);
        }
    }

    private boolean isDueSoon(String nextVisit) {
        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return false; // Not due soon if missing
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            long diffInMillis = nextVisitDate.getTime() - currentDate.getTime();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            return diffInDays >= 0 && diffInDays < 7; // True if due in the next 7 days
        } catch (Exception e) {
            Log.e("DateError", "Error parsing next visit date: " + nextVisit, e);
            return false; // Assume not due if error occurs
        }
    }

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
            Log.e("DateError", "Error parsing next visit date: " + nextVisit, e);
            return true; // Assume past due if parsing fails
        }
    }




    private void filterContracts(String query) {
        // All users now use in-memory filtering (allLoadedContracts populated on load)
        applySearchAndDisplay();
    }

    private void displayContracts(List<Map<String, Object>> contracts) {
        // Update in-memory cache of visible contracts for export
        currentDisplayedContracts = new ArrayList<>(contracts);

        contractsContainer.removeAllViews();
        for (Map<String, Object> contract : contracts) {
            Object docIdObj = contract.get("documentId");
            String documentId = docIdObj != null ? docIdObj.toString() : "";
            if (documentId.isEmpty()) continue;
            addContractToView(contract, documentId);
        }
    }



    private void addContractToView(Map<String, Object> contract, String documentId) {
        LinearLayout contractBox = new LinearLayout(this);
        contractBox.setOrientation(LinearLayout.VERTICAL);
        contractBox.setPadding(16, 16, 16, 16);
        contractBox.setBackgroundResource(R.drawable.surface_frame);
        contractBox.setTag(documentId);

        String owner = contract.get("owner") != null ? contract.get("owner").toString() : "Unknown";
        String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
        String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
        String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
        String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
        String visits = contract.get("visits") != null ? contract.get("visits").toString() : "0";
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
        String notes = getOptionalContractNotes(contract);
        String nextVisit = calculateNextVisit(contract);

        // Determine background color based on nextVisit conditions
        int bgColor = getBackgroundColor(lastVisit, nextVisit);
        contractBox.setBackgroundColor(bgColor);

        TextView contractDetails = new TextView(this);
        contractDetails.setText(
                "Owner: " + owner + "\n" +
                        "Name: " + name + "\n" +
                        "Address: " + address + "\n" +
                        "Email: " + email + "\n" +
                        "Contact: " + contact + "\n" +
                        (notes.isEmpty() ? "" : "Notes: " + notes + "\n") +
                        "Last Visit: " + lastVisit + "\n" +
                        "Next Visit: " + nextVisit
        );
        // Improve readability on bright status highlights (yellow/red) by picking a high-contrast text color.
        int preferred = resolveColorAttr(android.R.attr.textColorPrimary, contractDetails.getCurrentTextColor());
        contractDetails.setTextColor(pickReadableTextColor(bgColor, preferred));

        // ✅ Add the "Mark as Routine" Checkbox
        CheckBox markDoneCheckBox = new CheckBox(this);
        markDoneCheckBox.setText("Mark as Routine");
        try {
            markDoneCheckBox.setTextColor(pickReadableTextColor(bgColor,
                    resolveColorAttr(android.R.attr.textColorPrimary, markDoneCheckBox.getCurrentTextColor())));
        } catch (Exception ignored) {}

        // Handle checkbox click event
        markDoneCheckBox.setOnClickListener(v -> {
            if (markDoneCheckBox.isChecked()) {
                // Ensure the correct technician's collection is updated
                showRoutinePopup(name, documentId, owner, visits, markDoneCheckBox);
            }
        });

        CheckBox markCallOutCheckBox = new CheckBox(this);
        markCallOutCheckBox.setText("Mark as Call Out");
        try {
            markCallOutCheckBox.setTextColor(pickReadableTextColor(bgColor,
                    resolveColorAttr(android.R.attr.textColorPrimary, markCallOutCheckBox.getCurrentTextColor())));
        } catch (Exception ignored) {}
        markCallOutCheckBox.setOnClickListener(v -> {
            if (markCallOutCheckBox.isChecked()) {
                showCallOutCounterPopup(name, documentId, markCallOutCheckBox);
            }
        });

        // ✅ "Remind me" checkbox: 12h in-app notification until unchecked
        CheckBox remindMeCheckBox = new CheckBox(this);
        remindMeCheckBox.setText("Remind me");
        try {
            remindMeCheckBox.setTextColor(pickReadableTextColor(bgColor,
                    resolveColorAttr(android.R.attr.textColorPrimary, remindMeCheckBox.getCurrentTextColor())));
        } catch (Exception ignored) {}
        remindMeCheckBox.setChecked(contractIdsWithReminder.contains(documentId));
        remindMeCheckBox.setOnClickListener(v -> {
            if (remindMeCheckBox.isChecked()) {
                addContractReminder(documentId, name, address, remindMeCheckBox);
            } else {
                removeContractReminder(documentId, remindMeCheckBox);
            }
        });

        // Click Listener for Showing Contract Options
        contractBox.setOnClickListener(v -> {
            // Show contract options dialog
            showContractOptions(contract, lastVisit, documentId);
        });

        // Long Click Listener for Edit/Delete Dialog — RBAC (admin / permission flag)
        contractBox.setOnLongClickListener(v -> {
            SessionManager.ensureLoaded(this, null);
            if (SessionManager.canHardPressContracts(this) || SessionManager.isAdmin(this)) {
                showEditOrDeleteDialog(documentId, contract);
            } else {
                Toast.makeText(this, "You do not have permission to edit or delete this contract.", Toast.LENGTH_SHORT).show();
            }
            return true; // Indicate that the long press was handled
        });

        // Add "View Reports" button
        Button viewReportsButton = new Button(this);
        viewReportsButton.setText("View Reports");
        viewReportsButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        viewReportsButton.setOnClickListener(v -> {
            showReportYearPickerAndOpen(name, documentId);
        });

        Button viewMapsButton = new Button(this);
        viewMapsButton.setText("View Maps");
        viewMapsButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        viewMapsButton.setOnClickListener(v -> {
            Intent mapsIntent = new Intent(ViewContractActivity.this, MapsPlaceholderActivity.class);
            mapsIntent.putExtra("USER_NAME", userName);
            mapsIntent.putExtra("CONTRACT_ID", documentId);
            mapsIntent.putExtra("COMPANY_NAME", name);
            mapsIntent.putExtra("ADDRESS", address);
            startActivity(mapsIntent);
        });

        Button viewVisitsButton = new Button(this);
        viewVisitsButton.setText("View Visits");
        viewVisitsButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        viewVisitsButton.setOnClickListener(v -> showVisitsDialog(contract, documentId));

        // ✅ Add views to contract box
        contractBox.addView(contractDetails);
        contractBox.addView(markDoneCheckBox);
        contractBox.addView(markCallOutCheckBox);
        contractBox.addView(remindMeCheckBox);
        contractBox.addView(viewReportsButton);
        contractBox.addView(viewMapsButton);
        contractBox.addView(viewVisitsButton);

        // ✅ Add the contract box to the container
        contractsContainer.addView(contractBox);
    }

    /**
     * Shows a year picker based on available Firebase Storage folders (Reports25/Reports26/...).
     * If storage folders can't be listed, falls back to current year and previous year.
     */
    private void showReportYearPickerAndOpen(String contractName, String contractId) {
        if (ContractReportSync.useContractReportsOnly() && ContractReportSync.hasContractId(contractId)) {
            Intent intent = new Intent(ViewContractActivity.this, ContractReportsActivity.class);
            intent.putExtra("CONTRACT_ID", contractId);
            intent.putExtra("CONTRACT_NAME", contractName);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            return;
        }

        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("View Reports")
                .setMessage("Checking available years…")
                .setCancelable(false)
                .show();

        StorageReference root = FirebaseStorage.getInstance().getReference();
        root.listAll().addOnSuccessListener(result -> {
            loading.dismiss();

            List<YearFolder> yearFolders = new ArrayList<>();
            for (StorageReference prefix : result.getPrefixes()) {
                String name = prefix.getName(); // e.g. Reports26
                YearFolder yf = YearFolder.tryParse(name);
                if (yf != null) yearFolders.add(yf);
            }

            final List<YearFolder> finalYearFolders;
            if (yearFolders.isEmpty()) {
                finalYearFolders = buildFallbackYears();
            } else {
                Collections.sort(yearFolders, (a, b) -> Integer.compare(b.year, a.year)); // newest first
                finalYearFolders = yearFolders;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select report year")
                    .setItems(buildReportPickerLabels(finalYearFolders, contractId), (d, which) ->
                            openSelectedReportPickerOption(which, finalYearFolders, contractName, contractId))
                    .setNegativeButton("Cancel", null)
                    .show();
        }).addOnFailureListener(e -> {
            loading.dismiss();
            List<YearFolder> fallback = buildFallbackYears();

            new AlertDialog.Builder(this)
                    .setTitle("Select report year")
                    .setMessage("Could not list Storage folders. Showing default years.")
                    .setItems(buildReportPickerLabels(fallback, contractId), (d, which) ->
                            openSelectedReportPickerOption(which, fallback, contractName, contractId))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private String[] buildReportPickerLabels(List<YearFolder> yearFolders, String contractId) {
        int extra = ContractReportSync.hasContractId(contractId) ? 1 : 0;
        String[] labels = new String[yearFolders.size() + extra];
        int index = 0;
        if (extra == 1) {
            labels[index++] = "Contracts";
        }
        for (YearFolder folder : yearFolders) {
            labels[index++] = String.valueOf(folder.year);
        }
        return labels;
    }

    private void openSelectedReportPickerOption(int which, List<YearFolder> yearFolders, String contractName, String contractId) {
        boolean hasContractReports = ContractReportSync.hasContractId(contractId);
        if (hasContractReports && which == 0) {
            Intent intent = new Intent(ViewContractActivity.this, ContractReportsActivity.class);
            intent.putExtra("CONTRACT_ID", contractId);
            intent.putExtra("CONTRACT_NAME", contractName);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("OPEN_CONTRACT_FOLDER_ONLY", true);
            startActivity(intent);
            return;
        }

        int yearIndex = hasContractReports ? which - 1 : which;
        if (yearIndex < 0 || yearIndex >= yearFolders.size()) return;

        YearFolder selected = yearFolders.get(yearIndex);
        Intent intent = new Intent(ViewContractActivity.this, ContractReportsActivity.class);
        intent.putExtra("CONTRACT_ID", contractId);
        intent.putExtra("CONTRACT_NAME", contractName);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("REPORTS_FOLDER", selected.folderName);
        intent.putExtra("REPORT_YEAR", selected.year);
        startActivity(intent);
    }

    private List<YearFolder> buildFallbackYears() {
        int y = Calendar.getInstance().get(Calendar.YEAR);
        List<YearFolder> out = new ArrayList<>();
        out.add(new YearFolder(y, "Reports" + String.format(Locale.getDefault(), "%02d", y % 100)));
        out.add(new YearFolder(y - 1, "Reports" + String.format(Locale.getDefault(), "%02d", (y - 1) % 100)));
        return out;
    }

    private static class YearFolder {
        final int year;
        final String folderName;

        YearFolder(int year, String folderName) {
            this.year = year;
            this.folderName = folderName;
        }

        static YearFolder tryParse(String folder) {
            if (folder == null) return null;
            if (!folder.startsWith("Reports")) return null;
            String suffix = folder.substring("Reports".length());
            if (suffix.isEmpty()) return null;
            // Support Reports26 and Reports2026
            try {
                int raw = Integer.parseInt(suffix);
                int year = (suffix.length() <= 2) ? (2000 + raw) : raw;
                if (year < 2000 || year > 2100) return null;
                return new YearFolder(year, folder);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private int getBackgroundColor(String lastVisit, String nextVisit) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault()); // Adjust to match Firestore format

        if (lastVisit == null || lastVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(lastVisit)) {
            return Color.RED; // Red if last visit is missing
        }

        if (nextVisit == null || nextVisit.trim().isEmpty() || "N/A".equalsIgnoreCase(nextVisit)) {
            return Color.RED; // Red if next visit is missing
        }

        try {
            Date nextVisitDate = dateFormat.parse(nextVisit);
            Date currentDate = new Date();

            if (nextVisitDate.before(currentDate)) {
                return Color.RED; // Red if next visit date has already passed
            }

            long diffInMillis = nextVisitDate.getTime() - currentDate.getTime();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            if (diffInDays < 7) {
                return Color.YELLOW; // Yellow if next visit is within 7 days
            }
        } catch (Exception e) {
            Log.e("DateError", "Error parsing next visit date: " + nextVisit, e);
            return Color.RED; // Default to red if there's an error
        }

        // Default background should respect theme in dark mode
        return resolveColorAttr(com.google.android.material.R.attr.colorSurface,
                resolveColorAttr(android.R.attr.colorBackground, 0));
    }

    private String buildYearlyCounterKey(String prefix) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        return prefix + String.format(Locale.getDefault(), "%02d", year % 100);
    }

    private String buildYearlyCounterKey(String prefix, String visitDate) {
        String suffix = extractTwoDigitYear(visitDate);
        return prefix + (suffix.isEmpty() ? String.format(Locale.getDefault(), "%02d", Calendar.getInstance().get(Calendar.YEAR) % 100) : suffix);
    }

    private String extractTwoDigitYear(String visitDate) {
        if (visitDate == null) return "";
        String trimmed = visitDate.trim();
        if (trimmed.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
            return trimmed.substring(trimmed.length() - 2);
        }
        if (trimmed.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            return trimmed.substring(trimmed.length() - 2);
        }
        return "";
    }

    private int readCounterNumber(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private void incrementYearlyCounter(String documentId, String prefix, String label, Runnable onComplete) {
        incrementYearlyCounter(documentId, prefix, label, null, onComplete);
    }

    private void incrementYearlyCounter(String documentId, String prefix, String label, String visitDate, Runnable onComplete) {
        if (db == null || documentId == null || documentId.trim().isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        DocumentReference ref = db.collection(FirestorePaths.CONTRACTS).document(documentId);
        String counterKey = visitDate != null && !visitDate.trim().isEmpty()
                ? buildYearlyCounterKey(prefix, visitDate)
                : buildYearlyCounterKey(prefix);
        String numberPath = counterKey + ".number";

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(ref);
            int current = readCounterNumber(snapshot.get(numberPath));
            transaction.update(ref, numberPath, current + 1);
            return current + 1;
        }).addOnSuccessListener(value -> {
            Toast.makeText(this, label + " counter updated.", Toast.LENGTH_SHORT).show();
            if (onComplete != null) onComplete.run();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update " + label.toLowerCase(Locale.getDefault()) + " counter: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (onComplete != null) onComplete.run();
        });
    }

    private void showVisitsDialog(Map<String, Object> loadedContract, String documentId) {
        if (db == null || documentId == null || documentId.trim().isEmpty()) {
            showVisitsDialogFromData(loadedContract);
            return;
        }

        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("Visit History")
                .setMessage("Loading history...")
                .setCancelable(false)
                .show();

        db.collection(FirestorePaths.CONTRACTS)
                .document(documentId)
                .collection("visitHistory")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshot -> {
                    loading.dismiss();
                    if (snapshot == null || snapshot.isEmpty()) {
                        // Fall back to yearly counter totals if no history entries yet
                        showVisitsDialogFromData(loadedContract);
                        return;
                    }

                    // Build a scrollable list of visit entries
                    android.widget.LinearLayout listLayout = new android.widget.LinearLayout(this);
                    listLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    listLayout.setPadding(32, 16, 32, 16);

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshot) {
                        String type = doc.getString("type") != null ? doc.getString("type") : "Visit";
                        String date = doc.getString("date") != null ? doc.getString("date") : "Unknown date";
                        String tech = doc.getString("tech") != null ? doc.getString("tech") : "";

                        String label = "● " + type + "  –  " + date
                                + (tech.isEmpty() ? "" : "  (" + tech + ")");

                        android.widget.TextView entry = new android.widget.TextView(this);
                        entry.setText(label);
                        entry.setTextSize(14f);
                        entry.setPadding(0, 10, 0, 10);
                        listLayout.addView(entry);

                        // Divider
                        android.view.View divider = new android.view.View(this);
                        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                        divider.setBackgroundColor(android.graphics.Color.LTGRAY);
                        listLayout.addView(divider);
                    }

                    android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
                    scrollView.addView(listLayout);

                    // Fixed height so the dialog doesn't fill the screen
                    int maxHeightPx = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
                    scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, maxHeightPx));

                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Visit History (" + snapshot.size() + " entries)")
                            .setView(scrollView)
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    loading.dismiss();
                    showVisitsDialogFromData(loadedContract);
                });
    }

    private void writeVisitHistoryEntry(String documentId, String type, String date) {
        if (db == null || documentId == null || documentId.trim().isEmpty()) return;
        try {
            java.util.Map<String, Object> entry = new java.util.HashMap<>();
            entry.put("type", type);
            entry.put("date", date);
            entry.put("tech", SessionManager.getName(this));
            entry.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
            db.collection(FirestorePaths.CONTRACTS)
                    .document(documentId)
                    .collection("visitHistory")
                    .add(entry)
                    .addOnFailureListener(e -> Log.w("VisitHistory",
                            "Failed to write visit history entry: " + e.getMessage()));
        } catch (Exception e) {
            Log.w("VisitHistory", "Error writing visit history: " + e.getMessage());
        }
    }

    private void showVisitsDialogFromData(Map<String, Object> contract) {
        String routineLines = buildCounterDisplayLines(contract, "Routines", "Routine");
        String callOutLines = buildCounterDisplayLines(contract, "Callout", "Callout");

        StringBuilder message = new StringBuilder();
        message.append("Routine visits:\n");
        message.append(routineLines.isEmpty() ? "No routine visits recorded." : routineLines);
        message.append("\n\nCall outs:\n");
        message.append(callOutLines.isEmpty() ? "No call outs recorded." : callOutLines);

        new AlertDialog.Builder(this)
                .setTitle("View Visits")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private String buildCounterDisplayLines(Map<String, Object> contract, String storagePrefix, String displayPrefix) {
        if (contract == null || contract.isEmpty()) return "";

        TreeMap<Integer, String> newestFirst = new TreeMap<>(Collections.reverseOrder());
        for (Map.Entry<String, Object> entry : contract.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(storagePrefix)) continue;
            String suffix = key.substring(storagePrefix.length());
            if (!suffix.matches("\\d{2}")) continue;

            int count = readCounterNumberFromEntry(entry.getValue());
            if (count <= 0) continue;

            int year = 2000 + Integer.parseInt(suffix);
            newestFirst.put(year, displayPrefix + suffix + " - " + count);
        }

        StringBuilder out = new StringBuilder();
        for (String line : newestFirst.values()) {
            if (out.length() > 0) out.append("\n");
            out.append(line);
        }
        return out.toString();
    }

    private int readCounterNumberFromEntry(Object entryValue) {
        if (entryValue instanceof Map) {
            Object number = ((Map<?, ?>) entryValue).get("number");
            return readCounterNumber(number);
        }
        return readCounterNumber(entryValue);
    }

    private String getOptionalContractNotes(Map<String, Object> contract) {
        if (contract == null) return "";
        Object raw = contract.get("notes");
        if (raw == null) return "";
        return raw.toString().trim();
    }

    /**
     * Choose a readable text color for a given background.
     * Uses contrast against black/white; falls back to preferred if anything fails.
     */
    private int pickReadableTextColor(int backgroundColor, int preferredTextColor) {
        try {
            // If preferred already has decent contrast, keep it.
            double preferredContrast = ColorUtils.calculateContrast(preferredTextColor, backgroundColor);
            if (preferredContrast >= 3.0) return preferredTextColor; // WCAG-ish for larger text

            int black = Color.BLACK;
            int white = Color.WHITE;
            double cBlack = ColorUtils.calculateContrast(black, backgroundColor);
            double cWhite = ColorUtils.calculateContrast(white, backgroundColor);
            return (cBlack >= cWhite) ? black : white;
        } catch (Exception ignored) {
            return preferredTextColor;
        }
    }

    private int resolveColorAttr(int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) {
                    return getResources().getColor(tv.resourceId);
                }
                return tv.data;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }




    private void showEditOrDeleteDialog(String documentId, Map<String, Object> contract) {
        // RBAC: admin-level can edit/delete contracts (fine-grained: CanHardPressContracts can override)
        SessionManager.ensureLoaded(this, null);
        if (!(SessionManager.isAdmin(this) || SessionManager.canHardPressContracts(this))) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission required")
                    .setMessage("Only an administrator can edit or delete contracts. Contact the office if you need changes.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        {
            // Create a layout for the dialog
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(16, 16, 16, 16);

            // Editable fields for the contract details
            EditText nameInput = new EditText(this);
            nameInput.setHint("Name");
            nameInput.setText(contract.get("name") != null ? contract.get("name").toString() : "N/A");
            nameInput.setBackgroundResource(R.drawable.edit_text_border);
            nameInput.setPadding(16, 16, 16, 16);
            layout.addView(nameInput);

            EditText addressInput = new EditText(this);
            addressInput.setHint("Address");
            addressInput.setText(contract.get("address") != null ? contract.get("address").toString() : "N/A");
            addressInput.setBackgroundResource(R.drawable.edit_text_border);
            addressInput.setPadding(16, 16, 16, 16);
            layout.addView(addressInput);

            EditText emailInput = new EditText(this);
            emailInput.setHint("Email");
            emailInput.setText(contract.get("email") != null ? contract.get("email").toString() : "N/A");
            emailInput.setBackgroundResource(R.drawable.edit_text_border);
            emailInput.setPadding(16, 16, 16, 16);
            layout.addView(emailInput);

            EditText contactInput = new EditText(this);
            contactInput.setHint("Contact");
            contactInput.setText(contract.get("contact") != null ? contract.get("contact").toString() : "N/A");
            contactInput.setBackgroundResource(R.drawable.edit_text_border);
            contactInput.setPadding(16, 16, 16, 16);
            layout.addView(contactInput);

            EditText visitsInput = new EditText(this);
            visitsInput.setHint("Visits");
            visitsInput.setText(contract.get("visits") != null ? contract.get("visits").toString() : "N/A");
            visitsInput.setBackgroundResource(R.drawable.edit_text_border);
            visitsInput.setPadding(16, 16, 16, 16);
            layout.addView(visitsInput);

            EditText notesInput = new EditText(this);
            notesInput.setHint("Notes");
            notesInput.setText(getOptionalContractNotes(contract));
            notesInput.setBackgroundResource(R.drawable.edit_text_border);
            notesInput.setPadding(16, 16, 16, 16);
            notesInput.setMinLines(3);
            notesInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            layout.addView(notesInput);

            // Owner / technician selector: use contractKey list with autocomplete dropdown.
            AutoCompleteTextView ownerInput = new AutoCompleteTextView(this);
            ownerInput.setHint("Owner (contractKey)");
            String currentOwner = contract.get("owner") != null ? contract.get("owner").toString() : "N/A";
            ownerInput.setText(currentOwner);
            ownerInput.setBackgroundResource(R.drawable.edit_text_border);
            ownerInput.setPadding(16, 16, 16, 16);

            // Build list of available contractKeys from assignable users (admin/super_admin/tech).
            java.util.List<String> ownerKeys = new java.util.ArrayList<>();
            if (staffOptionsForSpinner != null) {
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                for (UserRepository.AssignableUser u : staffOptionsForSpinner) {
                    if (u == null || u.contractKey == null) continue;
                    String ck = u.contractKey.trim();
                    if (ck.isEmpty()) continue;
                    String lower = ck.toLowerCase(java.util.Locale.getDefault());
                    if (seen.add(lower)) {
                        ownerKeys.add(ck);
                    }
                }
            }
            if (!ownerKeys.contains(currentOwner) && currentOwner != null && !"N/A".equalsIgnoreCase(currentOwner)) {
                ownerKeys.add(currentOwner);
            }
            java.util.Collections.sort(ownerKeys, String::compareToIgnoreCase);

            ArrayAdapter<String> ownerAdapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    ownerKeys.toArray(new String[0])
            );
            ownerInput.setAdapter(ownerAdapter);
            ownerInput.setThreshold(1); // start filtering after 1 character
            layout.addView(ownerInput);

            new AlertDialog.Builder(this)
                    .setTitle("Edit or Delete Contract")
                    .setView(layout)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String newName = nameInput.getText().toString().trim();
                        String newAddress = addressInput.getText().toString().trim();
                        String newEmail = emailInput.getText().toString().trim();
                        String newContact = contactInput.getText().toString().trim();
                        String newVisits = visitsInput.getText().toString().trim();
                        String newNotes = notesInput.getText().toString().trim();
                        String newOwner = ownerInput.getText().toString().trim();

                        // Validate required fields
                        if (newName.isEmpty() || newAddress.isEmpty()) {
                            Toast.makeText(this, "Name and Address are required.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // If the owner has changed, transfer the contract to the new owner's collection
                        String currentOwnerLocal = contract.get("owner") != null ? contract.get("owner").toString() : "N/A";
                        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
                        String contractNameForNotif = newName != null ? newName : (contract.get("name") != null ? contract.get("name").toString() : "");
                        // Normalize owner/contractKey to lowercase for storage in assignedTech.
                        String normalizedOwner = newOwner.toLowerCase(java.util.Locale.getDefault());

                        Map<String, Object> updates = new HashMap<>();
                        if (!newOwner.equalsIgnoreCase(currentOwnerLocal)) {
                            updates.put("assignedTech", normalizedOwner);
                        }
                        updates.put("name", newName);
                        updates.put("address", newAddress);
                        updates.put("email", newEmail);
                        updates.put("contact", newContact);
                        updates.put("visits", newVisits);
                        updates.put("notes", newNotes);
                        updateContractFields(currentOwnerLocal, documentId, updates, contractNameForNotif);
                    })
                    .setNegativeButton("Delete", (dialog, which) -> {
                        String owner = contract.get("owner") != null ? contract.get("owner").toString() : "Unknown";
                        deleteContract(owner, documentId);
                    })
                    .setNeutralButton("Cancel", null)
                    .show();
        }
    }

    // Legacy transfer method retained for reference but no longer used with shared contracts collection.











    private void updateStatistics(int total, int behind, int due, int upToDate) {
        TextView totalContractsText = findViewById(R.id.totalContracts);
        TextView behindContractsText = findViewById(R.id.behindContracts);
        TextView dueContractsText = findViewById(R.id.dueContracts);
        TextView upToDateContractsText = findViewById(R.id.upToDateContracts);

        totalContractsText.setText("Total: " + total);
        behindContractsText.setText("Behind: " + behind);
        dueContractsText.setText("Due: " + due);
        upToDateContractsText.setText("Up-to-Date: " + upToDate);
    }







    private void showRoutinePopup(String contractName, String documentId, String owner, String visits, CheckBox checkBox) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Routine Confirmation");
        dialog.setMessage("Was a routine done on " + contractName + "?");

        dialog.setPositiveButton("Yes", (dialogInterface, which) -> {
            // Use dd/MM/yy format to match the rest of the app
            SimpleDateFormat shortYearFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            String currentDate = shortYearFormat.format(Calendar.getInstance().getTime());

            // Update Firestore with the new lastVisit and calculate nextVisit
            updateVisitDates(owner, documentId, currentDate, visits, contractName != null ? contractName : "");
            writeVisitHistoryEntry(documentId, "Routine", currentDate);

            // Reset checkbox to be clickable again
            checkBox.setChecked(false);
            checkBox.setEnabled(true);

            Toast.makeText(this, "Routine marked complete for " + contractName, Toast.LENGTH_SHORT).show();
        });

        dialog.setNegativeButton("No", (dialogInterface, which) -> checkBox.setChecked(false));

        dialog.show();
    }

    private void showCallOutCounterPopup(String contractName, String documentId, CheckBox checkBox) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Call Out Confirmation");
        dialog.setMessage("Was a call out done on " + contractName + "?");

        dialog.setPositiveButton("Yes", (dialogInterface, which) -> {
            checkBox.setChecked(false);
            checkBox.setEnabled(false);
            incrementYearlyCounter(documentId, "Callout", "Call out", () -> {
                checkBox.setEnabled(true);
                loadContracts();
            });
            String calloutDate = new SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                    .format(Calendar.getInstance().getTime());
            writeVisitHistoryEntry(documentId, "Callout", calloutDate);
        });

        dialog.setNegativeButton("No", (dialogInterface, which) -> checkBox.setChecked(false));
        dialog.setOnCancelListener(dialogInterface -> checkBox.setChecked(false));
        dialog.show();
    }

    private void showContractOptions(Map<String, Object> contract, String lastVisit, String documentId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Contract Options");


        dialog.setItems(new CharSequence[]{"Update Last Visit", "Create Report", "Action Form", "Route"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            switch (which) {
                case 0: // Update Last Visit
                    showUpdateVisitDialog(
                            documentId,
                            contract.get("owner") != null ? contract.get("owner").toString() : userName,
                            contract.get("visits") != null ? contract.get("visits").toString() : "0",
                            companyName
                    );
                    break;

                case 1: // Create Report
                    Intent createReportIntent = new Intent(ViewContractActivity.this, ReportActivity.class);
                    createReportIntent.putExtra("USER_NAME", userName);
                    createReportIntent.putExtra("CONTRACT_ID", documentId);
                    createReportIntent.putExtra("COMPANY_NAME", companyName);
                    createReportIntent.putExtra("ADDRESS", address);
                    startActivity(createReportIntent);
                    break;

                case 2: // Action Form
                    Intent actionFormIntent = new Intent(ViewContractActivity.this, ActionFormActivity.class);
                    actionFormIntent.putExtra("USER_NAME", userName);
                    actionFormIntent.putExtra("CONTRACT_ID", documentId);
                    actionFormIntent.putExtra("COMPANY_NAME", companyName);
                    actionFormIntent.putExtra("ADDRESS", address);
                    startActivity(actionFormIntent);
                    break;
                case 3: // Route
                    if (!address.equals("N/A")) {
                        openInMaps(address);
                    } else {
                        Toast.makeText(this, "No address available to open in Maps.", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        });

        dialog.setNegativeButton("Report", (dialogInterface, which) -> {
            showRoutineDialog(documentId, contract);
        });

        dialog.show();
    }





    private String calculateNextVisit(Map<String, Object> contract) {
        // Use dd/MM/yy for two-digit years
        SimpleDateFormat shortYearFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";

        // Safely parse visits – treat non-numeric (e.g. \"N/A\") as 0
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
            calendar.setTime(dateFormat.parse(lastVisit)); // Parse using full date format
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

        // Return the next visit date in the short year format (dd/MM/yy)
        return shortYearFormat.format(calendar.getTime());
    }

    private void showRoutineDialog(String documentId, Map<String, Object> contract) {
        AlertDialog.Builder routineDialog = new AlertDialog.Builder(this);
        routineDialog.setTitle("Routine Type");

        routineDialog.setItems(new CharSequence[]{"No Activity", "Activity"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            Intent intent;

            switch (which) {
                case 0: // No Activity
                    intent = new Intent(ViewContractActivity.this, RodentRoutineActivity.class);
                    intent.putExtra("ROUTINE_TYPE", "No Activity"); // ✅ Explicitly setting correct routine type
                    break;
                case 1: // Activity
                    intent = new Intent(ViewContractActivity.this, RodentActivityRoutine.class);
                    intent.putExtra("ROUTINE_TYPE", "Activity"); // ✅ Explicitly setting correct routine type
                    break;
                default:
                    return; // Exit if something unexpected happens
            }

            intent.putExtra("USER_NAME", userName);
            intent.putExtra("COMPANY_NAME", companyName);
            intent.putExtra("ADDRESS", address);
            intent.putExtra("DOCUMENT_ID", documentId);

            startActivity(intent);
        });

        routineDialog.show();
    }



    private void showCallOutDialog(String documentId, Map<String, Object> contract){
        AlertDialog.Builder routineDialog = new AlertDialog.Builder(this);
        routineDialog.setTitle("Routine Type");

        routineDialog.setItems(new CharSequence[]{"Internal", "External"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            Intent intent;

            switch (which) {
                case 0: // No Activity
                    intent = new Intent(ViewContractActivity.this, RodentCallOutActivity.class);
                    intent.putExtra("ROUTINE_TYPE", "Internal"); // ✅ Explicitly setting correct routine type
                    break;
                case 1: // Activity
                    intent = new Intent(ViewContractActivity.this, RodentCallOutExternalActivity.class);
                    intent.putExtra("ROUTINE_TYPE", "External"); // ✅ Explicitly setting correct routine type
                    break;
                default:
                    return; // Exit if something unexpected happens
            }

            intent.putExtra("USER_NAME", userName);
            intent.putExtra("COMPANY_NAME", companyName);
            intent.putExtra("ADDRESS", address);
            intent.putExtra("DOCUMENT_ID", documentId);

            startActivity(intent);
        });

        routineDialog.show();
    }





    private void showUpdateVisitDialog(String documentId, String owner, String visits, String contractName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Update Last Visit");

        EditText dateInput = new EditText(this);
        dateInput.setHint("Enter Last Visit Date (dd/MM/yy or dd/MM/yyyy)");
        dateInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        dateInput.setSingleLine();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        layout.addView(dateInput);

        builder.setView(layout);
        builder.setPositiveButton("Update", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button updateButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (updateButton != null) {
                updateButton.setOnClickListener(v -> {
                    String normalizedDate = normalizeManualVisitDate(dateInput.getText().toString().trim());
                    if (!normalizedDate.isEmpty()) {
                        updateVisitDates(owner, documentId, normalizedDate, visits, contractName != null ? contractName : "");
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "Invalid date. Use dd/MM/yy or dd/MM/yyyy.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        dialog.show();
    }

    private String normalizeManualVisitDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return "";

        String[] acceptedFormats = {"dd/MM/yy", "dd/MM/yyyy"};
        SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        outputFormat.setLenient(false);

        for (String format : acceptedFormats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.getDefault());
                parser.setLenient(false);
                Date parsed = parser.parse(rawDate.trim());
                if (parsed != null) {
                    return outputFormat.format(parsed);
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }





    /** Add "Remind me" for this contract: write to Firestore and schedule 12h worker. */
    private void addContractReminder(String documentId, String contractName, String address, CheckBox checkBox) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || db == null) {
            checkBox.setChecked(false);
            return;
        }
        String uid = user.getUid();
        String docId = uid + "_" + documentId;
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("contractId", documentId);
        data.put("contractName", contractName != null ? contractName : "");
        data.put("contractAddress", address != null ? address : "");
        data.put("lastNotifiedAt", null);
        db.collection(FirestorePaths.CONTRACT_REMINDERS).document(docId).set(data)
                .addOnSuccessListener(aVoid -> {
                    contractIdsWithReminder.add(documentId);
                    scheduleContractReminderWorker();
                    Toast.makeText(this, "You'll get a reminder every 12 hours. Uncheck to stop.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    checkBox.setChecked(false);
                    Toast.makeText(this, "Could not set reminder: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
                });
    }

    /** Remove "Remind me": delete from Firestore and update local set. */
    private void removeContractReminder(String documentId, CheckBox checkBox) {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || db == null) return;
        String uid = user.getUid();
        String docId = uid + "_" + documentId;
        db.collection(FirestorePaths.CONTRACT_REMINDERS).document(docId).delete()
                .addOnSuccessListener(aVoid -> contractIdsWithReminder.remove(documentId))
                .addOnFailureListener(e -> {
                    checkBox.setChecked(true);
                    Toast.makeText(this, "Could not remove reminder.", Toast.LENGTH_SHORT).show();
                });
    }

    private static final String CONTRACT_REMINDER_WORK_NAME = "contract_reminder_12h";

    private void scheduleContractReminderWorker() {
        try {
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(
                    com.grpc.grpc.contracts.worker.ContractReminderWorker.class,
                    12, TimeUnit.HOURS
            ).build();
            WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                    CONTRACT_REMINDER_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    work
            );
        } catch (Exception ignored) {}
    }

    private void updateVisitDates(String owner, String documentId, String lastVisit, String visits, String contractName) {
        String tableName = StaffDirectory.getContractsCollectionNameFromAnyKey(owner);

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastVisit", lastVisit);

        // Build a minimal contract map to reuse existing next-visit calculation logic
        Map<String, Object> temp = new HashMap<>();
        temp.put("lastVisit", lastVisit);
        temp.put("visits", visits != null ? visits : "0");
        String nextVisit = calculateNextVisit(temp);
        updates.put("nextVisit", nextVisit);

        // Debug: log contract update for mark-as-done / visit changes.
        try {
            com.google.firebase.auth.FirebaseUser authUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String authUid = authUser != null ? authUser.getUid() : "null";
            SessionManager.Session session = SessionManager.getCached(this);
            String role = session != null ? session.roleNorm : "unknown";
            String sessionContractKey = session != null ? session.contractKey : SessionManager.getContractKey(this);
            Log.d("ViewContractActivity", "Updating contract visit in collection=" + FirestorePaths.CONTRACTS
                    + " docId=" + documentId
                    + " lastVisit=" + lastVisit
                    + " nextVisit=" + nextVisit
                    + " (ownerKey=" + owner
                    + ", authUid=" + authUid
                    + ", role=" + role
                    + ", sessionContractKey=" + (sessionContractKey != null ? sessionContractKey : "") + ")");
        } catch (Exception e) {
            Log.w("ViewContractActivity", "Failed to log contract visit update: " + e.getMessage());
        }

        db.collection(FirestorePaths.CONTRACTS).document(documentId).update(updates).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Visit updated successfully.", Toast.LENGTH_SHORT).show();

            // Notify owner + admins that a contract visit was updated (display: contract name only).
            try {
                if (!BuildConfig.IS_OFFLINE) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("contractId", documentId);
                    data.put("userName", owner);
                    data.put("contractName", contractName != null ? contractName : "");
                    String docId = "contract_visit_" + documentId + "_" + System.currentTimeMillis();
                    NotificationUtils.writeInAppNotification(
                            owner != null ? owner : SessionManager.getName(this),
                            docId,
                            contractName != null && !contractName.isEmpty() ? contractName : "Contract visit updated",
                            "",
                            "contract_update",
                            data
                    );
                }
            } catch (Exception ignored) { }

            incrementYearlyCounter(documentId, "Routines", "Routine", lastVisit, this::loadContracts);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to update visit: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void openInMaps(String address) {
        if (!address.equals("N/A")) {
            // Record this as the user's last "map opened" location
            try {
                FirebaseFirestore.getInstance()
                        .collection(LocationSharing.COLLECTION_LAST_LOCATIONS)
                        .document(LocationSharing.userKey(userName))
                        .set(new java.util.HashMap<String, Object>() {{
                            put("userName", userName);
                            put("lastMapQuery", address);
                            put("lastMapClientTimestampMs", System.currentTimeMillis());
                            put("lastMapAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
                            put("source", "map_open");
                        }}, com.google.firebase.firestore.SetOptions.merge());
            } catch (Exception ignored) {}

            Uri gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "No address available to open in Maps.", Toast.LENGTH_SHORT).show();
        }
    }




    /** Updates a field in the contract. Uses the contract's owner collection so admins can edit other technicians' contracts. */
    private void updateContractField(String owner, String documentId, String field, String newValue, String contractName) {
        // Validate the 'Visits' field to ensure it's a valid single- or double-digit number
        if (field.equalsIgnoreCase("visits")) {
            try {
                int visits = Integer.parseInt(newValue);
                if (visits < 1 || visits > 99) {
                    Toast.makeText(this, "Visits must be a number between 1 and 99.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for Visits.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(field, newValue);

        db.collection(FirestorePaths.CONTRACTS).document(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Contract updated successfully.", Toast.LENGTH_SHORT).show();

                    // Notify technician + admins (display: contract name only).
                    try {
                        if (!BuildConfig.IS_OFFLINE) {
                            Map<String, Object> data = new HashMap<>();
                            data.put("contractId", documentId);
                            data.put("userName", owner);
                            data.put("contractName", contractName != null ? contractName : "");
                            String docId = "contract_field_" + field + "_" + documentId + "_" + System.currentTimeMillis();
                            NotificationUtils.writeInAppNotification(
                                    owner != null ? owner : SessionManager.getName(this),
                                    docId,
                                    contractName != null && !contractName.isEmpty() ? contractName : "Contract updated",
                                    "",
                                    "contract_update",
                                    data
                            );
                        }
                    } catch (Exception ignored) { }

                    loadContracts();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateContractFields(String owner, String documentId, Map<String, Object> updates, String contractName) {
        if (updates == null || updates.isEmpty()) return;

        Object visitsValue = updates.get("visits");
        if (visitsValue != null) {
            try {
                int visits = Integer.parseInt(String.valueOf(visitsValue).trim());
                if (visits < 1 || visits > 99) {
                    Toast.makeText(this, "Visits must be a number between 1 and 99.", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format for Visits.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        db.collection(FirestorePaths.CONTRACTS).document(documentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> finalUpdates = new HashMap<>(updates);

                    String lastVisit = snapshot != null && snapshot.get("lastVisit") != null
                            ? String.valueOf(snapshot.get("lastVisit"))
                            : "N/A";
                    Object visitsRaw = finalUpdates.containsKey("visits")
                            ? finalUpdates.get("visits")
                            : (snapshot != null ? snapshot.get("visits") : "0");

                    Map<String, Object> temp = new HashMap<>();
                    temp.put("lastVisit", lastVisit);
                    temp.put("visits", visitsRaw != null ? visitsRaw : "0");
                    finalUpdates.put("nextVisit", calculateNextVisit(temp));

                    db.collection(FirestorePaths.CONTRACTS).document(documentId)
                            .update(finalUpdates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Contract updated successfully.", Toast.LENGTH_SHORT).show();

                                try {
                                    if (!BuildConfig.IS_OFFLINE) {
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("contractId", documentId);
                                        data.put("userName", owner);
                                        data.put("contractName", contractName != null ? contractName : "");
                                        String docId = "contract_update_" + documentId;
                                        NotificationUtils.writeInAppNotification(
                                                owner != null ? owner : SessionManager.getName(this),
                                                docId,
                                                contractName != null && !contractName.isEmpty() ? contractName : "Contract updated",
                                                "",
                                                "contract_update",
                                                data
                                        );
                                    }
                                } catch (Exception ignored) { }

                                loadContracts();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to update contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    /** Deletes the contract from the shared contracts collection. Only super admin can trigger this. */
    private void deleteContract(String owner, String documentId) {
        db.collection(FirestorePaths.CONTRACTS).document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Contract deleted successfully.", Toast.LENGTH_SHORT).show();
                    loadContracts();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    /**
     * Generate a PDF containing all currently displayed contracts and allow
     * viewing or sharing it.
     */
    private void exportContractsToPDF() {
        try {
            SessionManager.ensureLoaded(this, null);
            boolean isAdmin = SessionManager.isAdmin(this);
            boolean canExportFromFirebase = isAdmin || SessionManager.seesAllJobs(this);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "PDF export requires Android 13 or higher.", Toast.LENGTH_LONG).show();
                return;
            }

            List<Map<String, Object>> contractsToExport = canExportFromFirebase && allLoadedContracts != null && !allLoadedContracts.isEmpty()
                    ? new ArrayList<>(allLoadedContracts)
                    : (currentDisplayedContracts != null ? new ArrayList<>(currentDisplayedContracts) : new ArrayList<>());

            if (contractsToExport.isEmpty()) {
                Toast.makeText(this, "No contracts to export.", Toast.LENGTH_SHORT).show();
                return;
            }

            String title = getCurrentContractsExportTitle();
            File pdfFile = ContractListPDFGenerator.generateContractsListPDF(
                    title,
                    deduplicateContractsForExport(contractsToExport),
                    this
            );
            if (pdfFile == null) {
                Toast.makeText(this, "Failed to generate contracts PDF.", Toast.LENGTH_SHORT).show();
                return;
            }
            showContractsPdfOptions(pdfFile);
        } catch (Exception e) {
            Log.e("ViewContractActivity", "Error exporting contracts to PDF", e);
            Toast.makeText(this, "Error exporting contracts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCurrentContractsExportTitle() {
        if (canSelectTechnician() && technicianSpinner != null && technicianSpinner.getSelectedItem() != null) {
            String selected = technicianSpinner.getSelectedItem().toString().trim();
            if (!selected.isEmpty()) {
                return "All".equalsIgnoreCase(selected) ? "All Contracts" : selected + " Contracts";
            }
        }
        return StaffDirectory.getContractsCollectionNameFromAnyKey(userName);
    }

    private void showExportChoiceDialog() {
        if (db == null) {
            Toast.makeText(this, "Firestore not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            java.util.TreeSet<String> contractKeys = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    String key = doc.getString("assignedTech");
                    if (key != null && !key.trim().isEmpty()) {
                        contractKeys.add(key.trim());
                    }
                }
            }

            if (contractKeys.isEmpty()) {
                UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> showExportChoiceDialogForKeys(users)));
            } else {
                runOnUiThread(() -> showExportChoiceDialogForKeys(new ArrayList<>(), contractKeys));
            }
        });
    }

    private void showExportChoiceDialogForKeys(List<UserRepository.AssignableUser> users) {
        java.util.TreeSet<String> contractKeys = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (users != null) {
            for (UserRepository.AssignableUser u : users) {
                if (u == null || u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                contractKeys.add(u.contractKey.trim());
            }
        }
        showExportChoiceDialogForKeys(users, contractKeys);
    }

    private void showExportChoiceDialogForKeys(List<UserRepository.AssignableUser> users, java.util.Set<String> contractKeys) {
        java.util.List<String> displayList = new ArrayList<>();
        java.util.List<String> keyList = new ArrayList<>();
        displayList.add("All");
        keyList.add(TECH_ID_ALL);

        if (contractKeys != null) {
            for (String key : contractKeys) {
                if (key == null || key.trim().isEmpty()) continue;
                displayList.add(key.trim());
                keyList.add(key.trim().toLowerCase(Locale.getDefault()));
            }
        }

        String[] display = displayList.toArray(new String[0]);
        String[] keyArray = keyList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Export contracts to PDF")
                .setMessage("Select a contractKey or export All Firebase contracts.")
                .setItems(display, (dialog, which) -> {
                    if (which >= 0 && which < keyArray.length) {
                        if (TECH_ID_ALL.equals(keyArray[which])) {
                            loadAllTechniciansContractsAndExportPdf();
                        } else {
                            loadTechnicianContractsAndExportPdfByContractKey(keyArray[which]);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadTechnicianContractsAndExportPdfByContractKey(String contractKey) {
        if (db == null) {
            Toast.makeText(this, "Firestore not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        String keyFilter = contractKey != null ? contractKey.trim().toLowerCase(Locale.getDefault()) : "";
        if (keyFilter.isEmpty()) {
            Toast.makeText(this, "No contractKey selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<Map<String, Object>> contracts = Collections.synchronizedList(new ArrayList<>());
        db.collection(FirestorePaths.CONTRACTS)
                .whereEqualTo("assignedTech", keyFilter)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> c = doc.getData();
                            c.put("documentId", doc.getId());
                            c.put("contractKey", keyFilter);
                            String owner = c.get("assignedTech") != null
                                    ? c.get("assignedTech").toString()
                                    : keyFilter;
                            c.put("owner", owner);
                            contracts.add(c);
                        }
                    } else if (task.getException() != null) {
                        Toast.makeText(this, "Failed to load contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    exportMergedContractsPdf(keyFilter + " Contracts", contracts);
                });
    }

    private void loadAllTechniciansContractsAndExportPdf() {
        if (db == null) {
            Toast.makeText(this, "Firestore not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Map<String, Object>> allContracts = Collections.synchronizedList(new ArrayList<>());

        // Export every contract in the current Firebase contracts collection.
        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    Map<String, Object> c = doc.getData();
                    c.put("documentId", doc.getId());
                    String owner = c.get("assignedTechName") != null
                            ? c.get("assignedTechName").toString()
                            : (c.get("assignedTech") != null ? c.get("assignedTech").toString() : "Technician");
                    c.put("owner", owner);
                    if (c.get("assignedTech") != null) {
                        c.put("contractKey", c.get("assignedTech").toString());
                    }
                    allContracts.add(c);
                }
            } else if (task.getException() != null) {
                Toast.makeText(this, "Failed to load all contracts: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            exportMergedContractsPdf("All Contracts", allContracts);
        });
    }

    private void showContractsPdfOptions(File pdfFile) {
        new AlertDialog.Builder(this)
                .setTitle("Contracts PDF Generated")
                .setMessage("A PDF for the current contract list has been created. What would you like to do?")
                .setPositiveButton("View", (dialog, which) -> viewContractsPdf(pdfFile))
                .setNegativeButton("Share", (dialog, which) -> shareContractsPdf(pdfFile))
                .show();
    }

    private void exportMergedContractsPdf(String title, List<Map<String, Object>> contracts) {
        runOnUiThread(() -> {
            try {
                List<Map<String, Object>> exportContracts = deduplicateContractsForExport(contracts);
                if (exportContracts == null || exportContracts.isEmpty()) {
                    Toast.makeText(this, "No contracts to export.", Toast.LENGTH_SHORT).show();
                    return;
                }
                File pdf = ContractListPDFGenerator.generateContractsListPDF(title, exportContracts, this);
                if (pdf != null) {
                    showContractsPdfOptions(pdf);
                } else {
                    Toast.makeText(this, "Failed to generate PDF.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("ViewContractActivity", "Error generating merged contracts PDF", e);
                Toast.makeText(this, "Failed to generate PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<Map<String, Object>> deduplicateContractsForExport(List<Map<String, Object>> contracts) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (contracts == null || contracts.isEmpty()) return out;

        Set<String> seen = new HashSet<>();
        for (Map<String, Object> contract : contracts) {
            if (contract == null) continue;
            String key = buildContractExportDedupKey(contract);
            if (key.isEmpty()) {
                key = "row:" + out.size();
            }
            if (seen.add(key)) {
                out.add(contract);
            }
        }
        return out;
    }

    private String buildContractExportDedupKey(Map<String, Object> contract) {
        if (contract == null) return "";

        Object docId = contract.get("documentId");
        if (docId != null) {
            String id = docId.toString().trim();
            if (!id.isEmpty()) return "id:" + id;
        }

        String displayName = firstNonBlank(contract,
                "name", "Name", "companyName", "Company", "CustomerName", "customerName",
                "contractName", "ContractName", "contractKey", "address");
        String address = firstNonBlank(contract, "address", "Address", "customerAddress", "CustomerAddress");
        String tech = firstNonBlank(contract, "assignedTech", "contractKey", "owner", "assignedTechName");
        return (tech + "|" + displayName + "|" + address).toLowerCase(Locale.getDefault());
    }

    private String firstNonBlank(Map<String, Object> contract, String... keys) {
        if (contract == null || keys == null) return "";
        for (String key : keys) {
            Object value = contract.get(key);
            if (value == null) continue;
            String text = value.toString().trim();
            if (!text.isEmpty() && !"N/A".equalsIgnoreCase(text)) {
                return text;
            }
        }
        return "";
    }

    private void viewContractsPdf(File file) {
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

    private void shareContractsPdf(File file) {
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

            startActivity(Intent.createChooser(shareIntent, "Share Contracts PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the PDF.", Toast.LENGTH_SHORT).show();
        }
    }
}