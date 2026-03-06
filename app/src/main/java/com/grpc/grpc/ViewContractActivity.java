package com.grpc.grpc;

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
import java.util.concurrent.TimeUnit;
import android.content.SharedPreferences;
import java.util.Collections;

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
 * Company: Good Riddance Pest Control
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

    /** Optional override used by global search to pick a technician collection. */
    public static final String EXTRA_TECHNICIAN_OVERRIDE = "TECHNICIAN_OVERRIDE";

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
                loadInitialContractsWithOverrides();
            }));
        }));

        // Optional: pre-fill search from global Search screen
        String initialQuery = getIntent().getStringExtra(SearchActivity.EXTRA_SEARCH_QUERY);
        if (initialQuery != null && !initialQuery.trim().isEmpty() && searchBar != null) {
            searchBar.setText(initialQuery.trim());
            searchBar.setSelection(searchBar.getText().length());
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

            // Default selection: current auth user when present; else "All".
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
            technicianSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                    if (technicianIdsForSpinner == null || position < 0 || position >= technicianIdsForSpinner.length) return;
                    loadContractsForSelection(technicianIdsForSpinner[position]);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }));
    }

    private void loadInitialContractsWithOverrides() {
        // Optional: select a specific technician (used by global search results)
        String techOverride = getIntent().getStringExtra(EXTRA_TECHNICIAN_OVERRIDE);
        if (canSelectTechnician() && technicianSpinner != null && techOverride != null && !techOverride.trim().isEmpty()) {
            String overrideKey = techOverride.trim();
            // Try matching by display label (user name) as a fallback.
            if (staffOptionsForSpinner != null) {
                for (UserRepository.AssignableUser u : staffOptionsForSpinner) {
                    if (u != null && u.name != null && u.name.equalsIgnoreCase(overrideKey) && u.uid != null) {
                        overrideKey = u.uid.trim();
                        break;
                    }
                }
            }
            if (!overrideKey.trim().isEmpty()) {
                trySelectTechnicianInSpinnerById(overrideKey);
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
        // Stats + UI
        sendDailyBehindSummaryIfNeeded(SessionManager.getName(this), allLoadedContracts);
        applySearchAndDisplay();
    }

    /** Apply current search filter to allLoadedContracts and display. */
    private void applySearchAndDisplay() {
        String query = searchBar.getText() != null ? searchBar.getText().toString().toLowerCase() : "";
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
        handleContractsData(filtered);
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
                        String owner = contract.get("assignedTechContractKey") != null
                                ? contract.get("assignedTechContractKey").toString()
                                : (contract.get("assignedTechName") != null
                                    ? contract.get("assignedTechName").toString()
                                    : "Technician");
                        contract.put("owner", owner);
                        merged.add(contract);
                    }
                }
            } catch (Exception e) {
                Log.w("ViewContractActivity", "Error loading shared contracts for admin view: " + e.getMessage());
            } finally {
                allLoadedContracts = new ArrayList<>(merged);
                applySearchAndDisplay();
            }
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

        // Sort contracts alphabetically
        contractsList.sort((c1, c2) -> {
            String name1 = c1.get("name") != null ? c1.get("name").toString() : "";
            String name2 = c2.get("name") != null ? c2.get("name").toString() : "";
            return name1.compareToIgnoreCase(name2);
        });

        // Keep track of what is currently displayed (for PDF export)
        currentDisplayedContracts = new ArrayList<>(contractsList);

        // Clear and add updated contract views
        contractsContainer.removeAllViews();
        for (Map<String, Object> contract : contractsList) {
            String documentId = contract.get("documentId").toString();
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
            String documentId = contract.get("documentId").toString();
            addContractToView(contract, documentId);
        }
    }



    private void addContractToView(Map<String, Object> contract, String documentId) {
        LinearLayout contractBox = new LinearLayout(this);
        contractBox.setOrientation(LinearLayout.VERTICAL);
        contractBox.setPadding(16, 16, 16, 16);
        contractBox.setBackgroundResource(R.drawable.surface_frame);

        String owner = contract.get("owner") != null ? contract.get("owner").toString() : "Unknown";
        String name = contract.get("name") != null ? contract.get("name").toString() : "N/A";
        String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";
        String email = contract.get("email") != null ? contract.get("email").toString() : "N/A";
        String contact = contract.get("contact") != null ? contract.get("contact").toString() : "N/A";
        String visits = contract.get("visits") != null ? contract.get("visits").toString() : "0";
        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
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
                        "Last Visit: " + lastVisit + "\n" +
                        "Next Visit: " + nextVisit
        );
        // Improve readability on bright status highlights (yellow/red) by picking a high-contrast text color.
        int preferred = resolveColorAttr(android.R.attr.textColorPrimary, contractDetails.getCurrentTextColor());
        contractDetails.setTextColor(pickReadableTextColor(bgColor, preferred));

        // ✅ Add the "Mark as Done" Checkbox
        CheckBox markDoneCheckBox = new CheckBox(this);
        markDoneCheckBox.setText("Mark as Done");
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
            showReportYearPickerAndOpen(name);
        });

        // ✅ Add views to contract box
        contractBox.addView(contractDetails);
        contractBox.addView(markDoneCheckBox);  // ✅ Add checkbox
        contractBox.addView(viewReportsButton); // Add View Reports button

        // ✅ Add the contract box to the container
        contractsContainer.addView(contractBox);
    }

    /**
     * Shows a year picker based on available Firebase Storage folders (Reports25/Reports26/...).
     * If storage folders can't be listed, falls back to current year and previous year.
     */
    private void showReportYearPickerAndOpen(String contractName) {
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

            String[] labels = new String[finalYearFolders.size()];
            for (int i = 0; i < finalYearFolders.size(); i++) {
                labels[i] = String.valueOf(finalYearFolders.get(i).year);
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select report year")
                    .setItems(labels, (d, which) -> {
                        YearFolder selected = finalYearFolders.get(which);
                        Intent intent = new Intent(ViewContractActivity.this, ContractReportsActivity.class);
                        intent.putExtra("CONTRACT_NAME", contractName);
                        intent.putExtra("USER_NAME", userName);
                        intent.putExtra("REPORTS_FOLDER", selected.folderName);
                        intent.putExtra("REPORT_YEAR", selected.year);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }).addOnFailureListener(e -> {
            loading.dismiss();
            List<YearFolder> fallback = buildFallbackYears();
            String[] labels = new String[fallback.size()];
            for (int i = 0; i < fallback.size(); i++) labels[i] = String.valueOf(fallback.get(i).year);

            new AlertDialog.Builder(this)
                    .setTitle("Select report year")
                    .setMessage("Could not list Storage folders. Showing default years.")
                    .setItems(labels, (d, which) -> {
                        YearFolder selected = fallback.get(which);
                        Intent intent = new Intent(ViewContractActivity.this, ContractReportsActivity.class);
                        intent.putExtra("CONTRACT_NAME", contractName);
                        intent.putExtra("USER_NAME", userName);
                        intent.putExtra("REPORTS_FOLDER", selected.folderName);
                        intent.putExtra("REPORT_YEAR", selected.year);
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
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
                        String newOwner = ownerInput.getText().toString().trim();

                        // Validate required fields
                        if (newName.isEmpty() || newAddress.isEmpty()) {
                            Toast.makeText(this, "Name and Address are required.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // If the owner has changed, transfer the contract to the new owner's collection
                        String currentOwnerLocal = contract.get("owner") != null ? contract.get("owner").toString() : "N/A";
                        String lastVisit = contract.get("lastVisit") != null ? contract.get("lastVisit").toString() : "N/A";
                        // Normalize owner/contractKey to lowercase for storage in assignedTech.
                        String normalizedOwner = newOwner.toLowerCase(java.util.Locale.getDefault());

                        // Reassign owner when changed.
                        if (!newOwner.equalsIgnoreCase(currentOwnerLocal)) {
                            updateContractField(currentOwnerLocal, documentId, "assignedTech", normalizedOwner);
                        }

                        // Update other editable fields on the same contract document.
                        updateContractField(currentOwnerLocal, documentId, "name", newName);
                        updateContractField(currentOwnerLocal, documentId, "address", newAddress);
                        updateContractField(currentOwnerLocal, documentId, "email", newEmail);
                        updateContractField(currentOwnerLocal, documentId, "contact", newContact);
                        updateContractField(currentOwnerLocal, documentId, "visits", newVisits);
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
            updateVisitDates(owner, documentId, currentDate, visits);

            // Reset checkbox to be clickable again
            checkBox.setChecked(false);
            checkBox.setEnabled(true);

            Toast.makeText(this, "Routine marked complete for " + contractName, Toast.LENGTH_SHORT).show();
        });

        dialog.setNegativeButton("No", (dialogInterface, which) -> checkBox.setChecked(false));

        dialog.show();
    }

    private void showContractOptions(Map<String, Object> contract, String lastVisit, String documentId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Contract Options");


        dialog.setItems(new CharSequence[]{"Update Last Visit", "Create Report", "Generic Report", "Action Form", "Route"}, (dialogInterface, which) -> {
            String companyName = contract.get("name") != null ? contract.get("name").toString() : "N/A";
            String address = contract.get("address") != null ? contract.get("address").toString() : "N/A";

            switch (which) {
                case 0: // Update Last Visit
                    showUpdateVisitDialog(
                            documentId,
                            contract.get("owner") != null ? contract.get("owner").toString() : userName,
                            contract.get("visits") != null ? contract.get("visits").toString() : "0"
                    );
                    break;

                case 1: // Create Report
                    Intent createReportIntent = new Intent(ViewContractActivity.this, ReportActivity.class);
                    createReportIntent.putExtra("USER_NAME", userName);
                    createReportIntent.putExtra("COMPANY_NAME", companyName);
                    createReportIntent.putExtra("ADDRESS", address);
                    startActivity(createReportIntent);
                    break;

                case 2: // Generic Report
                    Intent genericReportIntent = new Intent(ViewContractActivity.this, GeneralReportActivity.class);
                    genericReportIntent.putExtra("USER_NAME", userName);
                    genericReportIntent.putExtra("COMPANY_NAME", companyName);
                    genericReportIntent.putExtra("ADDRESS", address);
                    startActivity(genericReportIntent);
                    break;
                case 3: // Action Form
                    Intent actionFormIntent = new Intent(ViewContractActivity.this, ActionFormActivity.class);
                    actionFormIntent.putExtra("USER_NAME", userName);
                    actionFormIntent.putExtra("COMPANY_NAME", companyName);
                    actionFormIntent.putExtra("ADDRESS", address);
                    startActivity(actionFormIntent);
                    break;
                case 4: // Route
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
        int visits = contract.get("visits") != null ? Integer.parseInt(contract.get("visits").toString()) : 0;

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





    private void showUpdateVisitDialog(String documentId, String owner, String visits) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Update Last Visit");

        EditText dateInput = new EditText(this);
        dateInput.setHint("Enter Last Visit Date (dd/MM/yy)");
        dateInput.setSingleLine();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        layout.addView(dateInput);

        dialog.setView(layout);

        dialog.setPositiveButton("Update", (dialogInterface, which) -> {
            String newDate = dateInput.getText().toString().trim();
            if (!newDate.isEmpty() && newDate.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
                updateVisitDates(owner, documentId, newDate, visits);
            } else {
                Toast.makeText(this, "Invalid date format! Use dd/MM/yy.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }





    private void updateVisitDates(String owner, String documentId, String lastVisit, String visits) {
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

            // Notify owner + admins that a contract visit was updated.
            try {
                if (!BuildConfig.IS_OFFLINE) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("contractId", documentId);
                    data.put("owner", owner);
                    data.put("lastVisit", lastVisit);
                    String docId = "contract_visit_" + documentId + "_" + System.currentTimeMillis();
                    NotificationUtils.writeInAppNotification(
                            owner != null ? owner : SessionManager.getName(this),
                            docId,
                            "Contract visit updated",
                            "Last visit was updated to " + lastVisit,
                            "contract_update",
                            data
                    );
                }
            } catch (Exception ignored) { }

            loadContracts();
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
    private void updateContractField(String owner, String documentId, String field, String newValue) {
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

                    // Notify technician + admins when admin updates a contract field.
                    try {
                        if (!BuildConfig.IS_OFFLINE) {
                            Map<String, Object> data = new HashMap<>();
                            data.put("contractId", documentId);
                            data.put("field", field);
                            data.put("newValue", newValue);
                            data.put("updatedBy", SessionManager.getName(this));
                            String docId = "contract_field_" + field + "_" + documentId + "_" + System.currentTimeMillis();
                            NotificationUtils.writeInAppNotification(
                                    owner != null ? owner : SessionManager.getName(this),
                                    docId,
                                    "Contract updated",
                                    "Field \"" + field + "\" was updated.",
                                    "contract_update",
                                    data
                            );
                        }
                    } catch (Exception ignored) { }

                    loadContracts();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to update contract: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

            // Admins can load/export by choice from Firestore; others need current list
            if (!isAdmin && (currentDisplayedContracts == null || currentDisplayedContracts.isEmpty())) {
                Toast.makeText(this, "No contracts to export.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "PDF export requires Android 13 or higher.", Toast.LENGTH_LONG).show();
                return;
            }

            if (isAdmin) {
                showExportChoiceDialog();
                return;
            }
            // Non-admin: export only currently displayed list
            if (currentDisplayedContracts == null || currentDisplayedContracts.isEmpty()) {
                Toast.makeText(this, "No contracts to export.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (SessionManager.seesAllJobs(this)) {
                if (currentDisplayedContracts == null || currentDisplayedContracts.isEmpty()) {
                    Toast.makeText(this, "No contracts to export.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
                for (Map<String, Object> contract : currentDisplayedContracts) {
                    String owner = contract.get("owner") != null ? contract.get("owner").toString() : "Unknown";
                    grouped.computeIfAbsent(owner, k -> new ArrayList<>()).add(contract);
                }

                Map<String, File> ownerPdfs = new HashMap<>();
                for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                    String owner = entry.getKey();
                    List<Map<String, Object>> list = entry.getValue();
                    File pdf = ContractListPDFGenerator.generateContractsListPDF(
                            owner + " Contracts", list, this);
                    if (pdf != null) {
                        ownerPdfs.put(owner, pdf);
                    }
                }

                File allPdf = ContractListPDFGenerator.generateContractsListPDF(
                        "All Contracts", currentDisplayedContracts, this);
                if (allPdf != null) {
                    ownerPdfs.put("All", allPdf);
                }

                if (ownerPdfs.isEmpty()) {
                    Toast.makeText(this, "Failed to generate contracts PDFs.", Toast.LENGTH_SHORT).show();
                    return;
                }

                showContractsPdfOptionsForUser004(ownerPdfs);
            } else {
                String title = StaffDirectory.getContractsCollectionNameFromAnyKey(userName);
                File pdfFile = ContractListPDFGenerator.generateContractsListPDF(title, currentDisplayedContracts, this);
                if (pdfFile == null) {
                    Toast.makeText(this, "Failed to generate contracts PDF.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showContractsPdfOptions(pdfFile);
            }
        } catch (Exception e) {
            Log.e("ViewContractActivity", "Error exporting contracts to PDF", e);
            Toast.makeText(this, "Error exporting contracts: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showExportChoiceDialog() {
        // Dynamic staff list (ContractKey) for export dialog too.
        UserRepository.fetchAssignableUsers(users -> runOnUiThread(() -> {
            List<UserRepository.AssignableUser> opts = users != null ? users : new ArrayList<>();
            java.util.List<String> displayList = new ArrayList<>();
            java.util.List<String> ids = new ArrayList<>();
            for (UserRepository.AssignableUser u : opts) {
                if (u == null) continue;
                if (u.contractKey == null || u.contractKey.trim().isEmpty()) continue;
                displayList.add(u.contractKey.trim());
                ids.add(u.uid != null ? u.uid : "");
            }
            displayList.add("All");
            ids.add(TECH_ID_ALL);

            String[] display = displayList.toArray(new String[0]);
            String[] idArray = ids.toArray(new String[0]);

            new AlertDialog.Builder(this)
                    .setTitle("Export contracts to PDF")
                    .setMessage("Whose contracts do you want to export?")
                    .setItems(display, (dialog, which) -> {
                        if (which >= 0 && which < idArray.length) {
                            if (TECH_ID_ALL.equals(idArray[which])) {
                                loadAllTechniciansContractsAndExportPdf();
                            } else {
                                loadTechnicianContractsAndExportPdfById(idArray[which]);
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }));
    }

    private void loadTechnicianContractsAndExportPdfById(String techUid) {
        if (db == null) {
            Toast.makeText(this, "Firestore not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Map<String, Object>> merged = Collections.synchronizedList(new ArrayList<>());
        final int[] remaining = {2};

        // Resolve contractKey for this technician for shared collection exports.
        String contractKey = null;
        if (staffOptionsForSpinner != null) {
            for (UserRepository.AssignableUser u : staffOptionsForSpinner) {
                if (u != null && u.uid != null && u.uid.equals(techUid)) {
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

        // 1) Shared contracts collection: filter by assignedTech (contractKey, stored normalized to lowercase).
        String keyFilter = contractKey != null ? contractKey.trim().toLowerCase(Locale.getDefault()) : "";
        db.collection(FirestorePaths.CONTRACTS)
                .whereEqualTo("assignedTech", keyFilter)
                .get()
                .addOnCompleteListener(task -> {
                    try {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot doc : task.getResult()) {
                                Map<String, Object> c = doc.getData();
                                c.put("documentId", doc.getId());
                                String owner = c.get("assignedTechName") != null
                                        ? c.get("assignedTechName").toString()
                                        : "Technician";
                                c.put("owner", owner);
                                merged.add(c);
                            }
                        }
                    } finally {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            exportMergedContractsPdf("Contracts", merged);
                        }
                    }
                });

        // 2) Legacy per-tech collection.
        String legacyCollection = StaffDirectory.getContractsCollectionNameFromAnyKey(contractKey != null ? contractKey : techUid);
        final String legacyOwnerName = legacyCollection.replace(" Contracts", "");
        db.collection(legacyCollection).get().addOnCompleteListener(task -> {
            try {
                if (task.isSuccessful() && task.getResult() != null) {
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        Map<String, Object> c = doc.getData();
                        c.put("documentId", doc.getId());
                        c.put("owner", legacyOwnerName);
                        merged.add(c);
                    }
                }
            } finally {
                remaining[0]--;
                if (remaining[0] <= 0) {
                    exportMergedContractsPdf("Contracts", merged);
                }
            }
        });
    }

    private void loadAllTechniciansContractsAndExportPdf() {
        if (db == null) {
            Toast.makeText(this, "Firestore not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<Map<String, Object>> allContracts = Collections.synchronizedList(new ArrayList<>());
        final int[] remaining = {2};

        // 1) Shared contracts collection.
        db.collection(FirestorePaths.CONTRACTS).get().addOnCompleteListener(task -> {
            try {
                if (task.isSuccessful() && task.getResult() != null) {
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        Map<String, Object> c = doc.getData();
                        c.put("documentId", doc.getId());
                        String owner = c.get("assignedTechName") != null
                                ? c.get("assignedTechName").toString()
                                : "Technician";
                        c.put("owner", owner);
                        allContracts.add(c);
                    }
                }
            } finally {
                remaining[0]--;
                if (remaining[0] <= 0) {
                    exportMergedContractsPdf("All Contracts", allContracts);
                }
            }
        });

        // 2) Legacy per-tech collections.
        String[] techIds = getAllStaffIdsForContracts();
        if (techIds == null || techIds.length == 0) {
            remaining[0]--;
            if (remaining[0] <= 0) {
                exportMergedContractsPdf("All Contracts", allContracts);
            }
            return;
        }
        final int totalLegacy = techIds.length;
        final int[] completedLegacy = {0};
        for (String techId : techIds) {
            String collectionName = StaffDirectory.getContractsCollectionName(techId);
            String ownerName = collectionName.replace(" Contracts", "");
            db.collection(collectionName).get().addOnCompleteListener(task -> {
                try {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> c = doc.getData();
                            c.put("documentId", doc.getId());
                            c.put("owner", ownerName);
                            allContracts.add(c);
                        }
                    }
                } finally {
                    if (++completedLegacy[0] == totalLegacy) {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            exportMergedContractsPdf("All Contracts", allContracts);
                        }
                    }
                }
            });
        }
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
                if (contracts == null || contracts.isEmpty()) {
                    Toast.makeText(this, "No contracts to export.", Toast.LENGTH_SHORT).show();
                    return;
                }
                File pdf = ContractListPDFGenerator.generateContractsListPDF(title, contracts, this);
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

    /**
     * Admin export: allow choosing which PDF to open/share: per-technician or All.
     */
    private void showContractsPdfOptionsForUser004(Map<String, File> ownerPdfs) {
        List<String> owners = new ArrayList<>(ownerPdfs.keySet());
        owners.sort((a, b) -> {
            if ("All".equalsIgnoreCase(a)) return -1;
            if ("All".equalsIgnoreCase(b)) return 1;
            return a.compareToIgnoreCase(b);
        });

        CharSequence[] items = owners.toArray(new CharSequence[0]);

        new AlertDialog.Builder(this)
                .setTitle("Contracts PDFs Generated")
                .setMessage("Select a PDF to view or share. \"All\" contains every contract.")
                .setItems(items, (dialog, which) -> {
                    String owner = owners.get(which);
                    File pdf = ownerPdfs.get(owner);
                    if (pdf != null) {
                        showContractsPdfOptions(pdf);
                    } else {
                        Toast.makeText(this, "PDF not found for " + owner, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

}
