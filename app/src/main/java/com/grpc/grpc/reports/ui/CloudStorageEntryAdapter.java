package com.grpc.grpc.reports.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.grpc.grpc.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Combined folder + file rows for Firebase Storage drill-down browsing.
 */
public final class CloudStorageEntryAdapter extends RecyclerView.Adapter<CloudStorageEntryAdapter.Holder> {

    public static final class Entry {
        public final String name;
        public final boolean folder;
        /** Optional UI title (e.g. company name for {@code contracts/{id}} folders). */
        public final String displayTitle;
        /** Full bucket path when known (search results, move operations). */
        @Nullable
        public final String storagePath;

        public Entry(String name, boolean folder) {
            this(name, folder, null, null);
        }

        public Entry(String name, boolean folder, String displayTitle) {
            this(name, folder, displayTitle, null);
        }

        public Entry(String name, boolean folder, String displayTitle, @Nullable String storagePath) {
            this.name = name != null ? name : "";
            this.folder = folder;
            this.displayTitle = displayTitle != null ? displayTitle.trim() : "";
            this.storagePath = storagePath;
        }

        public String sortKey() {
            if (folder && !displayTitle.isEmpty()) {
                return displayTitle.toLowerCase(Locale.ROOT);
            }
            return name.toLowerCase(Locale.ROOT);
        }

        @Nullable
        public String resolveStoragePath(@Nullable String currentPath) {
            if (storagePath != null && !storagePath.trim().isEmpty()) {
                return storagePath.trim();
            }
            if (folder || currentPath == null || currentPath.trim().isEmpty()) {
                return null;
            }
            return currentPath.trim() + "/" + name;
        }
    }

    public interface Listener {
        void onEntryClick(Entry entry);

        default void onEntryLongClick(Entry entry) {
        }
    }

    private final List<Entry> items = new ArrayList<>();
    private final Listener listener;
    private boolean selectionMode;
    private final Set<String> selectedStoragePaths = new LinkedHashSet<>();
    @Nullable
    private String listCurrentPath;

    public CloudStorageEntryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Entry> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        pruneMissingSelections();
        notifyDataSetChanged();
    }

    public void setListCurrentPath(@Nullable String currentPath) {
        this.listCurrentPath = currentPath;
        notifyDataSetChanged();
    }

    @Nullable
    public String getListCurrentPath() {
        return listCurrentPath;
    }

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) {
            return;
        }
        selectionMode = enabled;
        if (!enabled) {
            selectedStoragePaths.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public int getSelectedCount() {
        return selectedStoragePaths.size();
    }

    public List<String> getSelectedStoragePaths() {
        return new ArrayList<>(selectedStoragePaths);
    }

    public void toggleSelected(@Nullable String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            return;
        }
        String path = storagePath.trim();
        if (selectedStoragePaths.contains(path)) {
            selectedStoragePaths.remove(path);
        } else {
            selectedStoragePaths.add(path);
        }
        notifyDataSetChanged();
    }

    public void selectAllVisibleFilePaths(@Nullable String currentPath) {
        String basePath = currentPath != null && !currentPath.trim().isEmpty()
                ? currentPath.trim()
                : (listCurrentPath != null ? listCurrentPath.trim() : null);
        for (Entry entry : items) {
            if (entry.folder) {
                continue;
            }
            String path = entry.resolveStoragePath(basePath);
            if (path != null) {
                selectedStoragePaths.add(path);
            }
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedStoragePaths.clear();
        notifyDataSetChanged();
    }

    private void pruneMissingSelections() {
        if (selectedStoragePaths.isEmpty()) {
            return;
        }
        Set<String> visible = new LinkedHashSet<>();
        String basePath = listCurrentPath != null ? listCurrentPath.trim() : null;
        for (Entry entry : items) {
            if (entry.folder) {
                continue;
            }
            String path = entry.resolveStoragePath(basePath);
            if (path != null) {
                visible.add(path);
            }
        }
        if (!visible.isEmpty()) {
            selectedStoragePaths.retainAll(visible);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cloud_storage_entry, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Entry e = items.get(position);
        android.content.Context ctx = holder.itemView.getContext();
        holder.icon.setImageResource(e.folder ? R.drawable.ic_list_folder : R.drawable.ic_list_file);
        String primary = (!e.displayTitle.isEmpty()) ? e.displayTitle : e.name;
        holder.title.setText(primary);
        if (e.folder && !e.displayTitle.isEmpty() && !e.displayTitle.equals(e.name)) {
            holder.subtitle.setText(ctx.getString(R.string.cloud_storage_contract_folder_subtitle, e.name));
        } else {
            holder.subtitle.setText(e.folder
                    ? ctx.getString(R.string.cloud_storage_row_type_folder)
                    : ctx.getString(R.string.cloud_storage_row_type_file));
        }

        String resolvedPath = e.resolveStoragePath(listCurrentPath);
        boolean showCheckbox = selectionMode && !e.folder && resolvedPath != null;
        holder.checkBox.setVisibility(showCheckbox ? View.VISIBLE : View.GONE);
        if (showCheckbox) {
            holder.checkBox.setChecked(selectedStoragePaths.contains(resolvedPath));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener == null) {
                return;
            }
            if (selectionMode && !e.folder && resolvedPath != null) {
                toggleSelected(resolvedPath);
                listener.onEntryClick(e);
                return;
            }
            listener.onEntryClick(e);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onEntryLongClick(e);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
      