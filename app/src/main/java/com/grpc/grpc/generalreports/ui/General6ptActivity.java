package com.grpc.grpc.generalreports.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.PdfPasswordPrompt;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.core.TenantBranding;
import com.grpc.grpc.reports.pdf.PDFReportGenerator;
import com.grpc.grpc.reports.pdf.PdfFooterPageNumberStamper;
import com.grpc.grpc.quotations.ui.QuotesActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.grpc.grpc.core.FirestorePaths;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * General6ptActivity.java
 *
 * This activity generates a comprehensive pest management quotation report for a 6-point service contract.
 * Users can input company details, and a structured quote is created based on predefined services.
 * The generated quotation includes pest control services, monitoring plans, pricing, and contract details.
 * The report is saved as a PDF and formatted professionally with a company logo and payment structure.
 *
 * Features:
 * - Allows users to enter company details for the quotation
 * - Generates a structured PDF report with pre-defined services
 * - Automatically assigns quarterly payments and VAT calculations
 * - Saves the generated quote and allows navigation back to the main quote activity
 * - Supports a 6-visit service contract (every 8 weeks)
 *
 * Author: GRPC
 */


public class General6ptActivity extends AppCompatActivity {

    private static final String PREF_KEY_ANNUAL_FEE = "CONTRACT_QUOTE_ANNUAL_FEE_6PT";
    private static final double DEFAULT_ANNUAL_FEE = 800.0;
    private static final double EXTERNAL_UNIT_PRICE = 25.0;
    private static final int EXTERNAL_FREE_COUNT = 2;
    private static final double FLY_UNIT_PRICE = 25.0;
    private static final int FLY_FREE_COUNT = 1;
    private static final double INSECT_MONITOR_PRICE = 50.0;
    private static final double VAT_MULTIPLIER = 1.23;
    private static final int REQUEST_PREVIEW_CONTRACT_QUOTE = 906;
    private String lastPreviewQuoteNumber;

    private String userName;
    private String userEmail;
    private String userMobile;
    private String staffDisplayName;
    private String staffTitle;

    private EditText companyNameInput;
    private EditText companyAddressInput;
    private EditText companyContactInput;
    private EditText annualServiceFeeInput;
    private Spinner requiredMaterialTypeSpinner;
    private EditText requiredMaterialQtyInput;
    private EditText requiredMaterialPriceInput;
    private TextView requiredMaterialsSummaryText;
    private TextView requiredMaterialsTotalText;
    private TextView firstQuarterPaymentDueText;
    private CheckBox passwordProtectCheckbox;
    private final Map<String, RequiredMaterialEntry> requiredMaterials = new LinkedHashMap<>();
    private String activeRequiredMaterialKey = "externals";
    private boolean requiredMaterialUiSyncInProgress = false;

    private final List<Uri> selectedImageUris = new ArrayList<>();
    private final ActivityResultLauncher<Intent> pickImagesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                selectedImageUris.clear();
                selectedImageUris.addAll(
                        com.grpc.grpc.core.ReportImageStorage.persistFromPickerResult(this, result.getData()));
                Toast.makeText(this, "Added " + selectedImageUris.size() + " image(s)", Toast.LENGTH_SHORT).show();
            });

    private static final class RequiredMaterialEntry {
        int qty;
        double unitPrice;

        RequiredMaterialEntry(int qty, double unitPrice) {
            this.qty = qty;
            this.unitPrice = unitPrice;
        }
    }

    private static final class RequiredMaterialLine {
        final String key;
        final String label;
        final int qty;
        final double unitPrice;

        RequiredMaterialLine(String key, String label, int qty, double unitPrice) {
            this.key = key;
            this.label = label;
            this.qty = qty;
            this.unitPrice = unitPrice;
        }

        double lineTotal() {
            return qty * unitPrice;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general6pt);

        // Retrieve the username
        userName = getIntent().getStringExtra("USER_NAME");

        // Default from saved login (no hardcoded emails)
        userEmail = getSharedPreferences("GRPC", MODE_PRIVATE).getString("USER_EMAIL", "");
        userMobile = "";
        staffDisplayName = userName;
        staffTitle = "";

        // Fetch Email/Name/Title from centralized session (users/{StaffID})
        SessionManager.ensureLoaded(this, session -> {
            String email = SessionManager.getEmail(this);
            String mobile = SessionManager.getNumber(this);
            String name = SessionManager.getName(this);
            String title = SessionManager.getTitle(this);
            if (email != null && !email.isEmpty()) userEmail = email;
            if (mobile != null && !mobile.isEmpty()) userMobile = mobile;
            if (name != null && !name.isEmpty()) staffDisplayName = name;
            if (title != null && !title.isEmpty()) staffTitle = title;
        });

        // Display welcome message
        TextView welcomeTextView = findViewById(R.id.welcomeTextView);
        if (welcomeTextView != null) {
            welcomeTextView.setText("Welcome, " + userName + "!");
        }
        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (welcomeTextView == null) return;
            String name = SessionManager.getName(this);
            if (name != null && !name.trim().isEmpty()) {
                welcomeTextView.setText("Welcome, " + name.trim() + "!");
            }
        }));

        // Input fields for company details
        companyNameInput = findViewById(R.id.companyNameInput);
        companyAddressInput = findViewById(R.id.companyAddressInput);
        companyContactInput = findViewById(R.id.companyContactInput);
        annualServiceFeeInput = findViewById(R.id.annualServiceFeeInput);
        requiredMaterialTypeSpinner = findViewById(R.id.requiredMaterialTypeSpinner);
        requiredMaterialQtyInput = findViewById(R.id.requiredMaterialQtyInput);
        requiredMaterialPriceInput = findViewById(R.id.requiredMaterialPriceInput);
        requiredMaterialsSummaryText = findViewById(R.id.requiredMaterialsSummaryText);
        requiredMaterialsTotalText = findViewById(R.id.requiredMaterialsTotal);
        firstQuarterPaymentDueText = findViewById(R.id.firstQuarterPaymentDue);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        // Allow a custom price (persisted per device)
        if (annualServiceFeeInput != null) {
            String saved = getSharedPreferences("GRPC", MODE_PRIVATE)
                    .getString(PREF_KEY_ANNUAL_FEE, String.valueOf(DEFAULT_ANNUAL_FEE));
            annualServiceFeeInput.setText(saved);
        }

        initialiseRequiredMaterialsSection();
        wirePricingListeners();
        updateTotals();

        Button addImageButton = findViewById(R.id.addImageButton);
        if (addImageButton != null) {
            addImageButton.setOnClickListener(v -> pickImagesLauncher.launch(
                    Intent.createChooser(
                            com.grpc.grpc.core.ReportImageStorage.createImagePickerIntent(),
                            getString(R.string.quotation_add_image))));
        }


        // Generate PDF button
        Button generatePdfButton = findViewById(R.id.generatePdfButton);
        Button previewPdfButton = findViewById(R.id.previewPdfButton);
        if (previewPdfButton != null) {
            previewPdfButton.setOnClickListener(v -> previewQuote());
        }
        generatePdfButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generateQuote(pw));
            } else {
                generateQuote(null);
            }
        });
    }

    private void wirePricingListeners() {
        EditText externalsQty = findViewById(R.id.externalsQtyInput);
        EditText flyQty = findViewById(R.id.flyUnitsQtyInput);
        CheckBox insectCb = findViewById(R.id.insectMonitorCheckbox);
        if (annualServiceFeeInput != null) {
            annualServiceFeeInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { updateTotals(); }
            });
        }
        if (externalsQty != null) {
            externalsQty.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { updateTotals(); }
            });
        }
        if (flyQty != null) {
            flyQty.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { updateTotals(); }
            });
        }
        if (insectCb != null) {
            insectCb.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> updateTotals());
        }
    }

    private void initialiseRequiredMaterialsSection() {
        requiredMaterials.clear();
        requiredMaterials.put("externals", new RequiredMaterialEntry(0, 0));
        requiredMaterials.put("fly_units", new RequiredMaterialEntry(0, 0));
        requiredMaterials.put("insect_monitor", new RequiredMaterialEntry(0, 0));

        if (requiredMaterialTypeSpinner != null) {
            String[] labels = new String[] {
                    getString(R.string.quotation_required_material_externals),
                    getString(R.string.quotation_required_material_fly_units),
                    getString(R.string.quotation_required_material_insect_monitor)
            };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            requiredMaterialTypeSpinner.setAdapter(adapter);
            requiredMaterialTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    syncCurrentRequiredMaterialFromInputs(false);
                    activeRequiredMaterialKey = getRequiredMaterialKey(position);
                    loadRequiredMaterialInputsForActiveKey();
                    updateTotals();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        }

        if (requiredMaterialQtyInput != null) {
            requiredMaterialQtyInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (requiredMaterialUiSyncInProgress) return;
                    syncCurrentRequiredMaterialFromInputs(true);
                    updateTotals();
                }
            });
        }

        if (requiredMaterialPriceInput != null) {
            requiredMaterialPriceInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (requiredMaterialUiSyncInProgress) return;
                    syncCurrentRequiredMaterialFromInputs(false);
                    updateTotals();
                }
            });
        }

        Button updateRequiredMaterialButton = findViewById(R.id.updateRequiredMaterialButton);
        if (updateRequiredMaterialButton != null) {
            updateRequiredMaterialButton.setOnClickListener(v -> {
                syncCurrentRequiredMaterialFromInputs(true);
                updateTotals();
                Toast.makeText(this, "Required material updated.", Toast.LENGTH_SHORT).show();
            });
        }

        loadRequiredMaterialInputsForActiveKey();
    }

    private String getRequiredMaterialKey(int position) {
        switch (position) {
            case 1:
                return "fly_units";
            case 2:
                return "insect_monitor";
            default:
                return "externals";
        }
    }

    private String getRequiredMaterialLabel(String key) {
        if ("fly_units".equals(key)) {
            return getString(R.string.quotation_required_material_fly_units);
        }
        if ("insect_monitor".equals(key)) {
            return getString(R.string.quotation_required_material_insect_monitor);
        }
        return getString(R.string.quotation_required_material_externals);
    }

    private int parseOptionalInt(EditText input) {
        if (input == null) return 0;
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double parseOptionalDouble(EditText input) {
        if (input == null) return 0.0;
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void loadRequiredMaterialInputsForActiveKey() {
        RequiredMaterialEntry entry = requiredMaterials.get(activeRequiredMaterialKey);
        if (entry == null) entry = new RequiredMaterialEntry(0, 0);
        requiredMaterialUiSyncInProgress = true;
        if (requiredMaterialQtyInput != null) {
            requiredMaterialQtyInput.setText(entry.qty > 0 ? String.valueOf(entry.qty) : "");
        }
        if (requiredMaterialPriceInput != null) {
            requiredMaterialPriceInput.setText(entry.unitPrice > 0 ? String.valueOf(entry.unitPrice) : "");
        }
        requiredMaterialUiSyncInProgress = false;
    }

    private void syncCurrentRequiredMaterialFromInputs(boolean autoFillOnSite) {
        RequiredMaterialEntry entry = requiredMaterials.get(activeRequiredMaterialKey);
        if (entry == null) {
            entry = new RequiredMaterialEntry(0, 0);
            requiredMaterials.put(activeRequiredMaterialKey, entry);
        }
        entry.qty = parseOptionalInt(requiredMaterialQtyInput);
        entry.unitPrice = parseOptionalDouble(requiredMaterialPriceInput);
        if (autoFillOnSite && entry.qty > 0) {
            autofillOnSiteMaterials(activeRequiredMaterialKey, entry.qty);
        }
    }

    private void autofillOnSiteMaterials(String key, int qty) {
        if (requiredMaterialUiSyncInProgress) return;
        requiredMaterialUiSyncInProgress = true;
        try {
            if ("externals".equals(key)) {
                EditText onsite = findViewById(R.id.externalsQtyInput);
                if (onsite != null && !String.valueOf(qty).equals(onsite.getText().toString().trim())) {
                    onsite.setText(String.valueOf(qty));
                }
            } else if ("fly_units".equals(key)) {
                EditText onsite = findViewById(R.id.flyUnitsQtyInput);
                if (onsite != null && !String.valueOf(qty).equals(onsite.getText().toString().trim())) {
                    onsite.setText(String.valueOf(qty));
                }
            } else if ("insect_monitor".equals(key)) {
                CheckBox onsite = findViewById(R.id.insectMonitorCheckbox);
                if (onsite != null && qty > 0) {
                    onsite.setChecked(true);
                }
            }
        } finally {
            requiredMaterialUiSyncInProgress = false;
        }
    }

    private List<RequiredMaterialLine> getRequiredMaterialLines() {
        syncCurrentRequiredMaterialFromInputs(false);
        List<RequiredMaterialLine> lines = new ArrayList<>();
        for (Map.Entry<String, RequiredMaterialEntry> entry : requiredMaterials.entrySet()) {
            RequiredMaterialEntry value = entry.getValue();
            if (value == null) continue;
            if (value.qty <= 0 && value.unitPrice <= 0) continue;
            lines.add(new RequiredMaterialLine(entry.getKey(), getRequiredMaterialLabel(entry.getKey()), value.qty, value.unitPrice));
        }
        return lines;
    }

    private void updateTotals() {
        double basePrice = DEFAULT_ANNUAL_FEE;
        if (annualServiceFeeInput != null) {
            String s = annualServiceFeeInput.getText().toString().trim();
            if (!s.isEmpty()) {
                try { basePrice = Double.parseDouble(s); } catch (NumberFormatException ignored) {}
            }
        }
        int externalQty = 0;
        EditText eq = findViewById(R.id.externalsQtyInput);
        if (eq != null) {
            String s = eq.getText().toString().trim();
            if (!s.isEmpty()) {
                try { externalQty = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }
        int flyQty = 0;
        EditText fq = findViewById(R.id.flyUnitsQtyInput);
        if (fq != null) {
            String s = fq.getText().toString().trim();
            if (!s.isEmpty()) {
                try { flyQty = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }
        int chargeableExternals = Math.max(externalQty - EXTERNAL_FREE_COUNT, 0);
        int chargeableFly = Math.max(flyQty - FLY_FREE_COUNT, 0);
        double externalsTotal = chargeableExternals * EXTERNAL_UNIT_PRICE;
        double flyUnitsTotal = chargeableFly * FLY_UNIT_PRICE;
        boolean insectChecked = false;
        CheckBox ic = findViewById(R.id.insectMonitorCheckbox);
        if (ic != null) insectChecked = ic.isChecked();
        double insectTotal = insectChecked ? INSECT_MONITOR_PRICE : 0.0;

        double totalExcl = basePrice + externalsTotal + flyUnitsTotal + insectTotal;
        double totalIncl = totalExcl * VAT_MULTIPLIER;
        double perQuarter = totalIncl / 4.0;
        List<RequiredMaterialLine> requiredMaterialLines = getRequiredMaterialLines();
        double requiredMaterialsTotalExcl = 0.0;
        for (RequiredMaterialLine line : requiredMaterialLines) {
            requiredMaterialsTotalExcl += line.lineTotal();
        }
        double requiredMaterialsTotalIncl = requiredMaterialsTotalExcl * VAT_MULTIPLIER;
        double firstQuarterDue = perQuarter + requiredMaterialsTotalIncl;

        TextView et = findViewById(R.id.externalsLineTotal);
        if (et != null) et.setText(String.format(Locale.UK, "€%.2f", externalsTotal));
        TextView ft = findViewById(R.id.flyUnitsLineTotal);
        if (ft != null) ft.setText(String.format(Locale.UK, "€%.2f", flyUnitsTotal));
        TextView it = findViewById(R.id.insectMonitorLineTotal);
        if (it != null) it.setText(String.format(Locale.UK, "€%.2f", insectTotal));
        TextView te = findViewById(R.id.totalExcludingVat);
        if (te != null) te.setText(String.format(Locale.UK, "€%.2f", totalExcl));
        TextView ti = findViewById(R.id.totalIncludingVat);
        if (ti != null) ti.setText(String.format(Locale.UK, "€%.2f", totalIncl));
        TextView pp = findViewById(R.id.pricePerQuarter);
        if (pp != null) pp.setText(String.format(Locale.UK, "€%.2f", perQuarter));
        if (requiredMaterialsSummaryText != null) {
            if (requiredMaterialLines.isEmpty()) {
                requiredMaterialsSummaryText.setText(getString(R.string.quotation_required_material_none));
            } else {
                StringBuilder summary = new StringBuilder();
                for (RequiredMaterialLine line : requiredMaterialLines) {
                    if (summary.length() > 0) summary.append("\n");
                    summary.append(line.label)
                            .append(": ")
                            .append(line.qty)
                            .append(" @ €")
                            .append(String.format(Locale.UK, "%.2f", line.unitPrice))
                            .append(" = €")
                            .append(String.format(Locale.UK, "%.2f", line.lineTotal()));
                }
                requiredMaterialsSummaryText.setText(summary.toString());
            }
        }
        if (requiredMaterialsTotalText != null) {
            requiredMaterialsTotalText.setText(String.format(Locale.UK, "€%.2f", requiredMaterialsTotalIncl));
        }
        if (firstQuarterPaymentDueText != null) {
            firstQuarterPaymentDueText.setText(String.format(Locale.UK, "€%.2f", firstQuarterDue));
        }
    }

    private File buildAndGenerateQuotePdf(String ownerPassword) {
        return buildAndGenerateQuotePdf(ownerPassword, null, null);
    }

    private File buildAndGenerateQuotePdf(String ownerPassword, File outputDirectory) {
        return buildAndGenerateQuotePdf(ownerPassword, outputDirectory, null);
    }

    private File buildAndGenerateQuotePdf(String ownerPassword, File outputDirectory, @Nullable String quoteNumberOverride) {
        if (userEmail == null || userEmail.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.quotation_profile_loading), Toast.LENGTH_SHORT).show();
            return null;
        }
        String companyName = companyNameInput.getText().toString().trim();
        String companyAddress = companyAddressInput.getText().toString().trim();
        String companyContact = companyContactInput.getText().toString().trim();
        String annualFeeStr = annualServiceFeeInput != null ? annualServiceFeeInput.getText().toString().trim() : "";

        if (companyName.isEmpty() || companyAddress.isEmpty() || companyContact.isEmpty()) {
            Toast.makeText(this, getString(R.string.quotation_fill_company_details), Toast.LENGTH_SHORT).show();
            return null;
        }

        double annualFee = DEFAULT_ANNUAL_FEE;
        if (!annualFeeStr.isEmpty()) {
            try {
                annualFee = Double.parseDouble(annualFeeStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.quotation_invalid_annual_fee), Toast.LENGTH_SHORT).show();
                return null;
            }
        }
        getSharedPreferences("GRPC", MODE_PRIVATE).edit()
                .putString(PREF_KEY_ANNUAL_FEE, String.valueOf(annualFee))
                .apply();

        String quoteNumber = quoteNumberOverride != null ? quoteNumberOverride : String.format("%04d", new Random().nextInt(10000));
        lastPreviewQuoteNumber = quoteNumber;
        List<String> descriptions = new ArrayList<>();
        List<Double> lineTotals = new ArrayList<>();

        descriptions.add("Annual Service Fee for Pest Management Solutions:\n" +
                "This fee covers a comprehensive annual pest management program tailored to the specific needs of your premises. " +
                "The service includes routine inspections, preventative treatments, and continuous monitoring to maintain a high standard of pest control across the site. " +
                "Our technicians will assess risk areas during each visit and apply appropriate control measures to maintain a safe and pest-free environment.");

        lineTotals.add(annualFee);

        descriptions.add("Internal Monitors Placement and Maintenance:\n" +
                "Internal monitoring devices will be strategically positioned throughout the premises to detect pest activity at an early stage. " +
                "These monitors are inspected and maintained during each scheduled service visit to ensure continued effectiveness and rapid response to any signs of pest activity.");

        lineTotals.add(0.0);

        descriptions.add("Fly Control Unit Maintenance:\n" +
                "The base contract price includes the servicing and maintenance of one fly control unit already installed on site. " +
                "This includes routine inspections during each scheduled visit and one complimentary bulb replacement per year to maintain optimal performance.");

        lineTotals.add(0.0);

        descriptions.add("External Pest Control Measures:\n" +
                "The base contract price includes the servicing and maintenance of two external pest control boxes currently installed on site. " +
                "These units are inspected and maintained during each service visit to ensure effective perimeter protection and early detection of pest activity.");

        lineTotals.add(0.0);

        String additionalExternalDesc = "Additional External Pest Control Boxes:\n" +
                "Any external pest control boxes in addition to the two included within the base contract will be charged at €25 per external box plus VAT. " +
                "Where additional external units are required to improve site coverage, a separate quotation can be provided for the supply and installation of these units.";

        String additionalFlyDesc = "Additional Fly Control Units:\n" +
                "The base contract includes the maintenance of one fly control unit. " +
                "Any additional fly control units currently installed on site will be charged at €25 per unit plus VAT. " +
                "If additional fly control units or external pest control boxes are required to improve coverage, a separate quotation can be provided for the supply, installation, and maintenance of the required equipment.";

        double externalsTotal = 0, flyTotal = 0, insectTotal = 0;
        int externalQty = 0;
        EditText eq = findViewById(R.id.externalsQtyInput);
        if (eq != null) try { externalQty = Integer.parseInt(eq.getText().toString().trim()); } catch (NumberFormatException ignored) {}
        int flyQty = 0;
        EditText fq = findViewById(R.id.flyUnitsQtyInput);
        if (fq != null) try { flyQty = Integer.parseInt(fq.getText().toString().trim()); } catch (NumberFormatException ignored) {}
        int chargeableExternals = Math.max(externalQty - EXTERNAL_FREE_COUNT, 0);
        int chargeableFly = Math.max(flyQty - FLY_FREE_COUNT, 0);
        externalsTotal = chargeableExternals * EXTERNAL_UNIT_PRICE;
        flyTotal = chargeableFly * FLY_UNIT_PRICE;
        CheckBox ic = findViewById(R.id.insectMonitorCheckbox);
        if (ic != null && ic.isChecked()) insectTotal = INSECT_MONITOR_PRICE;
        double totalExcl = annualFee + externalsTotal + flyTotal + insectTotal;
        double totalIncl = totalExcl * VAT_MULTIPLIER;
        double perQuarter = totalIncl / 4.0;
        List<RequiredMaterialLine> requiredMaterialLines = getRequiredMaterialLines();
        List<String> requiredMaterialsPdfLines = new ArrayList<>();
        double requiredMaterialsTotalExcl = 0.0;
        for (RequiredMaterialLine line : requiredMaterialLines) {
            requiredMaterialsTotalExcl += line.lineTotal();
            requiredMaterialsPdfLines.add(
                    line.label + ": " + line.qty +
                            " @ €" + String.format(Locale.UK, "%.2f", line.unitPrice) +
                            " = €" + String.format(Locale.UK, "%.2f", line.lineTotal())
            );
        }
        double requiredMaterialsTotalIncl = requiredMaterialsTotalExcl * VAT_MULTIPLIER;
        double firstQuarterDue = perQuarter + requiredMaterialsTotalIncl;

        if (chargeableExternals > 0) {
            additionalExternalDesc += "\n(Quoted: " + chargeableExternals + " units @ €" + String.format(Locale.UK, "%.2f", EXTERNAL_UNIT_PRICE) + " = €" + String.format(Locale.UK, "%.2f", externalsTotal) + " + VAT)";
        }
        descriptions.add(additionalExternalDesc);
        lineTotals.add(0.0);
        if (chargeableFly > 0) {
            additionalFlyDesc += "\n(Quoted: " + chargeableFly + " units @ €" + String.format(Locale.UK, "%.2f", FLY_UNIT_PRICE) + " = €" + String.format(Locale.UK, "%.2f", flyTotal) + " + VAT)";
        }
        descriptions.add(additionalFlyDesc);
        lineTotals.add(0.0);
        descriptions.add("Callouts for Pest Control Services:\n" +
                "This service includes two complimentary callouts per year during working hours  for pest control maintenance, adjustments, or inspections as needed. " +
                "Any additional callouts beyond the included two will be charged at a discounted rate of €100 per visit plus VAT. " +
                "Our expert team ensures timely and efficient responses during each callout, addressing pest issues effectively and maintaining a safe, pest-free environment. " +
                "This service is ideal for ongoing support and ensures that your premises remain compliant with health and safety standards.");
        lineTotals.add(0.0);

        int insectQty = (ic != null && ic.isChecked()) ? 1 : 0;

        return generateQuotationReport(
                companyName + "_" + quoteNumber,
                companyAddress,
                getQuotationDescription(),
                descriptions,
                lineTotals,
                userEmail,
                userMobile,
                staffDisplayName,
                staffTitle,
                companyName,
                companyContact,
                this,
                ownerPassword,
                selectedImageUris,
                externalsTotal,
                flyTotal,
                insectTotal,
                totalExcl,
                totalIncl,
                perQuarter,
                chargeableExternals,
                EXTERNAL_UNIT_PRICE,
                chargeableFly,
                FLY_UNIT_PRICE,
                insectQty,
                INSECT_MONITOR_PRICE,
                requiredMaterialsPdfLines,
                requiredMaterialsTotalExcl,
                requiredMaterialsTotalIncl,
                firstQuarterDue,
                outputDirectory
        );
    }

    private void generateQuote(String ownerPassword) {
        generateQuote(ownerPassword, null);
    }

    private void generateQuote(String ownerPassword, @Nullable String quoteNumberOverride) {
        File pdfFile = buildAndGenerateQuotePdf(ownerPassword, null, quoteNumberOverride);
        if (pdfFile != null) {
            com.grpc.grpc.core.QuotationStorageUploader.uploadQuotationPdf(
                    pdfFile,
                    null,
                    e -> Toast.makeText(this, "Quotation upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
            Toast.makeText(this, "PDF Quote Generated: " + pdfFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            clearInputFields();
            navigateBackToQuotesActivity();
        } else {
            Toast.makeText(this, getString(R.string.quotation_pdf_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveReportToCloud() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, getString(R.string.quotation_sign_in_to_save), Toast.LENGTH_SHORT).show();
            return;
        }
        if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
            PdfPasswordPrompt.prompt(this, pw -> doSaveReportToCloud(pw));
        } else {
            doSaveReportToCloud(null);
        }
    }

    private void doSaveReportToCloud(String ownerPassword) {
        File pdfFile = buildAndGenerateQuotePdf(ownerPassword);
        if (pdfFile == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String storagePath = FirestorePaths.CONTRACT_QUOTATIONS + "/" + uid + "/" + pdfFile.getName();

        StorageReference ref = FirebaseStorage.getInstance().getReference().child(storagePath);
        ref.putFile(Uri.fromFile(pdfFile))
                .addOnSuccessListener(taskSnapshot -> {
                    com.grpc.grpc.core.StorageMetricsHelper.recordUpload();
                    Toast.makeText(this, getString(R.string.quotation_saved_to_cloud), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.quotation_upload_failed) + " " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void clearInputFields() {
        companyNameInput.setText("");
        companyAddressInput.setText("");
        companyContactInput.setText("");
    }

    private void navigateBackToQuotesActivity() {
        Intent intent = new Intent(General6ptActivity.this, QuotesActivity.class);
        intent.putExtra("USER_NAME", userName);
        startActivity(intent);
        finish();
    }

    private void previewQuote() {
        File previewDir = new File(getCacheDir(), "report_previews");
        if (!previewDir.exists()) previewDir.mkdirs();
        File previewPdf = buildAndGenerateQuotePdf(null, previewDir, null);
        if (previewPdf == null || !previewPdf.exists()) return;
        Intent previewIntent = new Intent(this, com.grpc.grpc.reports.ui.ReportPreviewActivity.class);
        previewIntent.putExtra(com.grpc.grpc.reports.ui.ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, previewPdf.getAbsolutePath());
        startActivityForResult(previewIntent, REQUEST_PREVIEW_CONTRACT_QUOTE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW_CONTRACT_QUOTE
                && resultCode == RESULT_OK
                && data != null
                && data.getBooleanExtra(com.grpc.grpc.reports.ui.ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, pw -> generateQuote(pw, lastPreviewQuoteNumber));
            } else {
                generateQuote(null, lastPreviewQuoteNumber);
            }
        }
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context) {
        return generateQuotationReport(fileName, address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber, staffName, staffTitle, companyName, companyContact, context, null, null);
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context,
            String ownerPassword) {
        return generateQuotationReport(fileName, address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber, staffName, staffTitle, companyName, companyContact, context, ownerPassword, null);
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context,
            String ownerPassword, List<Uri> imageUris) {
        return generateQuotationReport(fileName, address, quoteDescription, descriptions, lineTotals,
                userEmail, mobileNumber, staffName, staffTitle, companyName, companyContact, context,
                ownerPassword, imageUris, 0, 0, 0, 0, 0, 0);
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context,
            String ownerPassword, List<Uri> imageUris,
            double externalsLineTotal, double flyLineTotal, double insectMonitorLineTotal,
            double totalExclVat, double totalInclVat, double pricePerQuarter) {
        return generateQuotationReport(fileName, address, quoteDescription, descriptions, lineTotals,
                userEmail, mobileNumber, staffName, staffTitle, companyName, companyContact, context,
                ownerPassword, imageUris, externalsLineTotal, flyLineTotal, insectMonitorLineTotal,
                totalExclVat, totalInclVat, pricePerQuarter, 0, 0, 0, 0, 0, 0,
                new ArrayList<>(), 0, 0, 0);
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context,
            String ownerPassword, List<Uri> imageUris,
            double externalsLineTotal, double flyLineTotal, double insectMonitorLineTotal,
            double totalExclVat, double totalInclVat, double pricePerQuarter,
            int externalsQty, double externalsUnitPrice, int flyQty, double flyUnitPrice, int insectQty, double insectUnitPrice,
            List<String> requiredMaterialsLines, double requiredMaterialsTotalExcl, double requiredMaterialsTotalIncl, double firstQuarterPaymentDue) {
        return generateQuotationReport(fileName, address, quoteDescription, descriptions, lineTotals, userEmail, mobileNumber,
                staffName, staffTitle, companyName, companyContact, context, ownerPassword, imageUris,
                externalsLineTotal, flyLineTotal, insectMonitorLineTotal, totalExclVat, totalInclVat, pricePerQuarter,
                externalsQty, externalsUnitPrice, flyQty, flyUnitPrice, insectQty, insectUnitPrice,
                requiredMaterialsLines, requiredMaterialsTotalExcl, requiredMaterialsTotalIncl, firstQuarterPaymentDue, null);
    }

    public static File generateQuotationReport(
            String fileName, String address, String quoteDescription,
            List<String> descriptions, List<Double> lineTotals,
            String userEmail, String mobileNumber,
            String staffName, String staffTitle,
            String companyName, String companyContact, Context context,
            String ownerPassword, List<Uri> imageUris,
            double externalsLineTotal, double flyLineTotal, double insectMonitorLineTotal,
            double totalExclVat, double totalInclVat, double pricePerQuarter,
            int externalsQty, double externalsUnitPrice, int flyQty, double flyUnitPrice, int insectQty, double insectUnitPrice,
            List<String> requiredMaterialsLines, double requiredMaterialsTotalExcl, double requiredMaterialsTotalIncl, double firstQuarterPaymentDue,
            File outputDirectory) {

        File quotesFolder = outputDirectory != null
                ? outputDirectory
                : new File(context.getExternalFilesDir(null), TenantBranding.quotesFolderName(context));

        if (!quotesFolder.exists() && !quotesFolder.mkdirs()) {
            Toast.makeText(context, "Error creating quotes folder", Toast.LENGTH_SHORT).show();
            return null;
        }

        File pdfFile = new File(quotesFolder, fileName + ".pdf");

        WriterProperties writerProperties = new WriterProperties();
        writerProperties.setFullCompressionMode(true);
        if (ownerPassword != null && !ownerPassword.trim().isEmpty()) {
            writerProperties.setStandardEncryption(
                    null,
                    ownerPassword.trim().getBytes(),
                    EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_COPY,
                    EncryptionConstants.ENCRYPTION_AES_128
            );
        }

        try (PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile), writerProperties);
             PdfDocument pdfDocument = new PdfDocument(writer);
             Document document = new Document(pdfDocument)) {

            pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, new PDFReportGenerator.PdfWatermarkAndFooterHandler(context));

            // Add logo and header
            int logoResourceId = context.getResources().getIdentifier("logo", "drawable", context.getPackageName());
            ImageData logoData = ImageDataFactory.create(context.getResources().openRawResource(logoResourceId).readAllBytes());
            Image logo = new Image(logoData).scaleToFit(200, 200);

            // Header Table
            float[] headerWidths = {1, 1};
            Table headerTable = new Table(headerWidths).setWidth(UnitValue.createPercentValue(100));

            // Left Section: Logo and Company Info
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER);
            leftCell.add(logo);
            leftCell.add(new Paragraph("\n" + TenantBranding.companyName(context)).setBold().setFontSize(16));
            leftCell.add(new Paragraph("Name: " + (staffName != null && !staffName.isEmpty() ? staffName : "")).setFontSize(12));
            if (staffTitle != null && !staffTitle.isEmpty()) {
                leftCell.add(new Paragraph("Title: " + staffTitle).setFontSize(12));
            }
            leftCell.add(new Paragraph("Mobile: " + mobileNumber).setFontSize(12));
            leftCell.add(new Paragraph("Email: " + userEmail).setFontSize(12));
            leftCell.add(new Paragraph("Website: " + TenantBranding.companyWebsiteShort(context)).setFontSize(12));
            headerTable.addCell(leftCell);

            // Right Section: Date, Quote Info, and Company Info
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            String currentDate = sdf.format(new Date());

            Cell rightCell = new Cell().setBorder(Border.NO_BORDER);
            rightCell.add(new Paragraph("Date: " + currentDate).setFontSize(12).setBold());
            rightCell.add(new Paragraph("Quote Number: " + fileName.split("_")[1]).setFontSize(12));
            rightCell.add(new Paragraph("Quote Valid for 30 Days").setFontSize(12).setItalic());
            rightCell.add(new Paragraph("\nCompany Name:").setBold());
            rightCell.add(new Paragraph(companyName).setFontSize(12));
            rightCell.add(new Paragraph("Address:").setBold());
            rightCell.add(new Paragraph(address).setFontSize(12));
            rightCell.add(new Paragraph("Contact: " + companyContact).setFontSize(12));
            headerTable.addCell(rightCell);

            document.add(headerTable);

            // Quote Description Section
            document.add(new Paragraph("\nQuote Description:").setFontSize(14).setBold().setUnderline());
            document.add(new Paragraph(quoteDescription).setFontSize(12));

            // Line Items Table Setup
            // Description only. No visible Line Total / VAT / Total columns.
            float[] columnWidths = {1};
            Table table = new Table(columnWidths).setWidth(UnitValue.createPercentValue(100));
            table.addHeaderCell(new Cell().add(new Paragraph("Description").setBold()));

            double grandTotalInclVat = 0;
            double grandTotalExVat = 0;
            double totalVatAmount = 0;
            double firstQuarterPayment = 0;

            for (int i = 0; i < descriptions.size(); i++) {
                double lineTotal = lineTotals.get(i);
                double vatAmount = lineTotal * 0.23;
                double total = lineTotal + vatAmount;

                if (i == 0) {
                    firstQuarterPayment = total / 4.0;
                }

                // Show only description in the visible table
                table.addCell(new Paragraph(descriptions.get(i)));

                // Keep totals internally for bottom summary
                grandTotalExVat += lineTotal;
                totalVatAmount += vatAmount;
                grandTotalInclVat += total;
            }

            document.add(table);

            // Totals / Payment Summary
            double totalExclDisplay = totalExclVat > 0 ? totalExclVat : grandTotalExVat;
            double totalInclDisplay = totalInclVat > 0 ? totalInclVat : grandTotalInclVat;
            double vatDisplay = totalInclDisplay - totalExclDisplay;
            double pricePerQuarterDisplay = pricePerQuarter > 0 ? pricePerQuarter : totalInclDisplay / 4.0;

            document.add(new Paragraph("\nPricing Summary").setFontSize(16).setBold().setUnderline());
            document.add(new Paragraph(
                    "Base price (annual fee): €" +
                            String.format(Locale.UK, "%.2f", lineTotals.isEmpty() ? 0 : lineTotals.get(0))
            ).setFontSize(12));

            document.add(new Paragraph("Additional units on site:").setFontSize(12).setBold());

            if (externalsQty > 0) {
                document.add(new Paragraph(
                        "Externals: " + externalsQty +
                                " @ €" + String.format(Locale.UK, "%.2f", externalsUnitPrice) +
                                " = €" + String.format(Locale.UK, "%.2f", externalsLineTotal)
                ).setFontSize(12));
            }

            if (flyQty > 0) {
                document.add(new Paragraph(
                        "Fly units: " + flyQty +
                                " @ €" + String.format(Locale.UK, "%.2f", flyUnitPrice) +
                                " = €" + String.format(Locale.UK, "%.2f", flyLineTotal)
                ).setFontSize(12));
            }

            if (insectQty > 0) {
                document.add(new Paragraph(
                        "Insect monitoring required on site: €" +
                                String.format(Locale.UK, "%.2f", insectMonitorLineTotal)
                ).setFontSize(12));
            }

            if (requiredMaterialsLines != null && !requiredMaterialsLines.isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Required materials (one-off):").setFontSize(12).setBold());
                for (String line : requiredMaterialsLines) {
                    document.add(new Paragraph(line).setFontSize(12));
                }
                document.add(new Paragraph(
                        "Required materials total excluding VAT: €" + String.format(Locale.UK, "%.2f", requiredMaterialsTotalExcl)
                ).setFontSize(12));
                document.add(new Paragraph(
                        "Required materials total including VAT: €" + String.format(Locale.UK, "%.2f", requiredMaterialsTotalIncl)
                ).setFontSize(12).setBold());
            }

            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "Total excluding VAT: €" + String.format(Locale.UK, "%.2f", totalExclDisplay)
            ).setFontSize(12).setBold());

            document.add(new Paragraph(
                    "VAT (23%): €" + String.format(Locale.UK, "%.2f", vatDisplay)
            ).setFontSize(12).setBold());

            document.add(new Paragraph(
                    "Total including VAT per year: €" + String.format(Locale.UK, "%.2f", totalInclDisplay)
            ).setFontSize(14).setBold());

            document.add(new Paragraph(" "));
            document.add(new Paragraph(
                    "Price per quarter: €" + String.format(Locale.UK, "%.2f", pricePerQuarterDisplay)
            ).setFontSize(14).setBold());

            double firstQuarterDueDisplay = firstQuarterPaymentDue > 0 ? firstQuarterPaymentDue : pricePerQuarterDisplay;
            document.add(new Paragraph(
                    "First quarter payment due: €" + String.format(Locale.UK, "%.2f", firstQuarterDueDisplay)
            ).setFontSize(16).setBold());

            document.add(new Paragraph(
                    "First quarter payment can be taken on site after setup or an invoice can be sent and upon receipt of payment a setup can be done."
            ).setFontSize(11).setItalic());

            if (imageUris != null && !imageUris.isEmpty()) {
                document.add(new Paragraph("\nImages").setFontSize(14).setBold());
                float maxImageWidth = 400f;
                for (Uri uri : imageUris) {
                    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                        if (is != null) {
                            byte[] bytes = readAllBytes(is);
                            if (bytes != null && bytes.length > 0) {
                                ImageData imgData = ImageDataFactory.create(bytes);
                                Image img = new Image(imgData).setWidth(maxImageWidth).setAutoScaleHeight(true);
                                document.add(img);
                                document.add(new Paragraph(" "));
                            }
                        }
                    } catch (Exception e) { /* skip */ }
                }
            }

            document.close();
            byte[] stampPw = (ownerPassword != null && !ownerPassword.trim().isEmpty())
                    ? ownerPassword.trim().getBytes() : null;
            PdfFooterPageNumberStamper.stamp(context, pdfFile, TenantBranding.footerCompanyWebsiteLine(context), stampPw);
            return pdfFile;

        } catch (IOException e) {
            Toast.makeText(context, "Error generating PDF", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = is.read(b)) != -1) buf.write(b, 0, n);
        return buf.toByteArray();
    }

    private String getQuotationDescription() {
        return "Quotation for Comprehensive Pest Management Services\n\n"
                + "This quotation outlines the pest control services tailored to meet the specific needs of your premises. "
                + "Our offerings are designed to ensure a safe, pest-free environment through reliable, efficient, and environmentally friendly solutions. "
                + "The details below summarize the scope of work, service descriptions, and associated costs.\n\n"
                + "Scope of Services:\n"
                + "1. Initial Inspection: Thorough site inspection to identify existing pest infestations and potential entry points.\n"
                + "2. Treatment Plan: Implementation of appropriate treatments for pest elimination, including preventive measures.\n"
                + "3. Scheduled Maintenance: Regular follow-up visits for ongoing monitoring and maintenance of a pest-free environment.\n"
                + "4. Emergency Support: On-call service for urgent pest control needs.\n"
                + "   - 24-Hour Response Time: Guaranteed response time for internal pest activity.\n"
                + "   - 72-Hour Response Time: Guaranteed response time for external pest activity.\n"
                + "5. Detailed Reporting: Comprehensive documentation of each service visit, including treatments applied and recommendations for further action.\n\n"
                + "6pt Contract:\n"
                + "This quotation also includes a 6pt service contract that ensures a structured and consistent approach to pest control management. The 6pt contract includes:\n"
                + "1. Initial assessment and customized treatment planning.\n"
                + "2. Installation of monitoring devices to detect pest activity.\n"
                + "3. Regular servicing and inspection of all pest control equipment.\n"
                + "4. Emergency response as per agreed response times.\n"
                + "5. Preventive measures to reduce future infestations.\n"
                + "6. Safe and eco-friendly pest control treatments.\n"
                + "7. Six Visit a Year on site every 8 weeks.\n\n"
                + "Additional Benefits:\n"
                + "1. **Staff Discounts**: All pest control services are offered to your staff at discounted rates, ensuring comprehensive coverage for personal properties.\n"
                + "2. **Discounted Spray Treatments**: All-inclusive pest spray treatments are available at a reduced price, offering exceptional value while maintaining high service standards.\n\n"
                + "Why Choose Us:\n"
                + "- Licensed and certified pest control specialists.\n"
                + "- Use of environmentally friendly and safe pest management solutions.\n"
                + "- Flexible service schedules to minimize disruption to your daily operations.\n"
                + "- Guaranteed customer satisfaction with a commitment to excellence.\n\n"
                + "We appreciate the opportunity to serve you and are confident in our ability to deliver quality pest control solutions. "
                + "Should you have any questions or require further details, please do not hesitate to contact us.";
    }


}
