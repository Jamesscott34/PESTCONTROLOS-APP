package com.grpc.grpc;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;

public class PdfViewerActivity extends AppCompatActivity {

    private PDFView pdfView;
    private Button returnButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        pdfView = findViewById(R.id.pdf_viewer);
        returnButton = findViewById(R.id.buttonreturn);

        // Retrieve the file path from the intent
        String filePath = getIntent().getStringExtra("pdf_file_path");

        if (filePath != null) {
            File file = new File(filePath);
            if (file.exists()) {
                pdfView.fromFile(file)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .enableAntialiasing(true)
                        .spacing(10)
                        .load();
            } else {
                Toast.makeText(this, "PDF file not found!", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Error loading PDF.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Return button functionality
        returnButton.setOnClickListener(view -> finish());
    }
}
