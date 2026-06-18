package com.grpc.grpc.reports.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.reports.model.ProductUsageItem;
import com.grpc.grpc.reports.pdf.ActionFormPdfGenerator;
import com.grpc.grpc.reports.util.PrepProductsFormatter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.grpc.grpc.core.ContractStorageUploader;
import com.grpc.grpc.core.ContractStoragePathHelper;
import com.grpc.grpc.core.StorageFolderHelper;

// AI and Voice Recognition Imports
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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.GestureDetectorCompat;
import com.grpc.grpc.core.HorizontalSwipeGuard;
import com.grpc.grpc.main.MainActivity;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.FileOutputStream;

import com.grpc.grpc.core.ContractReportSync;
import com.grpc.grpc.core.DictateEditText;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.core.TenantBranding;

public class ActionFormActivity extends AppCompatActivity {
    private static final String CONTRACT_FILTER_ALL = "All";
    private static final int REQUEST_PREVIEW_ACTION_FORM = 902;
    private static final int SWIPE_THRESHOLD = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 50;

    private GestureDetectorCompat gestureDetector;
    private boolean swipeNavEligible;

    // UI Components - Action Form specific fields
    private DictateEditText serviceTypeInput, serviceNumberInput, premisesNameInput, premisesAddressInput;
    private DictateEditText serviceReportInput, recommendationsInput;
    private PrepProductsSectionController prepProductsSection;
    private DictateEditText followUp1Input;
    private DictateEditText roleInput;
    private EditText dateTimeInput;

    // User context and session management
    private String userName;
    private String contractId;
    private String contractCompanyName;
    private CheckBox jobReportCheckbox;
    private CheckBox managementReportCheckbox;
    private CheckBox contractReportCheckbox;
    private Spinner jobFolderSpinner;
    private Spinner jobRouteSubFolderSpinner;
    private Spinner jobRouteSubFolderSpinnerLevel3;
    private Spinner managementFolderSpinner;
    private Spinner routeSubFolderSpinner;
    private Spinner routeSubFolderSpinnerLevel3;
    private String managementRootStorageFolder = defaultManagementRootFolder();
    private boolean lastUploadSucceeded = false;
    private Spinner contractFilterSpinner;
    private Spinner contractSpinner;
    private final List<ContractOption> contractOptions = new ArrayList<>();
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Action Buttons
    private Button previewButton, saveButton, backButton, selectImageButton, readBackButton;
    private Button technicianSignatureButton, customerSignatureButton;
    private ToggleButton followUpToggle;
    private LinearLayout followUpContainer;
    private CheckBox passwordProtectCheckbox;

    // Data Management
    private List<Uri> selectedImageUris = new ArrayList<>();
    private Uri technicianSignatureUri = null;
    private Uri customerSignatureUri = null;
    private String lastAIResponse = ""; // Store the last AI response for read-back functionality

    // AI Fix: same keys as Chat (Firestore AI-Chat/AI-API: KEY = HF, key-grog = Groq)
    private static final String GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";
    private static final String HF_ROUTER_CHAT_URL = "https://router.huggingface.co/v1/chat/completions";
    private static final String HF_ROUTER_MODEL = "openai/gpt-oss-20b:groq";
    private static final int AI_FIX_MAX_TOKENS = 2048;
    
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    
    // Interactive Voice System
    private String pendingField = "";
    private boolean isInteractiveMode = false;
    private Handler autoProgressHandler = new Handler();
    private int currentFieldIndex = 0;
    private static final String[] AUTO_PROGRESS_FIELDS = {
        "service type", "service number", "premises name", "premises address",
        "prep", "service report", "recommendations", "follow up 1", "role"
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
        setContentView(R.layout.activity_action_form);
        
        // Performance optimizations
        getWindow().setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        
        // Handle keyboard properly
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        // User authentication
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null || userName.isEmpty()) {
            Toast.makeText(this, "Error: User name not found!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI components on main thread (light operations)
        initializeInputFields();
        initializeButtons();
        initializeGestureDetector();
        setCurrentDateTime();
        setupWelcomeMessage();
        
        // Initialize SpeechRecognizer on main thread (required)
        initializeSpeechRecognizer();
        
        // Initialize TextToSpeech on background thread
        new Thread(() -> {
            initializeTextToSpeech();
        }).start();
    }

    private void initializeGestureDetector() {
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                try {
                    float diffX = e2.getX() - e1.getX();
                    float diffY = e2.getY() - e1.getY();
                    if (Math.abs(diffX) > Math.abs(diffY)
                            && Math.abs(diffX) > SWIPE_THRESHOLD
                            && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                            && !HorizontalSwipeGuard.shouldBlock(ActionFormActivity.this)) {
                        if (diffX > 0) {
                            Intent intent = new Intent(ActionFormActivity.this, ReportActivity.class);
                            intent.putExtra("USER_NAME", userName);
                            if (contractId != null) intent.putExtra("CONTRACT_ID", contractId);
                            if (premisesNameInput != null && premisesNameInput.getEditText() != null) {
                                String n = premisesNameInput.getEditText().getText().toString().trim();
                                if (!n.isEmpty()) intent.putExtra("COMPANY_NAME", n);
                            }
                            if (premisesAddressInput != null && premisesAddressInput.getEditText() != null) {
                                String a = premisesAddressInput.getEditText().getText().toString().trim();
                                if (!a.isEmpty()) intent.putExtra("ADDRESS", a);
                            }
                            startActivity(intent);
                            finish();
                            return true;
                        } else {
                            Intent intent = new Intent(ActionFormActivity.this, MainActivity.class);
                            intent.putExtra("USER_NAME", userName);
                            startActivity(intent);
                            finish();
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        try {
            if (event != null && gestureDetector != null) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    swipeNavEligible = true;
                }
                if (swipeNavEligible && gestureDetector.onTouchEvent(event)) {
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    swipeNavEligible = false;
                }
            }
        } catch (Exception ignored) {
        }
        return super.dispatchTouchEvent(event);
    }

    private void initializeInputFields() {
        serviceTypeInput = findViewById(R.id.serviceTypeInput);
        serviceNumberInput = findViewById(R.id.serviceNumberInput);
        premisesNameInput = findViewById(R.id.premisesNameInput);
        premisesAddressInput = findViewById(R.id.premisesAddressInput);
        prepProductsSection = new PrepProductsSectionController(this, R.id.prepProductsSection);
        serviceReportInput = findViewById(R.id.serviceReportInput);
        new ServiceReportCatalogController(this, R.id.serviceReportCatalogBar, 0, serviceReportInput);
        recommendationsInput = findViewById(R.id.recommendationsInput);
        new RecommendationsCatalogController(this, R.id.recommendationsCatalogBar, recommendationsInput);
        followUp1Input = findViewById(R.id.followUp1Input);
        roleInput = findViewById(R.id.roleInput);
        dateTimeInput = findViewById(R.id.dateTimeInput);

        // Configure hints and settings
        serviceTypeInput.setHint("Enter Service Type");
        serviceNumberInput.setHint("Enter Service Number");
        premisesNameInput.setHint("Enter Premises Name");
        premisesAddressInput.setHint("Enter Premises Address");
        premisesAddressInput.setMinLines(3);
        premisesAddressInput.setGravity(android.view.Gravity.TOP);
        serviceReportInput.setHint("Enter service report (or use template above)");
        serviceReportInput.setMinLines(3);
        serviceReportInput.setGravity(android.view.Gravity.TOP);
        recommendationsInput.setHint("Enter recommendations (or use template above)");
        recommendationsInput.setMinLines(3);
        recommendationsInput.setGravity(android.view.Gravity.TOP);
        followUp1Input.setHint("Enter Follow-Up Details");
        roleInput.setHint("Role (title -- number)");

        // Auto-fill premises name and address if provided from intent
        contractId = getIntent().getStringExtra("CONTRACT_ID");
        String companyName = getIntent().getStringExtra("COMPANY_NAME");
        contractCompanyName = companyName;
        String address = getIntent().getStringExtra("ADDRESS");
        
        if (companyName != null && !companyName.isEmpty() && !companyName.equals("N/A")) {
            premisesNameInput.getEditText().setText(companyName);
        }
        
        if (address != null && !address.isEmpty() && !address.equals("N/A")) {
            premisesAddressInput.getEditText().setText(address);
        }

        // Auto-fill Role with "title -- number" when available.
        try {
            if (roleInput != null) {
                android.widget.EditText roleEdit = roleInput.getEditText();
                if (roleEdit != null) {
                    String userId = StaffDirectory.getUserId(userName);
                    String title = StaffDirectory.getTitleForUserId(userId);
                    String number = StaffDirectory.getMobileForUserId(userId);
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    if (title != null && !title.trim().isEmpty()) parts.add(title.trim());
                    if (number != null && !number.trim().isEmpty()) parts.add(number.trim());
                    if (!parts.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.size(); i++) {
                            if (i > 0) sb.append(" -- ");
                            sb.append(parts.get(i));
                        }
                        roleEdit.setText(sb.toString());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void initializeButtons() {
        previewButton = findViewById(R.id.previewButton);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        selectImageButton = findViewById(R.id.selectImageButton);
        readBackButton = findViewById(R.id.readBackButton);
        jobReportCheckbox = findViewById(R.id.jobReportCheckbox);
        managementReportCheckbox = findViewById(R.id.managementReportCheckbox);
        contractReportCheckbox = findViewById(R.id.contractReportCheckbox);
        jobFolderSpinner = findViewById(R.id.jobFolderSpinner);
        jobRouteSubFolderSpinner = findViewById(R.id.jobRouteSubFolderSpinner);
        jobRouteSubFolderSpinnerLevel3 = findViewById(R.id.jobRouteSubFolderSpinnerLevel3);
        managementFolderSpinner = findViewById(R.id.managementFolderSpinner);
        routeSubFolderSpinner = findViewById(R.id.routeSubFolderSpinner);
        routeSubFolderSpinnerLevel3 = findViewById(R.id.routeSubFolderSpinnerLevel3);
        contractFilterSpinner = findViewById(R.id.contractFilterSpinner);
        contractSpinner = findViewById(R.id.contractSpinner);
        setupRoutingControls();
        technicianSignatureButton = findViewById(R.id.technicianSignatureButton);
        customerSignatureButton = findViewById(R.id.customerSignatureButton);
        followUpToggle = findViewById(R.id.followUpToggle);
        followUpContainer = findViewById(R.id.followUpContainer);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        if (previewButton != null) {
            previewButton.setOnClickListener(v -> previewActionForm());
        }
        saveButton.setOnClickListener(v -> saveActionForm());
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ActionFormActivity.this, ReportSelectionActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
        });
        selectImageButton.setOnClickListener(v -> openImageSelector());
        // AI Fix: temporarily hidden for all users (backend logic retained)
        readBackButton.setOnClickListener(v -> readFormBack());
        
        // Signature capture buttons
        technicianSignatureButton.setOnClickListener(v -> captureSignature("technician"));
        customerSignatureButton.setOnClickListener(v -> captureSignature("customer"));
        
        // Follow-up toggle
        followUpToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                followUpContainer.setVisibility(android.view.View.VISIBLE);
            } else {
                followUpContainer.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void setupRoutingControls() {
        loadJobWorkFolderOptions();
        loadManagementFolderOptions();
        setupContractFilterSpinner();
        if (jobReportCheckbox != null) {
            jobReportCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (managementReportCheckbox != null) managementReportCheckbox.setChecked(false);
                    if (contractReportCheckbox != null) contractReportCheckbox.setChecked(false);
                }
                if (jobFolderSpinner != null) {
                    jobFolderSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    jobFolderSpinner.setEnabled(isChecked);
                }
                if (isChecked && jobFolderSpinner != null && jobFolderSpinner.getSelectedItem() != null) {
                    loadJobRouteSubFolderOptions("JobWorkReports", jobFolderSpinner.getSelectedItem().toString());
                }
                if (!isChecked) {
                    setJobRouteSubFolderSpinnerVisible(false);
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
                    managementFolderSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    managementFolderSpinner.setEnabled(isChecked);
                }
                if (jobFolderSpinner != null && isChecked) {
                    jobFolderSpinner.setVisibility(View.GONE);
                    jobFolderSpinner.setEnabled(false);
                }
                if (isChecked && managementFolderSpinner != null && managementFolderSpinner.getSelectedItem() != null) {
                    loadRouteSubFolderOptions(managementRootStorageFolder, managementFolderSpinner.getSelectedItem().toString());
                }
                if (!isChecked) {
                    setRouteSubFolderSpinnerVisible(false);
                }
            });
        }
        if (jobFolderSpinner != null) {
            jobFolderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (jobReportCheckbox == null || !jobReportCheckbox.isChecked()) return;
                    Object item = parent.getItemAtPosition(position);
                    loadJobRouteSubFolderOptions("JobWorkReports", item != null ? item.toString() : "");
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    setJobRouteSubFolderSpinnerVisible(false);
                }
            });
        }
        if (jobRouteSubFolderSpinner != null) {
            jobRouteSubFolderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    Object item = parent.getItemAtPosition(position);
                    String selectedSubFolder = item != null ? item.toString().trim() : "";
                    String basePath = jobRouteSubFolderSpinner.getTag() instanceof String
                            ? (String) jobRouteSubFolderSpinner.getTag() : "";
                    loadJobRouteThirdSubFolderOptions(basePath, selectedSubFolder);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    setJobRouteSubFolderLevel3SpinnerVisible(false);
                }
            });
        }
        if (managementFolderSpinner != null) {
            managementFolderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (managementReportCheckbox == null || !managementReportCheckbox.isChecked()) return;
                    Object item = parent.getItemAtPosition(position);
                    loadRouteSubFolderOptions(managementRootStorageFolder, item != null ? item.toString() : "");
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    setRouteSubFolderSpinnerVisible(false);
                }
            });
        }
        if (routeSubFolderSpinner != null) {
            routeSubFolderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    Object item = parent.getItemAtPosition(position);
                    String selectedSubFolder = item != null ? item.toString().trim() : "";
                    String basePath = routeSubFolderSpinner.getTag() instanceof String
                            ? (String) routeSubFolderSpinner.getTag() : "";
                    loadRouteThirdSubFolderOptions(basePath, selectedSubFolder);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    setRouteSubFolderLevel3SpinnerVisible(false);
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
                    contractSpinner.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    contractSpinner.setEnabled(isChecked);
                }
                if (contractFilterSpinner != null) {
                    boolean showFilter = isChecked && canChooseAllContracts();
                    contractFilterSpinner.setVisibility(showFilter ? View.VISIBLE : View.GONE);
                    contractFilterSpinner.setEnabled(showFilter);
                }
                if (isChecked) {
                    setRouteSubFolderSpinnerVisible(false);
                    setJobRouteSubFolderSpinnerVisible(false);
                }
                if (isChecked) loadContractsForPicker();
            });
        }
        if (contractSpinner != null) {
            contractSpinner.setVisibility((contractReportCheckbox != null && contractReportCheckbox.isChecked()) ? View.VISIBLE : View.GONE);
            contractSpinner.setEnabled(contractReportCheckbox != null && contractReportCheckbox.isChecked());
            contractSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position < 0 || position >= contractOptions.size()) return;
                    if (contractReportCheckbox == null || !contractReportCheckbox.isChecked()) return;
                    ContractOption option = contractOptions.get(position);
                    if (option.placeholder) return;
                    contractId = option.id;
                    contractCompanyName = option.companyName;
                    if (premisesNameInput != null && premisesNameInput.getEditText() != null && option.companyName != null) {
                        premisesNameInput.getEditText().setText(option.companyName);
                    }
                    if (premisesAddressInput != null && premisesAddressInput.getEditText() != null && option.address != null) {
                        premisesAddressInput.getEditText().setText(option.address);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        }
        if (contractReportCheckbox != null && contractId != null && !contractId.trim().isEmpty()) {
            contractReportCheckbox.setChecked(true);
            loadContractsForPicker();
        }
        String routeMode = getIntent().getStringExtra("ROUTE_MODE");
        if ("job".equalsIgnoreCase(routeMode) && jobReportCheckbox != null) {
            jobReportCheckbox.setChecked(true);
            if (jobFolderSpinner != null) {
                jobFolderSpinner.setVisibility(View.VISIBLE);
                jobFolderSpinner.setEnabled(true);
            }
        } else if ("management".equalsIgnoreCase(routeMode) && managementReportCheckbox != null) {
            managementReportCheckbox.setChecked(true);
            if (managementFolderSpinner != null) {
                managementFolderSpinner.setVisibility(View.VISIBLE);
                managementFolderSpinner.setEnabled(true);
            }
            String routeFolder = getIntent().getStringExtra("ROUTE_FOLDER");
            if (routeFolder != null && managementFolderSpinner != null && managementFolderSpinner.getAdapter() != null) {
                for (int i = 0; i < managementFolderSpinner.getAdapter().getCount(); i++) {
                    Object item = managementFolderSpinner.getAdapter().getItem(i);
                    if (item != null && routeFolder.equals(item.toString())) {
                        managementFolderSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void loadJobWorkFolderOptions() {
        if (jobFolderSpinner == null) return;
        FirebaseStorage.getInstance().getReference().child("JobWorkReports").listAll()
                .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                    List<String> folderNames = new ArrayList<>();
                    for (StorageReference p : listResult.getPrefixes()) {
                        if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                            folderNames.add(p.getName().trim());
                        }
                    }
                    if (folderNames.isEmpty()) folderNames.add("JobWorkReports");
                    jobFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames));
                    jobFolderSpinner.setVisibility(View.GONE);
                    jobFolderSpinner.setEnabled(false);
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    List<String> fallback = new ArrayList<>();
                    fallback.add("JobWorkReports");
                    jobFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fallback));
                    jobFolderSpinner.setVisibility(View.GONE);
                    jobFolderSpinner.setEnabled(false);
                }));
    }

    private void loadRouteSubFolderOptions(String rootFolder, String selectedFolder) {
        if (routeSubFolderSpinner == null) return;
        String selected = selectedFolder != null ? selectedFolder.trim() : "";
        if (selected.isEmpty() || rootFolder.equalsIgnoreCase(selected)) {
            setRouteSubFolderSpinnerVisible(false);
            return;
        }
        String path = rootFolder + "/" + selected;
        FirebaseStorage.getInstance().getReference().child(path).listAll()
                .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                    List<String> folderNames = new ArrayList<>();
                    for (StorageReference p : listResult.getPrefixes()) {
                        if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                            folderNames.add(p.getName().trim());
                        }
                    }
                    if (folderNames.isEmpty()) {
                        setRouteSubFolderSpinnerVisible(false);
                        return;
                    }
                    routeSubFolderSpinner.setTag(path);
                    routeSubFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames));
                    setRouteSubFolderSpinnerVisible(true);
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> setRouteSubFolderSpinnerVisible(false)));
    }

    private void setRouteSubFolderSpinnerVisible(boolean visible) {
        if (routeSubFolderSpinner == null) return;
        routeSubFolderSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
        routeSubFolderSpinner.setEnabled(visible);
        if (!visible) {
            routeSubFolderSpinner.setTag(null);
            setRouteSubFolderLevel3SpinnerVisible(false);
        }
    }

    private void loadRouteThirdSubFolderOptions(String basePath, String selectedSubFolder) {
        if (routeSubFolderSpinnerLevel3 == null) return;
        String base = basePath != null ? basePath.trim() : "";
        String selected = selectedSubFolder != null ? selectedSubFolder.trim() : "";
        if (base.isEmpty() || selected.isEmpty()) {
            setRouteSubFolderLevel3SpinnerVisible(false);
            return;
        }
        String thirdPath = base + "/" + selected;
        FirebaseStorage.getInstance().getReference().child(thirdPath).listAll()
                .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                    List<String> folderNames = new ArrayList<>();
                    for (StorageReference p : listResult.getPrefixes()) {
                        if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                            folderNames.add(p.getName().trim());
                        }
                    }
                    if (folderNames.isEmpty()) {
                        setRouteSubFolderLevel3SpinnerVisible(false);
                        return;
                    }
                    routeSubFolderSpinnerLevel3.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames));
                    setRouteSubFolderLevel3SpinnerVisible(true);
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> setRouteSubFolderLevel3SpinnerVisible(false)));
    }

    private void setRouteSubFolderLevel3SpinnerVisible(boolean visible) {
        if (routeSubFolderSpinnerLevel3 == null) return;
        routeSubFolderSpinnerLevel3.setVisibility(visible ? View.VISIBLE : View.GONE);
        routeSubFolderSpinnerLevel3.setEnabled(visible);
    }

    private void loadJobRouteSubFolderOptions(String rootFolder, String selectedFolder) {
        if (jobRouteSubFolderSpinner == null) return;
        String selected = selectedFolder != null ? selectedFolder.trim() : "";
        if (selected.isEmpty() || rootFolder.equalsIgnoreCase(selected)) {
            setJobRouteSubFolderSpinnerVisible(false);
            return;
        }
        String path = rootFolder + "/" + selected;
        FirebaseStorage.getInstance().getReference().child(path).listAll()
                .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                    List<String> folderNames = new ArrayList<>();
                    for (StorageReference p : listResult.getPrefixes()) {
                        if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                            folderNames.add(p.getName().trim());
                        }
                    }
                    if (folderNames.isEmpty()) {
                        setJobRouteSubFolderSpinnerVisible(false);
                        return;
                    }
                    jobRouteSubFolderSpinner.setTag(path);
                    jobRouteSubFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames));
                    setJobRouteSubFolderSpinnerVisible(true);
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> setJobRouteSubFolderSpinnerVisible(false)));
    }

    private void setJobRouteSubFolderSpinnerVisible(boolean visible) {
        if (jobRouteSubFolderSpinner == null) return;
        jobRouteSubFolderSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
        jobRouteSubFolderSpinner.setEnabled(visible);
        if (!visible) {
            jobRouteSubFolderSpinner.setTag(null);
            setJobRouteSubFolderLevel3SpinnerVisible(false);
        }
    }

    private void loadJobRouteThirdSubFolderOptions(String basePath, String selectedSubFolder) {
        if (jobRouteSubFolderSpinnerLevel3 == null) return;
        String base = basePath != null ? basePath.trim() : "";
        String selected = selectedSubFolder != null ? selectedSubFolder.trim() : "";
        if (base.isEmpty() || selected.isEmpty()) {
            setJobRouteSubFolderLevel3SpinnerVisible(false);
            return;
        }
        String thirdPath = base + "/" + selected;
        FirebaseStorage.getInstance().getReference().child(thirdPath).listAll()
                .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                    List<String> folderNames = new ArrayList<>();
                    for (StorageReference p : listResult.getPrefixes()) {
                        if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                            folderNames.add(p.getName().trim());
                        }
                    }
                    if (folderNames.isEmpty()) {
                        setJobRouteSubFolderLevel3SpinnerVisible(false);
                        return;
                    }
                    jobRouteSubFolderSpinnerLevel3.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames));
                    setJobRouteSubFolderLevel3SpinnerVisible(true);
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> setJobRouteSubFolderLevel3SpinnerVisible(false)));
    }

    private void setJobRouteSubFolderLevel3SpinnerVisible(boolean visible) {
        if (jobRouteSubFolderSpinnerLevel3 == null) return;
        jobRouteSubFolderSpinnerLevel3.setVisibility(visible ? View.VISIBLE : View.GONE);
        jobRouteSubFolderSpinnerLevel3.setEnabled(visible);
    }

    private boolean canChooseAllContracts() {
        return SessionManager.isAdmin(this) || SessionManager.isSuperAdmin(this);
    }

    private void setupContractFilterSpinner() {
        if (contractFilterSpinner == null) return;
        if (!canChooseAllContracts()) {
            contractFilterSpinner.setVisibility(View.GONE);
            contractFilterSpinner.setEnabled(false);
            return;
        }
        List<String> loading = new ArrayList<>();
        loading.add("Loading users...");
        contractFilterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, loading));
        contractFilterSpinner.setVisibility(View.GONE);
        contractFilterSpinner.setEnabled(false);
        contractFilterSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (contractReportCheckbox != null && contractReportCheckbox.isChecked()) {
                    loadContractsForPicker();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        FirebaseFirestore.getInstance().collection("users").get().addOnSuccessListener(snap -> runOnUiThread(() -> {
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            if (snap != null) {
                for (com.google.firebase.firestore.QueryDocumentSnapshot d : snap) {
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

    private void loadContractsForPicker() {
        if (contractSpinner == null) return;
        String contractKey = SessionManager.getContractKey(this);
        String selectedKey = contractFilterSpinner != null && contractFilterSpinner.getSelectedItem() != null
                ? contractFilterSpinner.getSelectedItem().toString().trim() : "";
        boolean showAll = canChooseAllContracts()
                && (selectedKey.isEmpty() || CONTRACT_FILTER_ALL.equalsIgnoreCase(selectedKey));
        com.google.firebase.firestore.Query q = FirebaseFirestore.getInstance().collection("contracts");
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
            for (com.google.firebase.firestore.QueryDocumentSnapshot d : snap) {
                ContractOption option = new ContractOption();
                option.id = d.getId();
                String name = d.getString("name");
                if (name == null || name.trim().isEmpty()) name = d.getString("companyName");
                option.companyName = name;
                option.address = d.getString("address");
                contractOptions.add(option);
            }
            if (contractOptions.size() == 1 && contractCompanyName != null && !contractCompanyName.trim().isEmpty()) {
                ContractOption fallback = new ContractOption();
                fallback.id = contractId;
                fallback.companyName = contractCompanyName;
                fallback.address = premisesAddressInput != null && premisesAddressInput.getEditText() != null
                        ? premisesAddressInput.getEditText().getText().toString() : "";
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

    private void loadManagementFolderOptions() {
        if (managementFolderSpinner == null) return;
        FirebaseStorage.getInstance().getReference().listAll().addOnSuccessListener(rootList -> {
            String preferredRoot = defaultManagementRootFolder();
            String fallbackRoot = alternateManagementRootFolder(preferredRoot);
            String managementRootName = preferredRoot;
            for (StorageReference p : rootList.getPrefixes()) {
                if (p == null || p.getName() == null) continue;
                String n = p.getName().trim();
                if (preferredRoot.equalsIgnoreCase(n)) {
                    managementRootName = n;
                    break;
                }
                if (fallbackRoot.equalsIgnoreCase(n)) {
                    managementRootName = n;
                }
            }
            final String rootNameFinal = managementRootName;
            managementRootStorageFolder = rootNameFinal;
            FirebaseStorage.getInstance().getReference().child(rootNameFinal).listAll()
                    .addOnSuccessListener(listResult -> runOnUiThread(() -> {
                        List<String> folderNames = new ArrayList<>();
                        for (StorageReference p : listResult.getPrefixes()) {
                            if (p != null && p.getName() != null && !p.getName().trim().isEmpty()) {
                                folderNames.add(p.getName().trim());
                            }
                        }
                        if (folderNames.isEmpty()) folderNames.add(rootNameFinal);
                        managementFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folderNames));
                        managementFolderSpinner.setVisibility(View.GONE);
                        managementFolderSpinner.setEnabled(false);
                    }))
                    .addOnFailureListener(e -> runOnUiThread(() -> {
                        List<String> fallback = new ArrayList<>();
                        fallback.add(rootNameFinal);
                        managementFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fallback));
                        managementFolderSpinner.setVisibility(View.GONE);
                        managementFolderSpinner.setEnabled(false);
                    }));
        }).addOnFailureListener(e -> {
            managementRootStorageFolder = defaultManagementRootFolder();
            List<String> fallback = new ArrayList<>();
            fallback.add(managementRootStorageFolder);
            managementFolderSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fallback));
            managementFolderSpinner.setVisibility(View.GONE);
            managementFolderSpinner.setEnabled(false);
        });
    }

    private String defaultManagementRootFolder() {
        return "grpc".equalsIgnoreCase(BuildConfig.FLAVOR) ? "managment jobs" : "management jobs";
    }

    private String alternateManagementRootFolder(String preferred) {
        return "managment jobs".equalsIgnoreCase(preferred) ? "management jobs" : "managment jobs";
    }

    private File resolveReportOutputDirectory() {
        // Keep local persistence stable: all generated reports are stored in Reports.
        File reports = new File(getExternalFilesDir(null), TenantBranding.reportsFolderName(this));
        if (!reports.exists()) reports.mkdirs();
        return reports;
    }

    private boolean hasRoutingSelection() {
        return (jobReportCheckbox != null && jobReportCheckbox.isChecked())
                || (managementReportCheckbox != null && managementReportCheckbox.isChecked())
                || (contractReportCheckbox != null && contractReportCheckbox.isChecked());
    }

    private void setCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault());
        String currentDateTime = sdf.format(new Date());
        if (dateTimeInput != null) {
            dateTimeInput.setText(currentDateTime);
        }
    }

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

    private void initializeSpeechRecognizer() {
        // Initialize SpeechRecognizer on main thread (required)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            setupSpeechRecognizer();
        }
    }
    
    private void initializeTextToSpeech() {
        // Initialize TextToSpeech on background thread
        textToSpeech = new TextToSpeech(this, status -> {
            runOnUiThread(() -> {
                if (status != TextToSpeech.SUCCESS) {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupSpeechRecognizer() {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    isListening = true;
                    Toast.makeText(ActionFormActivity.this, "🎙️ Listening... Speak now!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    isListening = false;
                    if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(ActionFormActivity.this, "No speech detected. Try again.", Toast.LENGTH_SHORT).show();
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
                runOnUiThread(() -> isListening = false);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void processVoiceCommand(String spokenText) {
        runOnUiThread(() -> {
            String lowerText = spokenText.toLowerCase();
            
            if (isInteractiveMode && !pendingField.isEmpty()) {
                fillFieldWithContent(pendingField, spokenText);
                if (currentFieldIndex < AUTO_PROGRESS_FIELDS.length) {
                    currentFieldIndex++;
                    new Handler().postDelayed(() -> progressToNextField(), 1000);
                } else {
                    isInteractiveMode = false;
                    pendingField = "";
                    currentFieldIndex = 0;
                }
                return;
            }
            
            // Process specific field commands
            if (lowerText.contains("service type")) {
                String content = extractContentAfterCommand(spokenText, "service type");
                if (!content.isEmpty()) {
                    serviceTypeInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Service Type filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("service type");
                }
            } else if (lowerText.contains("service number")) {
                String content = extractContentAfterCommand(spokenText, "service number");
                if (!content.isEmpty()) {
                    serviceNumberInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Service Number filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("service number");
                }
            } else if (lowerText.contains("premises name")) {
                String content = extractContentAfterCommand(spokenText, "premises name");
                if (!content.isEmpty()) {
                    premisesNameInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Premises Name filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("premises name");
                }
            } else if (lowerText.contains("premises address")) {
                String content = extractContentAfterCommand(spokenText, "premises address");
                if (!content.isEmpty()) {
                    premisesAddressInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Premises Address filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("premises address");
                }
            } else if (lowerText.contains("prep")) {
                String content = extractContentAfterCommand(spokenText, "prep");
                if (!content.isEmpty()) {
                    if (prepProductsSection != null) {
                        prepProductsSection.setLegacyPrepText(content);
                    }
                    Toast.makeText(this, "✅ Prep filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("prep");
                }
            } else if (lowerText.contains("service report")) {
                String content = extractContentAfterCommand(spokenText, "service report");
                if (!content.isEmpty()) {
                    serviceReportInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Service Report filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("service report");
                }
            } else if (lowerText.contains("recommendations")) {
                String content = extractContentAfterCommand(spokenText, "recommendations");
                if (!content.isEmpty()) {
                    recommendationsInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Recommendations filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("recommendations");
                }
            } else if (lowerText.contains("follow up 1")) {
                String content = extractContentAfterCommand(spokenText, "follow up 1");
                if (!content.isEmpty()) {
                    followUp1Input.getEditText().setText(content);
                    Toast.makeText(this, "✅ Follow-Up 1 filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("follow up 1");
                }
            } else if (lowerText.contains("role")) {
                String content = extractContentAfterCommand(spokenText, "role");
                if (!content.isEmpty()) {
                    roleInput.getEditText().setText(content);
                    Toast.makeText(this, "✅ Role filled: " + content, Toast.LENGTH_SHORT).show();
                } else {
                    askForFieldContent("role");
                }
            } else if (lowerText.contains("polish form") || lowerText.contains("ai polish")) {
                Toast.makeText(this, "🤖 Starting AI polish...", Toast.LENGTH_SHORT).show();
                showAIFixFieldPicker();
            } else if (lowerText.contains("read back") || lowerText.contains("read form")) {
                Toast.makeText(this, "📖 Reading form back...", Toast.LENGTH_SHORT).show();
                readFormBack();
            } else if (lowerText.contains("auto fill") || lowerText.contains("auto fill all")) {
                startAutoProgression();
            } else {
                Toast.makeText(this, "❌ Command not recognized. Try: 'Enter [field name] [content]', 'Polish form', 'Read back', or 'Auto fill all'", Toast.LENGTH_LONG).show();
            }
        });
    }

    private String extractContentAfterCommand(String spokenText, String... commands) {
        for (String command : commands) {
            int index = spokenText.indexOf(command);
            if (index != -1) {
                String afterCommand = spokenText.substring(index + command.length()).trim();
                afterCommand = afterCommand.replaceAll("^(is|are|was|were|the|a|an)\\s+", "");
                afterCommand = afterCommand.replaceAll("\\s+", " ").trim();
                return afterCommand;
            }
        }
        return "";
    }

    private void askForFieldContent(String fieldName) {
        pendingField = fieldName;
        isInteractiveMode = true;
        
        String question = "What is the " + fieldName + "?";
        textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, "FIELD_QUESTION");
        Toast.makeText(this, "🎙️ " + question, Toast.LENGTH_SHORT).show();
        startVoiceRecognition();
    }

    private void fillFieldWithContent(String fieldName, String content) {
        switch (fieldName.toLowerCase()) {
            case "service type":
                serviceTypeInput.getEditText().setText(content);
                break;
            case "service number":
                serviceNumberInput.getEditText().setText(content);
                break;
            case "premises name":
                premisesNameInput.getEditText().setText(content);
                break;
            case "premises address":
                premisesAddressInput.getEditText().setText(content);
                break;
            case "prep":
                if (prepProductsSection != null) {
                    prepProductsSection.setLegacyPrepText(content);
                }
                break;
            case "service report":
                serviceReportInput.getEditText().setText(content);
                break;
            case "recommendations":
                recommendationsInput.getEditText().setText(content);
                break;
            case "follow up 1":
                followUp1Input.getEditText().setText(content);
                break;
            case "role":
                roleInput.getEditText().setText(content);
                break;
        }
        
        String confirmation = fieldName + " filled with " + content;
        textToSpeech.speak(confirmation, TextToSpeech.QUEUE_FLUSH, null, "FIELD_CONFIRMATION");
        Toast.makeText(this, "✅ " + confirmation, Toast.LENGTH_SHORT).show();
    }

    private void startAutoProgression() {
        currentFieldIndex = 0;
        isInteractiveMode = true;
        Toast.makeText(this, "🔄 Starting auto-fill mode. Will progress through all fields.", Toast.LENGTH_SHORT).show();
        progressToNextField();
    }

    private void progressToNextField() {
        if (currentFieldIndex >= AUTO_PROGRESS_FIELDS.length) {
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
        
        String question = "What is the " + currentField + "?";
        if (textToSpeech != null) {
            textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, "AUTO_QUESTION");
        }
        
        Toast.makeText(this, "🎙️ " + question, Toast.LENGTH_SHORT).show();
        startVoiceRecognition();
    }

    private void startVoiceRecognition() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...");
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000);
            speechRecognizer.startListening(intent);
            
            autoProgressHandler.postDelayed(() -> {
                if (isListening && isInteractiveMode) {
                    progressToNextField();
                }
            }, 15000);
        }
    }

    private void openImageSelector() {
        startActivityForResult(com.grpc.grpc.core.ReportImageStorage.createImagePickerIntent(), 1);
    }
    
    private void captureSignature(String signatureType) {
        showSignatureDialog(signatureType);
    }
    
    private void showSignatureDialog(String signatureType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Draw " + (signatureType.equals("technician") ? "Technician" : "Customer") + " Signature");
        
        // Create the signature view
        SignatureDrawingView signatureView = new SignatureDrawingView(this);
        signatureView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                400)); // Fixed height for the drawing area
        
        // Create dialog layout
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(20, 20, 20, 20);
        dialogLayout.addView(signatureView);
        
        // Add instruction text
        TextView instructionText = new TextView(this);
        instructionText.setText("Draw your signature in the box above");
        instructionText.setGravity(android.view.Gravity.CENTER);
        instructionText.setPadding(0, 10, 0, 0);
        dialogLayout.addView(instructionText);
        
        builder.setView(dialogLayout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            if (!signatureView.isEmpty()) {
                // Capture the signature as bitmap and save to file
                Bitmap signatureBitmap = signatureView.getSignatureBitmap();
                Uri signatureUri = saveSignatureToFile(signatureBitmap, signatureType);
                
                if (signatureUri != null) {
                    if (signatureType.equals("technician")) {
                        technicianSignatureUri = signatureUri;
                        Toast.makeText(this, "Technician signature saved!", Toast.LENGTH_SHORT).show();
                    } else {
                        customerSignatureUri = signatureUri;
                        Toast.makeText(this, "Customer signature saved!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Error saving signature", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please draw a signature first", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Clear", (dialog, which) -> {
            signatureView.clear();
        });
        builder.setNeutralButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    private Uri saveSignatureToFile(Bitmap bitmap, String signatureType) {
        try {
            // Create signatures folder
            File signaturesFolder = new File(getExternalFilesDir(null), "SIGNATURES");
            if (!signaturesFolder.exists()) {
                signaturesFolder.mkdirs();
            }
            
            // Create unique filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = signatureType + "_signature_" + timestamp + ".png";
            File signatureFile = new File(signaturesFolder, filename);
            
            // Save bitmap to file
            FileOutputStream fos = new FileOutputStream(signatureFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            
            // Return the file URI
            return Uri.fromFile(signatureFile);
            
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    // Simple signature drawing view for the dialog
    private static class SignatureDrawingView extends View {
        private Path path;
        private Paint paint;
        private float lastX, lastY;
        private boolean hasSignature = false;
        
        public SignatureDrawingView(Context context) {
            super(context);
            init();
        }
        
        private void init() {
            path = new Path();
            paint = new Paint();
            paint.setColor(ContextCompat.getColor(getContext(), R.color.signature_ink));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(8f);
            paint.setAntiAlias(true);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            
            setBackgroundColor(ContextCompat.getColor(getContext(), R.color.signature_bg));
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(x, y);
                    lastX = x;
                    lastY = y;
                    hasSignature = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
                    lastX = x;
                    lastY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    path.lineTo(x, y);
                    break;
            }
            
            invalidate();
            return true;
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }
        
        public void clear() {
            path.reset();
            hasSignature = false;
            invalidate();
        }
        
        public boolean isEmpty() {
            return !hasSignature;
        }

        public Bitmap getSignatureBitmap() {
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            draw(canvas);
            return bitmap;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PREVIEW_ACTION_FORM) {
            if (resultCode == RESULT_OK && data != null && data.getBooleanExtra(ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
                saveActionForm();
            }
            return;
        }

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            List<Uri> persisted = com.grpc.grpc.core.ReportImageStorage.persistFromPickerResult(this, data);
            if (!persisted.isEmpty()) {
                selectedImageUris.addAll(persisted);
            }
            Toast.makeText(this, selectedImageUris.size() + " images selected!", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveActionForm() {
        lastUploadSucceeded = false;
        String premisesName = premisesNameInput.getEditText().getText().toString();
        if (premisesName.isEmpty()) {
            Toast.makeText(this, "Please enter Premises Name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (passwordProtectCheckbox.isChecked()) {
                requestPasswordCreation();
            } else {
                createUnprotectedPDF();
            }
        } else {
            Toast.makeText(this, "PDF generation requires Android 13 or higher", Toast.LENGTH_SHORT).show();
        }
    }

    /** Legacy prep cell text only when no structured products (lines render separately). */
    private String formatPrepForPdf() {
        if (prepProductsSection == null) {
            return "";
        }
        if (PrepProductsFormatter.hasStructuredProducts(prepProductsSection.getProducts())) {
            return "";
        }
        String legacy = prepProductsSection.getLegacyPrepText();
        return legacy != null ? legacy.trim() : "";
    }

    private List<ProductUsageItem> getPrepProductsForPdf() {
        return prepProductsSection != null ? prepProductsSection.getProducts() : null;
    }

    private void previewActionForm() {
        String premisesName = premisesNameInput.getEditText().getText().toString();
        if (premisesName.isEmpty()) {
            Toast.makeText(this, "Please enter Premises Name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "PDF preview requires Android 13 or higher", Toast.LENGTH_SHORT).show();
            return;
        }

        File previewDir = new File(getCacheDir(), "report_previews");
        if (!previewDir.exists()) {
            previewDir.mkdirs();
        }

        String followUp1 = followUp1Input.getEditText().getText().toString();
        File previewPdf = ActionFormPdfGenerator.generatePDFToDirectory(
                premisesNameInput.getEditText().getText().toString(),
                dateTimeInput.getText().toString(),
                serviceTypeInput.getEditText().getText().toString(),
                serviceNumberInput.getEditText().getText().toString(),
                premisesAddressInput.getEditText().getText().toString(),
                formatPrepForPdf(),
                serviceReportInput.getEditText().getText().toString(),
                recommendationsInput.getEditText().getText().toString(),
                followUp1,
                "",
                "",
                roleInput.getEditText().getText().toString(),
                this,
                !selectedImageUris.isEmpty() ? selectedImageUris : null,
                technicianSignatureUri,
                customerSignatureUri,
                followUpToggle.isChecked(),
                previewDir,
                getPrepProductsForPdf()
        );

        if (previewPdf == null || !previewPdf.exists()) {
            Toast.makeText(this, "Unable to generate preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent previewIntent = new Intent(this, ReportPreviewActivity.class);
        previewIntent.putExtra(ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, previewPdf.getAbsolutePath());
        startActivityForResult(previewIntent, REQUEST_PREVIEW_ACTION_FORM);
    }

    private void createUnprotectedPDF() {
        String followUp1 = followUp1Input.getEditText().getText().toString();
        // Use the date from the form field (not today's date).
        ActionFormPdfGenerator.generatePDFToDirectory(
            premisesNameInput.getEditText().getText().toString(),
            dateTimeInput.getText().toString(),
            serviceTypeInput.getEditText().getText().toString(),
            serviceNumberInput.getEditText().getText().toString(),
            premisesAddressInput.getEditText().getText().toString(),
            formatPrepForPdf(),
            serviceReportInput.getEditText().getText().toString(),
            recommendationsInput.getEditText().getText().toString(),
            followUp1,
            "",
            "",
            roleInput.getEditText().getText().toString(),
            this,
            !selectedImageUris.isEmpty() ? selectedImageUris : null,
            technicianSignatureUri,
            customerSignatureUri,
            followUpToggle.isChecked(),
            resolveReportOutputDirectory(),
            getPrepProductsForPdf()
        );
        if (contractReportCheckbox != null
                && contractReportCheckbox.isChecked()
                && ContractReportSync.hasContractId(contractId)) {
            uploadActionFormToFirebase(
                    ContractReportSync.buildContractStorageFolder(contractId),
                    () -> showSuccessDialog()
            );
        } else if (hasRoutingSelection()) {
            String autoFolder = resolveAutoRoutingFolderPath();
            if (autoFolder != null && !autoFolder.trim().isEmpty()) {
                uploadActionFormToFirebase(autoFolder, this::showSuccessDialog);
            } else {
                String fallbackFolder = (jobReportCheckbox != null && jobReportCheckbox.isChecked()) ? "JobWorkReports"
                        : (managementReportCheckbox != null && managementReportCheckbox.isChecked()) ? managementRootStorageFolder : "";
                if (!fallbackFolder.isEmpty()) {
                    uploadActionFormToFirebase(fallbackFolder, this::showSuccessDialog);
                } else {
                    showShareOrMoveDialog();
                }
            }
        } else if (shouldAutoUploadContractReport()) {
            uploadActionFormToFirebase(
                    ContractReportSync.buildContractStorageFolder(contractId),
                    () -> showSuccessDialog()
            );
        } else {
            showSuccessDialog();
        }
        deleteSignatureImages();
        clearInputFields();
    }

    private void requestPasswordCreation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Create Password for Action Form")
                .setMessage("Please create a password for your Action Form PDF.\n\n" +
                        "This password will be required to edit the PDF.");
        View passwordInputView = createPasswordInputView();
        builder.setView(passwordInputView);
        builder.setPositiveButton("Create PDF", (dialog, which) -> {
            LinearLayout layout = (LinearLayout) passwordInputView;
            EditText passwordInput = (EditText) layout.getChildAt(0);
            EditText confirmPasswordInput = (EditText) layout.getChildAt(1);
            String password = passwordInput.getText().toString();
            String confirmPassword = confirmPasswordInput.getText().toString();
            if (password.trim().isEmpty()) {
                Toast.makeText(this, "Please enter a password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            createPasswordProtectedPDF(password);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private View createPasswordInputView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter password (min 6 characters)");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(20, 20, 20, 20);
        EditText confirmPasswordInput = new EditText(this);
        confirmPasswordInput.setHint("Confirm password");
        confirmPasswordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPasswordInput.setPadding(20, 20, 20, 20);
        layout.addView(passwordInput);
        layout.addView(confirmPasswordInput);
        return layout;
    }

    private void createPasswordProtectedPDF(String password) {
        String followUp1 = followUp1Input.getEditText().getText().toString();
        // Use the date from the form field (not today's date).
        ActionFormPdfGenerator.generatePasswordProtectedPDFToDirectory(
            premisesNameInput.getEditText().getText().toString(),
            dateTimeInput.getText().toString(),
            serviceTypeInput.getEditText().getText().toString(),
            serviceNumberInput.getEditText().getText().toString(),
            premisesAddressInput.getEditText().getText().toString(),
            formatPrepForPdf(),
            serviceReportInput.getEditText().getText().toString(),
            recommendationsInput.getEditText().getText().toString(),
            followUp1,
            "",
            "",
            roleInput.getEditText().getText().toString(),
            password,
            this,
            !selectedImageUris.isEmpty() ? selectedImageUris : null,
            technicianSignatureUri,
            customerSignatureUri,
            followUpToggle.isChecked(),
            resolveReportOutputDirectory(),
            getPrepProductsForPdf()
        );
        if (contractReportCheckbox != null
                && contractReportCheckbox.isChecked()
                && ContractReportSync.hasContractId(contractId)) {
            uploadActionFormToFirebase(
                    ContractReportSync.buildContractStorageFolder(contractId),
                    () -> showSuccessDialog(password)
            );
        } else if (hasRoutingSelection()) {
            String autoFolder = resolveAutoRoutingFolderPath();
            if (autoFolder != null && !autoFolder.trim().isEmpty()) {
                uploadActionFormToFirebase(autoFolder, () -> showSuccessDialog(password));
            } else {
                String fallbackFolder = (jobReportCheckbox != null && jobReportCheckbox.isChecked()) ? "JobWorkReports"
                        : (managementReportCheckbox != null && managementReportCheckbox.isChecked()) ? managementRootStorageFolder : "";
                if (!fallbackFolder.isEmpty()) {
                    uploadActionFormToFirebase(fallbackFolder, () -> showSuccessDialog(password));
                } else {
                    showShareOrMoveDialog();
                }
            }
        } else if (shouldAutoUploadContractReport()) {
            uploadActionFormToFirebase(
                    ContractReportSync.buildContractStorageFolder(contractId),
                    () -> showSuccessDialog(password)
            );
        } else {
            showSuccessDialog(password);
        }
        deleteSignatureImages();
        clearInputFields();
    }

    private void showSuccessDialog() {
        boolean showUpload = FirebaseAuth.getInstance().getCurrentUser() != null
                && !"Offline User".equals(userName)
                && !shouldAutoUploadContractReport()
                && !lastUploadSucceeded;
        if (!hasRoutingSelection()) {
            showNoRoutingOptionsDialog(
                    "Action Form PDF Created Successfully!",
                    "Your Action Form PDF has been created.",
                    showUpload
            );
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Action Form PDF Created Successfully!")
                .setMessage("Your Action Form PDF has been created.")
                .setPositiveButton("View PDF", (dialog, which) -> viewLatestActionForm())
                .setNegativeButton("Share PDF", (dialog, which) -> shareActionForm())
                .setCancelable(false);
        if (showUpload) {
            builder.setNeutralButton("Upload to Firebase", (dialog, which) -> showFirebaseFolderSelectionPopup());
        } else {
            builder.setNeutralButton("OK", (dialog, which) -> dialog.dismiss());
        }
        builder.create().show();
    }

    private void showNoRoutingOptionsDialog(String title, String message, boolean showUpload) {
        List<String> actions = new ArrayList<>();
        actions.add("View PDF");
        actions.add("Share PDF");
        if (showUpload) {
            actions.add("Upload to Firebase");
        }
        String[] actionArray = actions.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setItems(actionArray, (dialog, which) -> {
                    String selected = actionArray[which];
                    if ("View PDF".equals(selected)) {
                        viewLatestActionForm();
                    } else if ("Share PDF".equals(selected)) {
                        shareActionForm();
                    } else {
                        showFirebaseFolderSelectionPopup();
                    }
                })
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void showShareOrMoveDialog() {
        boolean showUpload = FirebaseAuth.getInstance().getCurrentUser() != null
                && !"Offline User".equals(userName)
                && !shouldAutoUploadContractReport()
                && !lastUploadSucceeded;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Action Form PDF Created Successfully!")
                .setMessage("What would you like to do with your report?")
                .setPositiveButton("View PDF", (dialog, which) -> viewLatestActionForm())
                .setNegativeButton("Share PDF", (dialog, which) -> shareActionForm())
                .setCancelable(true);
        if (showUpload) {
            builder.setNeutralButton("Upload to Firebase", (dialog, which) -> showFirebaseFolderSelectionPopup());
        } else {
            builder.setNeutralButton("OK", (dialog, which) -> dialog.dismiss());
        }
        builder.create().show();
    }

    private void showSuccessDialog(String password) {
        boolean showUpload = FirebaseAuth.getInstance().getCurrentUser() != null
                && !"Offline User".equals(userName)
                && !shouldAutoUploadContractReport()
                && !lastUploadSucceeded;
        if (!hasRoutingSelection()) {
            showNoRoutingOptionsDialog(
                    "🔒 Action Form PDF Created Successfully!",
                    "Your Action Form PDF has been created with password protection.\n\n" +
                            "📄 Viewing: Anyone can view the PDF\n" +
                            "✏️ Editing: Requires password\n\n" +
                            "🔑 Owner Password: " + password + "\n\n" +
                            "⚠️ Please save this password securely!",
                    showUpload
            );
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Action Form PDF Created Successfully!")
                .setMessage("Your Action Form PDF has been created with password protection.\n\n" +
                        "📄 Viewing: Anyone can view the PDF\n" +
                        "✏️ Editing: Requires password\n\n" +
                        "🔑 Owner Password: " + password + "\n\n" +
                        "⚠️ Please save this password securely!")
                .setPositiveButton("View PDF", (dialog, which) -> viewLatestActionForm())
                .setNegativeButton("Share PDF", (dialog, which) -> shareActionForm())
                .setCancelable(false);
        if (showUpload) {
            builder.setNeutralButton("Upload to Firebase", (dialog, which) -> showFirebaseFolderSelectionPopup());
        } else {
            builder.setNeutralButton("OK", (dialog, which) -> dialog.dismiss());
        }
        builder.create().show();
    }

    private boolean shouldAutoUploadContractReport() {
        return ContractStorageUploader.shouldAutoUpload(contractId);
    }

    private String resolveAutoRoutingFolderPath() {
        if (jobReportCheckbox != null && jobReportCheckbox.isChecked()) {
            String selected = jobFolderSpinner != null && jobFolderSpinner.getSelectedItem() != null
                    ? jobFolderSpinner.getSelectedItem().toString().trim() : "";
            String base = (!selected.isEmpty() && !"JobWorkReports".equalsIgnoreCase(selected))
                    ? "JobWorkReports/" + selected
                    : "JobWorkReports";
            String sub = jobRouteSubFolderSpinner != null
                    && jobRouteSubFolderSpinner.getVisibility() == View.VISIBLE
                    && jobRouteSubFolderSpinner.getSelectedItem() != null
                    ? jobRouteSubFolderSpinner.getSelectedItem().toString().trim() : "";
            String sub2 = jobRouteSubFolderSpinnerLevel3 != null
                    && jobRouteSubFolderSpinnerLevel3.getVisibility() == View.VISIBLE
                    && jobRouteSubFolderSpinnerLevel3.getSelectedItem() != null
                    ? jobRouteSubFolderSpinnerLevel3.getSelectedItem().toString().trim() : "";
            if (!sub2.isEmpty()) return base + "/" + sub + "/" + sub2;
            if (!sub.isEmpty()) return base + "/" + sub;
            return base;
        }
        if (managementReportCheckbox != null && managementReportCheckbox.isChecked()) {
            String selected = managementFolderSpinner != null && managementFolderSpinner.getSelectedItem() != null
                    ? managementFolderSpinner.getSelectedItem().toString().trim() : "";
            String base = (!selected.isEmpty() && !managementRootStorageFolder.equalsIgnoreCase(selected))
                    ? managementRootStorageFolder + "/" + selected
                    : managementRootStorageFolder;
            String sub = routeSubFolderSpinner != null
                    && routeSubFolderSpinner.getVisibility() == View.VISIBLE
                    && routeSubFolderSpinner.getSelectedItem() != null
                    ? routeSubFolderSpinner.getSelectedItem().toString().trim() : "";
            String sub2 = routeSubFolderSpinnerLevel3 != null
                    && routeSubFolderSpinnerLevel3.getVisibility() == View.VISIBLE
                    && routeSubFolderSpinnerLevel3.getSelectedItem() != null
                    ? routeSubFolderSpinnerLevel3.getSelectedItem().toString().trim() : "";
            if (!sub2.isEmpty()) return base + "/" + sub + "/" + sub2;
            if (!sub.isEmpty()) return base + "/" + sub;
            return base;
        }
        return null;
    }

    private void shareActionForm() {
        try {
            File reportsFolder = resolveReportOutputDirectory();
            if (!reportsFolder.exists()) {
                Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
                return;
            }

            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files == null || files.length == 0) {
                Toast.makeText(this, "No PDF report found to share!", Toast.LENGTH_SHORT).show();
                return;
            }

            File latestFile = files[files.length - 1];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }

            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    latestFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Action Form"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share the report.", Toast.LENGTH_SHORT).show();
        }
    }

    private void viewLatestActionForm() {
        try {
            File reportsFolder = resolveReportOutputDirectory();
            if (!reportsFolder.exists()) {
                Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
                return;
            }

            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files == null || files.length == 0) {
                Toast.makeText(this, "No PDF report found.", Toast.LENGTH_SHORT).show();
                return;
            }

            File latestFile = files[files.length - 1];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }

            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    latestFile
            );
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(viewIntent, "View Action Form"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to view the report.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFirebaseFolderSelectionPopup() {
        if (ContractReportSync.hasContractId(contractId)) {
            uploadActionFormToFirebase(ContractReportSync.buildContractStorageFolder(contractId));
            return;
        }
        if (jobReportCheckbox != null && jobReportCheckbox.isChecked()) {
            showSubFolderSelectionDialog("JobWorkReports");
            return;
        }
        if (managementReportCheckbox != null && managementReportCheckbox.isChecked()) {
            String autoFolder = resolveAutoRoutingFolderPath();
            uploadActionFormToFirebase(autoFolder != null && !autoFolder.trim().isEmpty() ? autoFolder : managementRootStorageFolder);
            return;
        }
        StorageFolderHelper.discoverUploadParentFolders(folderList -> runOnUiThread(() -> {
            if (folderList.isEmpty()) {
                Toast.makeText(this, "No available folders to select.", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] foldersArray = folderList.toArray(new String[0]);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select a storage folder");
            builder.setItems(foldersArray, (dialog, which) -> {
                String selectedFolder = foldersArray[which];
                showSubFolderSelectionDialog(selectedFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
            builder.show();
        }));
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
                uploadActionFormToFirebase(parentFolder);
                return;
            }

            // Show subfolder selection dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select a Subfolder");
            builder.setItems(subFolderList.toArray(new String[0]), (dialog, which) -> {
                String selectedSubFolder = subFolderList.get(which);
                showSubFolderSelectionDialog(parentFolder + "/" + selectedSubFolder);
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
            });
            builder.show();

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to load subfolders: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void uploadActionFormToFirebase(String folderPath) {
        uploadActionFormToFirebase(folderPath, null);
    }

    private void uploadActionFormToFirebase(String folderPath, Runnable afterUpload) {
        File reportsFolder = resolveReportOutputDirectory();
        if (!reportsFolder.exists()) {
            Toast.makeText(this, "Report folder not found!", Toast.LENGTH_SHORT).show();
            if (afterUpload != null) afterUpload.run();
            return;
        }

        File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No PDF report found to upload!", Toast.LENGTH_SHORT).show();
            if (afterUpload != null) afterUpload.run();
            return;
        }

        File latestFile = files[files.length - 1];
        for (File file : files) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }
        if (!com.grpc.grpc.core.ReportImageStorage.validatePdfFile(this, latestFile)) {
            if (afterUpload != null) afterUpload.run();
            return;
        }

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageReference = storage.getReference();
        final File latestFileFinal = latestFile;
        final String targetFolderPath = ContractStoragePathHelper.resolveContractYearFolderPath(
                folderPath,
                latestFileFinal,
                dateTimeInput != null ? dateTimeInput.getText().toString() : null);
        final Runnable afterUploadFinal = afterUpload;
        ContractStoragePathHelper.RunnableCallback doUpload = () -> runOnUiThread(() -> uploadActionFormToFirebaseStorage(
                storageReference, latestFileFinal, targetFolderPath, afterUploadFinal));
        if (ContractReportSync.hasContractId(contractId)
                && targetFolderPath != null
                && targetFolderPath.toLowerCase(Locale.ROOT).startsWith("contracts/")) {
            String yearSegment = ContractStoragePathHelper.yearFromContractFolderPath(targetFolderPath);
            if (yearSegment == null) {
                yearSegment = ContractStoragePathHelper.extractYearFromFileName(latestFileFinal.getName());
            }
            final String yearForFolder = yearSegment != null ? yearSegment : "";
            ContractStoragePathHelper.ensureYearFolderExists(
                    ContractReportSync.buildContractStorageFolder(contractId),
                    yearForFolder,
                    doUpload,
                    e -> doUpload.run()
            );
        } else {
            doUpload.run();
        }
    }

    private void uploadActionFormToFirebaseStorage(
            StorageReference storageReference,
            File latestFileFinal,
            String targetFolderPath,
            Runnable afterUpload
    ) {
        String uploadedFileName = latestFileFinal.getName();
        StorageReference folderRef = storageReference.child(targetFolderPath);
        folderRef.listAll().addOnSuccessListener(listResult -> {
            java.util.Set<String> existingNames = new java.util.HashSet<>();
            for (StorageReference item : listResult.getItems()) {
                if (item != null && item.getName() != null) existingNames.add(item.getName());
            }
            String uniqueNameCandidate = uploadedFileName;
            if (existingNames.contains(uniqueNameCandidate)) {
                int dot = uploadedFileName.lastIndexOf('.');
                String base = dot > 0 ? uploadedFileName.substring(0, dot) : uploadedFileName;
                String ext = dot > 0 ? uploadedFileName.substring(dot) : "";
                uniqueNameCandidate = base + "_" + System.currentTimeMillis() + ext;
            }
            final String uniqueName = uniqueNameCandidate;
            String storagePath = targetFolderPath + "/" + uniqueName;
            StorageReference fileRef = storageReference.child(storagePath);
            UploadTask uploadTask = fileRef.putFile(Uri.fromFile(latestFileFinal));
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                lastUploadSucceeded = true;
                com.grpc.grpc.core.StorageMetricsHelper.recordUpload();
                ContractReportSync.syncMetadata(
                        contractId,
                        storagePath,
                        uniqueName,
                        "action_form",
                        contractCompanyName,
                        () -> {
                            Toast.makeText(this, "Action Form uploaded successfully to " + targetFolderPath, Toast.LENGTH_SHORT).show();
                            if (afterUpload != null) afterUpload.run();
                        },
                        error -> {
                            Toast.makeText(this, "Action Form uploaded but contract link failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                            if (afterUpload != null) afterUpload.run();
                        }
                );
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                if (afterUpload != null) afterUpload.run();
            });
        }).addOnFailureListener(e -> {
            String storagePath = targetFolderPath + "/" + uploadedFileName;
            StorageReference fileRef = storageReference.child(storagePath);
            fileRef.putFile(Uri.fromFile(latestFileFinal))
                    .addOnSuccessListener(taskSnapshot -> {
                        lastUploadSucceeded = true;
                        com.grpc.grpc.core.StorageMetricsHelper.recordUpload();
                        ContractReportSync.syncMetadata(
                                contractId, storagePath, uploadedFileName, "action_form", contractCompanyName,
                                () -> { if (afterUpload != null) afterUpload.run(); },
                                error -> { if (afterUpload != null) afterUpload.run(); }
                        );
                    })
                    .addOnFailureListener(err -> {
                        Toast.makeText(this, "Upload failed: " + err.getMessage(), Toast.LENGTH_SHORT).show();
                        if (afterUpload != null) afterUpload.run();
                    });
        });
    }

    private void clearInputFields() {
        serviceTypeInput.getEditText().setText("");
        serviceNumberInput.getEditText().setText("");
        premisesNameInput.getEditText().setText("");
        premisesAddressInput.getEditText().setText("");
        if (prepProductsSection != null) {
            prepProductsSection.clear();
        }
        serviceReportInput.getEditText().setText("");
        recommendationsInput.getEditText().setText("");
        followUp1Input.getEditText().setText("");
        roleInput.getEditText().setText("");
        selectedImageUris.clear();
        technicianSignatureUri = null;
        customerSignatureUri = null;
        followUpToggle.setChecked(false);
        followUpContainer.setVisibility(android.view.View.GONE);
        setCurrentDateTime();
    }

    /**
     * Show dialog for logged-in user to choose which fields to update with AI Fix (Service Report, Recommendations).
     */
    private void showAIFixFieldPicker() {
        String serviceReport = serviceReportInput.getEditText().getText().toString().trim();
        String recommendations = recommendationsInput.getEditText().getText().toString().trim();
        if (serviceReport.isEmpty() && recommendations.isEmpty()) {
            Toast.makeText(this, "Enter content in Service Report or Recommendations first", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        final CheckBox cbService = new CheckBox(this);
        cbService.setText("Service Report");
        cbService.setChecked(!serviceReport.isEmpty());
        final CheckBox cbRec = new CheckBox(this);
        cbRec.setText("Recommendations");
        cbRec.setChecked(!recommendations.isEmpty());
        layout.addView(cbService);
        layout.addView(cbRec);
        new AlertDialog.Builder(this)
                .setTitle("Which fields to update?")
                .setView(layout)
                .setPositiveButton("Fix selected", (dialog, which) -> {
                    boolean fixService = cbService.isChecked();
                    boolean fixRec = cbRec.isChecked();
                    if (!fixService && !fixRec) {
                        Toast.makeText(this, "Select at least one field.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    polishWithAIFields(fixService, fixRec);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * AI Fix: only selected fields (Service Report and/or Recommendations). Professional, grammatically correct, no * or filler. Uses key from Firestore (same as Chat).
     */
    private void polishWithAIFields(boolean fixServiceReport, boolean fixRecommendations) {
        String serviceReport = serviceReportInput.getEditText().getText().toString().trim();
        String recommendations = recommendationsInput.getEditText().getText().toString().trim();
        Toast.makeText(this, "✏️ AI Fix in progress...", Toast.LENGTH_SHORT).show();

        String systemPrompt = "You are a professional editor for pest control service reports. Rewrite the given text so it is professional, grammatically correct, and suitable for a formal report. Do not use asterisks (*), bullet symbols, or filler words like 'emm' or 'uh'. Add 3 to 4 extra sentences where appropriate to improve clarity and completeness. Output only the revised text.";
        String userPrompt = "Rewrite the following in the exact format below. Plain text only, no markdown.\n\n";
        if (fixServiceReport) {
            userPrompt += "Service Report: " + (!serviceReport.isEmpty() ? serviceReport : "(none)") + "\n\n";
        } else {
            userPrompt += "Service Report: (skip - do not include in response)\n\n";
        }
        if (fixRecommendations) {
            userPrompt += "Recommendations: " + (!recommendations.isEmpty() ? recommendations : "(none)") + "\n\n";
        } else {
            userPrompt += "Recommendations: (skip - do not include in response)\n\n";
        }
        userPrompt += "Respond with exactly:\nService Report: [revised text or leave empty if skipped]\n\nRecommendations: [revised text or leave empty if skipped]";

        final String finalSystemPrompt = systemPrompt;
        final String finalUserPrompt = userPrompt;
        final boolean applyService = fixServiceReport;
        final boolean applyRec = fixRecommendations;

        FirebaseFirestore.getInstance().document("AI-Chat/AI-API").get()
                .addOnSuccessListener(this, docSnap -> {
                    if (docSnap == null || !docSnap.exists()) {
                        setAiFixDone("Admin must set an API key in Firestore (AI-Chat/AI-API document).");
                        return;
                    }
                    Object grogObj = docSnap.get("key-grog");
                    Object hfObj = docSnap.get("KEY");
                    String keyGrog = grogObj != null ? grogObj.toString().trim() : "";
                    String keyHf = hfObj != null ? hfObj.toString().trim() : "";
                    String apiKey = !keyGrog.isEmpty() ? keyGrog : keyHf;
                    if (apiKey.isEmpty()) {
                        setAiFixDone("Admin must set an API key in Firestore (AI-Chat/AI-API document).");
                        return;
                    }
                    boolean useGroq = !keyGrog.isEmpty();
                    new Thread(() -> {
                        try {
                            String response = callChatAPI(apiKey, useGroq, finalSystemPrompt, finalUserPrompt);
                            runOnUiThread(() -> updateUIWithAIPolish(response, applyService, applyRec));
                        } catch (Exception e) {
                            runOnUiThread(() -> setAiFixDone("AI Fix failed: " + e.getMessage()));
                        }
                    }).start();
                })
                .addOnFailureListener(this, e -> setAiFixDone("Could not load API key. Check connection."));
    }

    private void setAiFixDone(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String callChatAPI(String apiKey, boolean useGroq, String systemPrompt, String userPrompt) throws IOException {
        try {
            OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build();
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            String url = useGroq ? GROQ_CHAT_URL : HF_ROUTER_CHAT_URL;
            String model = useGroq ? GROQ_MODEL : HF_ROUTER_MODEL;
            JSONObject body = new JSONObject().put("model", model).put("messages", messages).put("max_tokens", AI_FIX_MAX_TOKENS);
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(okhttp3.RequestBody.create(body.toString(), okhttp3.MediaType.parse("application/json")))
                    .build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) throw new IOException("API " + response.code() + ": " + responseBody);
                JSONObject json = new JSONObject(responseBody);
                JSONArray choices = json.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    Object content = choices.getJSONObject(0).optJSONObject("message").opt("content");
                    return content != null ? content.toString() : "";
                }
                throw new IOException("No response content");
            }
        } catch (org.json.JSONException e) {
            throw new IOException("JSON error: " + e.getMessage(), e);
        }
    }

    private void updateUIWithAIPolish(String aiResponse, boolean applyServiceReport, boolean applyRecommendations) {
        // Store the AI response for read-back functionality
        lastAIResponse = aiResponse;
        
        runOnUiThread(() -> {
            try {
                String serviceReport = "";
                String recommendations = "";
                if (aiResponse.contains("Service Report:")) {
                    int startIndex = aiResponse.indexOf("Service Report:") + "Service Report:".length();
                    int endIndex = aiResponse.indexOf("Recommendations:");
                    if (endIndex == -1) endIndex = aiResponse.length();
                    serviceReport = aiResponse.substring(startIndex, endIndex).trim().replaceAll("\\*\\*", "").replaceAll("\"", "").trim();
                }
                if (aiResponse.contains("Recommendations:")) {
                    int startIndex = aiResponse.indexOf("Recommendations:") + "Recommendations:".length();
                    recommendations = aiResponse.substring(startIndex, aiResponse.length()).trim().replaceAll("\\*\\*", "").replaceAll("\"", "").trim();
                }
                if (applyServiceReport && !serviceReport.isEmpty()) {
                    serviceReportInput.getEditText().setText(serviceReport);
                }
                if (applyRecommendations && !recommendations.isEmpty()) {
                    recommendationsInput.getEditText().setText(recommendations);
                }
                Toast.makeText(this, "✅ AI Fix completed! Tap AI Fix to hear response.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error parsing AI response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
            }
        });
    }

    /**
     * Reads back the last AI response using Text-to-Speech
     */
    private void readAIResponseBack() {
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    readAIResponseContent();
                } else {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        readAIResponseContent();
    }
    
    /**
     * Reads the AI response content using Text-to-Speech
     */
    private void readAIResponseContent() {
        if (lastAIResponse.isEmpty()) {
            Toast.makeText(this, "No AI response to read back", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Clean up the AI response for better speech
        String cleanResponse = lastAIResponse
                .replaceAll("\\*\\*", "")
                .replaceAll("\"", "")
                .replaceAll("Service Report:", "Service Report section:")
                .replaceAll("Recommendations:", "Recommendations section:")
                .trim();
        
        textToSpeech.speak(cleanResponse, TextToSpeech.QUEUE_FLUSH, null, "AI_RESPONSE_READBACK");
        Toast.makeText(this, "🔊 Reading AI response...", Toast.LENGTH_SHORT).show();
        
        // Reset the AI response after reading it back
        lastAIResponse = "";
    }

    private void readFormBack() {
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    readFormContent();
                } else {
                    Toast.makeText(this, "Text-to-Speech not available", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        readFormContent();
    }
    
    private void readFormContent() {
        StringBuilder formText = new StringBuilder();
        formText.append("Date and Time: ").append(dateTimeInput.getText().toString()).append(". ");
        formText.append("Service Type: ").append(serviceTypeInput.getEditText().getText().toString()).append(". ");
        formText.append("Service Number: ").append(serviceNumberInput.getEditText().getText().toString()).append(". ");
        formText.append("Premises Name: ").append(premisesNameInput.getEditText().getText().toString()).append(". ");
        formText.append("Premises Address: ").append(premisesAddressInput.getEditText().getText().toString()).append(". ");
        formText.append("Prep: ").append(formatPrepForPdf()).append(". ");
        formText.append("Service Report: ").append(serviceReportInput.getEditText().getText().toString()).append(". ");
        formText.append("Recommendations: ").append(recommendationsInput.getEditText().getText().toString()).append(". ");
        
        // Only include follow-up if toggle is on
        if (followUpToggle.isChecked()) {
            formText.append("Follow-Up Details: ").append(followUp1Input.getEditText().getText().toString()).append(". ");
        }
        
        formText.append("Role: ").append(roleInput.getEditText().getText().toString()).append(". ");
        
        textToSpeech.speak(formText.toString(), TextToSpeech.QUEUE_FLUSH, null, "FORM_READBACK");
        Toast.makeText(this, "📖 Reading Action Form back...", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // AI Fix: temporarily hidden for all users (backend logic retained)
    }

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

    private void deleteSignatureImages() {
        try {
            // Delete technician signature if exists
            if (technicianSignatureUri != null) {
                File techSigFile = new File(technicianSignatureUri.getPath());
                if (techSigFile.exists()) {
                    boolean deleted = techSigFile.delete();
                    if (deleted) {
                        Log.d("ActionForm", "Technician signature deleted successfully");
                    }
                }
                technicianSignatureUri = null;
            }
            
            // Delete customer signature if exists
            if (customerSignatureUri != null) {
                File customerSigFile = new File(customerSignatureUri.getPath());
                if (customerSigFile.exists()) {
                    boolean deleted = customerSigFile.delete();
                    if (deleted) {
                        Log.d("ActionForm", "Customer signature deleted successfully");
                    }
                }
                customerSignatureUri = null;
            }
            
            // Clean up any remaining signature files in the SIGNATURES folder
            File signaturesFolder = new File(getExternalFilesDir(null), "SIGNATURES");
            if (signaturesFolder.exists() && signaturesFolder.isDirectory()) {
                File[] signatureFiles = signaturesFolder.listFiles();
                if (signatureFiles != null) {
                    for (File file : signatureFiles) {
                        if (file.getName().endsWith(".png")) {
                            boolean deleted = file.delete();
                            if (deleted) {
                                Log.d("ActionForm", "Cleaned up signature file: " + file.getName());
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("ActionForm", "Error deleting signature images", e);
        }
    }
} 