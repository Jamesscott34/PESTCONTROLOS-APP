package com.grpc.grpc;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * General Quotation (catalog-driven). Loads sales.json from current flavor (grpc/demo),
 * lets user pick customer, address, date, issue (spinner), base price; shows VAT 13.5% and total;
 * generates PDF via GeneralQuotationPDF.generateCatalogQuotation with filename CustomerName_IssueKey.pdf.
 */
public class GeneralQuotationFromCatalogActivity extends AppCompatActivity {

    private static final double VAT_RATE = 0.135;

    private EditText customerNameInput, customerAddressInput, dateInput, basePriceInput, basePriceInput2, additionalInfoInput, descriptionShortInput;
    private Spinner issueSpinner, issueSpinner2;
    private TextView vatValue, totalValue;
    private Button generatePdfButton, addSecondIssueButton;
    private CheckBox passwordProtectCheckbox;
    private LinearLayout secondIssueSection;

    private SalesCatalog catalog;
    private List<SalesCatalog.SalesItemWithCategory> flatItems;
    private String userName;

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
        descriptionShortInput = findViewById(R.id.descriptionShortInput);
        basePriceInput = findViewById(R.id.basePriceInput);
        addSecondIssueButton = findViewById(R.id.addSecondIssueButton);
        secondIssueSection = findViewById(R.id.secondIssueSection);
        issueSpinner2 = findViewById(R.id.issueSpinner2);
        basePriceInput2 = findViewById(R.id.basePriceInput2);
        additionalInfoInput = findViewById(R.id.additionalInfoInput);
        vatValue = findViewById(R.id.vatValue);
        totalValue = findViewById(R.id.totalValue);
        generatePdfButton = findViewById(R.id.generatePdfButton);
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

        // When issue selected, only refresh VAT/Total (no default price prefill)
        issueSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
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

        updateVatAndTotal();
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
        double vat = Math.round(totalBase * VAT_RATE * 100.0) / 100.0;
        double total = Math.round((totalBase + vat) * 100.0) / 100.0;
        vatValue.setText(String.format(Locale.getDefault(), "€%.2f", vat));
        totalValue.setText(String.format(Locale.getDefault(), "€%.2f", total));
    }

    private void generatePdf(String ownerPassword) {
        String customerName = customerNameInput.getText().toString().trim();
        String address = customerAddressInput.getText().toString().trim();
        String dateStr = dateInput.getText().toString().trim();
        int pos = issueSpinner.getSelectedItemPosition();
        if (customerName.isEmpty() || address.isEmpty() || dateStr.isEmpty() || pos < 0 || pos >= flatItems.size()) {
            Toast.makeText(this, R.string.general_quotation_error_fill_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double basePrice;
        try {
            basePrice = Double.parseDouble(basePriceInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.general_quotation_error_invalid_price, Toast.LENGTH_SHORT).show();
            return;
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
        String descriptionShort = descriptionShortInput != null ? descriptionShortInput.getText().toString().trim() : "";
        GeneralQuotationPDF.generateCatalogQuotation(
                customerName, address, dateStr,
                item1, basePrice, item2, basePrice2, descriptionShort, additionalInfo,
                userEmail, mobileNumber, ownerPassword,
                this);
    }
}
