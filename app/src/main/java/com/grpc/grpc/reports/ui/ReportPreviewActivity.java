package com.grpc.grpc.reports.ui;

import com.grpc.grpc.R;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;

/**
 * Displays a locally generated PDF preview with Back and Confirm actions.
 */
public class ReportPreviewActivity extends AppCompatActivity {

    public static final String EXTRA_PREVIEW_PDF_PATH = "preview_pdf_path";
    public static final String EXTRA_CONFIRM_SAVE = "confirm_save";

    private LinearLayout previewPagesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_preview);

        previewPagesContainer = findViewById(R.id.previewPagesContainer);
        Button backButton = findViewById(R.id.previewBackButton);
        Button confirmButton = findViewById(R.id.previewConfirmSaveButton);

        String pdfPath = getIntent().getStringExtra(EXTRA_PREVIEW_PDF_PATH);
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            Toast.makeText(this, "Preview file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File file = new File(pdfPath);
        if (!file.exists()) {
            Toast.makeText(this, "Preview file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        renderPdfPages(file);

        backButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED, getIntent());
            finish();
        });
        confirmButton.setOnClickListener(v -> {
            getIntent().putExtra(EXTRA_CONFIRM_SAVE, true);
            setResult(RESULT_OK, getIntent());
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED, getIntent());
        super.onBackPressed();
    }

    private void renderPdfPages(File file) {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {

            previewPagesContainer.removeAllViews();
            for (int i = 0; i < renderer.getPageCount(); i++) {
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                    ImageView imageView = new ImageView(this);
                    imageView.setImageBitmap(bitmap);
                    imageView.setAdjustViewBounds(true);
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    params.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
                    imageView.setLayoutParams(params);
                    previewPagesContainer.addView(imageView);
                }
            }
        } catch (IOException e) {
            Toast.makeText(this, "Unable to open preview.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
