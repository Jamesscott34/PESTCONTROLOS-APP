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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
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
                openPDFWithOptions(file);
            }

            @Override
            public void onReportLongClick(File file) {
                sharePDFWithOptions(file);
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

    // Open the PDF using the phone's default viewer options
    private void openPDFWithOptions(File file) {
        Intent intent = new Intent(this, PdfViewerActivity.class);
        intent.putExtra("pdf_file_path", file.getAbsolutePath());
        startActivity(intent);
    }


    // Share the PDF using the phone's default sharing options
    private void sharePDFWithOptions(File file) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            Uri fileUri = Uri.fromFile(file);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Sharing Report via GRPC App");
            startActivity(Intent.createChooser(shareIntent, "Share PDF using:"));
        } catch (Exception e) {
            Toast.makeText(this, "No application available to share this PDF.", Toast.LENGTH_SHORT).show();
        }
    }
}
