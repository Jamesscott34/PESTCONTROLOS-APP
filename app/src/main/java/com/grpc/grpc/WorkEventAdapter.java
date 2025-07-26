/**
 * WorkEventAdapter.java
 * 
 * RecyclerView adapter for displaying work events in the calendar view.
 * Handles the display of events with their status, time, and actions.
 * 
 * Author: James Scott
 * Company: Good Riddance Pest Control
 * Version: 1.0
 * Last Updated: 2024
 */

package com.grpc.grpc;

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
            eventTimeText.setText(event.getTime());
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
} 