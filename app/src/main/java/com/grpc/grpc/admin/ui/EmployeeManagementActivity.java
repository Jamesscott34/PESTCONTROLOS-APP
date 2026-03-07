package com.grpc.grpc.admin.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EmployeeManagementActivity extends AppCompatActivity {

    private EditText editName;
    private EditText editNumber;
    private EditText editEmail;
    private EditText editPassword;
    private Spinner spinnerRole;
    private EditText editStaffId;
    private EditText editContractKey;
    private EditText editTitle;
    private Button buttonCreateEmployee;

    private EditText editExistingUidOrEmail;
    private Button buttonInitializeProfile;

    private ListView employeeListView;
    private ArrayAdapter<String> employeeAdapter;
    private final List<EmployeeItem> employeeItems = new ArrayList<>();

    private FirebaseFunctions functions;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_management);

        editName = findViewById(R.id.editEmployeeName);
        editNumber = findViewById(R.id.editEmployeeNumber);
        editEmail = findViewById(R.id.editEmployeeEmail);
        editPassword = findViewById(R.id.editEmployeePassword);
        spinnerRole = findViewById(R.id.spinnerEmployeeRole);
        editStaffId = findViewById(R.id.editEmployeeStaffId);
        editContractKey = findViewById(R.id.editEmployeeContractKey);
        editTitle = findViewById(R.id.editEmployeeTitle);
        buttonCreateEmployee = findViewById(R.id.buttonCreateEmployee);

        editExistingUidOrEmail = findViewById(R.id.editExistingUidOrEmail);
        buttonInitializeProfile = findViewById(R.id.buttonInitializeProfile);

        employeeListView = findViewById(R.id.employeeListView);

        db = FirebaseHelper.getFirestore();
        functions = FirebaseFunctions.getInstance("us-central1");

        setupRoleSpinner();

        // RBAC: only super_admin may manage employees.
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.isSuperAdmin) {
                Toast.makeText(EmployeeManagementActivity.this,
                        "Employee management is available to super admin only.",
                        Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            if (BuildConfig.IS_OFFLINE || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
                Toast.makeText(EmployeeManagementActivity.this,
                        "Employee management is not available in offline / restricted demo mode.",
                        Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            setupButtonHandlers();
            loadEmployees();
        }));
    }

    private void setupRoleSpinner() {
        String[] labels = new String[]{"Technician", "Admin", "Super Admin"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);
        spinnerRole.setSelection(0);
    }

    private void setupButtonHandlers() {
        buttonCreateEmployee.setOnClickListener(v -> createEmployee());
        buttonInitializeProfile.setOnClickListener(v -> initializeProfile());
    }

    private void createEmployee() {
        String name = textOf(editName);
        String number = textOf(editNumber);
        String email = textOf(editEmail);
        String password = textOf(editPassword);
        String staffId = textOf(editStaffId);
        String contractKey = textOf(editContractKey);
        String title = textOf(editTitle);

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)
                || TextUtils.isEmpty(staffId) || TextUtils.isEmpty(contractKey)) {
            Toast.makeText(this,
                    "Name, email, password, staff ID and contract key are required.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedIndex = spinnerRole.getSelectedItemPosition();
        String role;
        if (selectedIndex == 1) {
            role = "admin";
        } else if (selectedIndex == 2) {
            role = "super_admin";
        } else {
            role = "tech";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("number", number);
        data.put("email", email);
        data.put("password", password);
        data.put("role", role);
        data.put("staffId", staffId);
        data.put("contractKey", contractKey);
        data.put("title", title);

        buttonCreateEmployee.setEnabled(false);
        functions
                .getHttpsCallable("createEmployee")
                .call(data)
                .addOnCompleteListener(task -> {
                    buttonCreateEmployee.setEnabled(true);
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        String msg = e != null && e.getMessage() != null ? e.getMessage() : "Unknown error";
                        Toast.makeText(EmployeeManagementActivity.this,
                                "Create failed: " + msg,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    HttpsCallableResult result = task.getResult();
                    Toast.makeText(EmployeeManagementActivity.this,
                            "Employee created.",
                            Toast.LENGTH_SHORT).show();
                    clearCreateForm();
                    loadEmployees();
                });
    }

    private void initializeProfile() {
        String value = textOf(editExistingUidOrEmail);
        if (TextUtils.isEmpty(value)) {
            Toast.makeText(this,
                    "Enter an existing UID or email.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> data = new HashMap<>();
        if (value.contains("@")) {
            data.put("email", value);
        } else {
            data.put("uid", value);
        }

        buttonInitializeProfile.setEnabled(false);
        functions
                .getHttpsCallable("initializeEmployeeProfile")
                .call(data)
                .addOnCompleteListener(task -> {
                    buttonInitializeProfile.setEnabled(true);
                    if (!task.isSuccessful()) {
                        Exception e = task.getException();
                        String msg = e != null && e.getMessage() != null ? e.getMessage() : "Unknown error";
                        Toast.makeText(EmployeeManagementActivity.this,
                                "Initialize failed: " + msg,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(EmployeeManagementActivity.this,
                            "Profile initialized.",
                            Toast.LENGTH_SHORT).show();
                    loadEmployees();
                });
    }

    private void loadEmployees() {
        if (employeeListView == null) return;

        db.collection(FirestorePaths.USERS)
                .get()
                .addOnSuccessListener(snap -> {
                    employeeItems.clear();
                    List<String> labels = new ArrayList<>();

                    if (snap != null) {
                        for (DocumentSnapshot ds : snap.getDocuments()) {
                            if (ds == null || !ds.exists()) continue;

                            String uid = ds.getId() != null ? ds.getId().trim() : "";
                            if (uid.matches("\\d{3}")) continue; // skip numeric staff docs

                            String name = safeString(ds.get("name"), ds.get("Name"));
                            String email = safeString(ds.get("email"), ds.get("Email"));
                            String contractKey = safeString(ds.get("contractKey"), ds.get("ContractKey"));
                            String staffId = safeString(ds.get("staffId"), ds.get("StaffID"), ds.get("staffID"));
                            String roleRaw = safeString(ds.get("role"), ds.get("Role"));
                            String roleNorm = SessionManager.normalizeRole(roleRaw);

                            // Flavor-aware employee list rule: hide super_admin except in grpc flavor.
                            if (!"grpc".equals(BuildConfig.FLAVOR) && "super_admin".equals(roleNorm)) {
                                continue;
                            }

                            String displayName = !name.isEmpty() ? name
                                    : (!email.isEmpty() ? email : uid);

                            EmployeeItem item = new EmployeeItem(uid, displayName, roleNorm, contractKey, staffId);
                            employeeItems.add(item);
                            labels.add(buildEmployeeLabel(item));
                        }
                    }

                    if (employeeAdapter == null) {
                        employeeAdapter = new ArrayAdapter<>(EmployeeManagementActivity.this,
                                R.layout.item_simple_card,
                                R.id.cardText,
                                labels);
                        employeeListView.setAdapter(employeeAdapter);
                    } else {
                        employeeAdapter.clear();
                        employeeAdapter.addAll(labels);
                        employeeAdapter.notifyDataSetChanged();
                    }

                    employeeListView.setOnItemClickListener((parent, view, position, id) -> {
                        if (position >= 0 && position < employeeItems.size()) {
                            EmployeeItem item = employeeItems.get(position);
                            Intent intent = new Intent(EmployeeManagementActivity.this, EditEmployeeActivity.class);
                            intent.putExtra(EditEmployeeActivity.EXTRA_EMPLOYEE_UID, item.uid);
                            startActivity(intent);
                        }
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(EmployeeManagementActivity.this,
                        "Failed to load employees.",
                        Toast.LENGTH_SHORT).show());
    }

    private void clearCreateForm() {
        editName.setText("");
        editNumber.setText("");
        editEmail.setText("");
        editPassword.setText("");
        editStaffId.setText("");
        editContractKey.setText("");
        editTitle.setText("");
        spinnerRole.setSelection(0);
    }

    private static String textOf(EditText e) {
        return e != null && e.getText() != null ? e.getText().toString().trim() : "";
    }

    private static String safeString(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private static String buildEmployeeLabel(EmployeeItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.displayName);
        if (item.role != null && !item.role.isEmpty()) {
            sb.append(" (").append(item.role).append(")");
        }
        List<String> extras = new ArrayList<>();
        if (item.staffId != null && !item.staffId.trim().isEmpty()) {
            extras.add("staffId=" + item.staffId.trim());
        }
        if (item.contractKey != null && !item.contractKey.trim().isEmpty()) {
            extras.add("contractKey=" + item.contractKey.trim());
        }
        if (!extras.isEmpty()) {
            sb.append(" · ");
            sb.append(android.text.TextUtils.join(" | ", extras));
        }
        return sb.toString();
    }

    private static final class EmployeeItem {
        final String uid;
        final String displayName;
        final String role;
        final String contractKey;
        final String staffId;

        EmployeeItem(String uid, String displayName, String role, String contractKey, String staffId) {
            this.uid = uid != null ? uid : "";
            this.displayName = displayName != null ? displayName : "";
            this.role = role != null ? role : "";
            this.contractKey = contractKey != null ? contractKey : "";
            this.staffId = staffId != null ? staffId : "";
        }
    }
}

