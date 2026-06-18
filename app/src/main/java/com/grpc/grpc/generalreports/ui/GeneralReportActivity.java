package com.grpc.grpc.generalreports.ui;

import com.grpc.grpc.R;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.grpc.grpc.core.HorizontalSwipeGuard;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.reports.ui.ReportActivity;
import com.grpc.grpc.jobs.rodent.RodentActivityExternalRoutine;
import com.grpc.grpc.jobs.rodent.RodentActivityRoutine;
import com.grpc.grpc.jobs.rodent.RodentCallOutActivity;
import com.grpc.grpc.jobs.rodent.RodentCallOutExternalActivity;
import com.grpc.grpc.jobs.rodent.RodentInitialActivity;
import com.grpc.grpc.jobs.rodent.RodentRoutineActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class GeneralReportActivity extends AppCompatActivity {
    private static final String CONTRACT_FILTER_ALL = "All";

    private EditText nameInput, addressInput, dateInput;
    private Spinner visitTypeSpinner;
    private Spinner managementFolderSpinner;
    private Spinner contractFilterSpinner;
    private Spinner contractSpinner;
    private Button saveButton, selectImageButton;
    private CheckBox jobReportCheckbox, managementReportCheckbox, contractReportCheckbox;
    private java.util.List<Uri> selectedImageUris = new java.util.ArrayList<>();
    private final List<ContractOption> contractOptions = new ArrayList<>();

    private String userName;
    private String contractId;
    private String initialCompanyName;
    private String initialAddress;
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    private final String[] visitTypes = {
            "Initial Setup",
            "Routine",
            "Rodent Activity Internal Routine",
            "Rodent Activity External Routine",
            "Rodent Call Out External",
            "Rodent Call Out Internal"
    };

    private static class ContractOption {
        String id;
        String companyName;
        String address;
        boolean placeholder;
        @Override public String toString() {
            if (placeholder) return "Select contract...";
            String n = companyName != null ? companyName.trim() : "";
            String a = getFriendlyLocation(address);
            if (!n.isEmpty() && !a.isEmpty()) return n + " | " + a;
            if (!n.isEmpty()) return n;
            if (!a.isEmpty()) return a;
            return "Unnamed contract";
        }

        private String getFriendlyLocation(String rawAddress) {
            if (rawAddress == null) return "";
            String clean = rawAddress.trim().replaceAll("\\s+", " ");
            if (clean.isEmpty()) return "";

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)\\b(?:[AC-FHKNPRTV-Y]\\d{2}|D6W)[\\s-]?[0-9AC-FHKNPRTV-Y]{4}\\b")
                    .matcher(clean);
            if (m.find()) {
                return m.group().replace(" ", "").toUpperCase(java.util.Locale.getDefault());
            }

            int comma = clean.indexOf(',');
            String area = comma > 0 ? clean.substring(0, comma).trim() : clean;
            if (area.length() > 28) area = area.substring(0, 28) + "...";
            return area;
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_report_creator);

        userName = getIntent().getStringExtra("USER_NAME");
        Log.d("GeneralReportActivity", "GeneralReportActivity created with user: " + userName);

        nameInput = findViewById(R.id.nameInput);
        addressInput = findViewById(R.id.addressInput);
        dateInput = findViewById(R.id.dateInput);
        visitTypeSpinner = findViewById(R.id.visitTypeSpinner);
        managementFolderSpinner = findViewById(R.id.managementFolderSpinner);
        contractFilterSpinner = findViewById(R.id.contractFilterSpinner);
        contractSpinner = findViewById(R.id.contractSpinner);
        jobReportCheckbox = findViewById(R.id.jobReportCheckbox);
        managementReportCheckbox = findViewById(R.id.managementReportCheckbox);
        contractReportCheckbox = findViewById(R.id.contractReportCheckbox);
        saveButton = findViewById(R.id.saveButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        View previewButton = findViewById(R.id.previewButton);
        if (previewButton != null) {
            previewButton.setVisibility(View.GONE);
        }

        // Auto-fill date only
        dateInput.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));

        // Auto-fill name and address if provided
        contractId = getIntent().getStringExtra("CONTRACT_ID");
        String companyName = getIntent().getStringExtra("COMPANY_NAME");
        String address = getIntent().getStringExtra("ADDRESS");
        initialCompanyName = companyName;
        initialAddress = address;
        
        if (companyName != null && !companyName.isEmpty()) {
            nameInput.setText(companyName);
        }
        
        if (address != null && !address.isEmpty()) {
            addressInput.setText(address);
        }

        setupRoutingControls();
        loadManagementFolders();
        loadContractsForPicker();

        // Dropdown setup
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, visitTypes);
        visitTypeSpinner.setAdapter(adapter);

        saveButton.setOnClickListener(v -> handleCreateReport());
        
        // Image selection button - opens image picker
        selectImageButton.setOnClickListener(v -> openImageSelector());
        
        // Initialize gesture detector for swipe navigation
        initializeGestureDetector();
    }

    private void handleCreateReport() {
        String companyName = nameInput.getText().toString().trim();
        String address = addressInput.getText().toString().trim();
        String date = dateInput.getText().toString().trim();
        String visitType = visitTypeSpinner.getSelectedItem().toString();

        if (companyName.isEmpty() || address.isEmpty()) {
            Toast.makeText(this, "Please enter both name and address", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent;

        switch (visitType) {
            case "Initial Setup":
                intent = new Intent(this, RodentInitialActivity.class);
                break;
            case "Routine":
                intent = new Intent(this, RodentRoutineActivity.class);
                break;
            case "Rodent Activity Internal Routine":
                intent = new Intent(this, RodentActivityRoutine.class);
                break;
            case "Rodent Activity External Routine":
                intent = new Intent(this, RodentActivityExternalRoutine.class);
                break;
            case "Rodent Call Out Internal":
                intent = new Intent(this, RodentCallOutActivity.class);
                break;
            case "Rodent Call Out External":
                intent = new Intent(this, RodentCallOutExternalActivity.class);
                break;
            default:
                Toast.makeText(this, "Unknown visit type", Toast.LENGTH_SHORT).show();
                return;
        }

        intent.putExtra("USER_NAME", userName);
        if (contractId != null && !contractId.trim().isEmpty()) {
            intent.putExtra("CONTRACT_ID", contractId);
        }
        intent.putExtra("COMPANY_NAME", companyName);
        intent.putExtra("ADDRESS", address);
        intent.putExtra("ROUTINE_TYPE", visitType);
        intent.putExtra("REPORT_DATE", date); // Pass the date to target activities
        intent.putExtra("ROUTE_MODE", getRouteMode());
        intent.putExtra("ROUTE_FOLDER", getRouteFolder());

        // Pass selected images if any
        if (!selectedImageUris.isEmpty()) {
            intent.putParcelableArrayListExtra("SELECTED_IMAGES", new java.util.ArrayList<>(selectedImageUris));
        }

        startActivity(intent);
        finish();
    }

    private void setupRoutingControls() {
        setupContractFilterSpinner();
        if (jobReportCheckbox != null) {
            jobReportCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (managementReportCheckbox != null) managementReportCheckbox.setChecked(false);
                    if (contractReportCheckbox != null) contractReportCheckbox.setChecked(false);
                }
            });
        }
        if (managementReportCheckbox != null) {
            managementReportCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (jobReportCheckbox != null) jobReportCheckbox.setChecked(false);
                    if (contractReportCheckbox != null) contractReportCheckbox.setChecked(false);
                }
                if (managementFolderSpinner != null) {
                    managementFolderSpinner.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
                    managementFolderSpinner.setEnabled(isChecked);
                }
            });
        }
        if (contractReportCheckbox != null) {
            contractReportCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (jobReportCheckbox != null) jobReportCheckbox.setChecked(false);
                    if (managementReportCheckbox != null) managementReportCheckbox.setChecked(false);
                }
                if (contractSpinner != null) {
                    contractSpinner.setEnabled(isChecked);
                    contractSpinner.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                if (contractFilterSpinner != null) {
                    boolean showFilter = isChecked && canChooseAllContracts();
                    contractFilterSpinner.setEnabled(showFilter);
                    contractFilterSpinner.setVisibility(showFilter ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                if (isChecked) loadContractsForPicker();
            });
        }
        if (contractSpinner != null) {
            contractSpinner.setEnabled(false);
            contractSpinner.setVisibility(android.view.View.GONE);
            contractSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    if (position >= 0 && position < contractOptions.size() && contractReportCheckbox != null && contractReportCheckbox.isChecked()) {
                        ContractOption option = contractOptions.get(position);
                        if (option.placeholder) return;
                        contractId = option.id;
                        if (option.companyName != null) nameInput.setText(option.companyName);
                        if (option.address != null) addressInput.setText(option.address);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        if (contractId != null && !contractId.trim().isEmpty() && contractReportCheckbox != null) {
            contractReportCheckbox.setChecked(true);
            if (contractSpinner != null) {
                contractSpinner.setEnabled(true);
                contractSpinner.setVisibility(android.view.View.VISIBLE);
            }
        }

        String routeMode = getIntent().getStringExtra("ROUTE_MODE");
        if ("management".equalsIgnoreCase(routeMode) && managementReportCheckbox != null) {
            managementReportCheckbox.setChecked(true);
            if (managementFolderSpinner != null) {
                managementFolderSpinner.setVisibility(android.view.View.VISIBLE);
                managementFolderSpinner.setEnabled(true);
            }
        }
    }

    private String getRouteMode() {
        if (jobReportCheckbox != null && jobReportCheckbox.isChecked()) return "job";
        if (managementReportCheckbox != null && managementReportCheckbox.isChecked()) return "management";
        if (contractReportCheckbox != null && contractReportCheckbox.isChecked()) return "contract";
        return "default";
    }

    private String getRouteFolder() {
        if ("management".equals(getRouteMode()) && managementFolderSpinner != null && managementFolderSpinner.getSelectedItem() != null) {
            return managementFolderSpinner.getSelectedItem().toString();
        }
        return "";
    }

    private void loadManagementFolders() {
        if (managementFolderSpinner == null) return;
        com.google.firebase.storage.FirebaseStorage.getInstance().getReference().listAll().addOnSuccessListener(rootList -> {
            String managementRootName = "management jobs";
            for (com.google.firebase.storage.StorageReference p : rootList.getPrefixes()) {
                if (p == null || p.getName() == null) continue;
                String n = p.getName().trim();
                if ("management jobs".equalsIgnoreCase(n)) {
                    managementRootName = n;
                    break;
                }
                if ("managment jobs".equalsIgnoreCase(n)) managementRootName = n;
            }
            final String rootNameFinal = managementRootName;
            com.google.firebase.storage.FirebaseStorage.getInstance().getReference().child(rootNameFinal).listAll()
                    .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                        List<String> folderNames = new ArrayList<>();
                        for (com.google.firebase.storage.StorageReference p : listResult.getPrefixes()) {
                            if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                                folderNames.add(p.getName().trim());
                            }
                        }
                        if (folderNames.isEmpty()) folderNames.add(rootNameFinal);
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames);
                        managementFolderSpinner.setAdapter(adapter);
                        managementFolderSpinner.setVisibility(android.view.View.GONE);
                        managementFolderSpinner.setEnabled(false);
                    }))
                    .addOnFailureListener(e -> runOnUiThread(() -> {
                        List<String> fallback = new ArrayList<>();
                        fallback.add(rootNameFinal);
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fallback);
                        managementFolderSpinner.setAdapter(adapter);
                        managementFolderSpinner.setVisibility(android.view.View.GONE);
                        managementFolderSpinner.setEnabled(false);
                    }));
        }).addOnFailureListener(e -> {
            List<String> fallback = new ArrayList<>();
            fallback.add("management jobs");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fallback);
            managementFolderSpinner.setAdapter(adapter);
            managementFolderSpinner.setVisibility(android.view.View.GONE);
            managementFolderSpinner.setEnabled(false);
        });
    }

    private void loadContractsForPicker() {
        if (contractSpinner == null) return;
        String contractKey = SessionManager.getContractKey(this);
        String selectedKey = contractFilterSpinner != null && contractFilterSpinner.getSelectedItem() != null
                ? contractFilterSpinner.getSelectedItem().toString().trim() : "";
        boolean showAll = canChooseAllContracts()
                && (selectedKey.isEmpty() || CONTRACT_FILTER_ALL.equalsIgnoreCase(selectedKey));
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.Query q = db.collection("contracts");
        if (!showAll && canChooseAllContracts() && !selectedKey.isEmpty()) {
            q = q.whereEqualTo("assignedTech", selectedKey.toLowerCase(Locale.getDefault()));
        } else if (!showAll && contractKey != null && !contractKey.trim().isEmpty()) {
            q = q.whereEqualTo("assignedTech", contractKey.trim().toLowerCase(Locale.getDefault()));
        }
        q.limit(300).get().addOnSuccessListener(snap -> {
            contractOptions.clear();
            ContractOption placeholder = new ContractOption();
            placeholder.placeholder = true;
            contractOptions.add(placeholder);
            for (QueryDocumentSnapshot d : snap) {
                ContractOption option = new ContractOption();
                option.id = d.getId();
                String nm = d.getString("name");
                if (nm == null || nm.trim().isEmpty()) nm = d.getString("companyName");
                option.companyName = nm;
                option.address = d.getString("address");
                contractOptions.add(option);
            }
            if (contractOptions.size() == 1 && initialCompanyName != null && !initialCompanyName.trim().isEmpty()) {
                ContractOption fallback = new ContractOption();
                fallback.id = contractId;
                fallback.companyName = initialCompanyName;
                fallback.address = initialAddress;
                contractOptions.add(fallback);
            }
            com.grpc.grpc.core.ContractIconSpinnerAdapter<ContractOption> adapter =
                    new com.grpc.grpc.core.ContractIconSpinnerAdapter<>(this, contractOptions);
            contractSpinner.setAdapter(adapter);
            if (contractId != null) {
                for (int i = 0; i < contractOptions.size(); i++) {
                    if (contractId.equals(contractOptions.get(i).id)) {
                        contractSpinner.setSelection(i);
                        break;
                    }
                }
            } else {
                contractSpinner.setSelection(0);
            }
        });
    }

    private boolean canChooseAllContracts() {
        return SessionManager.isAdmin(this) || SessionManager.isSuperAdmin(this);
    }

    private void setupContractFilterSpinner() {
        if (contractFilterSpinner == null) return;
        if (!canChooseAllContracts()) {
            contractFilterSpinner.setVisibility(android.view.View.GONE);
            contractFilterSpinner.setEnabled(false);
            return;
        }
        List<String> loading = new ArrayList<>();
        loading.add("Loading users...");
        contractFilterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, loading));
        contractFilterSpinner.setVisibility(android.view.View.GONE);
        contractFilterSpinner.setEnabled(false);
        contractFilterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (contractReportCheckbox != null && contractReportCheckbox.isChecked()) {
                    loadContractsForPicker();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        FirebaseFirestore.getInstance().collection("users").get().addOnSuccessListener(snap -> runOnUiThread(() -> {
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            if (snap != null) {
                for (QueryDocumentSnapshot d : snap) {
                    String ck = d.getString("contractKey");
                    if (ck == null || ck.trim().isEmpty()) ck = d.getString("ContractKey");
                    if (ck == null) continue;
                    ck = ck.trim();
                    if (!ck.isEmpty()) keys.add(ck);
                }
            }
            List<String> options = new ArrayList<>(keys);
            options.sort(String::compareToIgnoreCase);
            options.add(CONTRACT_FILTER_ALL);
            contractFilterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
            int allIndex = options.indexOf(CONTRACT_FILTER_ALL);
            contractFilterSpinner.setSelection(allIndex >= 0 ? allIndex : 0);
        })).addOnFailureListener(e -> runOnUiThread(() -> {
            List<String> options = new ArrayList<>();
            options.add(CONTRACT_FILTER_ALL);
            contractFilterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
            contractFilterSpinner.setSelection(0);
        }));
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
                    
                    Log.d("GeneralReportActivity", "Swipe detected - diffX: " + diffX + ", diffY: " + diffY + ", velocityX: " + velocityX);
                    
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        // Prevent accidental left/right navigation while a form on this screen has input focus/text.
                        if (HorizontalSwipeGuard.shouldBlock(GeneralReportActivity.this)) {
                            return false;
                        }
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Swipe right - open ReportActivity
                                Log.d("GeneralReportActivity", "Swipe RIGHT detected - opening ReportActivity with user: " + userName);
                                Intent intent = new Intent(GeneralReportActivity.this, ReportActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                if (contractId != null && !contractId.trim().isEmpty()) {
                                    intent.putExtra("CONTRACT_ID", contractId);
                                }
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            } else {
                                // Swipe left - open WorkViewActivity (previous in sequence)
                                Log.d("GeneralReportActivity", "Swipe LEFT detected - opening WorkViewActivity with user: " + userName);
                                Intent intent = new Intent(GeneralReportActivity.this, com.grpc.grpc.workview.ui.WorkViewActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            }
                        } else {
                            Log.d("GeneralReportActivity", "Swipe threshold not met - diffX: " + Math.abs(diffX) + ", velocityX: " + Math.abs(velocityX));
                        }
                    }
                } catch (Exception e) {
                    Log.e("GeneralReportActivity", "Error in swipe detection: " + e.getMessage());
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
     * Opens the image selector to choose images for the report
     */
    private void openImageSelector() {
        startActivityForResult(Intent.createChooser(
                com.grpc.grpc.core.ReportImageStorage.createImagePickerIntent(), "Select Images"), 1001);
    }

    /**
     * Handles the result from image selection
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            java.util.List<Uri> persisted = com.grpc.grpc.core.ReportImageStorage.persistFromPickerResult(this, data);
            if (!persisted.isEmpty()) {
                selectedImageUris.addAll(persisted);
            }
            Toast.makeText(this, "Selected " + selectedImageUris.size() + " image(s)", Toast.LENGTH_SHORT).show();
        }
    }
}
