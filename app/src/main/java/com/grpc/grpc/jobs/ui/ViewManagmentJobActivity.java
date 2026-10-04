package com.grpc.grpc.jobs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.jobs.data.ManagementCompanyRepository;
import com.grpc.grpc.jobs.data.ManagementJobRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Jobs for one management company. Every signed-in user sees the company's jobs.
 */
public class ViewManagmentJobActivity extends AppCompatActivity {

    private TextView companyTitle;
    private TextView jobCounts;
    private EditText searchBar;
    private LinearLayout jobsContainer;
    private Button addJobButton;
    private String userName = "";
    private String companyId = "";
    private String filter = "all";
    private ManagementCompanyRepository.Company company;
    private final List<ManagementJobRepository.JobRecord> allJobs = new ArrayList<>();
    private final ManagementCompanyRepository companies = new ManagementCompanyRepository();
    private final ManagementJobRepository jobs = new ManagementJobRepository();
    @Nullable
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_managment_jobs);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        companyId = getIntent().getStringExtra("COMPANY_ID");
        if (companyId == null || companyId.trim().isEmpty()) {
            Intent intent = new Intent(this, ManagementCompaniesActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
            finish();
            return;
        }

        companyTitle = findViewById(R.id.companyTitle);
        jobCounts = findViewById(R.id.jobCounts);
        searchBar = findViewById(R.id.searchBar);
        jobsContainer = findViewById(R.id.jobsContainer);
        addJobButton = findViewById(R.id.addJobButton);
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
        findViewById(R.id.filterAll).setOnClickListener(v -> setFilter("all"));
        findViewById(R.id.filterActive).setOnClickListener(v -> setFilter("active"));
        findViewById(R.id.filterFollowUp).setOnClickListener(v -> setFilter("follow_up"));
        findViewById(R.id.filterCompleted).setOnClickListener(v -> setFilter("completed"));
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        addJobButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddManagmentJobsActivity.class);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("COMPANY_ID", companyId);
            startActivity(intent);
        });

        SessionManager.ensureLoaded(this, session -> companies.getCompany(companyId, (loaded, error) -> runOnUiThread(() -> {
            if (loaded == null) {
                Toast.makeText(this, "Could not open this management company.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            company = loaded;
            companyTitle.setText(company.name);
            boolean canCreate = SessionManager.isAdmin(this) || SessionManager.isTech(this);
            addJobButton.setVisibility(company.active && canCreate ? View.VISIBLE : View.GONE);
            if (registration != null) registration.remove();
            registration = jobs.listenForCompany(this, company, (list, listenError) -> runOnUiThread(() -> {
                if (listenError != null) {
                    Toast.makeText(this, listenError.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                allJobs.clear();
                allJobs.addAll(list);
                render();
            }));
        })));
    }

    private void setFilter(String next) {
        filter = next;
        render();
    }

    private void render() {
        if (jobsContainer == null) return;
        int active = 0;
        int follow = 0;
        int completed = 0;
        for (ManagementJobRepository.JobRecord job : allJobs) {
            String status = job.normalizedStatus();
            if ("completed".equals(status)) completed++;
            else if ("follow_up".equals(status)) follow++;
            else active++;
        }
        jobCounts.setText("Active: " + active + "    Follow ups: " + follow + "    Completed: " + completed);
        String query = searchBar.getText() == null ? "" : searchBar.getText().toString().trim().toLowerCase(Locale.ROOT);
        jobsContainer.removeAllViews();
        boolean any = false;
        for (ManagementJobRepository.JobRecord job : allJobs) {
            if (!filter.equals("all") && !filter.equals(job.normalizedStatus())) continue;
            String haystack = (job.jobRef() + " " + job.text("Address") + " " + job.text("AssignedTech")
                    + " " + job.text("IssueDetails") + " " + job.text("CustomerName")).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            any = true;
            jobsContainer.addView(jobCard(job));
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("No management jobs in this list.");
            empty.setPadding(0, 24, 0, 0);
            jobsContainer.addView(empty);
        }
    }

    private View jobCard(ManagementJobRepository.JobRecord job) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundResource(R.drawable.surface_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 16;
        card.setLayoutParams(params);

        StringBuilder text = new StringBuilder();
        if (!job.jobRef().isEmpty()) text.append(job.jobRef()).append("\n");
        String address = job.text("Address");
        text.append(address.isEmpty() ? "No address yet" : address).append("\n");
        String issue = job.text("IssueDetails");
        if (!issue.isEmpty()) text.append(issue).append("\n");
        text.append("Technician: ").append(job.text("AssignedTech")).append("\n");
        text.append("Visits: ").append(job.visitCount()).append("\n");
        text.append(job.statusLabel());
        if (job.legacyWithoutStatus()) text.append("  (legacy)");
        if (job.legacyWithoutRef()) text.append("\nNo job reference");

        TextView body = new TextView(this);
        body.setText(text.toString());
        card.addView(body);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ManagementJobDetailActivity.class);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("COMPANY_ID", companyId);
            intent.putExtra("JOB_ID", job.id);
            startActivity(intent);
        });
        return card;
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
