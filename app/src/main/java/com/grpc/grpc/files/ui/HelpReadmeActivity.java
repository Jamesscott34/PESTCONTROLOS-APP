package com.grpc.grpc.files.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.SessionManager;

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
 * In-app staff manual with collapsible sections.
 * Super Admin can preview Technician or Admin wording.
 */
public class HelpReadmeActivity extends AppCompatActivity {

    private static final int SECTION_COUNT = 17;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_readme);

        Button backButton = findViewById(R.id.helpBackButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        LinearLayout roleRow = findViewById(R.id.helpRoleRow);
        Spinner roleSpinner = findViewById(R.id.helpRoleSpinner);
        LinearLayout sectionOwner = findViewById(R.id.sectionOwner);
        if (roleRow != null) roleRow.setVisibility(View.GONE);
        if (sectionOwner != null) sectionOwner.setVisibility(View.GONE);

        for (int i = 1; i <= SECTION_COUNT; i++) {
            int headerId = getResources().getIdentifier("section" + i + "Header", "id", getPackageName());
            int contentId = getResources().getIdentifier("section" + i + "Content", "id", getPackageName());
            setupSectionToggle(headerId, contentId);
        }

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            boolean isSuperAdmin = session != null && session.isSuperAdmin;

            if (isSuperAdmin && roleRow != null && roleSpinner != null) {
                roleRow.setVisibility(View.VISIBLE);
                final String[] displayNames = new String[]{
                        getString(R.string.help_role_admin_view),
                        getString(R.string.help_role_tech_view)
                };
                final String[] roleKeys = new String[]{"admin", "tech"};
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                roleSpinner.setAdapter(adapter);
                roleSpinner.setSelection(0);
                applyRoleContent(roleKeys[0]);
                roleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position >= 0 && position < roleKeys.length) {
                            applyRoleContent(roleKeys[position]);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        applyRoleContent(roleKeys[0]);
                    }
                });
            } else {
                if (roleRow != null) roleRow.setVisibility(View.GONE);
                String roleKey = (session != null && session.isAdmin) ? "admin" : "tech";
                applyRoleContent(roleKey);
            }

            if (sectionOwner != null) {
                if (isSuperAdmin) {
                    sectionOwner.setVisibility(View.VISIBLE);
                    setupSectionToggle(R.id.sectionOwnerHeader, R.id.sectionOwnerContent);
                    TextView ownerContent = findViewById(R.id.sectionOwnerContent);
                    if (ownerContent != null) {
                        ownerContent.setText(R.string.help_section_owner);
                    }
                } else {
                    sectionOwner.setVisibility(View.GONE);
                }
            }
        }));
    }

    private void applyRoleContent(String roleKey) {
        String rk = roleKey != null ? roleKey.trim().toLowerCase(Locale.getDefault()) : "";
        boolean admin = "admin".equals(rk) || "super_admin".equals(rk);
        boolean tech = "tech".equals(rk);

        int[] adminIds = new int[]{
                R.string.help_section1_admin, R.string.help_section2_admin, R.string.help_section3_admin,
                R.string.help_section4_admin, R.string.help_section5_admin, R.string.help_section6_admin,
                R.string.help_section7_admin, R.string.help_section8_admin, R.string.help_section9_admin,
                R.string.help_section10_admin, R.string.help_section11_admin, R.string.help_section12_admin,
                R.string.help_section13_admin, R.string.help_section14_admin, R.string.help_section15_admin,
                R.string.help_section16_admin, R.string.help_section17_admin
        };
        int[] techIds = new int[]{
                R.string.help_section1_tech, R.string.help_section2_tech, R.string.help_section3_tech,
                R.string.help_section4_tech, R.string.help_section5_tech, R.string.help_section6_tech,
                R.string.help_section7_tech, R.string.help_section8_tech, R.string.help_section9_tech,
                R.string.help_section10_tech, R.string.help_section11_tech, R.string.help_section12_tech,
                R.string.help_section13_tech, R.string.help_section14_tech, R.string.help_section15_tech,
                R.string.help_section16_tech, R.string.help_section17_tech
        };
        int[] genericIds = new int[]{
                R.string.help_section1_generic, R.string.help_section2_generic, R.string.help_section3_generic,
                R.string.help_section4_generic, R.string.help_section5_generic, R.string.help_section6_generic,
                R.string.help_section7_generic, R.string.help_section8_generic, R.string.help_section9_generic,
                R.string.help_section10_generic, R.string.help_section11_generic, R.string.help_section12_generic,
                R.string.help_section13_generic, R.string.help_section14_generic, R.string.help_section15_generic,
                R.string.help_section16_generic, R.string.help_section17_generic
        };

        int[] chosen = tech ? techIds : (admin ? adminIds : genericIds);
        for (int i = 0; i < SECTION_COUNT; i++) {
            int contentId = getResources().getIdentifier("section" + (i + 1) + "Content", "id", getPackageName());
            setTextIfPresent(contentId, chosen[i]);
        }
    }

    private void setTextIfPresent(int viewId, int stringRes) {
        if (viewId == 0) return;
        TextView tv = findViewById(viewId);
        if (tv != null) tv.setText(stringRes);
    }

    private void setupSectionToggle(int headerId, int contentId) {
        if (headerId == 0 || contentId == 0) return;
        TextView header = findViewById(headerId);
        View content = findViewById(contentId);
        if (header == null || content == null) return;

        header.setOnClickListener(v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
            } else {
                content.setVisibility(View.VISIBLE);
            }
        });
    }
}
