package com.grpc.grpc;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.StorageReference;

import java.util.List;

/**
 * FileAdapter.java
 *
 * This adapter is used for displaying a list of files stored in Firebase Storage within a RecyclerView.
 * It binds file names to the RecyclerView items and allows users to click on a file to interact with it.
 *
 * Features:
 * - Displays file names from Firebase Storage
 * - Handles user clicks on files using an interface callback
 * - Utilizes a RecyclerView for efficient file listing
 *
 * Author: GRPC
 */

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
    private List<StorageReference> fileList;
    private OnFileClickListener listener;

    public interface OnFileClickListener {
        void onFileClick(StorageReference fileRef);
    }

    /**
     * Interface to handle file click events.
     */
    public FileAdapter(List<StorageReference> fileList, OnFileClickListener listener) {
        this.fileList = fileList;
        this.listener = listener;
    }
    /**
     * Creates and returns a ViewHolder for the file item.
     *
     * @param parent   The parent ViewGroup.
     * @param viewType The type of view.
     * @return A new ViewHolder instance.
     */
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Binds a file reference to the ViewHolder, displaying the file name
     * and setting up the click listener.
     *
     * @param holder   The ViewHolder instance.
     * @param position The position of the file in the list.
     */
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        StorageReference fileRef = fileList.get(position);
        holder.fileName.setText(fileRef.getName());
        holder.itemView.setOnClickListener(v -> listener.onFileClick(fileRef));
    }
    /**
     * Returns the total number of files in the adapter.
     *
     * @return The number of files in the list.
     */
    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        /**
         * ViewHolder class for displaying a single file item.
         */
        public ViewHolder(View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
        }
    }
}
