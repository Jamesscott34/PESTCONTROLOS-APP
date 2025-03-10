package com.grpc.grpc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
/**
 * FolderAdapter.java
 *
 * This adapter is used for displaying a list of folders within a RecyclerView.
 * It binds folder names to the RecyclerView items and allows users to click on a folder to navigate into it.
 *
 * Features:
 * - Displays folder names in a RecyclerView
 * - Handles user clicks on folders using an interface callback
 * - Provides a structured way to list and interact with folders
 *
 * Author: James Scott
 */
public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
    private List<String> folderList;
    private OnFolderClickListener listener;
    /**
     * Interface to handle folder click events.
     */
    public interface OnFolderClickListener {
        /**
         * Called when a folder is clicked.
         *
         * @param folderName The name of the clicked folder.
         */
        void onFolderClick(String folderName);
    }
    /**
     * Constructor for FolderAdapter.
     *
     * @param folderList The list of folder names to be displayed.
     * @param listener   The click listener for handling folder selection.
     */
    public FolderAdapter(List<String> folderList, OnFolderClickListener listener) {
        this.folderList = folderList;
        this.listener = listener;
    }
    /**
     * Creates and returns a ViewHolder for the folder item.
     *
     * @param parent   The parent ViewGroup.
     * @param viewType The type of view.
     * @return A new ViewHolder instance.
     */
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.folder_item, parent, false);
        return new ViewHolder(view);
    }
    /**
     * Binds a folder name to the ViewHolder, displaying the folder name
     * and setting up the click listener.
     *
     * @param holder   The ViewHolder instance.
     * @param position The position of the folder in the list.
     */
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        String folderName = folderList.get(position);
        holder.folderName.setText(folderName);
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folderName));
    }
    /**
     * Returns the total number of folders in the adapter.
     *
     * @return The number of folders in the list.
     */
    @Override
    public int getItemCount() {
        return folderList.size();
    }
    /**
     * Returns the total number of folders in the adapter.
     *
     * @return The number of folders in the list.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView folderName;
        /**
         * Constructor for ViewHolder.
         *
         * @param itemView The view representing a single folder item.
         */
        public ViewHolder(View itemView) {
            super(itemView);
            folderName = itemView.findViewById(R.id.folderName);
        }
    }
}

