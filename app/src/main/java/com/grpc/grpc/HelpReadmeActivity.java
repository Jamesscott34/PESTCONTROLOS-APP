package com.grpc.grpc;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * HelpReadmeActivity
 *
 * In-app README / help screen with collapsible sections that
 * explain how to use the GRPest Control application step by step.
 */
public class HelpReadmeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_readme);

        String currentUser = getIntent() != null ? getIntent().getStringExtra("USER_NAME") : null;
        if (currentUser == null || currentUser.trim().isEmpty()) {
            currentUser = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_NAME", "User");
        }
        final String currentUserFinal = currentUser;

        Button backButton = findViewById(R.id.helpBackButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Role-aware help: only show the current user's content.
        // Super admin can preview help content by role.
        LinearLayout roleRow = findViewById(R.id.helpRoleRow);
        Spinner roleSpinner = findViewById(R.id.helpRoleSpinner);
        LinearLayout section8 = findViewById(R.id.section8);
        if (roleRow != null) roleRow.setVisibility(View.GONE);
        if (section8 != null) section8.setVisibility(View.GONE);

        // Set up expandable sections (static wiring)
        setupSectionToggle(R.id.section1Header, R.id.section1Content);
        setupSectionToggle(R.id.section2Header, R.id.section2Content);
        setupSectionToggle(R.id.section3Header, R.id.section3Content);
        setupSectionToggle(R.id.section4Header, R.id.section4Content);
        setupSectionToggle(R.id.section5Header, R.id.section5Content);
        setupSectionToggle(R.id.section6Header, R.id.section6Content);
        setupSectionToggle(R.id.section7Header, R.id.section7Content);

        // Load RBAC before deciding super_admin UI.
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            boolean isSuperAdmin = session != null && session.isSuperAdmin;

            if (isSuperAdmin && roleRow != null && roleSpinner != null) {
                roleRow.setVisibility(View.VISIBLE);
                // Super admin can preview help content by ROLE (no hardcoded staff names).
                final String[] displayNames = new String[]{"Admin view", "Technician view"};
                final String[] roleKeys = new String[]{"admin", "tech"};
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    roleSpinner.setAdapter(adapter);
                    roleSpinner.setSelection(0);

                    applyRoleContent(roleKeys[0]);
                    roleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < roleKeys.length && roleKeys[position] != null)
                                applyRoleContent(roleKeys[position]);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            applyRoleContent(roleKeys[0]);
                        }
                    });
            } else {
                if (roleRow != null) roleRow.setVisibility(View.GONE);
                // Non-super-admin: show role-appropriate help.
                String roleKey = (session != null && session.isAdmin) ? "admin" : "tech";
                applyRoleContent(roleKey);
            }

            // Admin-only section 8: visible only for super_admin
            if (section8 != null && currentUserFinal != null && !currentUserFinal.trim().isEmpty()) {
                if (isSuperAdmin) {
                    section8.setVisibility(View.VISIBLE);
                    setupSectionToggle(R.id.section8Header, R.id.section8Content);
                } else {
                    section8.setVisibility(View.GONE);
                }
            }
        }));
    }

    private void applyRoleContent(String roleKey) {
        // Role-based help content (no staff-name-driven logic).
        int s1, s2, s3, s4, s5, s6, s7;
        String rk = (roleKey != null ? roleKey.trim().toLowerCase(Locale.getDefault()) : "");
        if ("admin".equals(rk) || "super_admin".equals(rk)) {
            s1 = R.string.help_section1_admin;
            s2 = R.string.help_section2_admin;
            s3 = R.string.help_section3_admin;
            s4 = R.string.help_section4_admin;
            s5 = R.string.help_section5_admin;
            s6 = R.string.help_section6_admin;
            s7 = R.string.help_section7_admin;
        } else if ("tech".equals(rk)) {
            s1 = R.string.help_section1_tech;
            s2 = R.string.help_section2_tech;
            s3 = R.string.help_section3_tech;
            s4 = R.string.help_section4_tech;
            s5 = R.string.help_section5_tech;
            s6 = R.string.help_section6_tech;
            s7 = R.string.help_section7_tech;
        } else {
            s1 = R.string.help_section1_generic;
            s2 = R.string.help_section2_generic;
            s3 = R.string.help_section3_generic;
            s4 = R.string.help_section4_generic;
            s5 = R.string.help_section5_generic;
            s6 = R.string.help_section6_generic;
            s7 = R.string.help_section7_generic;
        }

        setTextIfPresent(R.id.section1Content, s1);
        setTextIfPresent(R.id.section2Content, s2);
        setTextIfPresent(R.id.section3Content, s3);
        setTextIfPresent(R.id.section4Content, s4);
        setTextIfPresent(R.id.section5Content, s5);
        setTextIfPresent(R.id.section6Content, s6);
        setTextIfPresent(R.id.section7Content, s7);
    }

    private void setTextIfPresent(int viewId, int stringRes) {
        TextView tv = findViewById(viewId);
        if (tv != null) tv.setText(stringRes);
    }

    /**
     * Simple helper to wire a header tap to show/hide its content.
     */
    private void setupSectionToggle(int headerId, int contentId) {
        TextView header = findViewById(headerId);
        View content = findViewById(contentId);

        if (header == null || content == null) {
            return;
        }

        header.setOnClickListener(v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
            } else {
                content.setVisibility(View.VISIBLE);
            }
        });
    }
}

