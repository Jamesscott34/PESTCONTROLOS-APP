package com.grpc.grpc.jobs.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.storage.FirebaseStorage;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StaffDirectory;
import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.jobs.data.JobWorkRepository;
import com.grpc.grpc.jobs.data.JobWorkRepository.JobRecord;
import com.grpc.grpc.jobs.data.JobWorkRepository.ReportLink;
import com.grpc.grpc.jobs.data.JobWorkRepository.VisitRecord;
import com.grpc.grpc.reports.ui.CloudStorageBrowserActivity;
import com.grpc.grpc.reports.ui.ReportActivity;
import com.grpc.grpc.reports.ui.ReportPreviewActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JobWorkDetailActivity extends AppCompatActivity {

    private LinearLayout container;
    private String userName = "";
    private String jobId = "";
    private boolean busy;
    private String followUpVisitId;
    private String completeVisitId;
    private String reopenVisitId;
    private JobRecord job;
    private List<ReportLink> reports = new ArrayList<>();
    private LinearLayout visitsContainer;
    private LinearLayout reportsContainer;
    private final JobWorkRepository jobs = new JobWorkRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jobwork_detail);
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, "Job Work is unavailable offline.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;
        container = findViewById(R.id.detailContainer);
        userName = getIntent().getStringExtra("USER_NAME");
        jobId = getIntent().getStringExtra("JOB_ID");
        if (jobId == null || jobId.trim().isEmpty()) {
            finish();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (jobId == null || jobId.trim().isEmpty()) return;
        reload();
    }

    private void reload() {
        jobs.getJob(jobId, (loadedJob, jobError) -> runOnUiThread(() -> {
            if (loadedJob == null) {
                Toast.makeText(this, "Job not found.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
                job = loadedJob;
                renderShell();
                jobs.listVisits(jobId, (visits, visitError) -> runOnUiThread(() -> renderVisits(visits)));
                jobs.listReports(jobId, (loaded, reportError) -> runOnUiThread(() -> {
                    reports = loaded == null ? new ArrayList<>() : loaded;
                    renderReports(reports);
                }));
            }));
        }));
    }

    private void renderShell() {
        container.removeAllViews();
        addText("Service job", 20, true);
        addText("Customer\n" + blank(job.text("CustomerName"), "Not recorded"), 16, false);
        addText("Address\n" + blank(job.text("Address"), "Not recorded"), 16, false);
        addText("Contact\n" + blank(job.text("CustomerContact"), "Not recorded"), 16, false);
        addText("Email\n" + blank(job.text("CustomerEmail"), "Not recorded"), 16, false);
        addText("Issue\n" + blank(job.text("IssueDetails"), "Not recorded"), 16, false);
        addText("Assigned technician\n" + blank(job.text("AssignedTech"), "Not recorded"), 16, false);
        addText("Status\n" + job.statusLabel() + (job.legacyWithoutStatus() ? " (legacy)" : ""), 16, false);
        addText("Created\n" + blank(JobWorkRepository.formatWhen(job.data.get("CreatedAt")), "Not recorded"), 16, false);
        String lastVisit = JobWorkRepository.formatWhen(job.data.get("lastVisitAt"));
        addText("Last visit\n" + blank(lastVisit, "None yet"), 16, false);
        addText("Total visits\n" + job.visitCount(), 16, false);
        int year = job.displayYear();
        if (year > 0) addText("Year\n" + year, 16, false);
        String setup = job.text("SetupDate");
        if (!setup.isEmpty()) addText("Initial setup\n" + setup, 16, false);
        String payment = job.text("PaymentAmount");
        if (!payment.isEmpty()) {
            String method = job.text("PaymentMethod");
            addText("Payment\n" + payment + (method.isEmpty() ? "" : " (" + method + ")"), 16, false);
        }
        String followUpDate = job.text("FollowUpDate");
        if (!followUpDate.isEmpty()) addText("Follow-up date\n" + followUpDate, 16, false);
        String notes = job.text("completionNotes");
        if (!notes.isEmpty()) addText("Completion notes\n" + notes, 16, false);
        String completed = JobWorkRepository.formatWhen(job.data.get("completedAt"));
        if (!completed.isEmpty() || !job.text("completedByName").isEmpty()) {
            addText("Completed\n" + blank(completed, "Not recorded") + "\n" + blank(job.text("completedByName"), ""), 16, false);
        }

        boolean admin = SessionManager.isAdmin(this);
        boolean canUpdate = admin || job.assignedTo(SessionManager.getContractKey(this));
        boolean completedJob = "completed".equals(job.normalizedStatus());
        if (canUpdate) {
            addButton("Create report", this::createReport);
            addButton("View reports", this::scrollReports);
            addButton("View visit history", this::scrollVisits);
        }
        if (canUpdate && !completedJob) {
            addButton("Mark follow up", () -> confirmVisit("follow_up", null));
            addButton("Mark complete", this::confirmComplete);
        }
        if (admin && completedJob) addButton("Reopen job", () -> confirmVisit("reopened", null));
        addButton("Browse historical reports", this::browseHistoricalReports);
        addButton("Open map", this::openMap);
        addButton("Email", this::emailCustomer);
        if (canUpdate) {
            addButton("Save address", this::editAddress);
            addButton("Set follow-up date", this::editFollowUpDate);
            addButton("Change email", this::editEmail);
        }
        if (admin) addButton("Change technician", this::changeTechnician);
        if (admin) addButton("Delete job", this::confirmDelete);
        addButton("Back", this::finish);

        addText("Visit history", 18, true);
        visitsContainer = section();
        addText("Linked reports", 18, true);
        reportsContainer = section();
    }

    private LinearLayout section() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        container.addView(section);
        return section;
    }

    private void renderVisits(List<VisitRecord> visits) {
        if (visitsContainer == null) return;
        visitsContainer.removeAllViews();
        if (visits == null || visits.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No visits yet.");
            visitsContainer.addView(empty);
            return;
        }
        for (VisitRecord visit : visits) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 12, 0, 12);
            TextView body = new TextView(this);
            String when = JobWorkRepository.formatWhen(visit.when);
            body.setText((when.isEmpty() ? "Undated" : when) + "\n" + visit.techName + "\n" + visit.actionLabel()
                    + (visit.notes.isEmpty() ? "" : "\n" + visit.notes));
            row.addView(body);
            if (!visit.storagePath.isEmpty()) {
                Button open = new Button(this);
                open.setText(visit.fileName.isEmpty() ? "Report" : visit.fileName);
                open.setOnClickListener(v -> showReportActions(visit.storagePath, visit.fileName, when));
                row.addView(open);
            }
            visitsContainer.addView(row);
        }
    }

    private void renderReports(List<ReportLink> loaded) {
        if (reportsContainer == null) return;
        reportsContainer.removeAllViews();
        if (loaded == null || loaded.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No reports linked to this job yet. Older reports remain in Job Work reports.");
            reportsContainer.addView(empty);
            return;
        }
        for (ReportLink report : loaded) {
            Button open = new Button(this);
            String when = JobWorkRepository.formatWhen(report.createdAt);
            String label = report.fileName.isEmpty() ? "Report" : report.fileName;
            if (!when.isEmpty()) label = label + "\n" + when;
            open.setText(label);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = 8;
            open.setLayoutParams(params);
            open.setOnClickListener(v -> showReportActions(report.storagePath, report.fileName, when));
            reportsContainer.addView(open);
        }
    }

    private void showReportActions(String storagePath, String fileName, String when) {
        String title = fileName == null || fileName.trim().isEmpty() ? "Report" : fileName.trim();
        String message = when == null || when.trim().isEmpty() ? "Choose an action" : when;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("View", (d, w) -> viewReport(storagePath, fileName))
                .setNegativeButton("Share", (d, w) -> shareReport(storagePath, fileName))
                .setNeutralButton("Download", (d, w) -> downloadReport(storagePath, fileName))
                .show();
    }

    private void confirmComplete() {
        EditText input = new EditText(this);
        input.setHint("Completion notes");
        input.setMinLines(3);
        new AlertDialog.Builder(this)
                .setTitle("Complete job")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Complete job", (dialog, which) -> confirmVisit("completed", input.getText().toString().trim()))
                .show();
    }

    private void confirmVisit(String action, String notes) {
        if (busy || job == null) return;
        String visitId = visitIdFor(action);
        busy = true;
        String actingName = SessionManager.getName(this);
        if (actingName == null || actingName.trim().isEmpty()) actingName = userName;
        String actingKey = SessionManager.getContractKey(this);
        ReportLink report = latestUnlinkedReport();
        String reportId = report == null ? null : report.id;
        String storagePath = report == null ? null : report.storagePath;
        String fileName = report == null ? null : report.fileName;
        jobs.applyVisit(job.id, visitId, action, notes, actingName, actingKey, reportId, storagePath, fileName,
                new JobWorkRepository.DoneCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            busy = false;
                            clearVisitId(action);
                            reload();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            busy = false;
                            String message = error == null || error.getMessage() == null
                                    ? "Could not update the job." : error.getMessage();
                            Toast.makeText(JobWorkDetailActivity.this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private ReportLink latestUnlinkedReport() {
        if (reports == null) return null;
        for (ReportLink report : reports) {
            if (report.visitId == null || report.visitId.trim().isEmpty()) return report;
        }
        return null;
    }

    private String visitIdFor(String action) {
        if ("completed".equals(action)) {
            if (completeVisitId == null) completeVisitId = jobs.newVisitId();
            return completeVisitId;
        }
        if ("reopened".equals(action)) {
            if (reopenVisitId == null) reopenVisitId = jobs.newVisitId();
            return reopenVisitId;
        }
        if (followUpVisitId == null) followUpVisitId = jobs.newVisitId();
        return followUpVisitId;
    }

    private void clearVisitId(String action) {
        if ("completed".equals(action)) completeVisitId = null;
        else if ("reopened".equals(action)) reopenVisitId = null;
        else followUpVisitId = null;
    }

    static void openCreateReport(android.content.Context context, String userName, String jobId,
                                 String customerName, String address) {
        Intent intent = new Intent(context, ReportActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra("COMPANY_NAME", customerName == null ? "" : customerName);
        intent.putExtra("ADDRESS", address == null ? "" : address);
        intent.putExtra("ROUTE_MODE", "job");
        intent.putExtra("JOBWORK_JOB_ID", jobId);
        context.startActivity(intent);
    }

    private void createReport() {
        if (job == null) return;
        openCreateReport(this, userName, job.id, job.text("CustomerName"), job.text("Address"));
    }

    private void browseHistoricalReports() {
        Intent intent = new Intent(this, CloudStorageBrowserActivity.class);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_FIXED_ROOT);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_PATH, "JobWorkReports");
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_TITLE, "Job Work reports");
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, userName);
        startActivity(intent);
    }

    private void openMap() {
        String address = job == null ? "" : job.text("Address");
        if (address.isEmpty()) {
            Toast.makeText(this, "No address on this job.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
        mapIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapIntent);
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    private void confirmDelete() {
        if (job == null || !SessionManager.isAdmin(this)) return;
        String customer = job.text("CustomerName");
        String address = job.text("Address");
        String message = (customer.isEmpty() ? "Unnamed job" : customer) + "\n"
                + (address.isEmpty() ? "No address yet" : address) + "\n\n"
                + "This will remove this Job Work record.";
        new AlertDialog.Builder(this)
                .setTitle("Delete Job?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> jobs.deleteJob(job.id, new JobWorkRepository.DoneCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> finish());
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> Toast.makeText(JobWorkDetailActivity.this,
                                error == null || error.getMessage() == null ? "Could not delete the job." : error.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                }))
                .show();
    }

    private void editAddress() {
        EditText input = new EditText(this);
        input.setText(job.text("Address"));
        new AlertDialog.Builder(this)
                .setTitle("Address")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) ->
                        jobs.saveAddress(job.id, input.getText().toString(), done("Address saved")))
                .show();
    }

    private void editFollowUpDate() {
        if (job == null) return;
        JobWorkFollowUpDates.show(this, job.text("FollowUpDate"), normalized ->
                jobs.saveFollowUpDate(job.id, normalized, done("Follow-up date saved")));
    }

    private void emailCustomer() {
        String email = job == null ? "" : job.text("CustomerEmail").trim();
        if (!email.contains("@")) {
            Toast.makeText(this, "This job has no email address.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + email));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No email app is available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void editEmail() {
        EditText input = new EditText(this);
        input.setText(job.text("CustomerEmail"));
        new AlertDialog.Builder(this)
                .setTitle("Email")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) ->
                        jobs.saveEmail(job.id, input.getText().toString().trim(), done("Email saved")))
                .show();
    }

    private void changeTechnician() {
        StaffDirectory.fetchOwnerOptions(this, options -> runOnUiThread(() -> {
            List<StaffDirectory.OwnerOption> people = options == null ? new ArrayList<>() : options;
            if (people.isEmpty()) {
                Toast.makeText(this, "No technicians found.", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[people.size()];
            for (int i = 0; i < people.size(); i++) {
                labels[i] = StaffDirectory.capitalizeContractKey(people.get(i).ownerKey);
            }
            new AlertDialog.Builder(this)
                    .setTitle("Change technician")
                    .setItems(labels, (dialog, which) -> {
                        StaffDirectory.OwnerOption option = people.get(which);
                        jobs.reassign(job.id, option.ownerKey, done("Technician updated"));
                    })
                    .show();
        }));
    }

    private void scrollReports() {
        if (reportsContainer != null) reportsContainer.requestFocus();
    }

    private void scrollVisits() {
        if (visitsContainer != null) visitsContainer.requestFocus();
    }

    private void viewReport(String storagePath, String fileName) {
        String path = normalizePath(storagePath);
        if (path.isEmpty()) {
            Toast.makeText(this, "This report has no file to open.", Toast.LENGTH_LONG).show();
            return;
        }
        File local = cacheFile(fileName);
        if (local == null) return;
        Toast.makeText(this, "Opening report...", Toast.LENGTH_SHORT).show();
        FirebaseStorage.getInstance().getReference().child(path).getFile(local)
                .addOnSuccessListener(task -> {
                    Intent intent = new Intent(this, ReportPreviewActivity.class);
                    intent.putExtra(ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, local.getAbsolutePath());
                    intent.putExtra(ReportPreviewActivity.EXTRA_VIEW_ONLY, true);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> failFile(local, e, "Could not open the report."));
    }

    private void shareReport(String storagePath, String fileName) {
        String path = normalizePath(storagePath);
        if (path.isEmpty()) {
            Toast.makeText(this, "This report has no file to share.", Toast.LENGTH_LONG).show();
            return;
        }
        File local = cacheFile(fileName);
        if (local == null) return;
        Toast.makeText(this, "Preparing report...", Toast.LENGTH_SHORT).show();
        FirebaseStorage.getInstance().getReference().child(path).getFile(local)
                .addOnSuccessListener(task -> {
                    Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", local);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/pdf");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(Intent.createChooser(share, "Share report"));
                    } catch (Exception e) {
                        Toast.makeText(this, "No application available to share the report.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> failFile(local, e, "Could not share the report."));
    }

    private void downloadReport(String storagePath, String fileName) {
        String path = normalizePath(storagePath);
        if (path.isEmpty()) {
            Toast.makeText(this, "This report has no file to download.", Toast.LENGTH_LONG).show();
            return;
        }
        File out = reportsFolderFile(fileName);
        if (out == null) return;
        Toast.makeText(this, "Downloading report...", Toast.LENGTH_SHORT).show();
        FirebaseStorage.getInstance().getReference().child(path).getFile(out)
                .addOnSuccessListener(task -> Toast.makeText(this,
                        "Saved to " + out.getAbsolutePath(), Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> failFile(out, e, "Could not download the report."));
    }

    private File reportsFolderFile(String fileName) {
        File root = getExternalFilesDir(null);
        if (root == null) root = getFilesDir();
        File dir = new File(root, TenantBranding.reportsFolderName(this));
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "Could not create the reports folder.", Toast.LENGTH_LONG).show();
            return null;
        }
        String safe = safeFileName(fileName);
        File out = new File(dir, safe);
        if (!out.exists()) return out;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    private File cacheFile(String fileName) {
        File dir = new File(getCacheDir(), "jobwork_reports");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "Could not prepare the report.", Toast.LENGTH_LONG).show();
            return null;
        }
        return new File(dir, System.currentTimeMillis() + "_" + safeFileName(fileName));
    }

    private void failFile(File file, Exception error, String fallback) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
        String detail = error == null || error.getMessage() == null ? "" : " " + error.getMessage();
        Toast.makeText(this, fallback + detail, Toast.LENGTH_LONG).show();
    }

    private static String normalizePath(String storagePath) {
        String path = storagePath == null ? "" : storagePath.trim();
        while (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("gs://")) {
            int slash = path.indexOf('/', 5);
            path = slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : "";
        }
        return path;
    }

    private static String safeFileName(String fileName) {
        String safe = fileName == null || fileName.trim().isEmpty() ? "report.pdf" : fileName.trim();
        int slash = Math.max(safe.lastIndexOf('/'), safe.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < safe.length()) safe = safe.substring(slash + 1);
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!safe.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) safe = safe + ".pdf";
        return safe;
    }

    private void addText(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setPadding(0, 8, 0, 8);
        container.addView(text);
    }

    private void addButton(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        container.addView(button);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private JobWorkRepository.DoneCallback done(String success) {
        return new JobWorkRepository.DoneCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(JobWorkDetailActivity.this, success, Toast.LENGTH_SHORT).show();
                    reload();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(JobWorkDetailActivity.this,
                        error == null || error.getMessage() == null ? "Could not save." : error.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        };
    }
}
