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
import com.grpc.grpc.reports.data.ServiceReportTemplatesLoader;
import com.grpc.grpc.reports.model.ServiceReportTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional service report template dropdown. The text field stays fully editable;
 * users may ignore the dropdown and type or dictate their own report.
 * Each template selection appends a paragraph; paragraphs are separated by a blank line ({@code \n\n}).
 */
public class ServiceReportCatalogController {

    private final List<ServiceReportTemplate> templates = new ArrayList<>();
    private boolean suppressSelection = true;

    public ServiceReportCatalogController(@NonNull Activity activity,
                                        int catalogBarRootId,
                                        int catalogSpinnerId,
                                        @NonNull DictateEditText targetField) {
        ensureFieldEditable(targetField);

        View barRoot = catalogBarRootId != 0 ? activity.findViewById(catalogBarRootId) : null;
        Spinner spinner = catalogBarRootId != 0 && barRoot != null
                ? barRoot.findViewById(R.id.serviceReportCatalogSpinner)
                : activity.findViewById(catalogSpinnerId);
        if (spinner == null) {
            if (barRoot != null) barRoot.setVisibility(View.GONE);
            return;
        }

        List<ServiceReportTemplate> loaded = ServiceReportTemplatesLoader.loadAll(activity);
        if (loaded == null || loaded.isEmpty()) {
            if (barRoot != null) barRoot.setVisibility(View.GONE);
            return;
        }
        templates.addAll(loaded);

        List<String> labels = new ArrayList<>();
        labels.add(activity.getString(R.string.service_report_catalog_prompt));
        for (ServiceReportTemplate template : templates) {
            labels.add(template.getSpinnerLabel());
        }

        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSelection || position <= 0) return;
                applyTemplate(position - 1, targetField);
                resetSpinnerSelection(spinner);
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
        String addition = templates.get(index).getText();
        if (addition == null || addition.trim().isEmpty()) return;
        EditText edit = targetField.getEditText();
        if (edit == null) return;

        List<String> blocks = parseBlocks(edit.getText() != null ? edit.getText().toString() : "");
        blocks.add(addition.trim());
        edit.setText(formatBlocks(blocks));
    }

    /** Split field into paragraphs separated by blank lines. */
    @NonNull
    static List<String> parseBlocks(@NonNull String raw) {
        List<String> blocks = new ArrayList<>();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return blocks;
        }
        for (String part : trimmed.split("\\n\\s*\\n")) {
            String block = part.trim();
            if (!block.isEmpty()) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    @NonNull
    static String formatBlocks(@NonNull List<String> blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(blocks.get(i).trim());
        }
        return sb.toString();
    }

    private void resetSpinnerSelection(@NonNull Spinner spinner) {
        suppressSelection = true;
        spinner.setSelection(0, false);
        suppressSelection = false;
    }

    public void resetSpinner(@NonNull Activity activity, int catalogBarRootId) {
        View barRoot = activity.findViewById(catalogBarRootId);
        if (barRoot == null) return;
        Spinner spinner = barRoot.findViewById(R.id.serviceReportCatalogSpinner);
        if (spinner == null) return;
        resetSpinnerSelection(spinner);
    }
}
