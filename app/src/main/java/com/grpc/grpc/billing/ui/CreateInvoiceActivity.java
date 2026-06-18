package com.grpc.grpc.billing.ui;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.billing.pdf.InvoicePdfGenerator;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.StorageMetricsHelper;
import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.core.UserRepository;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.contracts.ContractSearch;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates a customer invoice PDF with flavor branding, uploads to Storage, and writes the ledger.
 */
public class CreateInvoiceActivity extends AppCompatActivity {

    public static final String EXTRA_CONTRACT_ID = "CONTRACT_ID";
    public static final String EXTRA_CUSTOMER_NAME = "CUSTOMER_NAME";
    public static final String EXTRA_CUSTOMER_EMAIL = "CUSTOMER_EMAIL";
    public static final String EXTRA_CUSTOMER_ADDRESS = "CUSTOMER_ADDRESS";

    private static final String TPL_INITIAL = "initial_setup";
    private static final String TPL_MONTHLY = "monthly_maintenance";
    private static final String TPL_FEATURE = "feature_update";
    private static final String TPL_CUSTOM = "custom";

    private Button buttonSelectContract;
    private TextView textSelectedContract;
    private RadioGroup radioTemplate;
    private EditText editName;
    private EditText editEmail;
    private EditText editTitle;
    private EditText editBody;
    private EditText editAmount;
    private EditText editVatRate;
    private EditText editNotes;
    private TextView textTotalsPreview;
    private Button buttonGo;
    private String selectedContractId = "";
    private String customerAddress = "";
    private final List<ContractOption> contractOptions = new ArrayList<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_invoice);

        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.invoice_not_available_offline, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) {
            return;
        }

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || !session.canInvoice) {
                Toast.makeText(this, R.string.invoice_access_denied, Toast.LENGTH_LONG).show();
                finish();
            }
        }));

        buttonSelectContract = findViewById(R.id.buttonSelectInvoiceContract);
        textSelectedContract = findViewById(R.id.textSelectedInvoiceContract);
        radioTemplate = findViewById(R.id.radioInvoiceTemplate);
        editName = findViewById(R.id.editInvoiceCustomerName);
        editEmail = findViewById(R.id.editInvoiceCustomerEmail);
        editTitle = findViewById(R.id.editInvoiceTitle);
        editBody = findViewById(R.id.editInvoiceBody);
        editAmount = findViewById(R.id.editInvoiceAmount);
        editVatRate = findViewById(R.id.editInvoiceVatRate);
        editNotes = findViewById(R.id.editInvoiceNotes);
        textTotalsPreview = findViewById(R.id.textInvoiceTotalsPreview);
        buttonGo = findViewById(R.id.buttonGenerateInvoice);

        radioTemplate.setOnCheckedChangeListener((group, checkedId) -> applyTemplate(checkedId));
        radioTemplate.check(R.id.radioTplInitial);

        TextWatcher totalsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshTotalsPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        editAmount.addTextChangedListener(totalsWatcher);
        editVatRate.addTextChangedListener(totalsWatcher);

        buttonGo.setOnClickListener(v -> submit());
        if (buttonSelectContract != null) {
            buttonSelectContract.setOnClickListener(v -> showContractPicker());
        }
        updateSelectedContractLabel();
        applyIntentPrefill();
        loadContracts();
    }

    private void applyIntentPrefill() {
        selectedContractId = value(getIntent().getStringExtra(EXTRA_CONTRACT_ID));
        String name = value(getIntent().getStringExtra(EXTRA_CUSTOMER_NAME));
        String email = value(getIntent().getStringExtra(EXTRA_CUSTOMER_EMAIL));
        if (!name.isEmpty()) editName.setText(name);
        if (!email.isEmpty()) editEmail.setText(email);
        customerAddress = value(getIntent().getStringExtra(EXTRA_CUSTOMER_ADDRESS));
    }

    private void loadContracts() {
        SessionManager.ensureLoaded(this, session -> UserRepository.ensureProfileForCurrentUser(this, session, profile -> runOnUiThread(() -> {
            if (session == null) return;

            Query query;
            if (session.isAdmin) {
                query = FirebaseHelper.getFirestore().collection(FirestorePaths.CONTRACTS);
            } else {
                String contractKey = session.contractKey != null ? session.contractKey.trim().toLowerCase(Locale.ROOT) : "";
                if (contractKey.isEmpty() && profile != null && profile.contractKey != null) {
                    contractKey = profile.contractKey.trim().toLowerCase(Locale.ROOT);
                }
                if (contractKey.isEmpty()) {
                    runOnUiThread(this::updateSelectedContractLabel);
                    return;
                }
                query = FirebaseHelper.getFirestore()
                        .collection(FirestorePaths.CONTRACTS)
                        .whereEqualTo("assignedTech", contractKey);
            }

            query.get()
                    .addOnSuccessListener(snapshot -> {
                        contractOptions.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            ContractOption option = new ContractOption(
                                    doc.getId(),
                                    value(doc.getString("name")),
                                    value(doc.getString("email")),
                                    value(doc.getString("address")),
                                    value(doc.getString("lastVisit"))
                            );
                            if (!option.name.isEmpty()) {
                                contractOptions.add(option);
                            }
                        }
                        if (!selectedContractId.isEmpty()) {
                            for (ContractOption option : contractOptions) {
                                if (option.id.equals(selectedContractId)) {
                                    applyContractOption(option);
                                    break;
                                }
                            }
                        }
                        updateSelectedContractLabel();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, getString(R.string.invoice_save_failed) + " " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        })));
    }

    private void showContractPicker() {
        View view = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = view.findViewById(R.id.searchBar);
        ListView listView = new ListView(this);
        if (view instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view).addView(listView);
        }

        List<ContractOption> filtered = new ArrayList<>();
        filtered.add(ContractOption.none(this));
        filtered.addAll(contractOptions);

        ArrayAdapter<ContractOption> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, filtered);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.invoice_select_contract_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            ContractOption option = filtered.get(position);
            if (option.id.isEmpty()) {
                clearContractSelection();
            } else {
                applyContractOption(option);
            }
            dialog.dismiss();
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtered.clear();
                String query = s != null ? s.toString() : "";
                if (query.trim().isEmpty()
                        || ContractSearch.matches(getString(R.string.invoice_contract_none), null, null, null, query)) {
                    filtered.add(ContractOption.none(CreateInvoiceActivity.this));
                }
                for (ContractOption option : contractOptions) {
                    if (ContractSearch.matches(option.name, option.email, option.address, option.id, query)) {
                        filtered.add(option);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private void applyContractOption(ContractOption option) {
        if (option == null || option.id.isEmpty()) return;
        selectedContractId = option.id;
        editName.setText(option.name);
        editEmail.setText(option.email);
        customerAddress = option.address;
        updateSelectedContractLabel();
    }

    private void clearContractSelection() {
        selectedContractId = "";
        customerAddress = "";
        updateSelectedContractLabel();
    }

    private void updateSelectedContractLabel() {
        if (textSelectedContract == null) return;
        if (selectedContractId == null || selectedContractId.isEmpty()) {
            textSelectedContract.setText(R.string.invoice_contract_none);
            return;
        }
        for (ContractOption option : contractOptions) {
            if (option.id.equals(selectedContractId)) {
                textSelectedContract.setText(option.toString());
                return;
            }
        }
        textSelectedContract.setText(R.string.invoice_contract_none);
    }

    private void applyTemplate(int checkedId) {
        if (checkedId == R.id.radioTplMonthly) {
            editTitle.setText(getString(R.string.invoice_tpl_monthly_title));
            editBody.setText(getString(R.string.invoice_tpl_monthly_body));
            editNotes.setText(getString(R.string.invoice_tpl_monthly_notes));
        } else if (checkedId == R.id.radioTplFeature) {
            editTitle.setText(getString(R.string.invoice_tpl_feature_title));
            editBody.setText(getString(R.string.invoice_tpl_feature_body));
            editNotes.setText(getString(R.string.invoice_tpl_feature_notes));
        } else if (checkedId == R.id.radioTplCustom) {
            editTitle.setText(getString(R.string.invoice_tpl_custom_title));
            editBody.setText(getString(R.string.invoice_tpl_custom_body));
            editNotes.setText(getString(R.string.invoice_tpl_custom_notes));
        } else {
            editTitle.setText(getString(R.string.invoice_tpl_initial_title));
            editBody.setText(getString(R.string.invoice_tpl_initial_body));
            editNotes.setText(getString(R.string.invoice_tpl_initial_notes));
        }
    }

    private void refreshTotalsPreview() {
        String amountStr = editAmount.getText() != null ? editAmount.getText().toString().trim() : "";
        String vatStr = editVatRate.getText() != null ? editVatRate.getText().toString().trim().replace(",", ".") : "13.5";
        if (vatStr.isEmpty()) vatStr = "13.5";
        try {
            double subtotal = Double.parseDouble(amountStr);
            double vatRate = Double.parseDouble(vatStr);
            if (subtotal <= 0 || vatRate < 0) {
                textTotalsPreview.setVisibility(TextView.GONE);
                return;
            }
            double vatAmount = Math.round(subtotal * (vatRate / 100.0d) * 100.0d) / 100.0d;
            double total = Math.round((subtotal + vatAmount) * 100.0d) / 100.0d;
            textTotalsPreview.setVisibility(TextView.VISIBLE);
            textTotalsPreview.setText(getString(
                    R.string.invoice_totals_preview,
                    money(subtotal),
                    trimMoneyRate(vatRate),
                    money(vatAmount),
                    money(total)
            ));
        } catch (Exception ignored) {
            textTotalsPreview.setVisibility(TextView.GONE);
        }
    }

    private String templateKeyForSelection() {
        int checkedId = radioTemplate.getCheckedRadioButtonId();
        if (checkedId == R.id.radioTplMonthly) return TPL_MONTHLY;
        if (checkedId == R.id.radioTplFeature) return TPL_FEATURE;
        if (checkedId == R.id.radioTplCustom) return TPL_CUSTOM;
        return TPL_INITIAL;
    }

    private String templateLabelForPdf() {
        int checkedId = radioTemplate.getCheckedRadioButtonId();
        RadioButton button = findViewById(checkedId);
        if (button != null && button.getText() != null) {
            return button.getText().toString();
        }
        return getString(R.string.invoice_template_initial_setup);
    }

    private String safeFilePrefix() {
        String raw = TenantBranding.filenamePrefix(this);
        if (raw == null) raw = "INV";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        if (sb.length() == 0) sb.append("INV");
        return sb.toString().toUpperCase(Locale.UK);
    }

    private void submit() {
        String customer = editName.getText() != null ? editName.getText().toString().trim() : "";
        String title = editTitle.getText() != null ? editTitle.getText().toString().trim() : "";
        String amount = editAmount.getText() != null ? editAmount.getText().toString().trim() : "";
        String vatRateText = editVatRate.getText() != null ? editVatRate.getText().toString().trim().replace(",", ".") : "13.5";
        if (vatRateText.isEmpty()) vatRateText = "13.5";
        double subtotal;
        double vatRate;
        try {
            subtotal = Double.parseDouble(amount);
            vatRate = Double.parseDouble(vatRateText);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.invoice_validation_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (customer.isEmpty() || title.isEmpty() || amount.isEmpty() || subtotal <= 0 || vatRate < 0) {
            Toast.makeText(this, R.string.invoice_validation_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (!SessionManager.canInvoice(this)) {
            Toast.makeText(this, R.string.invoice_access_denied, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final String contractId = selectedContractId != null ? selectedContractId.trim() : "";
        final boolean hasContract = !contractId.isEmpty();

        buttonGo.setEnabled(false);

        String companyId = BuildConfig.FLAVOR != null ? BuildConfig.FLAVOR : "grpc";
        FirebaseFirestore db = FirebaseHelper.getFirestore();
        DocumentReference counterRef = db.collection(FirestorePaths.INVOICE_COUNTERS).document(companyId);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(counterRef);
                    long seq = 1L;
                    if (snap.exists()) {
                        Long l = snap.getLong("nextSeq");
                        if (l != null && l > 0) seq = l;
                    }
                    Map<String, Object> upd = new HashMap<>();
                    upd.put("nextSeq", seq + 1);
                    transaction.set(counterRef, upd, SetOptions.merge());
                    return seq;
                })
                .addOnSuccessListener(seq -> io.execute(() -> {
                    try {
                        String prefix = safeFilePrefix();
                        String invNum = prefix + "-" + String.format(Locale.UK, "%03d", seq);
                        Date issue = new Date();
                        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(issue);
                        String year = new SimpleDateFormat("yyyy", Locale.UK).format(issue);
                        String safeCustomer = sanitizeFileToken(customer);
                        String pdfName = safeCustomer + "_" + invNum + "_" + day + ".pdf";
                        String dir = hasContract
                                ? "contracts/" + contractId + "/invoices/" + year
                                : "invoices/" + year;
                        String storagePath = dir + "/" + pdfName;

                        File cacheDir = new File(getCacheDir(), "invoices");
                        if (!cacheDir.exists()) cacheDir.mkdirs();
                        File out = new File(cacheDir, pdfName);

                        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
                        String body = editBody.getText() != null ? editBody.getText().toString().trim() : "";
                        String notes = editNotes.getText() != null ? editNotes.getText().toString().trim() : "";
                        double vatAmount = Math.round(subtotal * (vatRate / 100.0d) * 100.0d) / 100.0d;
                        double total = Math.round((subtotal + vatAmount) * 100.0d) / 100.0d;
                        String subtotalText = money(subtotal);
                        String vatAmountText = money(vatAmount);
                        String totalText = money(total);

                        InvoicePdfGenerator.generate(
                                this,
                                out,
                                invNum,
                                issue,
                                customer,
                                email,
                                customerAddress,
                                title,
                                body,
                                subtotal,
                                vatRate,
                                vatAmount,
                                total,
                                notes,
                                templateLabelForPdf()
                        );

                        StorageReference ref = FirebaseStorage.getInstance().getReference().child(storagePath);
                        Tasks.await(ref.putFile(Uri.fromFile(out)));
                        StorageMetricsHelper.recordUpload();

                        String uid = "";
                        try {
                            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            }
                        } catch (Exception ignored) {}

                        Map<String, Object> doc = new HashMap<>();
                        doc.put("companyId", companyId);
                        if (hasContract) {
                            doc.put("contractId", contractId);
                        }
                        doc.put("invoiceNumber", invNum);
                        doc.put("customerName", customer);
                        doc.put("customerEmail", email);
                        doc.put("title", title);
                        doc.put("description", body);
                        doc.put("amount", totalText);
                        doc.put("subtotal", subtotal);
                        doc.put("vatRate", vatRate);
                        doc.put("vatAmount", vatAmount);
                        doc.put("total", total);
                        doc.put("notes", notes);
                        doc.put("templateId", templateKeyForSelection());
                        doc.put("issueDateMillis", issue.getTime());
                        doc.put("storagePath", storagePath);
                        doc.put("pdfFileName", pdfName);
                        doc.put("createdByUid", uid);

                        Tasks.await(db.collection(FirestorePaths.INVOICE_LEDGER).add(doc));

                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.invoice_saved, Toast.LENGTH_LONG).show();
                            buttonGo.setEnabled(true);
                            finish();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            buttonGo.setEnabled(true);
                            Toast.makeText(this, getString(R.string.invoice_save_failed) + " " + (e.getMessage() != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
                        });
                    }
                }))
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    buttonGo.setEnabled(true);
                    Toast.makeText(this, getString(R.string.invoice_save_failed) + " " + (e.getMessage() != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
                }));
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private static String value(String raw) {
        return raw != null ? raw.trim() : "";
    }

    private static String money(double value) {
        return String.format(Locale.UK, "EUR %.2f", value);
    }

    private static String sanitizeFileToken(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "INVOICE";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < 80; i++) {
            char c = raw.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('_');
            }
        }
        String out = sb.toString().replaceAll("_+", "_").replaceAll("^-+", "").replaceAll("-+$", "");
        return out.isEmpty() ? "INVOICE" : out;
    }

    private static String trimMoneyRate(double value) {
        if (Math.rint(value) == value) {
            return String.format(Locale.UK, "%.0f", value);
        }
        return String.format(Locale.UK, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static final class ContractOption {
        final String id;
        final String name;
        final String email;
        final String address;
        final String lastVisit;

        ContractOption(String id, String name, String email, String address, String lastVisit) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.address = address;
            this.lastVisit = lastVisit;
        }

        static ContractOption none(CreateInvoiceActivity activity) {
            return new ContractOption("", activity.getString(R.string.invoice_contract_none), "", "", "");
        }

        @Override
        public String toString() {
            if (id == null || id.isEmpty()) return name;
            if (address != null && !address.isEmpty()) return name + " — " + address;
            return name;
        }
    }
}
