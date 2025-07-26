package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

/**
 * StoredReportsActivity.java
 *
 * This activity allows users to browse and manage stored reports in Firebase Storage.
 * Users can navigate through parent folders, access subfolders, and view stored report files.
 * Updated: Added search functionality to folder and file selection dialogs.
 *
 * Features:
 * - Loads parent folders from Firebase Storage
 * - Supports navigation into subfolders for organized report access
 * - Displays files stored in a selected folder
 * - Allows users to open and view reports in PDF format
 * - Excludes backup folders from the listing
 * - Provides user-friendly dialogs for file selection
 * - NEW: Search bars in folder and file selection dialogs
 *
 * Author: James Scott
 */


public class StoredReportsActivity extends AppCompatActivity {

    private RecyclerView folderRecyclerView;
    private FolderAdapter adapter;
    private List<String> folderList = new ArrayList<>();
    private Button buttonBack;
    private String selectedParentFolder = null; // Keeps track of the currently selected parent folder
    private String userName; // User name for permission checking

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stored_reports);

        // Get user name from intent
        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) {
            userName = "Unknown";
        }

        folderRecyclerView = findViewById(R.id.folderRecyclerView);
        folderRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        buttonBack = findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(v -> finish());

        // Load root folders from Firebase Storage
        loadParentFolders();
    }

    /**
     * Loads the parent folders (e.g., "ReportsXX/") from Firebase Storage.
     */
    private void loadParentFolders() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();

        storageRef.listAll().addOnSuccessListener(listResult -> {
            folderList.clear();
            for (StorageReference prefix : listResult.getPrefixes()) {
                String folderName = prefix.getName();
                if (!folderName.equals("backup")) { // Exclude "backup" folder
                    folderList.add(folderName);
                }
            }

            if (adapter == null) {
                adapter = new FolderAdapter(folderList, this::loadSubFolders);
                folderRecyclerView.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }

        }).addOnFailureListener(e ->
                Toast.makeText(StoredReportsActivity.this, "Failed to load folders: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Loads subfolders within the selected parent folder with search functionality.
     */
    private void loadSubFolders(String parentFolder) {
        selectedParentFolder = parentFolder; // Track the current parent folder
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference parentFolderRef = storage.getReference().child(parentFolder);

        parentFolderRef.listAll().addOnSuccessListener(listResult -> {
            List<String> subFolderList = new ArrayList<>();
            for (StorageReference prefix : listResult.getPrefixes()) {
                subFolderList.add(prefix.getName());
            }

            if (subFolderList.isEmpty()) {
                // If no subfolders exist, display the files instead
                loadFilesFromFolder(parentFolder);
            } else {
                // Show subfolder selection dialog with search
                showFolderSelectionDialog(subFolderList, parentFolder);
            }

        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load subfolders: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Shows folder selection dialog with search functionality.
     */
    private void showFolderSelectionDialog(List<String> subFolderList, String parentFolder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Subfolder");

        // Create custom layout for search and results
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = dialogView.findViewById(R.id.searchBar);
        RecyclerView resultsRecyclerView = dialogView.findViewById(R.id.resultsRecyclerView);
        
        // Set up RecyclerView
        resultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        SearchResultsAdapter adapter = new SearchResultsAdapter(subFolderList, selectedItem -> {
            loadFilesFromFolder(parentFolder + "/" + selectedItem);
        });
        resultsRecyclerView.setAdapter(adapter);
        
        builder.setView(dialogView);

        // Add search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                List<String> filteredList = new ArrayList<>();
                for (String folder : subFolderList) {
                    if (folder.toLowerCase().contains(s.toString().toLowerCase())) {
                        filteredList.add(folder);
                    }
                }
                adapter.updateResults(filteredList);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Loads files from the selected subfolder with search functionality.
     */
    private void loadFilesFromFolder(String folderPath) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference folderRef = storage.getReference().child(folderPath);

        folderRef.listAll().addOnSuccessListener(listResult -> {
            List<String> fileList = new ArrayList<>();
            for (StorageReference item : listResult.getItems()) {
                fileList.add(item.getName());
            }

            runOnUiThread(() -> {
                showFileSelectionDialog(fileList, folderPath);
            });
        }).addOnFailureListener(e ->
                runOnUiThread(() -> Toast.makeText(this, "Failed to load files: " + e.getMessage(), Toast.LENGTH_SHORT).show())
        );
    }

    /**
     * Shows file selection dialog with search functionality.
     */
    private void showFileSelectionDialog(List<String> fileList, String folderPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Files in " + folderPath);

        if (fileList.isEmpty()) {
            builder.setMessage("No files found in this folder.")
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        } else {
            // Create custom layout for search and results
            android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
            EditText searchBar = dialogView.findViewById(R.id.searchBar);
            RecyclerView resultsRecyclerView = dialogView.findViewById(R.id.resultsRecyclerView);
            
            // Set up RecyclerView
            resultsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            SearchResultsAdapter adapter = new SearchResultsAdapter(fileList, selectedItem -> {
                showFileOptions(folderPath, selectedItem);
            });
            resultsRecyclerView.setAdapter(adapter);
            
            builder.setView(dialogView);

            // Add search functionality
            searchBar.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    List<String> filteredList = new ArrayList<>();
                    for (String file : fileList) {
                        if (file.toLowerCase().contains(s.toString().toLowerCase())) {
                            filteredList.add(file);
                        }
                    }
                    adapter.updateResults(filteredList);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        }
        builder.show();
    }

    /**
     * Shows file options dialog (View/Delete for James)
     */
    private void showFileOptions(String folderPath, String fileName) {
        if ("james".equalsIgnoreCase(userName)) {
            // James can view or delete files
            String[] options = {"View", "Delete"};
            new AlertDialog.Builder(this)
                .setTitle("File Options")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            viewFile(folderPath, fileName);
                            break;
                        case 1:
                            deleteFile(folderPath, fileName);
                            break;
                    }
                })
                .show();
        } else {
            // Other users can only view files
            viewFile(folderPath, fileName);
        }
    }

    /**
     * Deletes a file from Firebase Storage
     */
    private void deleteFile(String folderPath, String fileName) {
        new AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete '" + fileName + "'? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                FirebaseStorage storage = FirebaseStorage.getInstance();
                StorageReference fileRef = storage.getReference().child(folderPath + "/" + fileName);

                fileRef.delete().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "File deleted successfully!", Toast.LENGTH_SHORT).show();
                    // Refresh the file list
                    loadFilesFromFolder(folderPath);
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Opens a selected file from Firebase Storage.
     */
    private void viewFile(String folder, String fileName) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference fileRef = storage.getReference().child(folder + "/" + fileName);

        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to open file: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }
}
