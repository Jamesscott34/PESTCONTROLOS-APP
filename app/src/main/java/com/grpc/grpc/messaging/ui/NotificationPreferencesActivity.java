package com.grpc.grpc.messaging.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Per-user notification type filters for the in-app notification inbox.
 */
public class NotificationPreferencesActivity extends AppCompatActivity {

    private static final String[][] NOTIFICATION_TYPES = {
            {"contract_reminder", "Contract due reminders"},
            {"contract_update", "Contract updates"},
            {"lead_update", "Lead / commission updates"},
            {"workview_update", "Work View updates"},
            {"bugreport_reply", "Bug / feature request replies"},
            {"bugreport_resolved", "Bug / feature request resolved"},
            {"daily_pdf", "Daily summary PDFs"},
            {"general", "General notifications"}
    };

    private final Switch[] switches = new Switch[NOTIFICATION_TYPES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPreferences();
    }

    public static boolean isTypeEnabled(Context context, String type) {
        if (type == null) return true;
        return context.getSharedPreferences("GRPC_NOTIF_PREFS", Context.MODE_PRIVATE)
                .getBoolean("pref_" + type, true);
    }

    private ScrollView buildContentView() {
        int pad = dp(16);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scrollView.setPadding(pad, pad, pad, pad);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = new TextView(this);
        heading.setText("Notification Preferences");
        heading.setTextSize(20f);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(16));
        root.addView(heading);

        for (int i = 0; i < NOTIFICATION_TYPES.length; i++) {
            String typeKey = NOTIFICATION_TYPES[i][0];
            String label = NOTIFICATION_TYPES[i][1];

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(12);
            row.setLayoutParams(rowLp);

            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextSize(16f);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelView.setLayoutParams(labelLp);
            row.addView(labelView);

            Switch toggle = new Switch(this);
            switches[i] = toggle;
            final String key = typeKey;
            toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                    savePreference(key, isChecked));
            row.addView(toggle);

            root.addView(row);
        }

        Button backButton = new Button(this);
        backButton.setText("Back");
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        backLp.topMargin = dp(24);
        backButton.setLayoutParams(backLp);
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton);

        return scrollView;
    }

    private void loadPreferences() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("GRPC_NOTIF_PREFS", MODE_PRIVATE);
        for (int i = 0; i < NOTIFICATION_TYPES.length; i++) {
            Switch toggle = switches[i];
            if (toggle == null) continue;
            String typeKey = NOTIFICATION_TYPES[i][0];
            boolean enabled = prefs.getBoolean("pref_" + typeKey, true);
            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(enabled);
            final String key = typeKey;
            toggle.setOnCheckedChangeListener((buttonView, isChecked) ->
                    savePreference(key, isChecked));
        }
    }

    private void savePreference(String typeKey, boolean isChecked) {
        getSharedPreferences("GRPC_NOTIF_PREFS", MODE_PRIVATE)
                .edit()
                .putBoolean("pref_" + typeKey, isChecked)
                .apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
