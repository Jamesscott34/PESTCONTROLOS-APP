package com.grpc.grpc;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SavedMapsActivity extends AppCompatActivity {

    private ListView listView;
    private EditText searchBar;
    private List<File> allMaps;
    private ArrayAdapter<String> adapter;
    private List<String> mapNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_maps);

        listView = findViewById(R.id.listView);
        searchBar = findViewById(R.id.searchBar);

        loadSavedMaps();

        // Filter maps based on search query
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Handle item clicks (View Map)
        listView.setOnItemClickListener((parent, view, position, id) -> {
            File selectedMap = allMaps.get(position);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(selectedMap), "image/*");
            startActivity(intent);
        });

        // Handle long clicks (Delete Map)
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            File selectedMap = allMaps.get(position);
            showDeleteDialog(selectedMap, position);
            return true;
        });
    }

    private void loadSavedMaps() {
        File directory = new File(Environment.getExternalStorageDirectory(), "Premises Maps");
        if (!directory.exists() || !directory.isDirectory()) {
            Toast.makeText(this, "No saved maps found!", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            allMaps = new ArrayList<>();
            mapNames = new ArrayList<>();

            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".png")) {
                    allMaps.add(file);
                    mapNames.add(file.getName());
                }
            }

            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mapNames);
            listView.setAdapter(adapter);
        }
    }

    private void showDeleteDialog(File mapFile, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Map")
                .setMessage("Are you sure you want to delete " + mapFile.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mapFile.delete()) {
                        allMaps.remove(position);
                        mapNames.remove(position);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "Map deleted successfully.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to delete map.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
