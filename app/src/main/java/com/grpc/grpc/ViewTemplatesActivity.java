package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * Lists saved named templates. User can tap "Use" to create a report with that template's headers.
 */
public class ViewTemplatesActivity extends AppCompatActivity {

    public static final String EXTRA_TEMPLATE_ID = "template_id";

    private PdfTemplateStorage storage;
    private String userName;
    private LinearLayout templatesContainer;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_templates);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) userName = "Offline User";

        storage = new PdfTemplateStorage(this);
        templatesContainer = findViewById(R.id.templatesContainer);
        emptyMessage = findViewById(R.id.emptyMessage);

        loadTemplates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTemplates();
    }

    private void loadTemplates() {
        templatesContainer.removeAllViews();
        List<SavedTemplate> list = storage.loadSavedTemplates(userName);

        if (list.isEmpty()) {
            emptyMessage.setVisibility(android.view.View.VISIBLE);
            return;
        }
        emptyMessage.setVisibility(android.view.View.GONE);

        for (SavedTemplate t : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding(0, 8, 0, 8);

            TextView nameLabel = new TextView(this);
            nameLabel.setText(t.getName());
            nameLabel.setTextSize(16);
            nameLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            nameLabel.setPadding(16, 0, 16, 0);

            Button useButton = new Button(this);
            useButton.setText("Use");
            useButton.setOnClickListener(v -> useTemplate(t.getId()));

            row.addView(nameLabel);
            row.addView(useButton);
            templatesContainer.addView(row);
        }
    }

    private void useTemplate(String templateId) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra(EXTRA_TEMPLATE_ID, templateId);
        startActivity(intent);
        Toast.makeText(this, "Fill in the report and save to use this template.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
