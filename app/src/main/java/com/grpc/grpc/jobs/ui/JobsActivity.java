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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.jobs.data.JobWorkRepository;
import com.grpc.grpc.search.ui.SearchActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Primary Service Job list. Management is not opened from this screen.
 */
public class JobsActivity extends AppCompatActivity {

    private TextView jobCounts;
    private EditText searchBar;
    private LinearLayout jobsContainer;
    private Button addJobButton;
    private String userName = "";
    private String filter = "active";
    private final List<JobWorkRepository.JobRecord> allJobs = new ArrayList<>();
    private final JobWorkRepository jobs = new JobWorkRepository();
    @Nullable
    private ListenerRegistration registration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobs_selection);
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, "Job Work is unavailable offline.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        userName = getIntent().getStringExtra("USER_NAME");
        TextView title = findViewById(R.id.welcomeTextView);
        title.setText("Job Work");
        jobCounts = findViewById(R.id.jobCounts);
        searchBar = findViewById(R.id.searchBar);
        jobsContainer = findViewById(R.id.jobsContainer);
        addJobButton = findViewById(R.id.addJobButton);
        findViewById(R.id.filterActive).setOnClickListener(v -> setFilter("active"));
        findViewById(R.id.filterFollowUp).setOnClickListener(v -> setFilter("follow_up"));
        findViewById(R.id.filterCompleted).setOnClickListener(v -> setFilter("completed"));
        findViewById(R.id.filterAll).setOnClickListener(v -> setFilter("all"));
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        String incoming = getIntent().getStringExtra(SearchActivity.EXTRA_SEARCH_QUERY);
        if (incoming != null && !incoming.trim().isEmpty()) {
            searchBar.setText(incoming.trim());
            filter = "all";
        }
        addJobButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddJobsActivity.class);
            intent.putExtra("USER_NAME", userName);
            startActivity(intent);
        });

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            boolean canCreate = SessionManager.isAdmin(this) || SessionManager.isTech(this);
            addJobButton.setVisibility(canCreate ? View.VISIBLE : View.GONE);
            if (registration != null) registration.remove();
            registration = jobs.listen(this, (list, error) -> runOnUiThread(() -> {
                if (error != null) {
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                allJobs.clear();
                if (list != null) allJobs.addAll(list);
                render();
            }));
        }));
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
        for (JobWorkRepository.JobRecord job : allJobs) {
            String status = job.normalizedStatus();
            if ("completed".equals(status)) completed++;
            else if ("follow_up".equals(status)) follow++;
            else active++;
        }
        jobCounts.setText("Active: " + active + "    Follow ups: " + follow + "    Completed: " + completed);
        String query = searchBar.getText() == null ? "" : searchBar.getText().toString().trim().toLowerCase(Locale.ROOT);
        jobsContainer.removeAllViews();
        boolean any = false;
        for (JobWorkRepository.JobRecord job : allJobs) {
            if (!"all".equals(filter) && !filter.equals(job.normalizedStatus())) continue;
            String haystack = (job.text("CustomerName") + " " + job.text("Address")).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            any = true;
            jobsContainer.addView(jobCard(job));
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("No service jobs in this list.");
            empty.setPadding(0, 24, 0, 0);
            jobsContainer.addView(empty);
        }
    }

    private View jobCard(JobWorkRepository.JobRecord job) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);
        card.setBackgroundResource(R.drawable.surface_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 16;
        card.setLayoutParams(params);

        String customer = job.text("CustomerName");
        String address = job.text("Address");
        String tech = job.text("AssignedTech");
        String followUp = job.text("FollowUpDate");
        String body = (customer.isEmpty() ? "Unnamed job" : customer) + "\n"
                + (address.isEmpty() ? "No address yet" : address) + "\n"
                + "Technician: " + (tech.isEmpty() ? "Not recorded" : tech) + "\n"
                + job.statusLabel();
        if (!followUp.isEmpty()) {
            body = body + "\nNext visit: " + followUp;
        }
        TextView text = new TextView(this);
        text.setText(body);
        card.addView(text);
        card.setClickable(true);
        card.setOnClickListener(v -> openJob(job));
        if (canManageJobs()) {
            View.OnLongClickListener hold = v -> {
                showAdminJobOptions(job);
                return true;
            };
            card.setLongClickable(true);
            card.setOnLongClickListener(hold);
            text.setLongClickable(true);
            text.setOnLongClickListener(hold);
        }
        return card;
    }

    private boolean canManageJobs() {
        return SessionManager.isAdmin(this) || SessionManager.isSuperAdmin(this);
    }

    private void openJob(JobWorkRepository.JobRecord job) {
        Intent intent = new Intent(this, JobWorkDetailActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("JOB_ID", job.id);
        startActivity(intent);
    }

    private void showAdminJobOptions(JobWorkRepository.JobRecord job) {
        if (!canManageJobs()) return;
        String customer = job.text("CustomerName");
        String address = job.text("Address");
        String titleName = customer.isEmpty() ? "Unnamed job" : customer;
        String title = address.isEmpty() ? titleName : titleName + "\n" + address;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(new String[]{"Create Report", "Next Visit", "Delete Job"}, (dialog, which) -> {
                    if (which == 0) {
                        JobWorkDetailActivity.openCreateReport(this, userName, job.id,
                                job.text("CustomerName"), job.text("Address"));
                    } else if (which == 1) {
                        JobWorkFollowUpDates.show(this, job.text("FollowUpDate"), normalized ->
                                jobs.saveFollowUpDate(job.id, normalized, saved("Next visit saved")));
                    } else if (which == 2) {
                        confirmDeleteJob(job);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteJob(JobWorkRepository.JobRecord job) {
        if (!canManageJobs()) return;
        String customer = job.text("CustomerName");
        String address = job.text("Address");
        String message = (customer.isEmpty() ? "Unnamed job" : customer) + "\n"
                + (address.isEmpty() ? "No address yet" : address) + "\n\n"
                + "This will remove this Job Work record.";
        new AlertDialog.Builder(this)
                .setTitle("Delete Job?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        jobs.deleteJob(job.id, saved("Job deleted")))
                .show();
    }

    private JobWorkRepository.DoneCallback saved(String success) {
        return new JobWorkRepository.DoneCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(JobsActivity.this, success, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(JobsActivity.this,
                        error == null || error.getMessage() == null ? "Could not update the job." : error.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        };
    }

    @Override
    protected void onDestroy() {
        if (registration != null) registration.remove();
        super.onDestroy();
    }
}
