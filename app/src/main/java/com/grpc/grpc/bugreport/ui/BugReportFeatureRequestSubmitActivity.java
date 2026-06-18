package com.grpc.grpc.bugreport.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin-only: submit a bug report or feature request. Stored in Firestore
 * for super_admin to view and set cost / days to complete.
 */
public class BugReportFeatureRequestSubmitActivity extends AppCompatActivity {

    public static final String EXTRA_USER_NAME = "USER_NAME";
    public static final String EXTRA_DEFAULT_TYPE = "DEFAULT_TYPE";

    private Spinner typeSpinner;
    private EditText titleEdit;
    private EditText descriptionEdit;
    private String userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bug_report_feature_request_submit);

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.canBugReport) {
                Toast.makeText(this, "Access denied. Bug report requires bugReport permission.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            initForm();
        }));
    }

    private void initForm() {
        userName = getIntent().getStringExtra(EXTRA_USER_NAME);
        if (userName == null || userName.trim().isEmpty()) {
            userName = SessionManager.getName(this);
            if (userName == null) userName = "Admin";
        }

        typeSpinner = findViewById(R.id.typeSpinner);
        titleEdit = findViewById(R.id.titleEdit);
        descriptionEdit = findViewById(R.id.descriptionEdit);
        Button submitButton = findViewById(R.id.submitButton);
        Button backButton = findViewById(R.id.backButton);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(this,
                R.array.bug_report_feature_request_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        String defaultType = getIntent().getStringExtra(EXTRA_DEFAULT_TYPE);
        if ("feature".equalsIgnoreCase(defaultType)) {
            typeSpinner.setSelection(1);
        } else if ("bug".equalsIgnoreCase(defaultType)) {
            typeSpinner.setSelection(0);
        }

        submitButton.setOnClickListener(v -> submit());
        backButton.setOnClickListener(v -> finish());
    }

    private void submit() {
        String title = titleEdit.getText() != null ? titleEdit.getText().toString().trim() : "";
        String description = descriptionEdit.getText() != null ? descriptionEdit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Please enter a title.", Toast.LENGTH_SHORT).show();
            return;
        }

        int pos = typeSpinner.getSelectedItemPosition();
        String type = (pos == 0) ? "bug" : "feature";

        String submittedByUid = SessionManager.getStaffId(this);
        if (submittedByUid == null) submittedByUid = "";

        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("title", title);
        data.put("description", description);
        data.put("submittedBy", userName);
        data.put("submittedByUid", submittedByUid);
        data.put("submittedAt", Timestamp.now());
        data.put("status", "open");

        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST)
                .add(data)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, "Submitted. Super admin can view and set cost/days.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + (e != null ? e.getMessage() : "unknown"), Toast.LENGTH_LONG).show());
    }
}
