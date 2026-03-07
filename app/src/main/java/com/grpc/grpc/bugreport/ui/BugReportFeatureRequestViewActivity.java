package com.grpc.grpc.bugreport.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.messaging.NotificationUtils;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Super_admin: view all bug reports and feature requests; set cost, days to complete, status.
 * Admin: read-only view of all submissions (can see cost/days once set by super_admin).
 */
public class BugReportFeatureRequestViewActivity extends AppCompatActivity {

    public static final String EXTRA_USER_NAME = "USER_NAME";

    private FirebaseFirestore db;
    private List<Map<String, Object>> allItems = new ArrayList<>();
    private LinearLayout itemsContainer;
    private EditText searchBar;
    private TextView statsText;
    private boolean isSuperAdmin;

    private static String safeStr(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static double toDouble(Object o, double def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long toLong(Object o, long def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String formatTs(Object ts) {
        if (ts instanceof Timestamp) {
            try {
                Date d = ((Timestamp) ts).toDate();
                return new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(d);
            } catch (Exception ignored) {}
        }
        return safeStr(ts);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bug_report_feature_request_view);

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.canBugReport) {
                Toast.makeText(this, "Access denied. Bug report requires bugReport permission.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            initAndLoad();
        }));
    }

    private void initAndLoad() {
        isSuperAdmin = SessionManager.isSuperAdmin(this);

        db = FirebaseHelper.getFirestore();
        itemsContainer = findViewById(R.id.itemsContainer);
        searchBar = findViewById(R.id.searchBar);
        statsText = findViewById(R.id.statsText);
        Button backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
        });

        loadItems();
    }

    private void loadItems() {
        db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST)
                .orderBy("submittedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Failed to load: " + (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    allItems.clear();
                    if (task.getResult() != null) {
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> item = doc.getData();
                            if (item != null) {
                                item.put("documentId", doc.getId());
                                allItems.add(item);
                            }
                        }
                    }
                    applyFilter();
                });
    }

    private void applyFilter() {
        String q = searchBar.getText() != null ? searchBar.getText().toString().toLowerCase(Locale.getDefault()).trim() : "";
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : allItems) {
            if (q.isEmpty() || matches(item, q)) filtered.add(item);
        }
        int open = 0;
        for (Map<String, Object> item : allItems) {
            if ("open".equalsIgnoreCase(safeStr(item.get("status")))) open++;
        }
        if (statsText != null) statsText.setText("Open: " + open + " | Total: " + allItems.size());
        displayItems(filtered);
    }

    private boolean matches(Map<String, Object> item, String q) {
        if (q.isEmpty()) return true;
        String title = safeStr(item.get("title")).toLowerCase(Locale.getDefault());
        String desc = safeStr(item.get("description")).toLowerCase(Locale.getDefault());
        String by = safeStr(item.get("submittedBy")).toLowerCase(Locale.getDefault());
        String type = safeStr(item.get("type")).toLowerCase(Locale.getDefault());
        return title.contains(q) || desc.contains(q) || by.contains(q) || type.contains(q);
    }

    private void displayItems(List<Map<String, Object>> list) {
        itemsContainer.removeAllViews();
        for (Map<String, Object> item : list) {
            addItemView(item);
        }
    }

    private void addItemView(Map<String, Object> item) {
        String docId = safeStr(item.get("documentId"));
        String type = safeStr(item.get("type"));
        String title = safeStr(item.get("title"));
        String desc = safeStr(item.get("description"));
        String submittedBy = safeStr(item.get("submittedBy"));
        String status = safeStr(item.get("status"));
        if (status.isEmpty()) status = "open";
        String submittedAt = formatTs(item.get("submittedAt"));
        double cost = toDouble(item.get("cost"), 0);
        long daysToComplete = toLong(item.get("daysToComplete"), 0);
        String currency = safeStr(item.get("costCurrency"));
        if (currency.isEmpty()) currency = "£";
        boolean isFeature = "feature".equalsIgnoreCase(type);
        double ratePerHour = toDouble(item.get("ratePerHour"), 0);
        double hours = toDouble(item.get("hours"), 0);
        long estimatedDays = toLong(item.get("estimatedDays"), 0);
        Boolean clientAgreed = item.get("clientAgreed") instanceof Boolean ? (Boolean) item.get("clientAgreed") : null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16, 16, 16, 16);
        box.setBackgroundResource(R.drawable.surface_frame);

        StringBuilder text = new StringBuilder();
        text.append("[").append(type.isEmpty() ? "?" : type).append("] ").append(title.isEmpty() ? "No title" : title).append("\n");
        text.append("By: ").append(submittedBy.isEmpty() ? "—" : submittedBy).append(" • ").append(submittedAt).append("\n");
        text.append("Status: ").append(status).append("\n");
        if (isFeature && (ratePerHour > 0 || cost > 0)) {
            text.append("Quote: ").append(currency).append(String.format(Locale.getDefault(), "%.2f", cost > 0 ? cost : ratePerHour * hours)).append(" • ");
            if (estimatedDays > 0 || daysToComplete > 0) text.append((estimatedDays > 0 ? estimatedDays : daysToComplete)).append(" days • ");
            if (clientAgreed == null) text.append("Client: Pending\n");
            else text.append("Client: ").append(Boolean.TRUE.equals(clientAgreed) ? "Agreed" : "Disagreed").append("\n");
        } else {
            if (cost > 0) text.append("Cost: ").append(currency).append(String.format(Locale.getDefault(), "%.2f", cost)).append("\n");
            if (daysToComplete > 0) text.append("Days to complete: ").append(daysToComplete).append("\n");
        }
        if (!desc.isEmpty()) text.append(desc.length() > 120 ? desc.substring(0, 120) + "…" : desc);

        TextView tv = new TextView(this);
        tv.setText(text.toString());
        box.addView(tv);

        box.setOnClickListener(v -> showDetailDialog(item, docId));
        box.setOnLongClickListener(v -> {
            if (isSuperAdmin) {
                showEditBugOrFeatureDialog(docId, item);
                return true;
            }
            return false;
        });
        itemsContainer.addView(box);
    }

    private void showDetailDialog(Map<String, Object> item, String documentId) {
        String type = safeStr(item.get("type"));
        String title = safeStr(item.get("title"));
        String desc = safeStr(item.get("description"));
        String submittedBy = safeStr(item.get("submittedBy"));
        String submittedByUid = safeStr(item.get("submittedByUid"));
        String status = safeStr(item.get("status"));
        if (status.isEmpty()) status = "open";
        String submittedAt = formatTs(item.get("submittedAt"));
        double cost = toDouble(item.get("cost"), 0);
        long daysToComplete = toLong(item.get("daysToComplete"), 0);
        String currency = safeStr(item.get("costCurrency"));
        if (currency.isEmpty()) currency = "£";
        String superNotes = safeStr(item.get("superAdminNotes"));
        boolean isFeature = "feature".equalsIgnoreCase(type);
        double ratePerHour = toDouble(item.get("ratePerHour"), 0);
        double hours = toDouble(item.get("hours"), 0);
        double additionalCost = toDouble(item.get("additionalCost"), 0);
        long estimatedDays = toLong(item.get("estimatedDays"), 0);
        Boolean clientAgreed = item.get("clientAgreed") instanceof Boolean ? (Boolean) item.get("clientAgreed") : null;
        String clientRespondedAt = formatTs(item.get("clientRespondedAt"));

        StringBuilder msg = new StringBuilder();
        msg.append("Type: ").append(type).append("\n\n");
        msg.append("Title: ").append(title).append("\n\n");
        msg.append("Description:\n").append(desc.isEmpty() ? "—" : desc).append("\n\n");
        msg.append("Submitted by: ").append(submittedBy).append("\n");
        msg.append("Submitted at: ").append(submittedAt).append("\n\n");
        msg.append("Status: ").append(status).append("\n");
        if (isFeature && (ratePerHour > 0 || cost > 0)) {
            msg.append("\n--- Quote ---\n");
            if (ratePerHour > 0) msg.append("Rate: ").append(currency).append(String.format(Locale.getDefault(), "%.0f", ratePerHour)).append("/hr\n");
            if (hours > 0) msg.append("Hours: ").append(String.format(Locale.getDefault(), "%.1f", hours)).append("\n");
            if (additionalCost > 0) msg.append("Additional (complex): ").append(currency).append(String.format(Locale.getDefault(), "%.2f", additionalCost)).append("\n");
            if (cost > 0) msg.append("Total: ").append(currency).append(String.format(Locale.getDefault(), "%.2f", cost)).append("\n");
            if (estimatedDays > 0 || daysToComplete > 0) msg.append("Estimated days: ").append(estimatedDays > 0 ? estimatedDays : daysToComplete).append("\n");
            if (clientAgreed != null) {
                msg.append("\nYour response: ").append(Boolean.TRUE.equals(clientAgreed) ? "Agreed" : "Disagreed");
                if (!clientRespondedAt.isEmpty()) msg.append(" (").append(clientRespondedAt).append(")");
                msg.append("\n");
            }
        } else {
            if (cost > 0) msg.append("Cost: ").append(currency).append(String.format(Locale.getDefault(), "%.2f", cost)).append("\n");
            if (daysToComplete > 0) msg.append("Days to complete: ").append(daysToComplete).append("\n");
        }
        if (!superNotes.isEmpty()) msg.append("\nNotes: ").append(superNotes);

        boolean isSubmitter = submittedByUid.equals(SessionManager.getStaffId(this)) || submittedBy.equalsIgnoreCase(SessionManager.getName(this));
        boolean showAgreeDisagree = !isSuperAdmin && isSubmitter && isFeature && (cost > 0 || ratePerHour > 0) && clientAgreed == null;

        if (isSuperAdmin) {
            new AlertDialog.Builder(this)
                    .setTitle(title.isEmpty() ? "Bug / Feature" : title)
                    .setMessage(msg.toString())
                    .setItems(new CharSequence[]{
                            "Update (cost & estimated time)",
                            "Update status",
                            "Mark complete (notify submitting admin)",
                            "Delete (remove from Firebase)"
                    }, (dialog, which) -> {
                        if (which == 0) showSetCostAndDaysDialog(documentId, item);
                        else if (which == 1) showStatusDialog(documentId, item);
                        else if (which == 2) setCompleteAndNotify(documentId, item);
                        else if (which == 3) confirmDelete(documentId, item);
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } else if (showAgreeDisagree) {
            new AlertDialog.Builder(this)
                    .setTitle(title.isEmpty() ? "Feature quote" : title)
                    .setMessage(msg.toString())
                    .setPositiveButton("Agree", (d, w) -> saveClientResponse(documentId, true))
                    .setNegativeButton("Disagree", (d, w) -> saveClientResponse(documentId, false))
                    .setNeutralButton("Close", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(title.isEmpty() ? "Bug / Feature" : title)
                    .setMessage(msg.toString())
                    .setNegativeButton("Close", null)
                    .show();
        }
    }

    private void saveClientResponse(String documentId, boolean agreed) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("clientAgreed", agreed);
        updates.put("clientRespondedAt", Timestamp.now());
        db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, agreed ? "You agreed to the quote." : "You disagreed.", Toast.LENGTH_SHORT).show();
                    loadItems();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
    }

    /** Super_admin only: long-press to edit title, description, and type. */
    private void showEditBugOrFeatureDialog(String documentId, Map<String, Object> item) {
        String title = safeStr(item.get("title"));
        String desc = safeStr(item.get("description"));
        String type = safeStr(item.get("type"));
        int typeIndex = "feature".equalsIgnoreCase(type) ? 1 : 0;

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextView typeLabel = new TextView(this);
        typeLabel.setText("Type");
        layout.addView(typeLabel);
        Spinner typeSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(this,
                R.array.bug_report_feature_request_types, android.R.layout.simple_spinner_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(typeIndex);
        layout.addView(typeSpinner);

        TextView titleLabel = new TextView(this);
        titleLabel.setText("Title");
        titleLabel.setPadding(0, 16, 0, 0);
        layout.addView(titleLabel);
        EditText titleEdit = new EditText(this);
        titleEdit.setHint("Title");
        titleEdit.setText(title);
        titleEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        layout.addView(titleEdit);

        TextView descLabel = new TextView(this);
        descLabel.setText("Description");
        descLabel.setPadding(0, 16, 0, 0);
        layout.addView(descLabel);
        EditText descEdit = new EditText(this);
        descEdit.setHint("Description");
        descEdit.setText(desc);
        descEdit.setMinLines(4);
        descEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        layout.addView(descEdit);

        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("Edit bug / feature (super_admin)")
                .setView(scroll)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = titleEdit.getText() != null ? titleEdit.getText().toString().trim() : "";
                    String newDesc = descEdit.getText() != null ? descEdit.getText().toString().trim() : "";
                    if (newTitle.isEmpty()) {
                        Toast.makeText(this, "Title is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int pos = typeSpinner.getSelectedItemPosition();
                    String newType = (pos == 0) ? "bug" : "feature";
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("title", newTitle);
                    updates.put("description", newDesc);
                    updates.put("type", newType);
                    updates.put("updatedAt", Timestamp.now());
                    db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show();
                                loadItems();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(String documentId, Map<String, Object> item) {
        String title = safeStr(item.get("title"));
        new AlertDialog.Builder(this)
                .setTitle("Delete?")
                .setMessage("Remove \"" + (title.isEmpty() ? "this item" : title) + "\" from Firebase? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteItem(documentId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItem(String documentId) {
        db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show();
                    loadItems();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
    }

    private void setCompleteAndNotify(String documentId, Map<String, Object> item) {
        String title = safeStr(item.get("title"));
        String submittedBy = safeStr(item.get("submittedBy"));
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "done");
        updates.put("updatedAt", Timestamp.now());
        db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (!submittedBy.isEmpty()) {
                        String notifDocId = "bugreport_done_" + documentId + "_" + System.currentTimeMillis();
                        String notifTitle = "Bug report / Feature request completed";
                        String body = title.isEmpty() ? "Your submission has been marked complete." : "\"" + title + "\" has been marked complete.";
                        Map<String, Object> data = new HashMap<>();
                        data.put("documentId", documentId);
                        data.put("type", "bugreport_complete");
                        NotificationUtils.writeInAppNotification(submittedBy, notifDocId, notifTitle, body, "bugreport_complete", data);
                    }
                    Toast.makeText(this, "Marked complete. Submitting admin has been notified.", Toast.LENGTH_SHORT).show();
                    loadItems();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
    }

    private void showSetCostAndDaysDialog(String documentId, Map<String, Object> item) {
        String type = safeStr(item.get("type"));
        if ("feature".equalsIgnoreCase(type)) {
            showFeatureQuoteDialog(documentId, item);
            return;
        }
        // Bug: simple cost and days
        double currentCost = toDouble(item.get("cost"), 0);
        long currentDays = toLong(item.get("daysToComplete"), 0);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextView costLabel = new TextView(this);
        costLabel.setText("Cost (e.g. 150.00)");
        layout.addView(costLabel);
        EditText costEdit = new EditText(this);
        costEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        costEdit.setHint("0");
        if (currentCost > 0) costEdit.setText(String.format(Locale.getDefault(), "%.2f", currentCost));
        layout.addView(costEdit);

        TextView daysLabel = new TextView(this);
        daysLabel.setText("Estimated time (days to complete)");
        daysLabel.setPadding(0, 24, 0, 0);
        layout.addView(daysLabel);
        EditText daysEdit = new EditText(this);
        daysEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        daysEdit.setHint("0");
        if (currentDays > 0) daysEdit.setText(String.valueOf(currentDays));
        layout.addView(daysEdit);

        TextView notesLabel = new TextView(this);
        notesLabel.setText("Notes (optional)");
        notesLabel.setPadding(0, 24, 0, 0);
        layout.addView(notesLabel);
        EditText notesEdit = new EditText(this);
        notesEdit.setMinLines(2);
        notesEdit.setHint("Internal notes");
        String existingNotes = safeStr(item.get("superAdminNotes"));
        if (!existingNotes.isEmpty()) notesEdit.setText(existingNotes);
        layout.addView(notesEdit);

        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("Update cost & estimated time")
                .setView(scroll)
                .setPositiveButton("Save", (dialog, which) -> saveBugCostAndDays(documentId, item, costEdit, daysEdit, notesEdit))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Feature only: rate per hour (default 75), hours (default 4), optional complex surcharge, estimated days. Updates client and notifies. */
    private void showFeatureQuoteDialog(String documentId, Map<String, Object> item) {
        double ratePerHour = toDouble(item.get("ratePerHour"), 75);
        double hours = toDouble(item.get("hours"), 4);
        double additionalCost = toDouble(item.get("additionalCost"), 0);
        long estimatedDays = toLong(item.get("estimatedDays"), 0);
        String c = safeStr(item.get("costCurrency"));
        final String currency = c.isEmpty() ? "£" : c;

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextView rateLabel = new TextView(this);
        rateLabel.setText("Rate per hour (" + currency + ")");
        layout.addView(rateLabel);
        EditText rateEdit = new EditText(this);
        rateEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rateEdit.setHint("75");
        rateEdit.setText(String.format(Locale.getDefault(), "%.0f", ratePerHour));
        layout.addView(rateEdit);

        TextView hoursLabel = new TextView(this);
        hoursLabel.setText("Hours (e.g. 4 for evening)");
        hoursLabel.setPadding(0, 16, 0, 0);
        layout.addView(hoursLabel);
        EditText hoursEdit = new EditText(this);
        hoursEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hoursEdit.setHint("4");
        hoursEdit.setText(String.format(Locale.getDefault(), "%.1f", hours));
        layout.addView(hoursEdit);

        TextView addLabel = new TextView(this);
        addLabel.setText("Additional (complex work, " + currency + ")");
        addLabel.setPadding(0, 16, 0, 0);
        layout.addView(addLabel);
        EditText addEdit = new EditText(this);
        addEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        addEdit.setHint("0");
        if (additionalCost > 0) addEdit.setText(String.format(Locale.getDefault(), "%.2f", additionalCost));
        layout.addView(addEdit);

        TextView daysLabel = new TextView(this);
        daysLabel.setText("Estimated days to complete");
        daysLabel.setPadding(0, 16, 0, 0);
        layout.addView(daysLabel);
        EditText daysEdit = new EditText(this);
        daysEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        daysEdit.setHint("0");
        if (estimatedDays > 0) daysEdit.setText(String.valueOf(estimatedDays));
        layout.addView(daysEdit);

        TextView totalPreview = new TextView(this);
        totalPreview.setPadding(0, 16, 0, 0);
        layout.addView(totalPreview);
        android.text.TextWatcher recalc = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateTotalPreview(rateEdit, hoursEdit, addEdit, totalPreview, currency); }
            @Override public void afterTextChanged(Editable s) { updateTotalPreview(rateEdit, hoursEdit, addEdit, totalPreview, currency); }
        };
        rateEdit.addTextChangedListener(recalc);
        hoursEdit.addTextChangedListener(recalc);
        addEdit.addTextChangedListener(recalc);
        updateTotalPreview(rateEdit, hoursEdit, addEdit, totalPreview, currency);

        TextView notesLabel = new TextView(this);
        notesLabel.setText("Notes (optional)");
        notesLabel.setPadding(0, 24, 0, 0);
        layout.addView(notesLabel);
        EditText notesEdit = new EditText(this);
        notesEdit.setMinLines(2);
        notesEdit.setHint("Internal notes");
        String existingNotes = safeStr(item.get("superAdminNotes"));
        if (!existingNotes.isEmpty()) notesEdit.setText(existingNotes);
        layout.addView(notesEdit);

        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("Feature quote (client will see and can agree/disagree)")
                .setView(scroll)
                .setPositiveButton("Save & notify client", (dialog, which) -> {
                    double r = toDoubleFromEdit(rateEdit.getText(), 75);
                    double h = toDoubleFromEdit(hoursEdit.getText(), 4);
                    double add = toDoubleFromEdit(addEdit.getText(), 0);
                    long d = toLongFromEdit(daysEdit.getText(), 0);
                    if (r < 0 || h < 0 || add < 0 || d < 0) {
                        Toast.makeText(this, "Invalid values.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double total = r * h + add;
                    String notes = notesEdit.getText() != null ? notesEdit.getText().toString().trim() : "";
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("ratePerHour", r);
                    updates.put("hours", h);
                    updates.put("additionalCost", add);
                    updates.put("cost", total);
                    updates.put("estimatedDays", d);
                    updates.put("daysToComplete", d);
                    updates.put("superAdminNotes", notes);
                    updates.put("updatedAt", Timestamp.now());
                    // Clear previous client response so they can respond to new quote
                    updates.put("clientAgreed", FieldValue.delete());
                    updates.put("clientRespondedAt", FieldValue.delete());
                    db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                String submittedBy = safeStr(item.get("submittedBy"));
                                String title = safeStr(item.get("title"));
                                String recipient = safeStr(item.get("submittedByUid"));
                                if (recipient.isEmpty()) recipient = submittedBy;
                                if (!recipient.isEmpty()) {
                                    String notifDocId = "bugreport_quote_" + documentId + "_" + System.currentTimeMillis();
                                    String notifTitle = "Feature quote ready";
                                    String body = title.isEmpty() ? "Your feature request has a quote." : "\"" + title + "\": " + currency + String.format(Locale.getDefault(), "%.2f", total) + " (" + (long) h + "h @ " + currency + (long) r + "/hr). Please view and agree or disagree.";
                                    Map<String, Object> data = new HashMap<>();
                                    data.put("documentId", documentId);
                                    data.put("type", "bugreport_quote");
                                    NotificationUtils.writeInAppNotification(recipient, notifDocId, notifTitle, body, "bugreport_quote", data);
                                }
                                Toast.makeText(this, "Saved. Client has been notified to view and agree/disagree.", Toast.LENGTH_SHORT).show();
                                loadItems();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void updateTotalPreview(EditText rateEdit, EditText hoursEdit, EditText addEdit, TextView totalPreview, String currency) {
        double r = toDoubleFromEdit(rateEdit != null && rateEdit.getText() != null ? rateEdit.getText().toString() : "", 75);
        double h = toDoubleFromEdit(hoursEdit != null && hoursEdit.getText() != null ? hoursEdit.getText().toString() : "", 4);
        double add = toDoubleFromEdit(addEdit != null && addEdit.getText() != null ? addEdit.getText().toString() : "", 0);
        double total = r * h + add;
        totalPreview.setText("Total: " + currency + String.format(Locale.getDefault(), "%.2f", total));
    }

    private static double toDoubleFromEdit(CharSequence s, double def) {
        if (s == null || s.toString().trim().isEmpty()) return def;
        try {
            return Double.parseDouble(s.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double toDoubleFromEdit(Editable s, double def) {
        return s != null ? toDoubleFromEdit((CharSequence) s, def) : def;
    }

    private static long toLongFromEdit(CharSequence s, long def) {
        if (s == null || s.toString().trim().isEmpty()) return def;
        try {
            return Long.parseLong(s.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long toLongFromEdit(Editable s, long def) {
        return s != null ? toLongFromEdit((CharSequence) s, def) : def;
    }

    private void saveBugCostAndDays(String documentId, Map<String, Object> item, EditText costEdit, EditText daysEdit, EditText notesEdit) {
        String costStr = costEdit.getText() != null ? costEdit.getText().toString().trim() : "";
        String daysStr = daysEdit.getText() != null ? daysEdit.getText().toString().trim() : "";
        String notes = notesEdit.getText() != null ? notesEdit.getText().toString().trim() : "";
        double costVal = 0;
        if (!costStr.isEmpty()) {
            try {
                costVal = Double.parseDouble(costStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid cost.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        long daysVal = 0;
        if (!daysStr.isEmpty()) {
            try {
                daysVal = Long.parseLong(daysStr);
                if (daysVal < 0) daysVal = 0;
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid days.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("cost", costVal);
        updates.put("daysToComplete", daysVal);
        updates.put("superAdminNotes", notes);
        updates.put("updatedAt", Timestamp.now());
        db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Saved. Submitter can view cost and days.", Toast.LENGTH_SHORT).show();
                    loadItems();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
    }

    private void showStatusDialog(String documentId, Map<String, Object> item) {
        String current = safeStr(item.get("status"));
        if (current.isEmpty()) current = "open";
        String[] options = new String[]{"open", "in_progress", "done"};
        int selected = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Update status")
                .setSingleChoiceItems(options, selected, null)
                .setPositiveButton("Save", (dialog, which) -> {
                    AlertDialog d = (AlertDialog) dialog;
                    int pos = d.getListView().getCheckedItemPosition();
                    if (pos < 0 || pos >= options.length) return;
                    String newStatus = options[pos];
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", newStatus);
                    updates.put("updatedAt", Timestamp.now());
                    db.collection(FirestorePaths.BUG_REPORT_FEATURE_REQUEST).document(documentId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                if ("done".equals(newStatus)) {
                                    String submittedBy = safeStr(item.get("submittedBy"));
                                    String title = safeStr(item.get("title"));
                                    if (!submittedBy.isEmpty()) {
                                        String notifDocId = "bugreport_done_" + documentId + "_" + System.currentTimeMillis();
                                        String notifTitle = "Bug report / Feature request completed";
                                        String body = title.isEmpty() ? "Your submission has been marked complete." : "\"" + title + "\" has been marked complete.";
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("documentId", documentId);
                                        data.put("type", "bugreport_complete");
                                        NotificationUtils.writeInAppNotification(submittedBy, notifDocId, notifTitle, body, "bugreport_complete", data);
                                    }
                                    Toast.makeText(this, "Status updated. Submitting admin has been notified.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Status updated.", Toast.LENGTH_SHORT).show();
                                }
                                loadItems();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
