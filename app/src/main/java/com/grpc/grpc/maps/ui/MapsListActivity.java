package com.grpc.grpc.maps.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.maps.util.MapsUtil;
import com.grpc.grpc.search.ui.SearchResultsAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapsListActivity extends AppCompatActivity {
    public static final String EXTRA_LOCAL_ONLY = "LOCAL_ONLY";

    private final List<String> allItems = new ArrayList<>();
    private SearchResultsAdapter adapter;
    private boolean localOnly;
    private String contractId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps_list);

        localOnly = getIntent().getBooleanExtra(EXTRA_LOCAL_ONLY, false);
        contractId = getIntent().getStringExtra("CONTRACT_ID");
        String companyName = getIntent().getStringExtra("COMPANY_NAME");
        String address = getIntent().getStringExtra("ADDRESS");

        TextView titleText = findViewById(R.id.mapsListTitleText);
        TextView subtitleText = findViewById(R.id.mapsListSubtitleText);
        EditText searchBar = findViewById(R.id.mapsListSearchBar);
        RecyclerView recyclerView = findViewById(R.id.mapsListRecyclerView);
        Button backButton = findViewById(R.id.mapsListBackButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultsAdapter(new ArrayList<>(), this::openSelectedMap);
        recyclerView.setAdapter(adapter);

        if (hasContract()) {
            titleText.setText("Contract Maps");
            subtitleText.setText(safe(companyName) + "\n" + safe(address));
            loadContractMaps();
        } else {
            titleText.setText("Saved Maps");
            subtitleText.setText("Maps saved on this device");
            loadLocalMaps();
        }

        backButton.setOnClickListener(v -> finish());
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString().toLowerCase() : "";
                List<String> filtered = new ArrayList<>();
                for (String item : allItems) {
                    if (item.toLowerCase().contains(query)) {
                        filtered.add(item);
                    }
                }
                adapter.updateResults(filtered);
            }
        });
    }

    private void loadLocalMaps() {
        File folder = MapsUtil.getLocalMapsFolder(this);
        File[] files = folder.listFiles((dir, name) -> MapsUtil.isMapFileName(name));
        allItems.clear();
        if (files != null) {
            for (File file : files) {
                allItems.add(file.getName());
            }
        }
        Collections.sort(allItems);
        adapter.updateResults(allItems);
        if (allItems.isEmpty()) {
            Toast.makeText(this, "No saved maps found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadContractMaps() {
        String folderPath = com.grpc.grpc.core.ContractReportSync.buildContractStorageFolder(contractId);
        FirebaseStorage.getInstance()
                .getReference()
                .child(folderPath)
                .listAll()
                .addOnSuccessListener(listResult -> {
                    allItems.clear();
                    for (StorageReference item : listResult.getItems()) {
                        if (MapsUtil.isMapFileName(item.getName())) {
                            allItems.add(item.getName());
                        }
                    }
                    Collections.sort(allItems);
                    adapter.updateResults(allItems);
                    if (allItems.isEmpty()) {
                        Toast.makeText(this, "No contract maps found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load maps: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void openSelectedMap(String item) {
        if (hasContract()) {
            String folderPath = com.grpc.grpc.core.ContractReportSync.buildContractStorageFolder(contractId);
            FirebaseStorage.getInstance()
                    .getReference()
                    .child(folderPath + "/" + item)
                    .getDownloadUrl()
                    .addOnSuccessListener(uri -> openUri(uri))
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to open map: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            return;
        }

        File file = new File(MapsUtil.getLocalMapsFolder(this), item);
        if (!file.exists()) {
            Toast.makeText(this, "Map file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                file
        );
        openUri(uri);
    }

    private void openUri(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "View Map"));
    }

    private boolean hasContract() {
        return !localOnly && contractId != null && !contractId.trim().isEmpty();
    }

    private String safe(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : "N/A";
    }
}
