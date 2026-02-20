package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

            Button deleteButton = new Button(this);
            deleteButton.setText("Delete");
            deleteButton.setOnClickListener(v -> confirmDeleteTemplate(t));

            row.addView(nameLabel);
            row.addView(useButton);
            row.addView(deleteButton);
            templatesContainer.addView(row);
        }
    }

    /** Ask for confirmation then delete the template and refresh the list. */
    private void confirmDeleteTemplate(SavedTemplate template) {
        new AlertDialog.Builder(this)
                .setTitle("Delete template?")
                .setMessage("Delete \"" + template.getName() + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    storage.deleteSavedTemplate(userName, template.getId());
                    Toast.makeText(this, "Template deleted.", Toast.LENGTH_SHORT).show();
                    loadTemplates();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /** Opens Create Custom Report with the selected template: dynamic headers + body fields, Save Report, Add Image, Password protect. */
    private void useTemplate(String templateId) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra(EXTRA_TEMPLATE_ID, templateId);
        intent.putExtra("USE_MY_TEMPLATE", true); // Show PDF template section and My Template selected
        startActivity(intent);
        Toast.makeText(this, "Fill in the report and save to use this template.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
