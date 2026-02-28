/**
 * ReportAdapter.java
 *
 * This adapter is used for displaying a list of report files in a RecyclerView.
 * It binds the data from a list of report files and provides click and long-click
 * interactions for opening and managing the reports.
 *
 * Key Features:
 * - Displays the list of report files.
 * - Handles item click and long-click events through a listener interface.
 * - Simplifies the binding of data to the RecyclerView.
 */

package com.grpc.grpc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ReportAdapter.java
 *
 * This adapter is responsible for displaying a list of report files in a RecyclerView.
 * It binds report file data and provides click and long-click interactions for viewing,
 * sharing, renaming, or deleting reports.
 *
 * Features:
 * - Displays a list of stored report files
 * - Handles user interactions through click and long-click events
 * - Supports opening, sharing, renaming, and deleting reports
 * - Efficiently binds report data to the RecyclerView
 *
 * Author: GRPC
 */

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    // List of report files to display in the RecyclerView
    private final List<File> reportFiles;

    // Context for inflating views and handling click events
    private final Context context;

    // Listener for handling click and long-click actions on report items
    private final OnReportClickListener onReportClickListener;

    // Multi-select state (driven by Activities)
    private boolean selectionMode = false;
    private final Set<String> selectedPaths = new LinkedHashSet<>();

    /**
     * Interface definition for click actions on the report items.
     */
    public interface OnReportClickListener {
        /**
         * Triggered when a report item is clicked.
         * @param file The file associated with the clicked report.
         */
        void onReportClick(File file);

        /**
         * Triggered when a report item is long-clicked.
         * @param file The file associated with the long-clicked report.
         */
        void onReportLongClick(File file);
    }

    /**
     * Constructs the ReportAdapter with the provided context, list of report files,
     * and a click listener for handling item interactions.
     *
     * @param context The context from the calling activity.
     * @param reportFiles List of report files to be displayed.
     * @param listener The listener for handling click events on the report items.
     */
    public ReportAdapter(Context context, List<File> reportFiles, OnReportClickListener listener) {
        this.context = context;
        this.reportFiles = reportFiles;
        this.onReportClickListener = listener;
    }

    public void setSelectionMode(boolean enabled) {
        if (this.selectionMode == enabled) return;
        this.selectionMode = enabled;
        if (!enabled) selectedPaths.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public int getSelectedCount() {
        return selectedPaths.size();
    }

    public List<File> getSelectedFiles() {
        List<File> out = new ArrayList<>();
        for (File f : reportFiles) {
            if (f != null && selectedPaths.contains(f.getAbsolutePath())) out.add(f);
        }
        return out;
    }

    public void toggleSelected(File file) {
        if (file == null) return;
        String path = file.getAbsolutePath();
        if (selectedPaths.contains(path)) selectedPaths.remove(path);
        else selectedPaths.add(path);
        notifyDataSetChanged();
    }

    public void selectAllVisible() {
        for (File f : reportFiles) {
            if (f != null) selectedPaths.add(f.getAbsolutePath());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        if (selectedPaths.isEmpty()) return;
        selectedPaths.clear();
        notifyDataSetChanged();
    }

    /**
     * Inflates the view for each report item in the RecyclerView.
     *
     * @param parent The parent view group where the new view will be added.
     * @param viewType The type of view for different types of data (not used here).
     * @return A new instance of ReportViewHolder for binding data.
     */
    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_report_file, parent, false);
        return new ReportViewHolder(view);
    }

    /**
     * Binds the report data to the view holder.
     * Also sets up click and long-click listeners for each report item.
     *
     * @param holder The view holder that should be updated with data.
     * @param position The position of the item within the list.
     */
    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        // Get the report file at the specified position
        File reportFile = reportFiles.get(position);

        // Display the report name in the TextView
        holder.reportName.setText(reportFile.getName());

        boolean isSelected = reportFile != null && selectedPaths.contains(reportFile.getAbsolutePath());
        holder.selectCheckbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.selectCheckbox.setChecked(isSelected);
        holder.itemView.setActivated(isSelected);

        // Important: users often tap the checkbox itself when multi-select is active.
        // If we don't handle checkbox clicks, it can visually toggle without updating selectedPaths,
        // causing "multi-select deletes only 1" because only the long-pressed item is truly selected.
        holder.selectCheckbox.setOnClickListener(selectionMode
                ? v -> onReportClickListener.onReportClick(reportFile)
                : null);

        // Set click listener for opening the report
        holder.itemView.setOnClickListener(v -> onReportClickListener.onReportClick(reportFile));

        // Set long-click listener for additional options on the report item
        holder.itemView.setOnLongClickListener(v -> {
            onReportClickListener.onReportLongClick(reportFile);
            return true;
        });
    }

    /**
     * Returns the total number of items in the report list.
     *
     * @return The size of the report files list.
     */
    @Override
    public int getItemCount() {
        return reportFiles.size();
    }

    /**
     * ViewHolder class to represent the UI elements for a single report item.
     */
    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        // TextView for displaying the report name
        TextView reportName;
        CheckBox selectCheckbox;

        /**
         * Constructs the ViewHolder and initializes the view elements.
         *
         * @param itemView The view representing the individual item layout.
         */
        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            reportName = itemView.findViewById(R.id.reportName);
            selectCheckbox = itemView.findViewById(R.id.selectCheckbox);
        }
    }
}
