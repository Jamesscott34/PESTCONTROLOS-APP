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
        final String currentUserKey = normalizeUserKey(currentUser);

        Button backButton = findViewById(R.id.helpBackButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // Role-aware help: only show the current user's content.
        // James can preview his own + Ian + Dean.
        LinearLayout roleRow = findViewById(R.id.helpRoleRow);
        Spinner roleSpinner = findViewById(R.id.helpRoleSpinner);

        if ("james".equals(currentUserKey) && roleRow != null && roleSpinner != null) {
            roleRow.setVisibility(View.VISIBLE);
            final String[] options = new String[]{"James", "Ian", "Dean"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            roleSpinner.setAdapter(adapter);
            roleSpinner.setSelection(0);

            applyRoleContent("james");
            roleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String pick = options[position];
                    applyRoleContent(normalizeUserKey(pick));
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    applyRoleContent("james");
                }
            });
        } else {
            if (roleRow != null) roleRow.setVisibility(View.GONE);
            applyRoleContent(currentUserKey);
        }

        // Set up expandable sections
        setupSectionToggle(R.id.section1Header, R.id.section1Content);
        setupSectionToggle(R.id.section2Header, R.id.section2Content);
        setupSectionToggle(R.id.section3Header, R.id.section3Content);
        setupSectionToggle(R.id.section4Header, R.id.section4Content);
        setupSectionToggle(R.id.section5Header, R.id.section5Content);
        setupSectionToggle(R.id.section6Header, R.id.section6Content);
        setupSectionToggle(R.id.section7Header, R.id.section7Content);
    }

    private void applyRoleContent(String userKey) {
        // Default to self-only behavior; unknown users get a generic view (no other-role details).
        int s1, s2, s3, s4, s5, s6, s7;
        if ("james".equals(userKey)) {
            s1 = R.string.help_section1_james;
            s2 = R.string.help_section2_james;
            s3 = R.string.help_section3_james;
            s4 = R.string.help_section4_james;
            s5 = R.string.help_section5_james;
            s6 = R.string.help_section6_james;
            s7 = R.string.help_section7_james;
        } else if ("ian".equals(userKey)) {
            s1 = R.string.help_section1_ian;
            s2 = R.string.help_section2_ian;
            s3 = R.string.help_section3_ian;
            s4 = R.string.help_section4_ian;
            s5 = R.string.help_section5_ian;
            s6 = R.string.help_section6_ian;
            s7 = R.string.help_section7_ian;
        } else if ("dean".equals(userKey)) {
            s1 = R.string.help_section1_dean;
            s2 = R.string.help_section2_dean;
            s3 = R.string.help_section3_dean;
            s4 = R.string.help_section4_dean;
            s5 = R.string.help_section5_dean;
            s6 = R.string.help_section6_dean;
            s7 = R.string.help_section7_dean;
        } else if ("kristine".equals(userKey)) {
            s1 = R.string.help_section1_kristine;
            s2 = R.string.help_section2_kristine;
            s3 = R.string.help_section3_kristine;
            s4 = R.string.help_section4_kristine;
            s5 = R.string.help_section5_kristine;
            s6 = R.string.help_section6_kristine;
            s7 = R.string.help_section7_kristine;
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

    private static String normalizeUserKey(String s) {
        String k = (s == null ? "" : s.trim().toLowerCase(Locale.getDefault()));
        if ("kristen".equals(k)) return "kristine";
        return k;
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

