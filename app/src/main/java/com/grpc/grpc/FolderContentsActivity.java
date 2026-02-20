package com.grpc.grpc;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;

/**
 * FolderContentsActivity.java
 *
 * This activity displays the contents of a selected folder stored in Firebase Storage.
 * It retrieves the folder name from the intent, loads files from Firebase, and displays them
 * in a RecyclerView. Users can view and share files directly from this activity.
 *
 * Features:
 * - Retrieves folder contents from Firebase Storage
 * - Displays files in a RecyclerView
 * - Allows users to share files via intent
 *
 * Author: GRPC
 */


public class FolderContentsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private List<StorageReference> fileList = new ArrayList<>();
    private String folderName, userName;
    /**
     * Initializes the activity, retrieves the folder name from intent,
     * and sets up UI elements for displaying files.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     *                           this Bundle contains the most recent data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_contents);

        folderName = getIntent().getStringExtra("FOLDER_NAME");
        userName = getIntent().getStringExtra("USER_NAME");
        // Offline User must not view Firebase folder contents (same rule as Stored Reports)
        if ("Offline User".equals(userName)) {
            Toast.makeText(this, "Folder view is not available in offline mode.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        recyclerView = findViewById(R.id.fileRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Button uploadButton = findViewById(R.id.buttonUploadFile);
        if (uploadButton != null) {
            // Same rule as stored reports: hide Upload for Offline User
            boolean showUpload = FirebaseAuth.getInstance().getCurrentUser() != null && !"Offline User".equals(userName);
            uploadButton.setVisibility(showUpload ? View.VISIBLE : View.GONE);
        }

        loadFilesFromFirebase();
    }
    /**
     * Loads files from the selected folder in Firebase Storage and updates the RecyclerView.
     */
    private void loadFilesFromFirebase() {

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference folderRef = storage.getReference().child(folderName);

        folderRef.listAll().addOnSuccessListener(listResult -> {
            fileList.clear();
            fileList.addAll(listResult.getItems());
            adapter = new FileAdapter(fileList, this::shareFile);
            recyclerView.setAdapter(adapter);
        });
    }
    /**
     * Retrieves the download URL of the selected file and shares it via an intent.
     *
     * @param fileRef The reference to the file in Firebase Storage.
     */
    private void shareFile(StorageReference fileRef) {
        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(shareIntent, "Share PDF"));
        });
    }
}
