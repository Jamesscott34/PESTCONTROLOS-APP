package com.grpc.grpc.search.ui;

import com.grpc.grpc.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying search results in a RecyclerView.
 * Supports real-time filtering and item selection.
 */
public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {

    public static final class ListItem {
        public final String displayLabel;
        public final String value;

        public ListItem(String displayLabel, String value) {
            this.displayLabel = displayLabel != null ? displayLabel : "";
            this.value = value != null ? value : "";
        }
    }

    private List<ListItem> items;
    private final OnItemClickListener listener;
    private final boolean folderRows;

    public interface OnItemClickListener {
        void onItemClick(String value);
    }

    public SearchResultsAdapter(List<String> items, OnItemClickListener listener) {
        this(toListItems(items), listener, false);
    }

    public static SearchResultsAdapter forStrings(
            List<String> items,
            OnItemClickListener listener,
            boolean folderRows
    ) {
        return new SearchResultsAdapter(toListItems(items), listener, folderRows);
    }

    public static SearchResultsAdapter forLabeledItems(
            List<ListItem> items,
            OnItemClickListener listener,
            boolean folderRows
    ) {
        return new SearchResultsAdapter(items, listener, folderRows);
    }

    private SearchResultsAdapter(List<ListItem> items, OnItemClickListener listener, boolean folderRows) {
        this.items = new ArrayList<>(items != null ? items : new ArrayList<>());
        this.listener = listener;
        this.folderRows = folderRows;
    }

    private static List<ListItem> toListItems(List<String> raw) {
        List<ListItem> out = new ArrayList<>();
        if (raw != null) {
            for (String item : raw) {
                out.add(new ListItem(item, item));
            }
        }
        return out;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dialog_list_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListItem item = items.get(position);
        holder.textView.setText(item.displayLabel);
        holder.icon.setImageResource(folderRows ? R.drawable.ic_list_folder : R.drawable.ic_list_file);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item.value);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateResults(List<String> newItems) {
        updateListItems(toListItems(newItems));
    }

    public void updateListItems(List<ListItem> newItems) {
        this.items = new ArrayList<>(newItems != null ? newItems : new ArrayList<>());
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView textView;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.dialogListRowIcon);
            textView = itemView.findViewById(R.id.dialogListRowText);
        }
    }
}
