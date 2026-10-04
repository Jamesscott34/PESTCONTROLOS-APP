package com.grpc.grpc.jobs.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.jobs.data.ManagementCompanyRepository;
import com.grpc.grpc.jobs.data.ManagementJobRepository;
import com.grpc.grpc.messaging.NotificationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates one management job for an existing management company.
 * The job reference is allocated in a transaction. The user does not type the number.
 */
public class AddManagmentJobsActivity extends AppCompatActivity {

    private TextView companyLabel;
    private TextView assignedTechLabel;
    private Spinner techNameSpinner;
    private EditText addressInput;
    private EditText customerContact;
    private EditText issueDetails;
    private Button submitButton;
    private String userName = "";
    private String companyId = "";
    private boolean isAdminUser;
    private boolean submitting;
    private String currentTechKey = "";
    private String currentTechDisplay = "";
    private String currentTechUid = "";
    private final List<StaffDirectory.OwnerOption> techOptions = new ArrayList<>();
    private final ManagementJobRepository jobs = new ManagementJobRepository();
    private final ManagementCompanyRepository companies = new ManagementCompanyRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_managment_jobs);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        companyId = getIntent().getStringExtra("COMPANY_ID");
        if (userName == null || userName.trim().isEmpty() || companyId == null || companyId.trim().isEmpty()) {
            Toast.makeText(this, "Open this screen from a management company.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        companyLabel = findViewById(R.id.companyLabel);
        assignedTechLabel = findViewById(R.id.assignedTechLabel);
        techNameSpinner = findViewById(R.id.techNameSpinner);
        addressInput = findViewById(R.id.addressInput);
        customerContact = findViewById(R.id.customerContact);
        issueDetails = findViewById(R.id.issueDetails);
        submitButton = findViewById(R.id.submitButton);
        submitButton.setText("Create job");

        SessionManager.ensureLoaded(this, session -> runOnUiThread(this::bindTechnician));
        companies.getCompany(companyId, (company, error) -> runOnUiThread(() -> {
            if (company == null) {
                Toast.makeText(this, "Management company not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (!company.active) {
                Toast.makeText(this, "This management company is inactive.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            companyLabel.setText(company.name + "\nJob reference assigned automatically (" + company.jobPrefix + ")");
        }));
        submitButton.setOnClickListener(v -> validateAndSubmitJob());
    }

    private void bindTechnician() {
        isAdminUser = SessionManager.isAdmin(this);
        String key = SessionManager.getContractKey(this);
        currentTechKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        currentTechDisplay = StaffDirectory.capitalizeContractKey(currentTechKey);
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                currentTechUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception ignored) {
        }
        if (!isAdminUser && !SessionManager.isTech(this)) {
            Toast.makeText(this, "You cannot create management jobs.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        assignedTechLabel.setText(isAdminUser
                ? "Assigned technician"
                : "Assigned technician: " + (currentTechDisplay.isEmpty() ? "Unknown" : currentTechDisplay)
                + "\nAutomatically assigned to you");
        techNameSpinner.setVisibility(isAdminUser ? View.VISIBLE : View.GONE);
        if (!isAdminUser) return;
        StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
            techOptions.clear();
            if (options != null) techOptions.addAll(options);
            String[] labels = new String[techOptions.size()];
            int selected = 0;
            for (int i = 0; i < techOptions.size(); i++) {
                StaffDirectory.OwnerOption option = techOptions.get(i);
                String ownerKey = option == null || option.ownerKey == null ? "" : option.ownerKey.trim();
                labels[i] = StaffDirectory.capitalizeContractKey(ownerKey);
                if (currentTechKey.equalsIgnoreCase(ownerKey)) selected = i;
            }
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            techNameSpinner.setAdapter(adapter);
            if (labels.length > 0) techNameSpinner.setSelection(selected);
        }));
    }

    private void validateAndSubmitJob() {
        if (submitting) return;
        String techKey = currentTechKey;
        String techDisplay = currentTechDisplay;
        String techUid = currentTechUid;
        if (isAdminUser) {
            int pos = techNameSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= techOptions.size()) {
                Toast.makeText(this, "Choose a technician.", Toast.LENGTH_SHORT).show();
                return;
            }
            StaffDirectory.OwnerOption option = techOptions.get(pos);
            techKey = option.ownerKey == null ? "" : option.ownerKey.trim().toLowerCase(Locale.ROOT);
            techDisplay = StaffDirectory.capitalizeContractKey(techKey);
            techUid = option.staffId == null ? "" : option.staffId;
        }
        String address = addressInput.getText().toString().trim();
        String contact = formatIrishMobile(customerContact.getText().toString().trim());
        String issue = issueDetails.getText().toString().trim();
        if (TextUtils.isEmpty(techKey) || TextUtils.isEmpty(address) || TextUtils.isEmpty(contact) || TextUtils.isEmpty(issue)) {
            Toast.makeText(this, "Enter the address, contact, issue, and technician.", Toast.LENGTH_SHORT).show();
            return;
        }
        ManagementJobRepository.NewJob job = new ManagementJobRepository.NewJob();
        job.companyId = companyId;
        job.address = address;
        job.contact = contact;
        job.issue = issue;
        job.assignedTo = techKey;
        job.assignedTech = techDisplay;
        job.assignedTechUid = techUid;
        job.createdBy = userName;
        submitting = true;
        submitButton.setEnabled(false);
        final String notifyKey = techKey;
        final String notifyDisplay = techDisplay;
        jobs.createJob(job, new ManagementJobRepository.CreatedCallback() {
            @Override
            public void onSuccess(ManagementJobRepository.CreatedJob created) {
                runOnUiThread(() -> {
                    writeInAppManagementJobNotifications(created.jobId, address, notifyDisplay, notifyKey, userName);
                    Toast.makeText(AddManagmentJobsActivity.this, "Job created: " + created.jobRef, Toast.LENGTH_LONG).show();
                    finish();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    submitting = false;
                    submitButton.setEnabled(true);
                    String message = error == null || error.getMessage() == null
                            ? "Could not create the job." : error.getMessage();
                    Toast.makeText(AddManagmentJobsActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void writeInAppManagementJobNotifications(String jobId, String customerName, String assignedTechDisplay,
                                                      String assignedTechKey, String createdBy) {
        try {
            String creator = createdBy == null ? "" : createdBy.trim();
            String techKey = assignedTechKey == null ? "" : assignedTechKey.trim();
            Map<String, Object> data = new HashMap<>();
            data.put("managementJobId", jobId);
            if (!techKey.isEmpty() && (creator.isEmpty() || !techKey.equalsIgnoreCase(creator))) {
                NotificationUtils.writeInAppNotification(
                        techKey,
                        "management_assign_" + jobId,
                        "New management job",
                        "Management job for " + customerName + " assigned to you",
                        "management",
                        data
                );
            }
        } catch (Exception ignored) {
        }
    }

    private String formatIrishMobile(String number) {
        if (number.startsWith("087") || number.startsWith("086") || number.startsWith("085")
                || number.startsWith("089") || number.startsWith("083") || number.startsWith("088")) {
            return "+353" + number.substring(1);
        }
        return number;
    }
}
