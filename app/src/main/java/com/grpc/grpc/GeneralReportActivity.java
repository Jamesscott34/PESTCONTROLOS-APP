package com.grpc.grpc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeneralReportActivity extends AppCompatActivity {

    private EditText nameInput, addressInput, dateInput;
    private Spinner visitTypeSpinner;
    private Button saveButton, selectImageButton;
    private java.util.List<Uri> selectedImageUris = new java.util.ArrayList<>();

    private String userName;
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
        saveButton = findViewById(R.id.saveButton);
        selectImageButton = findViewById(R.id.selectImageButton);

        // Auto-fill date only
        dateInput.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date()));

        // Auto-fill name and address if provided
        String companyName = getIntent().getStringExtra("COMPANY_NAME");
        String address = getIntent().getStringExtra("ADDRESS");
        
        if (companyName != null && !companyName.isEmpty()) {
            nameInput.setText(companyName);
        }
        
        if (address != null && !address.isEmpty()) {
            addressInput.setText(address);
        }

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
        intent.putExtra("COMPANY_NAME", companyName);
        intent.putExtra("ADDRESS", address);
        intent.putExtra("ROUTINE_TYPE", visitType);
        intent.putExtra("REPORT_DATE", date); // Pass the date to target activities

        // Pass selected images if any
        if (!selectedImageUris.isEmpty()) {
            intent.putParcelableArrayListExtra("SELECTED_IMAGES", new java.util.ArrayList<>(selectedImageUris));
        }

        startActivity(intent);
        finish();
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
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                // Swipe right - open ReportActivity
                                Log.d("GeneralReportActivity", "Swipe RIGHT detected - opening ReportActivity with user: " + userName);
                                Intent intent = new Intent(GeneralReportActivity.this, ReportActivity.class);
                                intent.putExtra("USER_NAME", userName);
                                startActivity(intent);
                                finish(); // Destroy this activity
                                return true;
                            } else {
                                // Swipe left - open WorkViewActivity (previous in sequence)
                                Log.d("GeneralReportActivity", "Swipe LEFT detected - opening WorkViewActivity with user: " + userName);
                                Intent intent = new Intent(GeneralReportActivity.this, WorkViewActivity.class);
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Select Images"), 1001);
    }

    /**
     * Handles the result from image selection
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // Multiple images selected
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    selectedImageUris.add(imageUri);
                }
            } else if (data.getData() != null) {
                // Single image selected
                Uri imageUri = data.getData();
                selectedImageUris.add(imageUri);
            }
            
            Toast.makeText(this, "Selected " + selectedImageUris.size() + " image(s)", Toast.LENGTH_SHORT).show();
        }
    }
}
