package com.grpc.grpc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReportViewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EditText searchBar;
    private Button returnButton;
    private ReportAdapter adapter;
    private List<File> reportFiles;
    private List<File> allReportFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_viewer);

        recyclerView = findViewById(R.id.report_recycler_view);
        searchBar = findViewById(R.id.search_bar);
        returnButton = findViewById(R.id.buttonreturn);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadReports();

        // Search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterReports(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        returnButton.setOnClickListener(view -> finish());
    }

    private void loadReports() {
        reportFiles = new ArrayList<>();
        allReportFiles = new ArrayList<>();

        File reportsFolder = new File(getExternalFilesDir(null), "GRPEST REPORTS");
        if (reportsFolder.exists()) {
            File[] files = reportsFolder.listFiles((dir, name) -> name.endsWith(".pdf"));
            if (files != null) {
                allReportFiles.addAll(Arrays.asList(files));
                reportFiles.addAll(Arrays.asList(files));
            }
        }

        adapter = new ReportAdapter(this, reportFiles, new ReportAdapter.OnReportClickListener() {
            @Override
            public void onReportClick(File file) {
                viewPDF(file); // Single press: Open PDF
            }

            @Override
            public void onReportLongClick(File file) {
                shareReport(file); // Long press: Share PDF
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void filterReports(String query) {
        reportFiles.clear();
        for (File report : allReportFiles) {
            if (report.getName().toLowerCase().startsWith(query.toLowerCase())) {
                reportFiles.add(report);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void shareReport(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Report"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share report.", Toast.LENGTH_SHORT).show();
        }
    }


    private void viewPDF(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    "com.grpc.grpc.fileprovider",
                    file
            );

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, "application/pdf");
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(viewIntent);
        } catch (Exception e) {
            Toast.makeText(this, "No application found to view this PDF.", Toast.LENGTH_SHORT).show();
        }
    }
}
