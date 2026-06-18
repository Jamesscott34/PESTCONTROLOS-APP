package com.grpc.grpc.pdfeditor;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.storage.FirebaseStorage;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.reports.ui.CloudStorageBrowserActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app PDF markup: open from device or Firebase Storage, add text and ink, export a flattened PDF.
 * Gated like PDF → Word via {@link SessionManager.Session#canConvert}.
 */
public class PdfEditorActivity extends AppCompatActivity implements PdfEditorPageAdapter.PageUiCallbacks {

    private RecyclerView recyclerView;
    private TextView hintText;
    private Button pickDeviceButton;
    private Button pickCloudButton;
    private Button modeTextButton;
    private Button modeDrawButton;
    private Button colorBlackButton;
    private Button colorRedButton;
    private Button colorBlueButton;
    private Button exportButton;
    private Button pageUpButton;
    private Button pageDownButton;

    @Nullable
    private File sourcePdfFile;
    @Nullable
    private ParcelFileDescriptor pfd;
    @Nullable
    private PdfRenderer pdfRenderer;
    private final SparseArray<PdfEditorPageAnnotations> annotationsByPage = new SparseArray<>();
    @Nullable
    private PdfEditorPageAdapter adapter;

    private PdfEditorOverlayView.EditMode editMode = PdfEditorOverlayView.EditMode.DRAW;
    private int inkColor = 0xFF000000;
    @Nullable
    private String cloudUserName;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final PdfEditorPageAdapter.PageUiCallbacks pageCallbacks = this;

    private final ActivityResultLauncher<Intent> pickLocalLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) {
                    return;
                }
                copyUriToCacheAndOpen(uri);
            });

    private final ActivityResultLauncher<Intent> cloudPickLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                String path = result.getData().getStringExtra(CloudStorageBrowserActivity.EXTRA_RESULT_STORAGE_PATH);
                if (path == null || path.isEmpty()) {
                    return;
                }
                downloadCloudPdfAndOpen(path);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_editor);
        setTitle(R.string.pdf_editor_title);

        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.pdf_editor_not_available, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) {
            return;
        }

        recyclerView = findViewById(R.id.pdfEditorRecycler);
        hintText = findViewById(R.id.pdfEditorHint);
        pickDeviceButton = findViewById(R.id.pdfEditorPickDevice);
        pickCloudButton = findViewById(R.id.pdfEditorPickCloud);
        modeTextButton = findViewById(R.id.pdfEditorModeText);
        modeDrawButton = findViewById(R.id.pdfEditorModeDraw);
        colorBlackButton = findViewById(R.id.pdfEditorColorBlack);
        colorRedButton = findViewById(R.id.pdfEditorColorRed);
        colorBlueButton = findViewById(R.id.pdfEditorColorBlue);
        exportButton = findViewById(R.id.pdfEditorExport);
        pageUpButton = findViewById(R.id.pdfEditorPageUp);
        pageDownButton = findViewById(R.id.pdfEditorPageDown);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (pageUpButton != null) {
            pageUpButton.setOnClickListener(v -> scrollPages(-1));
        }
        if (pageDownButton != null) {
            pageDownButton.setOnClickListener(v -> scrollPages(1));
        }

        if (pickDeviceButton != null) {
            pickDeviceButton.setOnClickListener(v -> launchPickLocalPdf());
        }
        if (pickCloudButton != null) {
            pickCloudButton.setOnClickListener(v -> showCloudSourceDialog());
        }
        if (modeTextButton != null) {
            modeTextButton.setOnClickListener(v -> setEditMode(PdfEditorOverlayView.EditMode.TEXT));
        }
        if (modeDrawButton != null) {
            modeDrawButton.setOnClickListener(v -> setEditMode(PdfEditorOverlayView.EditMode.DRAW));
        }
        if (colorBlackButton != null) {
            colorBlackButton.setOnClickListener(v -> setInkColor(0xFF000000));
        }
        if (colorRedButton != null) {
            colorRedButton.setOnClickListener(v -> setInkColor(0xFFE53935));
        }
        if (colorBlueButton != null) {
            colorBlueButton.setOnClickListener(v -> setInkColor(0xFF1E88E5));
        }
        if (exportButton != null) {
            exportButton.setEnabled(false);
            exportButton.setOnClickListener(v -> exportFlattenedPdf());
        }

        setEditMode(PdfEditorOverlayView.EditMode.DRAW);
        updateHintText();
        setPickEnabled(false);

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.canConvert) {
                Toast.makeText(this, R.string.pdf_editor_no_permission, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            cloudUserName = session.name;
            setPickEnabled(true);
        }));
    }

    private void setPickEnabled(boolean enabled) {
        if (pickDeviceButton != null) {
            pickDeviceButton.setEnabled(enabled);
        }
        if (pickCloudButton != null) {
            pickCloudButton.setEnabled(enabled);
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        closePdf();
        super.onDestroy();
    }

    private void launchPickLocalPdf() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickLocalLauncher.launch(Intent.createChooser(intent, getString(R.string.pdf_editor_pick_device)));
    }

    private void showCloudSourceDialog() {
        if (cloudUserName == null) {
            Toast.makeText(this, R.string.pdf_editor_session_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_editor_cloud_source_title)
                .setItems(new CharSequence[]{
                        getString(R.string.pdf_editor_cloud_contracts),
                        getString(R.string.pdf_editor_cloud_reports)
                }, (dialog, which) -> {
                    int mode = which == 0
                            ? CloudStorageBrowserActivity.MODE_CONTRACTS
                            : CloudStorageBrowserActivity.MODE_REPORTS;
                    launchCloudPicker(mode);
                })
                .show();
    }

    private void launchCloudPicker(int entryMode) {
        Intent i = new Intent(this, CloudStorageBrowserActivity.class);
        i.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, entryMode);
        i.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, cloudUserName);
        i.putExtra(CloudStorageBrowserActivity.EXTRA_RETURN_STORAGE_PATH_ON_PICK, true);
        cloudPickLauncher.launch(i);
    }

    private void copyUriToCacheAndOpen(Uri uri) {
        worker.execute(() -> {
            try {
                File cachePdf = new File(getCacheDir(), "pdf_editor_src_" + System.currentTimeMillis() + ".pdf");
                try (InputStream in = getApplicationContext().getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(cachePdf)) {
                    if (in == null) {
                        throw new IOException("Could not open file.");
                    }
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                runOnUiThread(() -> openPdfFile(cachePdf));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "error";
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.pdf_editor_open_failed, msg), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void downloadCloudPdfAndOpen(String storagePath) {
        File local = new File(getCacheDir(), "pdf_editor_cloud_" + System.currentTimeMillis() + ".pdf");
        Toast.makeText(this, R.string.pdf_editor_downloading, Toast.LENGTH_SHORT).show();
        FirebaseStorage.getInstance().getReference().child(storagePath).getFile(local)
                .addOnSuccessListener(taskSnapshot -> openPdfFile(local))
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "error";
                    Toast.makeText(this, getString(R.string.pdf_editor_download_failed, msg), Toast.LENGTH_LONG).show();
                });
    }

    private void openPdfFile(File file) {
        closePdf();
        sourcePdfFile = file;
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pfd);
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "error";
            Toast.makeText(this, getString(R.string.pdf_editor_open_failed, msg), Toast.LENGTH_LONG).show();
            closePdf();
            return;
        }
        annotationsByPage.clear();
        adapter = new PdfEditorPageAdapter(pdfRenderer, annotationsByPage, pageCallbacks, widthForPages());
        recyclerView.setAdapter(adapter);
        if (exportButton != null) {
            exportButton.setEnabled(true);
        }
        recyclerView.post(() -> {
            if (adapter != null && recyclerView.getWidth() > 0) {
                adapter.setMaxWidthPx(recyclerView.getWidth());
                adapter.notifyDataSetChanged();
            }
        });
        updateHintText();
        Toast.makeText(this, getString(R.string.pdf_editor_loaded_pages, pdfRenderer.getPageCount()), Toast.LENGTH_SHORT).show();
    }

    private void scrollPages(int delta) {
        LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (lm == null) {
            return;
        }
        int first = lm.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return;
        }
        int target = first + delta;
        if (pdfRenderer != null) {
            target = Math.max(0, Math.min(pdfRenderer.getPageCount() - 1, target));
        } else {
            target = Math.max(0, target);
        }
        recyclerView.smoothScrollToPosition(target);
    }

    private int widthForPages() {
        int w = recyclerView.getWidth();
        if (w <= 0) {
            w = getResources().getDisplayMetrics().widthPixels - 32;
        }
        return Math.max(1, w);
    }

    private void closePdf() {
        if (pdfRenderer != null) {
            try {
                pdfRenderer.close();
            } catch (Exception ignored) {
            }
            pdfRenderer = null;
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException ignored) {
            }
            pfd = null;
        }
        sourcePdfFile = null;
        adapter = null;
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        annotationsByPage.clear();
        if (exportButton != null) {
            exportButton.setEnabled(false);
        }
    }

    private void setEditMode(PdfEditorOverlayView.EditMode mode) {
        editMode = mode;
        if (modeTextButton != null) {
            modeTextButton.setAlpha(mode == PdfEditorOverlayView.EditMode.TEXT ? 1f : 0.5f);
        }
        if (modeDrawButton != null) {
            modeDrawButton.setAlpha(mode == PdfEditorOverlayView.EditMode.DRAW ? 1f : 0.5f);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateHintText();
    }

    private void setInkColor(int color) {
        inkColor = color;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateHintText() {
        if (hintText == null) {
            return;
        }
        if (pdfRenderer == null) {
            hintText.setText(R.string.pdf_editor_hint_empty);
        } else if (editMode == PdfEditorOverlayView.EditMode.TEXT) {
            hintText.setText(R.string.pdf_editor_hint_text);
        } else {
            hintText.setText(R.string.pdf_editor_hint_draw);
        }
    }

    @Override
    public PdfEditorOverlayView.EditMode getEditMode() {
        return editMode;
    }

    @Override
    public int getInkColor() {
        return inkColor;
    }

    @Override
    public PdfEditorPageAnnotations annotationsForPage(int pageIndex) {
        PdfEditorPageAnnotations a = annotationsByPage.get(pageIndex);
        if (a == null) {
            a = new PdfEditorPageAnnotations();
            annotationsByPage.put(pageIndex, a);
        }
        return a;
    }

    @Override
    public void onRequestTextForPage(int pageIndex, float nx, float ny) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pdf_editor_text_dialog_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String t = input.getText() != null ? input.getText().toString().trim() : "";
                    if (t.isEmpty()) {
                        return;
                    }
                    PdfEditorPageAnnotations ann = annotationsForPage(pageIndex);
                    PdfEditorPageAnnotations.TextStamp st = new PdfEditorPageAnnotations.TextStamp();
                    st.nx = nx;
                    st.ny = ny;
                    st.text = t;
                    st.color = inkColor;
                    st.relTextSize = 0.045f;
                    ann.texts.add(st);
                    if (adapter != null) {
                        adapter.notifyItemChanged(pageIndex);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportFlattenedPdf() {
        if (sourcePdfFile == null || !sourcePdfFile.exists()) {
            Toast.makeText(this, R.string.pdf_editor_nothing_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        final SparseArray<PdfEditorPageAnnotations> snapshot = copyAnnotations(annotationsByPage);
        if (exportButton != null) {
            exportButton.setEnabled(false);
        }
        Toast.makeText(this, R.string.pdf_editor_export_working, Toast.LENGTH_SHORT).show();

        final File src = sourcePdfFile;
        worker.execute(() -> {
            try (ParcelFileDescriptor pfdLocal = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer rendererExport = new PdfRenderer(pfdLocal)) {

                int count = rendererExport.getPageCount();
                PdfDocument doc = new PdfDocument();
                for (int i = 0; i < count; i++) {
                    PdfRenderer.Page page = rendererExport.openPage(i);
                    try {
                        int w = page.getWidth();
                        int h = page.getHeight();
                        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        Canvas cv = new Canvas(bmp);
                        PdfEditorPageAnnotations ann = snapshot.get(i);
                        if (ann != null) {
                            drawAnnotationsOnCanvas(cv, w, h, ann);
                        }
                        PdfDocument.Page docPage = doc.startPage(new PdfDocument.PageInfo.Builder(w, h, i + 1).create());
                        docPage.getCanvas().drawBitmap(bmp, 0, 0, null);
                        doc.finishPage(docPage);
                        bmp.recycle();
                    } finally {
                        page.close();
                    }
                }
                File outDir = new File(getExternalFilesDir(null), "pdf_editor");
                if (!outDir.exists() && !outDir.mkdirs()) {
                    throw new IOException("Could not create output folder.");
                }
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File out = new File(outDir, "edited_" + stamp + ".pdf");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    doc.writeTo(fos);
                }
                doc.close();
                runOnUiThread(() -> {
                    if (exportButton != null) {
                        exportButton.setEnabled(true);
                    }
                    sharePdf(out);
                    Toast.makeText(this, getString(R.string.pdf_editor_export_done, out.getName()), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "error";
                runOnUiThread(() -> {
                    if (exportButton != null) {
                        exportButton.setEnabled(true);
                    }
                    Toast.makeText(this, getString(R.string.pdf_editor_export_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static SparseArray<PdfEditorPageAnnotations> copyAnnotations(SparseArray<PdfEditorPageAnnotations> src) {
        SparseArray<PdfEditorPageAnnotations> dst = new SparseArray<>();
        for (int k = 0; k < src.size(); k++) {
            int key = src.keyAt(k);
            PdfEditorPageAnnotations a = src.get(key);
            if (a == null) {
                continue;
            }
            PdfEditorPageAnnotations c = new PdfEditorPageAnnotations();
            for (PdfEditorPageAnnotations.TextStamp t : a.texts) {
                PdfEditorPageAnnotations.TextStamp nt = new PdfEditorPageAnnotations.TextStamp();
                nt.nx = t.nx;
                nt.ny = t.ny;
                nt.text = t.text;
                nt.color = t.color;
                nt.relTextSize = t.relTextSize;
                c.texts.add(nt);
            }
            for (PdfEditorPageAnnotations.InkStroke s : a.strokes) {
                PdfEditorPageAnnotations.InkStroke ns = new PdfEditorPageAnnotations.InkStroke();
                ns.color = s.color;
                ns.relStrokeWidth = s.relStrokeWidth;
                for (PointF p : s.points) {
                    ns.points.add(new PointF(p.x, p.y));
                }
                c.strokes.add(ns);
            }
            dst.put(key, c);
        }
        return dst;
    }

    private static void drawAnnotationsOnCanvas(Canvas canvas, int w, int h, PdfEditorPageAnnotations ann) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (PdfEditorPageAnnotations.TextStamp t : ann.texts) {
            if (t.text == null || t.text.isEmpty()) {
                continue;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(t.color);
            paint.setTextSize(Math.max(8f, t.relTextSize * h));
            canvas.drawText(t.text, t.nx * w, t.ny * h, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();
        for (PdfEditorPageAnnotations.InkStroke s : ann.strokes) {
            if (s.points.isEmpty()) {
                continue;
            }
            paint.setColor(s.color);
            paint.setStrokeWidth(Math.max(1f, s.relStrokeWidth * h));
            path.reset();
            path.moveTo(s.points.get(0).x * w, s.points.get(0).y * h);
            for (int i = 1; i < s.points.size(); i++) {
                path.lineTo(s.points.get(i).x * w, s.points.get(i).y * h);
            }
            canvas.drawPath(path, paint);
        }
    }

    private void sharePdf(File file) {
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.pdf_editor_share)));
    }
}
