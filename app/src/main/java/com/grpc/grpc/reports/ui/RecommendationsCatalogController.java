package com.grpc.grpc.reports.ui;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;

import com.grpc.grpc.R;
import com.grpc.grpc.core.DictateEditText;
import com.grpc.grpc.reports.data.RecommendationsTemplatesLoader;
import com.grpc.grpc.reports.model.RecommendationTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional recommendations template dropdown. The text field stays fully editable;
 * users may ignore the dropdown and type or dictate their own recommendations.
 * Each template selection adds a numbered line: 1) … 2) … 3) …
 */
public class RecommendationsCatalogController {

    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\d+\\)\\s*(.*)$");

    private final List<RecommendationTemplate> templates = new ArrayList<>();
    private final int catalogBarRootId;
    private boolean suppressSelection = true;

    public RecommendationsCatalogController(@NonNull Activity activity,
                                            int catalogBarRootId,
                                            @NonNull DictateEditText targetField) {
        this.catalogBarRootId = catalogBarRootId;
        ensureFieldEditable(targetField);
        View barRoot = activity.findViewById(catalogBarRootId);
        Spinner spinner = barRoot != null ? barRoot.findViewById(R.id.recommendationsCatalogSpinner) : null;
        if (spinner == null) {
            if (barRoot != null) barRoot.setVisibility(View.GONE);
            return;
        }

        List<RecommendationTemplate> loaded = RecommendationsTemplatesLoader.loadAll(activity);
        if (loaded.isEmpty()) {
            barRoot.setVisibility(View.GONE);
            return;
        }
        templates.addAll(loaded);

        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.recommendations_catalog_prompt));
        for (RecommendationTemplate template : templates) {
            labels.add(template.getSpinnerLabel());
        }

        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSelection || position <= 0) return;
                applyTemplate(position - 1, targetField);
                resetSpinner(activity);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        suppressSelection = true;
        spinner.setSelection(0, false);
        suppressSelection = false;
    }

    private static void ensureFieldEditable(@NonNull DictateEditText targetField) {
        EditText edit = targetField.getEditText();
        if (edit == null) return;
        edit.setEnabled(true);
        edit.setFocusable(true);
        edit.setFocusableInTouchMode(true);
        edit.setClickable(true);
        edit.setLongClickable(true);
        if (edit.getInputType() == InputType.TYPE_NULL) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
    }

    private void applyTemplate(int index, @NonNull DictateEditText targetField) {
        if (index < 0 || index >= templates.size()) return;
        String addition = templates.get(index).getText().trim();
        if (addition.isEmpty()) return;
        EditText edit = targetField.getEditText();
        if (edit == null) return;

        List<String> items = parseItems(edit.getText() != null ? edit.getText().toString() : "");
        items.add(addition);
        edit.setText(formatNumbered(items));
    }

    /** Parse field into recommendation lines (numbered or plain). */
    @NonNull
    static List<String> parseItems(@NonNull String raw) {
        List<String> items = new ArrayList<>();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return items;
        }

        String[] lines = trimmed.split("\\r?\\n");
        boolean anyNumbered = false;
        for (String line : lines) {
            if (NUMBERED_LINE.matcher(line.trim()).matches()) {
                anyNumbered = true;
                break;
            }
        }

        if (!anyNumbered) {
            items.add(trimmed);
            return items;
        }

        StringBuilder current = null;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            Matcher m = NUMBERED_LINE.matcher(t);
            if (m.matches()) {
                if (current != null && current.length() > 0) {
                    items.add(current.toString().trim());
                }
                current = new StringBuilder(m.group(1).trim());
            } else if (current != null) {
                current.append(" ").append(t);
            }
        }
        if (current != null && current.length() > 0) {
            items.add(current.toString().trim());
        }
        return items;
    }

    @NonNull
    static String formatNumbered(@NonNull List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(i + 1).append(") ").append(items.get(i).trim());
        }
        return sb.toString();
    }

    private void resetSpinner(@NonNull Activity activity) {
        View barRoot = activity.findViewById(catalogBarRootId);
        if (barRoot == null) return;
        Spinner spinner = barRoot.findViewById(R.id.recommendationsCatalogSpinner);
        if (spinner == null) return;
        suppressSelection = true;
        spinner.setSelection(0, false);
        suppressSelection = false;
    }

    public void resetSpinner(@NonNull Activity activity, int catalogBarRootId) {
        resetSpinner(activity);
    }
}
