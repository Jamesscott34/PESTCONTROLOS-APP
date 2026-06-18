package com.grpc.grpc.admin.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Super-admin only: edit an employee's profile and permissions by authUid.
 * Reads and writes Firestore users/{authUid}.
 */
public class EditEmployeeActivity extends AppCompatActivity {

    public static final String EXTRA_EMPLOYEE_UID = "employee_uid";

    private String employeeUid;
    private EditText editName;
    private EditText editEmail;
    private EditText editNumber;
    private EditText editTitleField;
    private EditText editStaffId;
    private EditText editContractKey;
    private Spinner spinnerRole;
    private CheckBox checkCanSearch;
    private CheckBox checkCanUseLocationFinder;
    private CheckBox checkCanHardPressContracts;
    private CheckBox checkCanMarkPaidLeads;
    private CheckBox checkCanAccessCommission;
    private CheckBox checkSeesAllJobs;
    private CheckBox checkCanSeeContracts;
    private CheckBox checkCanViewAllContracts;
    private CheckBox checkCanMessage;
    private CheckBox checkCanMap;
    private CheckBox checkCanRoute;
    private CheckBox checkCanConvert;
    private CheckBox checkCanMove;
    private CheckBox checkCanRemember;
    private Button buttonSave;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_employee);

        employeeUid = getIntent() != null ? getIntent().getStringExtra(EXTRA_EMPLOYEE_UID) : null;
        if (TextUtils.isEmpty(employeeUid)) {
            Toast.makeText(this, "No employee selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db = FirebaseHelper.getFirestore();
        bindViews();
        setupRoleSpinner();
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.isSuperAdmin) {
                Toast.makeText(EditEmployeeActivity.this, "Only super admin can edit employees.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (BuildConfig.IS_OFFLINE || DemoFirebaseExpiryHelper.isFirebaseBlockedForCurrentUser(this)) {
                Toast.makeText(EditEmployeeActivity.this, "Not available in offline / restricted mode.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            loadProfile();
            buttonSave.setOnClickListener(v -> saveProfile());
        }));
    }

    private void bindViews() {
        TextView uidLabel = findViewById(R.id.editEmployeeUidLabel);
        if (uidLabel != null) uidLabel.setText("UID: " + employeeUid);

        editName = findViewById(R.id.editEmployeeName);
        editEmail = findViewById(R.id.editEmployeeEmail);
        editNumber = findViewById(R.id.editEmployeeNumber);
        editTitleField = findViewById(R.id.editEmployeeTitleField);
        editStaffId = findViewById(R.id.editEmployeeStaffId);
        editContractKey = findViewById(R.id.editEmployeeContractKey);
        spinnerRole = findViewById(R.id.editEmployeeRoleSpinner);
        checkCanSearch = findViewById(R.id.checkCanSearch);
        checkCanUseLocationFinder = findViewById(R.id.checkCanUseLocationFinder);
        checkCanHardPressContracts = findViewById(R.id.checkCanHardPressContracts);
        checkCanMarkPaidLeads = findViewById(R.id.checkCanMarkPaidLeads);
        checkCanAccessCommission = findViewById(R.id.checkCanAccessCommission);
        checkSeesAllJobs = findViewById(R.id.checkSeesAllJobs);
        checkCanSeeContracts = findViewById(R.id.checkCanSeeContracts);
        checkCanViewAllContracts = findViewById(R.id.checkCanViewAllContracts);
        checkCanMessage = findViewById(R.id.checkCanMessage);
        checkCanMap = findViewById(R.id.checkCanMap);
        checkCanRoute = findViewById(R.id.checkCanRoute);
        checkCanConvert = findViewById(R.id.checkCanConvert);
        checkCanMove = findViewById(R.id.checkCanMove);
        checkCanRemember = findViewById(R.id.checkCanRemember);
        buttonSave = findViewById(R.id.buttonSaveEmployee);
    }

    private void setupRoleSpinner() {
        String[] labels = new String[]{"Technician", "Admin", "Super Admin"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerRole != null) spinnerRole.setAdapter(adapter);
    }

    private void loadProfile() {
        db.collection(FirestorePaths.USERS).document(employeeUid).get()
                .addOnSuccessListener(this::applySnapshot)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void applySnapshot(DocumentSnapshot ds) {
        if (ds == null || !ds.exists()) {
            Toast.makeText(this, "Profile not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setText(editName, safeStr(ds.get("name"), ds.get("Name")));
        setText(editEmail, safeStr(ds.get("email"), ds.get("Email")));
        setText(editNumber, safeStr(ds.get("number"), ds.get("Number"), ds.get("mobile"), ds.get("Mobile")));
        setText(editTitleField, safeStr(ds.get("title"), ds.get("Title")));
        setText(editStaffId, safeStr(ds.get("staffId"), ds.get("StaffID"), ds.get("staffID")));
        setText(editContractKey, safeStr(ds.get("contractKey"), ds.get("ContractKey")));

        String role = SessionManager.normalizeRole(safeStr(ds.get("role"), ds.get("Role")));
        if ("admin".equals(role)) spinnerRole.setSelection(1);
        else if ("super_admin".equals(role)) spinnerRole.setSelection(2);
        else spinnerRole.setSelection(0);

        setCheck(checkCanSearch, ds, "canSearch");
        setCheck(checkCanUseLocationFinder, ds, "canUseLocationFinder");
        setCheck(checkCanHardPressContracts, ds, "canHardPressContracts");
        setCheck(checkCanMarkPaidLeads, ds, "canMarkPaidLeads");
        setCheck(checkCanAccessCommission, ds, "canAccessCommission");
        setCheck(checkSeesAllJobs, ds, "seesAllJobs");
        setCheck(checkCanSeeContracts, ds, "canSeeContracts");
        setCheck(checkCanViewAllContracts, ds, "canViewAllContracts");
        setCheck(checkCanMessage, ds, "canMessage");
        setCheck(checkCanMap, ds, "canMap");
        setCheck(checkCanRoute, ds, "canRoute");
        setCheck(checkCanConvert, ds, "canConvert");
        setCheck(checkCanMove, ds, "canMove");
        setCheck(checkCanRemember, ds, "canRemember");
    }

    private static void setText(EditText v, String s) {
        if (v != null) v.setText(s != null ? s : "");
    }

    private static void setCheck(CheckBox v, DocumentSnapshot ds, String key) {
        if (v == null) return;
        Boolean b = ds.getBoolean(key);
        v.setChecked(b != null && b);
    }

    private static String safeStr(Object... values) {
        for (Object v : values) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private void saveProfile() {
        String role;
        int pos = spinnerRole != null ? spinnerRole.getSelectedItemPosition() : 0;
        if (pos == 1) role = "admin";
        else if (pos == 2) role = "super_admin";
        else role = "tech";

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", getText(editName));
        updates.put("email", getText(editEmail));
        updates.put("number", getText(editNumber));
        updates.put("title", getText(editTitleField));
        updates.put("staffId", getText(editStaffId));
        updates.put("contractKey", getText(editContractKey));
        updates.put("role", role);
        updates.put("canSearch", checkCanSearch != null && checkCanSearch.isChecked());
        updates.put("canUseLocationFinder", checkCanUseLocationFinder != null && checkCanUseLocationFinder.isChecked());
        updates.put("canHardPressContracts", checkCanHardPressContracts != null && checkCanHardPressContracts.isChecked());
        updates.put("canMarkPaidLeads", checkCanMarkPaidLeads != null && checkCanMarkPaidLeads.isChecked());
        updates.put("canAccessCommission", checkCanAccessCommission != null && checkCanAccessCommission.isChecked());
        updates.put("seesAllJobs", checkSeesAllJobs != null && checkSeesAllJobs.isChecked());
        updates.put("canSeeContracts", checkCanSeeContracts != null && checkCanSeeContracts.isChecked());
        updates.put("canViewAllContracts", checkCanViewAllContracts != null && checkCanViewAllContracts.isChecked());
        updates.put("canMessage", checkCanMessage != null && checkCanMessage.isChecked());
        updates.put("canMap", checkCanMap != null && checkCanMap.isChecked());
        updates.put("canRoute", checkCanRoute != null && checkCanRoute.isChecked());
        updates.put("canConvert", checkCanConvert != null && checkCanConvert.isChecked());
        updates.put("canMove", checkCanMove != null && checkCanMove.isChecked());
        updates.put("canRemember", checkCanRemember != null && checkCanRemember.isChecked());

        buttonSave.setEnabled(false);
        db.collection(FirestorePaths.USERS).document(employeeUid).set(updates, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
                    buttonSave.setEnabled(true);
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Save failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
                    buttonSave.setEnabled(true);
                });
    }

    private static String getText(EditText v) {
        return v != null && v.getText() != null ? v.getText().toString().trim() : "";
    }
}
