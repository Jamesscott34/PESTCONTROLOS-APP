package com.grpc.grpc.email.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.ContractStoragePathHelper;
import com.grpc.grpc.core.FirebaseHelper;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.UserRepository;
import com.grpc.grpc.email.model.EmailTemplate;
import com.grpc.grpc.email.template.EmailTemplateService;
import com.grpc.grpc.reports.ui.CloudStorageBrowserActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EmailComposeActivity extends AppCompatActivity {

    public static final String EXTRA_CONTRACT_ID = "CONTRACT_ID";
    public static final String EXTRA_CUSTOMER_NAME = "CUSTOMER_NAME";
    public static final String EXTRA_CUSTOMER_EMAIL = "CUSTOMER_EMAIL";
    public static final String EXTRA_ADDRESS = "ADDRESS";
    public static final String EXTRA_LAST_VISIT = "LAST_VISIT";
    /** Firebase Storage path to a PDF attached after download (e.g. invoice PDF). */
    public static final String EXTRA_PREFILL_ATTACHMENT_STORAGE_PATH = "PREFILL_ATTACHMENT_STORAGE_PATH";
    public static final String EXTRA_INVOICE_NUMBER = "INVOICE_NUMBER";
    public static final String EXTRA_INVOICE_AMOUNT = "INVOICE_AMOUNT";

    private static final int REQUEST_LOCAL_PDF = 8101;

    private static final String JOBS_FOLDER_ROOT = "JobWorkReports";
    private static final String QUOTATIONS_FOLDER_ROOT = "quotations";

    private Button selectContractButton;
    private Button selectManagementJobButton;
    private Button addReportButton;
    private Button addManagementReportButton;
    private Button manageManagementFilesButton;
    private Button browseQuotationsButton;
    private Button addLocalButton;
    private Button sendEmailButton;
    private Button backButton;
    private TextView selectedContractText;
    private TextView selectedManagementJobText;
    private TextView attachmentsText;
    private Spinner templateSpinner;
    private EditText toInput;
    private EditText subjectInput;
    private EditText bodyInput;

    private final List<EmailTemplate> templates = new ArrayList<>();
    private final List<ContractOption> contracts = new ArrayList<>();
    private final List<String> managementJobFolders = new ArrayList<>();
    private final List<AttachmentItem> attachments = new ArrayList<>();
    private ContractOption selectedContract;
    private String selectedManagementJobFolder;
    private String managementJobsStorageRoot = ContractStoragePathHelper.preferredManagementJobsRootName();

    /** Optional invoice placeholders from {@link #EXTRA_INVOICE_NUMBER} / {@link #EXTRA_INVOICE_AMOUNT}. */
    private String prefilledInvoiceNumber = "";
    private String prefilledInvoiceAmount = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_compose);

        bindViews();
        setupTemplates();
        loadIntentPrefill();
        loadContracts();
        loadManagementJobFolders();
        maybeAttachPrefilledStoragePdf();

        selectContractButton.setOnClickListener(v -> showContractPicker());
        selectManagementJobButton.setOnClickListener(v -> showManagementJobPicker());
        addReportButton.setOnClickListener(v -> showContractYearFolderPicker());
        addManagementReportButton.setOnClickListener(v -> showManagementReportBrowser());
        if (manageManagementFilesButton != null) {
            manageManagementFilesButton.setOnClickListener(v -> openJobsFolderCloudBrowser());
        }
        if (browseQuotationsButton != null) {
            browseQuotationsButton.setOnClickListener(v -> openQuotationsCloudBrowser());
        }
        addLocalButton.setOnClickListener(v -> openLocalPdfPicker());
        sendEmailButton.setOnClickListener(v -> openEmailApp());
        backButton.setOnClickListener(v -> finish());

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            boolean canMove = SessionManager.canMove(EmailComposeActivity.this);
            if (manageManagementFilesButton != null) {
                manageManagementFilesButton.setVisibility(canMove ? View.VISIBLE : View.GONE);
            }
            adjustAddLocalButtonLayout(canMove);
        }));
    }

    private void bindViews() {
        selectContractButton = findViewById(R.id.selectContractButton);
        selectManagementJobButton = findViewById(R.id.selectManagementJobButton);
        selectedContractText = findViewById(R.id.selectedContractText);
        selectedManagementJobText = findViewById(R.id.selectedManagementJobText);
        templateSpinner = findViewById(R.id.templateSpinner);
        toInput = findViewById(R.id.toInput);
        subjectInput = findViewById(R.id.subjectInput);
        bodyInput = findViewById(R.id.bodyInput);
        addReportButton = findViewById(R.id.addReportButton);
        addManagementReportButton = findViewById(R.id.addManagementReportButton);
        manageManagementFilesButton = findViewById(R.id.manageManagementFilesButton);
        browseQuotationsButton = findViewById(R.id.browseQuotationsButton);
        addLocalButton = findViewById(R.id.addLocalButton);
        attachmentsText = findViewById(R.id.attachmentsText);
        sendEmailButton = findViewById(R.id.sendEmailButton);
        backButton = findViewById(R.id.backButton);
    }

    private void setupTemplates() {
        templates.clear();
        templates.addAll(EmailTemplateService.templates(this));
        ArrayAdapter<EmailTemplate> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, templates);
        templateSpinner.setAdapter(adapter);
        templateSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                applyTemplate(templates.get(position));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void loadIntentPrefill() {
        prefilledInvoiceNumber = safe(getIntent().getStringExtra(EXTRA_INVOICE_NUMBER));
        prefilledInvoiceAmount = safe(getIntent().getStringExtra(EXTRA_INVOICE_AMOUNT));

        String contractId = getIntent().getStringExtra(EXTRA_CONTRACT_ID);
        String name = getIntent().getStringExtra(EXTRA_CUSTOMER_NAME);
        String email = getIntent().getStringExtra(EXTRA_CUSTOMER_EMAIL);
        String address = getIntent().getStringExtra(EXTRA_ADDRESS);
        String lastVisit = getIntent().getStringExtra(EXTRA_LAST_VISIT);
        if (!TextUtils.isEmpty(name) || !TextUtils.isEmpty(email)) {
            selectedContract = new ContractOption(safe(contractId), safe(name), safe(email), safe(address), safe(lastVisit), "");
            applyContract(selectedContract);
        }
        if ((!prefilledInvoiceNumber.isEmpty() || !prefilledInvoiceAmount.isEmpty()) && !templates.isEmpty()) {
            selectTemplateById("invoice");
        }
    }

    private void selectTemplateById(String id) {
        for (int i = 0; i < templates.size(); i++) {
            if (id.equals(templates.get(i).id)) {
                templateSpinner.setSelection(i);
                break;
            }
        }
    }

    private void maybeAttachPrefilledStoragePdf() {
        String attachPath = getIntent().getStringExtra(EXTRA_PREFILL_ATTACHMENT_STORAGE_PATH);
        if (TextUtils.isEmpty(attachPath)) return;
        String label = fileNameFromPath(attachPath.substring(attachPath.lastIndexOf('/') + 1));
        downloadReportAttachment(attachPath, label);
    }

    private void loadContracts() {
        SessionManager.ensureLoaded(this, session -> UserRepository.ensureProfileForCurrentUser(this, session, profile -> runOnUiThread(() -> {
            if (session == null) {
                contracts.clear();
                return;
            }

            Query query;
            if (session.isAdmin) {
                query = FirebaseHelper.getFirestore().collection(FirestorePaths.CONTRACTS);
            } else {
                String contractKey = session.contractKey != null ? session.contractKey.trim().toLowerCase(Locale.ROOT) : "";
                if (contractKey.isEmpty() && profile != null && profile.contractKey != null) {
                    contractKey = profile.contractKey.trim().toLowerCase(Locale.ROOT);
                }
                if (contractKey.isEmpty()) {
                    contracts.clear();
                    Toast.makeText(this, "Your profile is missing a contract key.", Toast.LENGTH_SHORT).show();
                    return;
                }
                query = FirebaseHelper.getFirestore()
                        .collection(FirestorePaths.CONTRACTS)
                        .whereEqualTo("assignedTech", contractKey);
            }

            query.get()
                    .addOnSuccessListener(snapshot -> {
                        contracts.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            ContractOption option = new ContractOption(
                                    doc.getId(),
                                    safe(doc.getString("name")),
                                    safe(doc.getString("email")),
                                    safe(doc.getString("address")),
                                    safe(doc.getString("lastVisit")),
                                    safe(doc.getString("assignedTech"))
                            );
                            if (!option.name.isEmpty()) {
                                contracts.add(option);
                            }
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Unable to load contracts: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        })));
    }

    private void showContractPicker() {
        if (contracts.isEmpty()) {
            Toast.makeText(this, "Contracts are still loading or none are available.", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_search_with_list, null);
        EditText searchBar = view.findViewById(R.id.searchBar);
        ListView listView = new ListView(this);
        if (view instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view).addView(listView);
        }

        List<ContractOption> filtered = new ArrayList<>(contracts);
        ArrayAdapter<ContractOption> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, filtered);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select Contract")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .create();

        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            applyContract(filtered.get(position));
            dialog.dismiss();
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtered.clear();
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                for (ContractOption option : contracts) {
                    if (query.isEmpty()
                            || option.name.toLowerCase(Locale.ROOT).contains(query)
                            || option.address.toLowerCase(Locale.ROOT).contains(query)
                            || option.email.toLowerCase(Locale.ROOT).contains(query)
                            || option.assignedTech.toLowerCase(Locale.ROOT).contains(query)) {
                        filtered.add(option);
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private void applyContract(ContractOption contract) {
        selectedContract = contract;
        selectedContractText.setText(contract.name + "\n" + contract.address);
        if (!contract.email.isEmpty()) {
            toInput.setText(contract.email);
        }
        applyTemplate(currentTemplate());
    }

    private void applyTemplate(EmailTemplate template) {
        if (template == null || "custom".equals(template.id)) {
            return;
        }
        EmailTemplate processed = EmailTemplateService.process(template, templateVariables());
        subjectInput.setText(processed.subject);
        bodyInput.setText(processed.body);
    }

    private Map<String, String> templateVariables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("customerName", selectedContract != null && !selectedContract.name.isEmpty() ? selectedContract.name : "Customer");
        variables.put("address", selectedContract != null ? selectedContract.address : "");
        variables.put("serviceDate", today());
        variables.put("visitDate", today());
        variables.put("visitTime", "TBC");
        variables.put("technicianName", SessionManager.getName(this));
        variables.put("serviceType", "Pest Control");
        variables.put("lastVisit", selectedContract != null && !selectedContract.lastVisit.isEmpty() ? selectedContract.lastVisit : "Not recorded");
        variables.put("invoiceNumber", prefilledInvoiceNumber.isEmpty() ? "-" : prefilledInvoiceNumber);
        variables.put("invoiceAmount", prefilledInvoiceAmount.isEmpty() ? "-" : prefilledInvoiceAmount);
        variables.put("quotationDate", today());
        return variables;
    }

    private EmailTemplate currentTemplate() {
        Object item = templateSpinner.getSelectedItem();
        return item instanceof EmailTemplate ? (EmailTemplate) item : null;
    }

    private void loadManagementJobFolders() {
        ContractStoragePathHelper.resolveManagementJobsRoot(root -> {
            managementJobsStorageRoot = root != null ? root : ContractStoragePathHelper.preferredManagementJobsRootName();
            ContractStoragePathHelper.listManagementJobFolders(folders -> runOnUiThread(() -> {
                managementJobFolders.clear();
                if (folders != null) {
                    managementJobFolders.addAll(folders);
                }
            }));
        });
    }

    private void showManagementJobPicker() {
        if (managementJobFolders.isEmpty()) {
            Toast.makeText(this, R.string.email_no_management_jobs, Toast.LENGTH_SHORT).show();
            loadManagementJobFolders();
            return;
        }
        String[] labels = managementJobFolders.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.email_select_management_job)
                .setItems(labels, (dialog, which) -> applyManagementJob(managementJobFolders.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyManagementJob(String folderName) {
        selectedManagementJobFolder = folderName;
        selectedManagementJobText.setText(folderName);
    }

    private void showContractYearFolderPicker() {
        if (selectedContract == null || TextUtils.isEmpty(selectedContract.id)) {
            Toast.makeText(this, "Select a contract first to search linked reports.", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(R.string.email_select_year_folder)
                .setMessage("Loading folders...")
                .setCancelable(false)
                .show();

        ContractStoragePathHelper.listContractYearFolders(selectedContract.id, yearFolders -> runOnUiThread(() -> {
            List<String> options = new ArrayList<>();
            if (yearFolders != null) {
                options.addAll(yearFolders);
            }
            String contractFolder = com.grpc.grpc.core.ContractReportSync.buildContractStorageFolder(selectedContract.id);
            ContractStoragePathHelper.listFilesInFolder(contractFolder, (subfolders, rootFiles) -> runOnUiThread(() -> {
                loading.dismiss();
                if (!rootFiles.isEmpty()) {
                    options.add(0, "(Root files)");
                }
                if (options.isEmpty()) {
                    Toast.makeText(this, R.string.email_no_year_folders, Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = options.toArray(new String[0]);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.email_select_year_folder)
                        .setItems(labels, (dialog, which) -> {
                            String selected = labels[which];
                            if ("(Root files)".equals(selected)) {
                                showContractFolderFilePicker(contractFolder, "Root files");
                            } else {
                                showContractFolderFilePicker(contractFolder + "/" + selected, selected);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }));
        }));
    }

    private void showContractFolderFilePicker(String folderPath, String folderLabel) {
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(folderLabel)
                .setMessage("Loading files...")
                .setCancelable(false)
                .show();

        LinkedHashMap<String, String> reports = new LinkedHashMap<>();
        final int[] pending = {2};
        Runnable finish = () -> {
            pending[0]--;
            if (pending[0] > 0) {
                return;
            }
            loading.dismiss();
            showReportSelectionDialog(reports);
        };

        ContractStoragePathHelper.listFilesInFolder(folderPath, (subfolders, files) -> runOnUiThread(() -> {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String name = entry.getValue();
                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    reports.put(entry.getKey(), name);
                }
            }
            finish.run();
        }));

        FirebaseFirestore.getInstance()
                .collection(FirestorePaths.CONTRACT_REPORTS)
                .document(selectedContract.id)
                .collection("reports")
                .get()
                .addOnSuccessListener(snapshot -> {
                    String folderPrefix = folderPath.endsWith("/") ? folderPath : folderPath + "/";
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String storagePath = doc.getString("storagePath");
                        String fileName = doc.getString("fileName");
                        if (TextUtils.isEmpty(storagePath)) {
                            continue;
                        }
                        if (storagePath.equals(folderPath) || storagePath.startsWith(folderPrefix)) {
                            reports.put(storagePath, !TextUtils.isEmpty(fileName) ? fileName : fileNameFromPath(storagePath));
                        }
                    }
                    finish.run();
                })
                .addOnFailureListener(e -> finish.run());
    }

    private void showManagementReportBrowser() {
        if (TextUtils.isEmpty(selectedManagementJobFolder)) {
            Toast.makeText(this, R.string.email_no_management_job_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        String root = TextUtils.isEmpty(managementJobsStorageRoot)
                ? ContractStoragePathHelper.preferredManagementJobsRootName()
                : managementJobsStorageRoot;
        String startPath = root + "/" + selectedManagementJobFolder;
        browseManagementFolder(startPath, selectedManagementJobFolder);
    }

    private void openJobsFolderCloudBrowser() {
        if (!SessionManager.canMove(this)) {
            Toast.makeText(this, R.string.cloud_storage_move_not_allowed, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CloudStorageBrowserActivity.class);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_FIXED_ROOT);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_PATH, JOBS_FOLDER_ROOT);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_TITLE, "Jobs Folder");
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, SessionManager.getName(this));
        startActivity(intent);
    }

    private void openQuotationsCloudBrowser() {
        Intent intent = new Intent(this, CloudStorageBrowserActivity.class);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_ENTRY_MODE, CloudStorageBrowserActivity.MODE_FIXED_ROOT);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_PATH, QUOTATIONS_FOLDER_ROOT);
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_FIXED_ROOT_TITLE, "Cloud Quotations");
        intent.putExtra(CloudStorageBrowserActivity.EXTRA_USER_NAME, SessionManager.getName(this));
        startActivity(intent);
    }

    private void adjustAddLocalButtonLayout(boolean showManageJobFiles) {
        if (addLocalButton == null) {
            return;
        }
        android.widget.LinearLayout.LayoutParams params =
                (android.widget.LinearLayout.LayoutParams) addLocalButton.getLayoutParams();
        if (showManageJobFiles) {
            params.width = 0;
            params.weight = 1f;
            params.setMarginStart((int) (6 * getResources().getDisplayMetrics().density + 0.5f));
        } else {
            params.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT;
            params.weight = 0f;
            params.setMarginStart(0);
        }
        addLocalButton.setLayoutParams(params);
    }

    private void browseManagementFolder(String folderPath, String folderLabel) {
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(folderLabel)
                .setMessage("Loading...")
                .setCancelable(false)
                .show();

        ContractStoragePathHelper.listNestedManagementEntries(folderPath, (subfolders, files) -> runOnUiThread(() -> {
            loading.dismiss();
            List<String> options = new ArrayList<>();
            List<String> optionPaths = new ArrayList<>();

            for (String subfolder : subfolders) {
                options.add("[Folder] " + subfolder);
                optionPaths.add(folderPath + "/" + subfolder);
            }
            List<String> pdfPaths = new ArrayList<>();
            List<String> pdfNames = new ArrayList<>();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String name = entry.getValue();
                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    pdfNames.add(name);
                    pdfPaths.add(entry.getKey());
                }
            }
            options.addAll(pdfNames);
            optionPaths.addAll(pdfPaths);

            if (options.isEmpty()) {
                Toast.makeText(this, R.string.email_no_files_in_folder, Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(folderLabel)
                    .setItems(options.toArray(new String[0]), (dialog, which) -> {
                        String chosen = options.get(which);
                        String chosenPath = optionPaths.get(which);
                        if (chosen.startsWith("[Folder] ")) {
                            browseManagementFolder(chosenPath, chosen.substring("[Folder] ".length()));
                        } else {
                            downloadReportAttachment(chosenPath, chosen);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }));
    }

    private void showReportSelectionDialog(LinkedHashMap<String, String> reports) {
        if (reports.isEmpty()) {
            Toast.makeText(this, "No linked reports found for this contract.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> paths = new ArrayList<>(reports.keySet());
        String[] labels = new String[paths.size()];
        boolean[] checked = new boolean[paths.size()];
        for (int i = 0; i < paths.size(); i++) {
            labels[i] = reports.get(paths.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Attach Reports")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Attach", (dialog, which) -> {
                    for (int i = 0; i < paths.size(); i++) {
                        if (checked[i]) {
                            downloadReportAttachment(paths.get(i), labels[i]);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadReportAttachment(String storagePath, String fileName) {
        File dir = new File(getCacheDir(), "email_attachments");
        if (!dir.exists()) dir.mkdirs();
        File localFile = new File(dir, fileNameFromPath(fileName));
        FirebaseStorage.getInstance()
                .getReference()
                .child(storagePath)
                .getFile(localFile)
                .addOnSuccessListener(task -> {
                    Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", localFile);
                    attachments.add(new AttachmentItem(fileName, uri));
                    updateAttachmentsText();
                    Toast.makeText(this, "Attached " + fileName, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to attach report: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void openLocalPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_LOCAL_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_LOCAL_PDF || resultCode != RESULT_OK || data == null) return;

        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                attachments.add(new AttachmentItem("Local PDF " + (attachments.size() + 1), uri));
            }
        } else if (data.getData() != null) {
            Uri uri = data.getData();
            grantUriPermission(getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            attachments.add(new AttachmentItem("Local PDF " + (attachments.size() + 1), uri));
        }
        updateAttachmentsText();
    }

    private void openEmailApp() {
        String[] recipients = parseRecipients(toInput.getText().toString());
        if (recipients.length == 0) {
            Toast.makeText(this, "Enter at least one recipient email.", Toast.LENGTH_SHORT).show();
            return;
        }
        for (String recipient : recipients) {
            if (!Patterns.EMAIL_ADDRESS.matcher(recipient).matches()) {
                Toast.makeText(this, "Invalid email: " + recipient, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (subjectInput.getText().toString().trim().isEmpty() || bodyInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Subject and body are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(attachments.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, recipients);
        intent.putExtra(Intent.EXTRA_SUBJECT, subjectInput.getText().toString().trim());
        intent.putExtra(Intent.EXTRA_TEXT, bodyInput.getText().toString().trim());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (attachments.size() == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, attachments.get(0).uri);
            intent.setClipData(ClipData.newRawUri("attachment", attachments.get(0).uri));
        } else if (attachments.size() > 1) {
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData clipData = null;
            for (AttachmentItem attachment : attachments) {
                uris.add(attachment.uri);
                if (clipData == null) clipData = ClipData.newRawUri("attachment", attachment.uri);
                else clipData.addItem(new ClipData.Item(attachment.uri));
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.setClipData(clipData);
        }

        try {
            startActivity(Intent.createChooser(intent, "Send Email"));
        } catch (Exception e) {
            Toast.makeText(this, "No email app is available.", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] parseRecipients(String raw) {
        String[] parts = raw.split("[,;\\s]+");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out.toArray(new String[0]);
    }

    private void updateAttachmentsText() {
        if (attachments.isEmpty()) {
            attachmentsText.setText(R.string.email_attachments_none);
            return;
        }
        StringBuilder builder = new StringBuilder("Attachments:\n");
        for (AttachmentItem attachment : attachments) {
            builder.append("- ").append(attachment.name).append("\n");
        }
        attachmentsText.setText(builder.toString().trim());
    }

    private String fileNameFromPath(String path) {
        if (path == null || path.trim().isEmpty()) return "attachment.pdf";
        int index = path.lastIndexOf('/');
        String fileName = index >= 0 ? path.substring(index + 1) : path;
        return fileName.endsWith(".pdf") ? fileName : fileName + ".pdf";
    }

    private String today() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
    }

    private String safe(String value) {
        return value != null ? value.trim() : "";
    }

    private static final class ContractOption {
        final String id;
        final String name;
        final String email;
        final String address;
        final String lastVisit;
        final String assignedTech;

        ContractOption(String id, String name, String email, String address, String lastVisit, String assignedTech) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.address = address;
            this.lastVisit = lastVisit;
            this.assignedTech = assignedTech;
        }

        @Override
        public String toString() {
            String owner = assignedTech.isEmpty() ? "" : " (" + assignedTech + ")";
            return name + owner + (email.isEmpty() ? "" : " - " + email);
        }
    }

    private static final class AttachmentItem {
        final String name;
        final Uri uri;

        AttachmentItem(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }
}
