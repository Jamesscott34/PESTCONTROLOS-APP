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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.List;

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
 * Author: James Scott
 */

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    // List of report files to display in the RecyclerView
    private final List<File> reportFiles;

    // Context for inflating views and handling click events
    private final Context context;

    // Listener for handling click and long-click actions on report items
    private final OnReportClickListener onReportClickListener;

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
        // Inflate a simple list item layout for displaying the report name
        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
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

        /**
         * Constructs the ViewHolder and initializes the view elements.
         *
         * @param itemView The view representing the individual item layout.
         */
        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            // Reference the default TextView in the simple list item layout
            reportName = itemView.findViewById(android.R.id.text1);
        }
    }
}
