package com.grpc.grpc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * SearchResultsAdapter.java
 *
 * Adapter for displaying search results in a RecyclerView.
 * Supports real-time filtering and item selection.
 *
 * Features:
 * - Displays list of items (folders or files)
 * - Supports item selection via callback
 * - Real-time filtering with updateResults method
 * - Theme-aware styling
 *
 * Author: James Scott
 */

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {

    private List<String> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String item);
    }

    public SearchResultsAdapter(List<String> items, OnItemClickListener listener) {
        this.items = new ArrayList<>(items);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String item = items.get(position);
        holder.textView.setText(item);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * Updates the displayed results with filtered list.
     */
    public void updateResults(List<String> newItems) {
        this.items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
} 