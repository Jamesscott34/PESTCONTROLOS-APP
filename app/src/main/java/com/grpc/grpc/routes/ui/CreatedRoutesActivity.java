package com.grpc.grpc.routes.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.reports.ui.ReportAdapter;
import com.grpc.grpc.routes.pdf.BestRoutePdfGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CreatedRoutesActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button backButton;
    private ReportAdapter adapter;
    private final List<File> routeFiles = new ArrayList<>();
    private final List<File> allRouteFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_viewer);

        recyclerView = findViewById(R.id.report_recycler_view);
        searchBar = findViewById(R.id.search_bar);
        backButton = findViewById(R.id.buttonreturn);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (searchBar != null) {
            searchBar.setHint("Search BEST ROUTES...");
            searchBar.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterFiles(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                startActivity(new Intent(this, RouterActivity.class));
                finish();
            });
        }

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.canRoute) {
                Toast.makeText(this, "Router is not enabled for this profile.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            loadFiles();
        }));
    }

    private void loadFiles() {
        routeFiles.clear();
        allRouteFiles.clear();

        File folder = BestRoutePdfGenerator.getRoutesFolder(this);
        if (folder.exists()) {
            File[] files = folder.listFiles((dir, name) -> name != null && name.toLowerCase().endsWith(".pdf"));
            if (files != null) {
                List<File> sorted = new ArrayList<>(Arrays.asList(files));
                sorted.sort(Comparator.comparingLong(File::lastModified).reversed());
                allRouteFiles.addAll(sorted);
                routeFiles.addAll(sorted);
            }
        }

        adapter = new ReportAdapter(this, routeFiles, new ReportAdapter.OnReportClickListener() {
            @Override
            public void onReportClick(File file) {
                showSinglePressOptions(file);
            }

            @Override
            public void onReportLongClick(File file) {
                showLongPressOptions(file);
            }
        });
        recyclerView.setAdapter(adapter);

        if (allRouteFiles.isEmpty()) {
            Toast.makeText(this, "No created routes found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterFiles(String query) {
        routeFiles.clear();
        for (File file : allRouteFiles) {
            if (file.getName().toLowerCase().contains(query.toLowerCase())) {
                routeFiles.add(file);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showSinglePressOptions(File file) {
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(new CharSequence[]{"View"}, (dialog, which) -> viewPdf(file))
                .show();
    }

    private void showLongPressOptions(File file) {
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(new CharSequence[]{"Share", "Delete", "Rename"}, (dialog, which) -> {
                    if (which == 0) {
                        shareFile(file);
                    } else if (which == 1) {
                        deleteFile(file);
                    } else if (which == 2) {
                        renameFile(file);
                    }
                })
                .show();
    }

    private void viewPdf(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No PDF viewer found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share route PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(File file) {
        if (file.delete()) {
            Toast.makeText(this, "Route deleted.", Toast.LENGTH_SHORT).show();
            loadFiles();
        } else {
            Toast.makeText(this, "Could not delete route.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renameFile(File file) {
        EditText input = new EditText(this);
        String currentName = file.getName();
        String baseName = currentName.toLowerCase().endsWith(".pdf")
                ? currentName.substring(0, currentName.length() - 4)
                : currentName;
        input.setText(baseName);
        input.setSelection(baseName.length());

        new AlertDialog.Builder(this)
                .setTitle("Rename route")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newBaseName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(newBaseName)) {
                        Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File renamed = new File(file.getParentFile(), newBaseName + ".pdf");
                    if (renamed.exists()) {
                        Toast.makeText(this, "A file with that name already exists.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (file.renameTo(renamed)) {
                        Toast.makeText(this, "Route renamed.", Toast.LENGTH_SHORT).show();
                        loadFiles();
                    } else {
                        Toast.makeText(this, "Could not rename route.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
