/**
 * WorkEventAdapter.java
 *
 * RecyclerView adapter for displaying work events in the calendar view.
 * Handles the display of events with their status, time, and actions.
 *
 * Author: GRPC
 * Company: [Company 1]
 * Version: 1.0
 * Last Updated: 2024
 */

package com.grpc.grpc.workview.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.workview.model.WorkEvent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class WorkEventAdapter extends RecyclerView.Adapter<WorkEventAdapter.WorkEventViewHolder> {

    private List<WorkEvent> eventsList;
    private OnEventClickListener listener;

    public interface OnEventClickListener {
        void onEventClick(WorkEvent event);
    }

    public WorkEventAdapter(List<WorkEvent> eventsList, OnEventClickListener listener) {
        this.eventsList = eventsList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public WorkEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_work_event, parent, false);
        return new WorkEventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkEventViewHolder holder, int position) {
        WorkEvent event = eventsList.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return eventsList.size();
    }

    public void updateEvents(List<WorkEvent> newEvents) {
        this.eventsList = newEvents;
        notifyDataSetChanged();
    }

    class WorkEventViewHolder extends RecyclerView.ViewHolder {
        private TextView eventTimeText;
        private TextView eventNameText;
        private TextView eventTypeText;
        private TextView eventStatusText;

        public WorkEventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventTimeText = itemView.findViewById(R.id.eventTimeText);
            eventNameText = itemView.findViewById(R.id.eventNameText);
            eventTypeText = itemView.findViewById(R.id.eventTypeText);
            eventStatusText = itemView.findViewById(R.id.eventStatusText);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEventClick(eventsList.get(position));
                }
            });
        }

        public void bind(WorkEvent event) {
            eventTimeText.setText(formatTimeRange(event.getTime(), event.getEndTime()));
            eventNameText.setText(event.getEventName());
            eventTypeText.setText(event.getEventTypeDisplay());

            // Set status text and color
            String statusText = "";
            int statusColor = android.graphics.Color.GRAY;

            switch (event.getStatus()) {
                case "scheduled":
                    statusText = "Scheduled";
                    statusColor = android.graphics.Color.BLUE;
                    break;
                case "completed":
                    statusText = "Completed";
                    statusColor = android.graphics.Color.GREEN;
                    break;
                case "cancelled":
                    statusText = "Cancelled";
                    statusColor = android.graphics.Color.RED;
                    break;
            }

            eventStatusText.setText(statusText);
            eventStatusText.setTextColor(statusColor);
        }
    }

    /**
     * Formats the time range for display.
     * If an explicit endTime is provided (e.g. 08:30 and 15:00), shows \"08:30 - 15:00\".
     * Otherwise, preserves the previous behaviour of showing a 1-hour range from startTime.
     */
    private static String formatTimeRange(String startTime, String endTime) {
        if (startTime == null) return "";
        String s = startTime.trim();
        // If an explicit endTime is stored, use it directly.
        if (endTime != null && !endTime.trim().isEmpty()) {
            return s + " - " + endTime.trim();
        }

        // Backwards-compatible: one-hour slot from start time.
        if (startTime == null) return "";
        String t = startTime.trim();
        try {
            String[] p = t.split(":");
            if (p.length != 2) return t;
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return t;
            int endMin = (h * 60 + m) + 60;
            int endH = (endMin / 60) % 24;
            int endM = endMin % 60;
            return String.format(java.util.Locale.getDefault(), "%s - %02d:%02d", t, endH, endM);
        } catch (Exception ignored) {
            return t;
        }
    }
}
