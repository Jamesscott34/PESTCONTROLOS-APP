package com.grpc.grpc.converter.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.converter.PdfPasswordRequiredException;
import com.grpc.grpc.converter.PdfTextExtractor;
import com.grpc.grpc.converter.PdfToWordExporter;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PDF → Word (.docx) and PDF → plain text: Word uses page images; text uses hybrid extraction.
 * Gated by Firestore {@code users/{uid}.canConvert}.
 */
public class ConverterActivity extends AppCompatActivity {

    private TextView statusText;
    private Button convertToWordButton;
    private Button openButton;
    private Button shareButton;
    private Button pickPdfTextButton;
    private EditText extractedText;
    private Button copyTextButton;
    private Button shareTextButton;
    private Button saveAsTextButton;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    @Nullable
    private File lastOutputFile;
    @Nullable
    private Uri pendingTextExtractUri;

    private final ActivityResultLauncher<Intent> pickPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) return;
                convertUriToDocx(uri);
            });

    private final ActivityResultLauncher<Intent> pickPdfTextLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) {
                    return;
                }
                extractPlainTextFromUri(uri, null);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_converter);

        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.converter_not_available, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) {
            return;
        }

        setTitle(R.string.converter_title);
        statusText = findViewById(R.id.converterStatusText);
        convertToWordButton = findViewById(R.id.converterConvertToWordButton);
        openButton = findViewById(R.id.converterOpenDocxButton);
        shareButton = findViewById(R.id.converterShareDocxButton);
        Button howButton = findViewById(R.id.converterHowItWorksButton);
        pickPdfTextButton = findViewById(R.id.converterPickPdfTextButton);
        extractedText = findViewById(R.id.converterExtractedText);
        copyTextButton = findViewById(R.id.converterCopyTextButton);
        shareTextButton = findViewById(R.id.converterShareTextButton);
        saveAsTextButton = findViewById(R.id.converterSaveTextButton);

        if (openButton != null) {
            openButton.setEnabled(false);
            openButton.setOnClickListener(v -> openLastOutput());
        }
        if (shareButton != null) {
            shareButton.setEnabled(false);
            shareButton.setOnClickListener(v -> shareLastOutput());
        }
        if (convertToWordButton != null) {
            convertToWordButton.setEnabled(false);
            convertToWordButton.setOnClickListener(v -> launchPickPdf());
        }
        if (howButton != null) {
            howButton.setOnClickListener(v ->
                    Toast.makeText(this, R.string.converter_how_it_works_detail, Toast.LENGTH_LONG).show());
        }
        if (pickPdfTextButton != null) {
            pickPdfTextButton.setOnClickListener(v -> launchPickPdfForText());
        }
        if (copyTextButton != null) {
            copyTextButton.setOnClickListener(v -> copyExtractedText());
        }
        if (shareTextButton != null) {
            shareTextButton.setOnClickListener(v -> shareExtractedTextPlain());
        }
        if (saveAsTextButton != null) {
            saveAsTextButton.setOnClickListener(v -> showSaveExtractedAsDialog());
        }
        if (extractedText != null) {
            extractedText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateTextActionButtons();
                }
            });
        }

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.canConvert) {
                Toast.makeText(this, R.string.converter_no_permission, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (convertToWordButton != null) {
                convertToWordButton.setEnabled(true);
            }
            if (pickPdfTextButton != null) {
                pickPdfTextButton.setEnabled(true);
            }
            setStatus(getString(R.string.converter_ready));
        }));
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void launchPickPdf() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickPdfLauncher.launch(Intent.createChooser(intent, getString(R.string.converter_convert_to_word)));
    }

    private void launchPickPdfForText() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        pickPdfTextLauncher.launch(Intent.createChooser(intent, getString(R.string.converter_pick_pdf_text)));
    }

    private void updateTextActionButtons() {
        String t = extractedText != null && extractedText.getText() != null
                ? extractedText.getText().toString().trim()
                : "";
        boolean has = !t.isEmpty();
        if (copyTextButton != null) {
            copyTextButton.setEnabled(has);
        }
        if (shareTextButton != null) {
            shareTextButton.setEnabled(has);
        }
        if (saveAsTextButton != null) {
            saveAsTextButton.setEnabled(has);
        }
    }

    private void copyExtractedText() {
        if (extractedText == null || extractedText.getText() == null) {
            Toast.makeText(this, R.string.converter_text_empty_clipboard, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = extractedText.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, R.string.converter_text_empty_clipboard, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.converter_text_section_title), text));
        Toast.makeText(this, R.string.converter_text_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareExtractedTextPlain() {
        String text = getExtractedTextOrNull();
        if (text == null) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, getString(R.string.converter_share_text_chooser)));
    }

    private void showSaveExtractedAsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.converter_save_as_title)
                .setItems(new CharSequence[]{
                        getString(R.string.converter_save_as_txt),
                        getString(R.string.converter_save_as_pdf)
                }, (d, which) -> {
                    if (which == 0) {
                        saveExtractedTextToTxtFile();
                    } else {
                        saveExtractedTextToPdfFile();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveExtractedTextToTxtFile() {
        File f = writeExtractedTextToConverterTxt();
        if (f == null) {
            return;
        }
        Toast.makeText(this, getString(R.string.converter_text_saved, f.getName()), Toast.LENGTH_LONG).show();
    }

    private void saveExtractedTextToPdfFile() {
        String text = getExtractedTextOrNull();
        if (text == null) {
            return;
        }
        try {
            File outDir = new File(getExternalFilesDir(null), "converter");
            if (!outDir.exists() && !outDir.mkdirs()) {
                throw new IOException("Could not create converter folder.");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File out = new File(outDir, "extracted_" + stamp + ".pdf");
            try (PdfWriter writer = new PdfWriter(out);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document doc = new Document(pdf)) {
                for (String line : text.split("\n", -1)) {
                    doc.add(new Paragraph(line.isEmpty() ? "\u00a0" : line));
                }
            }
            Toast.makeText(this, getString(R.string.converter_text_saved_pdf, out.getName()), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "error";
            Toast.makeText(this, getString(R.string.converter_text_failed, msg), Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private File writeExtractedTextToConverterTxt() {
        String text = getExtractedTextOrNull();
        if (text == null) {
            return null;
        }
        try {
            File outDir = new File(getExternalFilesDir(null), "converter");
            if (!outDir.exists() && !outDir.mkdirs()) {
                throw new IOException("Could not create converter folder.");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File out = new File(outDir, "extracted_" + stamp + ".txt");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return out;
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.converter_text_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Nullable
    private String getExtractedTextOrNull() {
        if (extractedText == null || extractedText.getText() == null) {
            Toast.makeText(this, R.string.converter_text_empty_clipboard, Toast.LENGTH_SHORT).show();
            return null;
        }
        String text = extractedText.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, R.string.converter_text_empty_clipboard, Toast.LENGTH_SHORT).show();
            return null;
        }
        return text;
    }

    private void extractPlainTextFromUri(Uri uri, @Nullable byte[] passwordUtf8) {
        pendingTextExtractUri = uri;
        extractPlainTextWithPassword(uri, passwordUtf8);
    }

    private void extractPlainTextWithPassword(Uri uri, @Nullable byte[] passwordUtf8) {
        SessionManager.Session s = SessionManager.getCached(this);
        if (s == null || !s.canConvert) {
            Toast.makeText(this, R.string.converter_no_permission, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (pickPdfTextButton != null) {
            pickPdfTextButton.setEnabled(false);
        }
        if (convertToWordButton != null) {
            convertToWordButton.setEnabled(false);
        }
        setStatus(getString(R.string.converter_text_working));

        worker.execute(() -> {
            try {
                File cachePdf = new File(getCacheDir(), "convert_text_src_" + System.currentTimeMillis() + ".pdf");
                copyStreamToFile(uri, cachePdf);
                String extracted = PdfTextExtractor.extract(cachePdf, passwordUtf8, getCacheDir());
                //noinspection ResultOfMethodCallIgnored
                cachePdf.delete();
                int markers = countPageMarkers(extracted);
                final String finalText = extracted;
                runOnUiThread(() -> {
                    pendingTextExtractUri = null;
                    if (pickPdfTextButton != null) {
                        pickPdfTextButton.setEnabled(true);
                    }
                    if (convertToWordButton != null) {
                        convertToWordButton.setEnabled(true);
                    }
                    if (extractedText != null) {
                        extractedText.setText(finalText);
                    }
                    updateTextActionButtons();
                    setStatus(getString(R.string.converter_text_done, markers));
                });
            } catch (PdfPasswordRequiredException e) {
                runOnUiThread(() -> {
                    if (pickPdfTextButton != null) {
                        pickPdfTextButton.setEnabled(true);
                    }
                    if (convertToWordButton != null) {
                        convertToWordButton.setEnabled(true);
                    }
                    String em = e.getMessage() != null ? e.getMessage() : "";
                    if (em.toLowerCase(Locale.ROOT).contains("incorrect")) {
                        Toast.makeText(this, R.string.converter_password_wrong, Toast.LENGTH_SHORT).show();
                    }
                    showPdfPasswordDialog();
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "error";
                runOnUiThread(() -> {
                    pendingTextExtractUri = null;
                    if (pickPdfTextButton != null) {
                        pickPdfTextButton.setEnabled(true);
                    }
                    if (convertToWordButton != null) {
                        convertToWordButton.setEnabled(true);
                    }
                    setStatus(getString(R.string.converter_text_failed, msg));
                    Toast.makeText(this, getString(R.string.converter_text_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showPdfPasswordDialog() {
        if (pendingTextExtractUri == null) {
            return;
        }
        final EditText input = new EditText(this);
        input.setHint(R.string.converter_password_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.converter_password_title)
                .setMessage(R.string.converter_password_message)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String p = input.getText() != null ? input.getText().toString() : "";
                    byte[] bytes = p.getBytes(StandardCharsets.UTF_8);
                    Uri uri = pendingTextExtractUri;
                    if (uri != null) {
                        extractPlainTextWithPassword(uri, bytes);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    pendingTextExtractUri = null;
                    setStatus(getString(R.string.converter_ready));
                })
                .show();
    }

    private static int countPageMarkers(String extracted) {
        if (extracted == null || extracted.isEmpty()) {
            return 0;
        }
        int c = 0;
        int i = 0;
        while (true) {
            int j = extracted.indexOf("--- Page ", i);
            if (j < 0) {
                break;
            }
            c++;
            i = j + 1;
        }
        return c;
    }

    private void setStatus(String s) {
        if (statusText != null) {
            statusText.setText(s);
        }
    }

    private void convertUriToDocx(Uri uri) {
        SessionManager.Session s = SessionManager.getCached(this);
        if (s == null || !s.canConvert) {
            Toast.makeText(this, R.string.converter_no_permission, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (convertToWordButton != null) {
            convertToWordButton.setEnabled(false);
        }
        if (openButton != null) {
            openButton.setEnabled(false);
        }
        if (shareButton != null) {
            shareButton.setEnabled(false);
        }
        lastOutputFile = null;
        setStatus(getString(R.string.converter_working));

        worker.execute(() -> {
            try {
                File cachePdf = new File(getCacheDir(), "convert_src_" + System.currentTimeMillis() + ".pdf");
                copyStreamToFile(uri, cachePdf);
                File outDir = new File(getExternalFilesDir(null), "converter");
                if (!outDir.exists() && !outDir.mkdirs()) {
                    throw new IOException("Could not create converter folder.");
                }
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                final File outFile = new File(outDir, "converted_" + stamp + ".docx");
                int pages = PdfToWordExporter.exportPdfFileToDocx(cachePdf, outFile);
                //noinspection ResultOfMethodCallIgnored
                cachePdf.delete();
                final int pagesFinal = pages;
                runOnUiThread(() -> {
                    lastOutputFile = outFile;
                    if (convertToWordButton != null) {
                        convertToWordButton.setEnabled(true);
                    }
                    if (openButton != null) {
                        openButton.setEnabled(true);
                    }
                    if (shareButton != null) {
                        shareButton.setEnabled(true);
                    }
                    setStatus(getString(R.string.converter_done, pagesFinal, outFile.getName()));
                    Toast.makeText(this, R.string.converter_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "error";
                runOnUiThread(() -> {
                    if (convertToWordButton != null) {
                        convertToWordButton.setEnabled(true);
                    }
                    setStatus(getString(R.string.converter_failed, msg));
                    Toast.makeText(this, getString(R.string.converter_failed, msg), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void copyStreamToFile(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IOException("Could not open file.");
            }
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private void openLastOutput() {
        if (lastOutputFile == null || !lastOutputFile.exists()) {
            Toast.makeText(this, R.string.converter_no_output, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", lastOutputFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.converter_open_with)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.converter_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareLastOutput() {
        if (lastOutputFile == null || !lastOutputFile.exists()) {
            Toast.makeText(this, R.string.converter_no_output, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", lastOutputFile);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, getString(R.string.converter_share)));
    }
}