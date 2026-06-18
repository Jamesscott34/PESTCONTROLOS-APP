package com.grpc.grpc.quotations.ui;

import com.grpc.grpc.R;
import com.grpc.grpc.core.*;
import com.grpc.grpc.core.QuotationStorageUploader;
import com.grpc.grpc.quotations.data.SalesCatalogLoader;
import com.grpc.grpc.quotations.model.GeneralQuotationPDF;
import com.grpc.grpc.quotations.model.SalesCatalog;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * General Quotation (catalog-driven). Loads shared sales.json from main assets (all flavors),
 * lets user pick customer, address, date, issue (spinner), base price; shows VAT 13.5% and total;
 * generates PDF via GeneralQuotationPDF.generateCatalogQuotation with filename CustomerName_IssueKey.pdf.
 */
public class GeneralQuotationFromCatalogActivity extends AppCompatActivity {
    private static final int REQUEST_PREVIEW_CATALOG_QUOTATION = 922;

    private EditText customerNameInput, customerAddressInput, dateInput, basePriceInput, basePriceInput2, additionalInfoInput;
    private Spinner issueSpinner, issueSpinner2;
    private TextView vatValue, totalValue;
    private RadioGroup vatRateGroup;
    private RadioButton vat13_5, vat23;
    private Button generatePdfButton, previewPdfButton, addSecondIssueButton;
    private CheckBox passwordProtectCheckbox;
    private LinearLayout secondIssueSection;

    private SalesCatalog catalog;
    private List<SalesCatalog.SalesItemWithCategory> flatItems;
    private String userName;
    private boolean additionalInfoEditedByUser;
    private boolean suppressAdditionalInfoWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_quotation_from_catalog);

        userName = getIntent().getStringExtra("USER_NAME");
        if (userName == null) userName = "";

        catalog = SalesCatalogLoader.load(this);
        if (catalog == null) {
            Toast.makeText(this, R.string.general_quotation_error_no_catalog, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        flatItems = catalog.getAllItems();
        if (flatItems == null || flatItems.isEmpty()) {
            Toast.makeText(this, R.string.general_quotation_error_no_catalog, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        customerNameInput = findViewById(R.id.customerNameInput);
        customerAddressInput = findViewById(R.id.customerAddressInput);
        dateInput = findViewById(R.id.dateInput);
        issueSpinner = findViewById(R.id.issueSpinner);
        basePriceInput = findViewById(R.id.basePriceInput);
        addSecondIssueButton = findViewById(R.id.addSecondIssueButton);
        secondIssueSection = findViewById(R.id.secondIssueSection);
        issueSpinner2 = findViewById(R.id.issueSpinner2);
        basePriceInput2 = findViewById(R.id.basePriceInput2);
        additionalInfoInput = findViewById(R.id.additionalInfoInput);
        vatValue = findViewById(R.id.vatValue);
        totalValue = findViewById(R.id.totalValue);
        vatRateGroup = findViewById(R.id.vatRateGroup);
        vat13_5 = findViewById(R.id.vat13_5);
        vat23 = findViewById(R.id.vat23);
        generatePdfButton = findViewById(R.id.generatePdfButton);
        previewPdfButton = findViewById(R.id.previewPdfButton);
        passwordProtectCheckbox = findViewById(R.id.passwordProtectCheckbox);

        // Prefill from intent (Create Report → General Quotation)
        String prefilledName = getIntent().getStringExtra("PREFILL_CUSTOMER_NAME");
        String prefilledAddress = getIntent().getStringExtra("PREFILL_CUSTOMER_ADDRESS");
        String prefilledDate = getIntent().getStringExtra("PREFILL_DATE");
        if (prefilledName != null && !prefilledName.isEmpty()) customerNameInput.setText(prefilledName);
        if (prefilledAddress != null && !prefilledAddress.isEmpty()) customerAddressInput.setText(prefilledAddress);
        if (prefilledDate != null && !prefilledDate.isEmpty()) {
            dateInput.setText(prefilledDate);
        } else {
            dateInput.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
        }

        String[] labels = new String[flatItems.size()];
        for (int i = 0; i < flatItems.size(); i++) {
            labels[i] = flatItems.get(i).getSpinnerLabel();
        }
        issueSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        issueSpinner2.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));

        addSecondIssueButton.setOnClickListener(v -> {
            if (secondIssueSection.getVisibility() == View.VISIBLE) {
                secondIssueSection.setVisibility(View.GONE);
                addSecondIssueButton.setText(R.string.general_quotation_add_second_issue);
                if (basePriceInput2 != null) basePriceInput2.setText("");
            } else {
                secondIssueSection.setVisibility(View.VISIBLE);
                addSecondIssueButton.setText(R.string.general_quotation_remove_second_issue);
            }
            updateVatAndTotal();
        });

        if (additionalInfoInput != null) {
            additionalInfoInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!suppressAdditionalInfoWatcher) {
                        additionalInfoEditedByUser = true;
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        issueSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                applySelectedCatalogItem(position);
                updateVatAndTotal();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateVatAndTotal();
            }
        });
        issueSpinner2.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                updateVatAndTotal();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateVatAndTotal();
            }
        });

        basePriceInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateVatAndTotal();
            }
        });
        if (basePriceInput2 != null) {
            basePriceInput2.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    updateVatAndTotal();
                }
            });
        }

        generatePdfButton.setOnClickListener(v -> {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, this::generatePdf);
            } else {
                generatePdf(null);
            }
        });
        previewPdfButton.setOnClickListener(v -> previewPdf());

        if (vatRateGroup != null) {
            vatRateGroup.setOnCheckedChangeListener((group, checkedId) -> updateVatAndTotal());
        }

        if (issueSpinner.getAdapter() != null && issueSpinner.getAdapter().getCount() > 0) {
            applySelectedCatalogItem(issueSpinner.getSelectedItemPosition());
        }
        updateVatAndTotal();
    }

    private void applySelectedCatalogItem(int position) {
        if (position < 0 || position >= flatItems.size()) return;
        SalesCatalog.SalesItem item = flatItems.get(position).getItem();
        if (item == null) return;

        if (!additionalInfoEditedByUser && additionalInfoInput != null) {
            String breakdown = item.getQuoteBreakdown();
            suppressAdditionalInfoWatcher = true;
            additionalInfoInput.setText(breakdown != null ? breakdown : "");
            suppressAdditionalInfoWatcher = false;
        }

        if (item.getDefaultPrice() > 0 && basePriceInput != null) {
            String current = basePriceInput.getText() != null ? basePriceInput.getText().toString().trim() : "";
            if (current.isEmpty()) {
                basePriceInput.setText(String.format(Locale.getDefault(), "%.2f", item.getDefaultPrice()));
            }
        }
    }

    private void updateVatAndTotal() {
        double base1 = 0;
        try {
            String t1 = basePriceInput.getText().toString().trim();
            if (!t1.isEmpty()) base1 = Double.parseDouble(t1);
        } catch (NumberFormatException ignored) {}
        double base2 = 0;
        if (secondIssueSection != null && secondIssueSection.getVisibility() == View.VISIBLE && basePriceInput2 != null) {
            try {
                String t2 = basePriceInput2.getText().toString().trim();
                if (!t2.isEmpty()) base2 = Double.parseDouble(t2);
            } catch (NumberFormatException ignored) {}
        }
        double totalBase = base1 + base2;
        if (totalBase <= 0) {
            vatValue.setText("");
            totalValue.setText("");
            return;
        }
        double vatRate = getSelectedVatRate();
        double vat = Math.round(totalBase * vatRate * 100.0) / 100.0;
        double total = Math.round((totalBase + vat) * 100.0) / 100.0;
        vatValue.setText(CurrencyFormatter.formatEuro(vat));
        totalValue.setText(CurrencyFormatter.formatEuro(total));
    }

    private double getSelectedVatRate() {
        if (vat23 != null && vat23.isChecked()) {
            return GeneralQuotationPDF.VAT_RATE_REDUCED;
        }
        return GeneralQuotationPDF.VAT_RATE_STANDARD;
    }

    private void generatePdf(String ownerPassword) {
        File generatedFile = generatePdfInternal(ownerPassword, null, true);
        if (generatedFile != null) {
            QuotationStorageUploader.uploadQuotationPdf(
                    generatedFile,
                    () -> Toast.makeText(this, R.string.general_quotation_success, Toast.LENGTH_LONG).show(),
                    e -> Toast.makeText(this, "Quotation upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
            );
        }
    }

    private File generatePdfInternal(String ownerPassword, File outputDirectory, boolean persistAndFinish) {
        String customerName = customerNameInput.getText().toString().trim();
        String address = customerAddressInput.getText().toString().trim();
        String dateStr = dateInput.getText().toString().trim();
        int pos = issueSpinner.getSelectedItemPosition();
        if (customerName.isEmpty() || address.isEmpty() || dateStr.isEmpty() || pos < 0 || pos >= flatItems.size()) {
            Toast.makeText(this, R.string.general_quotation_error_fill_required, Toast.LENGTH_SHORT).show();
            return null;
        }
        double basePrice;
        try {
            basePrice = Double.parseDouble(basePriceInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.general_quotation_error_invalid_price, Toast.LENGTH_SHORT).show();
            return null;
        }
        SalesCatalog.SalesItem item1 = flatItems.get(pos).getItem();

        SalesCatalog.SalesItem item2 = null;
        double basePrice2 = 0;
        if (secondIssueSection != null && secondIssueSection.getVisibility() == View.VISIBLE && issueSpinner2 != null && basePriceInput2 != null) {
            int pos2 = issueSpinner2.getSelectedItemPosition();
            String price2Str = basePriceInput2.getText().toString().trim();
            if (pos2 >= 0 && pos2 < flatItems.size() && !price2Str.isEmpty()) {
                try {
                    basePrice2 = Double.parseDouble(price2Str);
                    item2 = flatItems.get(pos2).getItem();
                } catch (NumberFormatException ignored) {}
            }
        }

        String userEmail = SessionManager.getEmail(this);
        String mobileNumber = SessionManager.getNumber(this);
        if (userEmail == null) userEmail = "";
        if (mobileNumber == null) mobileNumber = "";

        String additionalInfo = additionalInfoInput != null ? additionalInfoInput.getText().toString().trim() : "";
        return GeneralQuotationPDF.generateCatalogQuotation(
                customerName, address, dateStr,
                item1, basePrice, item2, basePrice2, additionalInfo,
                userEmail, mobileNumber, ownerPassword,
                this, outputDirectory, persistAndFinish, getSelectedVatRate());
    }

    private void previewPdf() {
        File previewDir = new File(getCacheDir(), "report_previews");
        if (!previewDir.exists()) previewDir.mkdirs();
        File previewFile = generatePdfInternal(null, previewDir, false);
        if (previewFile == null || !previewFile.exists()) return;
        android.content.Intent previewIntent = new android.content.Intent(this, com.grpc.grpc.reports.ui.ReportPreviewActivity.class);
        previewIntent.putExtra(com.grpc.grpc.reports.ui.ReportPreviewActivity.EXTRA_PREVIEW_PDF_PATH, previewFile.getAbsolutePath());
        startActivityForResult(previewIntent, REQUEST_PREVIEW_CATALOG_QUOTATION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREVIEW_CATALOG_QUOTATION
                && resultCode == RESULT_OK
                && data != null
                && data.getBooleanExtra(com.grpc.grpc.reports.ui.ReportPreviewActivity.EXTRA_CONFIRM_SAVE, false)) {
            if (passwordProtectCheckbox != null && passwordProtectCheckbox.isChecked()) {
                PdfPasswordPrompt.prompt(this, this::generatePdf);
            } else {
                generatePdf(null);
            }
        }
    }
}
