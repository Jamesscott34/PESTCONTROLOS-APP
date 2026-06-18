package com.grpc.grpc.billing.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.InvoiceStoragePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.email.ui.EmailComposeActivity;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Lists platform invoices for the current build tenant ({@link BuildConfig#FLAVOR}).
 * Admin + super_admin, or users with canInvoice, may open and create invoices.
 */
public class InvoiceListActivity extends AppCompatActivity {

    public static final String EXTRA_CAN_CREATE = "INVOICE_CAN_CREATE";

    private ListView listView;
    private Button createButton;
    private final List<InvoiceRow> rows = new ArrayList<>();
    private InvoiceRowAdapter adapter;
    private boolean canCreate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice_list);

        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.invoice_not_available_offline, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) {
            return;
        }

        canCreate = getIntent().getBooleanExtra(EXTRA_CAN_CREATE, false);

        listView = findViewById(R.id.invoiceListView);
        createButton = findViewById(R.id.buttonCreateInvoice);
        createButton.setVisibility(canCreate ? View.VISIBLE : View.GONE);
        createButton.setOnClickListener(v -> startActivity(new Intent(this, CreateInvoiceActivity.class)));

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !canAccessInvoiceList(session)) {
                Toast.makeText(this, R.string.invoice_admin_only_list, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (canCreate && !session.canInvoice) {
                canCreate = false;
                createButton.setVisibility(View.GONE);
            }
            loadInvoices();
        }));

        adapter = new InvoiceRowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= rows.size()) return;
            showInvoiceActions(rows.get(position));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BuildConfig.IS_OFFLINE) {
            SessionManager.Session s = SessionManager.getCached(this);
            if (s != null && canAccessInvoiceList(s)) {
                loadInvoices();
            }
        }
    }

    private static boolean canAccessInvoiceList(@Nullable SessionManager.Session session) {
        return session != null && (session.isAdmin || session.canInvoice);
    }

    private void loadInvoices() {
        String companyId = InvoiceStoragePaths.companyIdFromBuild();
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        db.collection(FirestorePaths.INVOICE_LEDGER)
                .whereEqualTo("companyId", companyId)
                .get()
                .addOnSuccessListener(snap -> {
                    rows.clear();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            if (d == null || !d.exists()) continue;
                            InvoiceRow r = InvoiceRow.fromSnapshot(d);
                            if (r != null) rows.add(r);
                        }
                    }
                    Collections.sort(rows, (a, b) -> Long.compare(b.issueDateMillis, a.issueDateMillis));
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.invoice_list_load_failed, Toast.LENGTH_LONG).show());
    }

    private void showInvoiceActions(InvoiceRow row) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.invoice_action_open));
        actions.add(getString(R.string.invoice_action_download));
        actions.add(getString(R.string.invoice_action_email));
        actions.add(getString(R.string.invoice_action_share));
        SessionManager.Session session = SessionManager.getCached(this);
        boolean canDelete = session != null && session.canInvoice
                && (row.contractId == null || row.contractId.trim().isEmpty());
        if (canDelete) {
            actions.add(getString(R.string.invoice_action_delete));
        }
        new AlertDialog.Builder(this)
                .setTitle(row.invoiceNumber)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String a = actions.get(which);
                    if (a.equals(getString(R.string.invoice_action_open))) {
                        downloadAndOpen(row);
                    } else if (a.equals(getString(R.string.invoice_action_download))) {
                        enqueueInvoiceDownload(row);
                    } else if (a.equals(getString(R.string.invoice_action_email))) {
                        openInvoiceEmailComposer(row);
                    } else if (a.equals(getString(R.string.invoice_action_share))) {
                        downloadAndShare(row);
                    } else if (a.equals(getString(R.string.invoice_action_delete))) {
                        confirmDelete(row);
                    }
                })
                .show();
    }

    private void openInvoiceEmailComposer(InvoiceRow row) {
        Intent i = new Intent(this, EmailComposeActivity.class);
        if (row.contractId != null && !row.contractId.trim().isEmpty()) {
            i.putExtra(EmailComposeActivity.EXTRA_CONTRACT_ID, row.contractId.trim());
        }
        String name = row.customerName != null ? row.customerName : "";
        String email = row.customerEmail != null ? row.customerEmail : "";
        i.putExtra(EmailComposeActivity.EXTRA_CUSTOMER_NAME, name);
        i.putExtra(EmailComposeActivity.EXTRA_CUSTOMER_EMAIL, email);
        i.putExtra(EmailComposeActivity.EXTRA_ADDRESS, "");
        i.putExtra(EmailComposeActivity.EXTRA_INVOICE_NUMBER, row.invoiceNumber != null ? row.invoiceNumber : "");
        String amount = row.amountText != null ? row.amountText : "";
        i.putExtra(EmailComposeActivity.EXTRA_INVOICE_AMOUNT, amount);
        if (row.storagePath != null && !row.storagePath.trim().isEmpty()) {
            i.putExtra(EmailComposeActivity.EXTRA_PREFILL_ATTACHMENT_STORAGE_PATH, row.storagePath.trim());
        }
        startActivity(i);
    }

    private void enqueueInvoiceDownload(InvoiceRow row) {
        if (row.storagePath == null || row.storagePath.trim().isEmpty()) {
            Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String fileLabel = row.pdfFileName != null && !row.pdfFileName.trim().isEmpty()
                ? row.pdfFileName.trim().replaceAll("[\\\\/]", "_") : "invoice.pdf";
        StorageReference refPath = FirebaseStorage.getInstance().getReference().child(row.storagePath.trim());
        refPath.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    DownloadManager.Request request = new DownloadManager.Request(uri);
                    request.setTitle(fileLabel);
                    request.setMimeType("application/pdf");
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    try {
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileLabel);
                    } catch (IllegalStateException e) {
                        Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if (dm == null) {
                        Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    dm.enqueue(request);
                    Toast.makeText(this, R.string.invoice_download_started, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_LONG).show());
    }

    private File cacheFileFor(InvoiceRow row) {
        File dir = new File(getCacheDir(), "invoices");
        if (!dir.exists() && !dir.mkdirs()) {
            return new File(getCacheDir(), row.pdfFileName != null ? row.pdfFileName : "invoice.pdf");
        }
        String name = row.pdfFileName != null && !row.pdfFileName.trim().isEmpty()
                ? row.pdfFileName.trim().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "invoice.pdf";
        return new File(dir, name);
    }

    private void downloadAndOpen(InvoiceRow row) {
        if (row.storagePath == null || row.storagePath.trim().isEmpty()) {
            Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        File out = cacheFileFor(row);
        Toast.makeText(this, R.string.pdf_editor_downloading, Toast.LENGTH_SHORT).show();
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(row.storagePath);
        ref.getFile(out)
                .addOnSuccessListener(t -> {
                    Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", out);
                    Intent view = new Intent(Intent.ACTION_VIEW);
                    view.setDataAndType(uri, "application/pdf");
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(Intent.createChooser(view, getString(R.string.invoice_action_open)));
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_LONG).show());
    }

    private void downloadAndShare(InvoiceRow row) {
        if (row.storagePath == null || row.storagePath.trim().isEmpty()) {
            Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        File out = cacheFileFor(row);
        StorageReference ref = FirebaseStorage.getInstance().getReference().child(row.storagePath);
        ref.getFile(out)
                .addOnSuccessListener(t -> {
                    Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", out);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/pdf");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, getString(R.string.invoice_action_share)));
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.invoice_download_failed, Toast.LENGTH_LONG).show());
    }

    private void confirmDelete(InvoiceRow row) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.invoice_delete_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> deleteInvoice(row))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteInvoice(InvoiceRow row) {
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        if (row.docId == null) return;
        db.collection(FirestorePaths.INVOICE_LEDGER).document(row.docId).delete()
                .addOnSuccessListener(v -> {
                    if (row.storagePath != null && !row.storagePath.trim().isEmpty()) {
                        FirebaseStorage.getInstance().getReference().child(row.storagePath).delete()
                                .addOnCompleteListener(t -> runOnUiThread(() -> {
                                    Toast.makeText(this, R.string.invoice_deleted, Toast.LENGTH_SHORT).show();
                                    loadInvoices();
                                }));
                    } else {
                        Toast.makeText(this, R.string.invoice_deleted, Toast.LENGTH_SHORT).show();
                        loadInvoices();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.invoice_delete_failed, Toast.LENGTH_LONG).show());
    }

    private class InvoiceRowAdapter extends ArrayAdapter<InvoiceRow> {
        InvoiceRowAdapter() {
            super(InvoiceListActivity.this, R.layout.item_invoice_row, rows);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_invoice_row, parent, false);
            }
            InvoiceRow r = rows.get(position);
            TextView l1 = v.findViewById(R.id.invoiceRowLine1);
            TextView l2 = v.findViewById(R.id.invoiceRowLine2);
            l1.setText(r.invoiceNumber + " — " + (r.title != null ? r.title : ""));
            String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(new Date(r.issueDateMillis));
            l2.setText(dateStr + " · " + (r.customerName != null ? r.customerName : ""));
            return v;
        }
    }

    private static final class InvoiceRow {
        final String docId;
        final String invoiceNumber;
        final String title;
        final String customerName;
        final String customerEmail;
        final String contractId;
        final String amountText;
        final long issueDateMillis;
        final String storagePath;
        final String pdfFileName;

        InvoiceRow(String docId, String invoiceNumber, String title, String customerName, String customerEmail,
                   String contractId, String amountText, long issueDateMillis, String storagePath, String pdfFileName) {
            this.docId = docId;
            this.invoiceNumber = invoiceNumber != null ? invoiceNumber : "";
            this.title = title;
            this.customerName = customerName;
            this.customerEmail = customerEmail;
            this.contractId = contractId;
            this.amountText = amountText;
            this.issueDateMillis = issueDateMillis;
            this.storagePath = storagePath;
            this.pdfFileName = pdfFileName;
        }

        @Nullable
        static InvoiceRow fromSnapshot(DocumentSnapshot d) {
            if (d == null) return null;
            String num = d.getString("invoiceNumber");
            if (num == null) num = "";
            String title = d.getString("title");
            String customer = d.getString("customerName");
            String customerEmail = d.getString("customerEmail");
            String contractId = d.getString("contractId");
            String amountText = d.getString("amount");
            if (amountText == null) amountText = "";
            long ms = 0L;
            Object o = d.get("issueDateMillis");
            if (o instanceof Number) ms = ((Number) o).longValue();
            String path = d.getString("storagePath");
            String fn = d.getString("pdfFileName");
            return new InvoiceRow(d.getId(), num, title, customer, customerEmail, contractId, amountText, ms, path, fn);
        }
    }
}
