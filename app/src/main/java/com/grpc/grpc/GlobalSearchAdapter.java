package com.grpc.grpc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

enum GlobalSearchItemType { HEADER, RESULT }

enum GlobalSearchKind { JOB, CONTRACT, LEAD, REPORT }

final class GlobalSearchItem {
    final GlobalSearchItemType type;
    final GlobalSearchKind kind;
    final String title;
    final String subtitle;
    final String owner; // only used for contracts

    private GlobalSearchItem(GlobalSearchItemType type, GlobalSearchKind kind, String title, String subtitle, String owner) {
        this.type = type;
        this.kind = kind;
        this.title = title;
        this.subtitle = subtitle;
        this.owner = owner;
    }

    static GlobalSearchItem header(String title) {
        return new GlobalSearchItem(GlobalSearchItemType.HEADER, GlobalSearchKind.JOB, title, null, null);
    }

    static GlobalSearchItem result(GlobalSearchKind kind, String title, String subtitle, String owner) {
        return new GlobalSearchItem(GlobalSearchItemType.RESULT, kind, title, subtitle, owner);
    }
}

public class GlobalSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemClick {
        void onClick(GlobalSearchItem item);
    }

    private static final int VT_HEADER = 0;
    private static final int VT_RESULT = 1;

    private final List<GlobalSearchItem> items = new ArrayList<>();
    private final OnItemClick onItemClick;

    public GlobalSearchAdapter(OnItemClick onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void setItems(List<GlobalSearchItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        GlobalSearchItem it = items.get(position);
        return it.type == GlobalSearchItemType.HEADER ? VT_HEADER : VT_RESULT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VT_HEADER) {
            View v = inflater.inflate(R.layout.item_search_header, parent, false);
            return new HeaderVH(v);
        }
        View v = inflater.inflate(R.layout.item_search_result, parent, false);
        return new ResultVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GlobalSearchItem it = items.get(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).bind(it);
        } else if (holder instanceof ResultVH) {
            ((ResultVH) holder).bind(it, onItemClick);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final TextView text;
        HeaderVH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.searchHeaderText);
        }
        void bind(GlobalSearchItem it) {
            text.setText(it.title != null ? it.title : "");
        }
    }

    static final class ResultVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        ResultVH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.searchResultTitle);
            subtitle = itemView.findViewById(R.id.searchResultSubtitle);
        }
        void bind(GlobalSearchItem it, OnItemClick click) {
            title.setText(it.title != null ? it.title : "");
            subtitle.setText(it.subtitle != null ? it.subtitle : "");
            itemView.setOnClickListener(v -> {
                if (click != null) click.onClick(it);
            });
        }
    }
}

