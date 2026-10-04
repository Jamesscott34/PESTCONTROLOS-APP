package com.grpc.grpc.contracts.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.contracts.data.ContractAssetsRepository;
import com.grpc.grpc.contracts.model.ContractAsset;
import com.grpc.grpc.contracts.util.StandardContractAssets;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Edit standard quantities and custom asset names for one contract.
 */
public class ContractAssetsEditActivity extends AppCompatActivity {

    private String contractId = "";
    private String contractName = "";
    private boolean saving;
    private boolean accessRequestStarted;

    private final EditText[] standardInputs = new EditText[StandardContractAssets.NAMES.length];
    private final List<View> customRows = new ArrayList<>();
    private LinearLayout customContainer;
    private Button saveButton;
    private Button addButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.contract_assets_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_contract_assets_edit);

        contractId = getIntent().getStringExtra(ContractAssetsActivity.EXTRA_CONTRACT_ID);
        contractName = getIntent().getStringExtra(ContractAssetsActivity.EXTRA_CONTRACT_NAME);
        if (contractId == null) contractId = "";
        if (contractName == null) contractName = "";
        contractId = contractId.trim();

        TextView nameView = findViewById(R.id.assetsEditContractName);
        nameView.setText(contractName);
        LinearLayout standardContainer = findViewById(R.id.standardAssetsContainer);
        customContainer = findViewById(R.id.customAssetsContainer);
        addButton = findViewById(R.id.buttonAddCustomAsset);
        saveButton = findViewById(R.id.buttonSaveAssets);

        for (int i = 0; i < StandardContractAssets.NAMES.length; i++) {
            View row = getLayoutInflater().inflate(R.layout.item_contract_asset_quantity, standardContainer, false);
            TextView label = row.findViewById(R.id.assetTypeLabel);
            label.setText(StandardContractAssets.NAMES[i]);
            standardInputs[i] = row.findViewById(R.id.assetQuantityInput);
            standardInputs[i].setText("0");
            standardContainer.addView(row);
        }

        addButton.setOnClickListener(v -> addCustomRow("", ""));
        saveButton.setOnClickListener(v -> save());
        saveButton.setEnabled(false);
        addButton.setEnabled(false);

        if (contractId.isEmpty()) {
            Toast.makeText(this, R.string.contract_assets_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        SessionManager.ensureLoaded(this, session -> runOnUiThread(this::checkAccess));
    }

    private void checkAccess() {
        if (accessRequestStarted) return;
        accessRequestStarted = true;
        ContractAssetsRepository.checkAccess(contractId, this, (allowed, loadedName, error) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (error != null && "missing".equals(error.getMessage())) {
                Toast.makeText(this, R.string.contract_assets_missing, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (!allowed) {
                Toast.makeText(this, R.string.contract_assets_denied, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (loadedName != null && !loadedName.trim().isEmpty()) {
                contractName = loadedName.trim();
                TextView nameView = findViewById(R.id.assetsEditContractName);
                nameView.setText(contractName);
            }
            loadExisting();
        }));
    }

    private void loadExisting() {
        ContractAssetsRepository.load(contractId, (exists, items, error) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (error != null) {
                Toast.makeText(this, R.string.contract_assets_load_failed, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            applyLoaded(items);
            saveButton.setEnabled(true);
            addButton.setEnabled(true);
        }));
    }

    private void applyLoaded(@Nullable List<ContractAsset> items) {
        Map<String, Integer> standardQty = new LinkedHashMap<>();
        for (String name : StandardContractAssets.NAMES) {
            standardQty.put(name, 0);
        }
        if (items == null) {
            fillStandards(standardQty);
            return;
        }
        for (ContractAsset item : items) {
            String canonical = StandardContractAssets.canonical(item.name);
            if (canonical != null) {
                standardQty.put(canonical, item.quantity);
            } else {
                addCustomRow(item.name, String.valueOf(item.quantity));
            }
        }
        fillStandards(standardQty);
    }

    private void fillStandards(Map<String, Integer> standardQty) {
        for (int i = 0; i < StandardContractAssets.NAMES.length; i++) {
            Integer qty = standardQty.get(StandardContractAssets.NAMES[i]);
            standardInputs[i].setText(String.valueOf(qty == null ? 0 : qty));
        }
    }

    private void addCustomRow(String name, String quantity) {
        if (customRows.size() >= StandardContractAssets.MAX_CUSTOM) {
            Toast.makeText(this, R.string.contract_assets_too_many_custom, Toast.LENGTH_LONG).show();
            return;
        }
        View row = getLayoutInflater().inflate(R.layout.item_contract_asset_custom, customContainer, false);
        EditText nameInput = row.findViewById(R.id.customAssetName);
        EditText quantityInput = row.findViewById(R.id.customAssetQuantity);
        Button remove = row.findViewById(R.id.customAssetRemove);
        nameInput.setText(name);
        quantityInput.setText(quantity);
        remove.setOnClickListener(v -> {
            customContainer.removeView(row);
            customRows.remove(row);
        });
        customRows.add(row);
        customContainer.addView(row);
    }

    private void save() {
        if (saving) return;
        List<ContractAsset> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < StandardContractAssets.NAMES.length; i++) {
            String label = StandardContractAssets.NAMES[i];
            Integer quantity = StandardContractAssets.parseQuantity(standardInputs[i].getText().toString());
            if (quantity == null) {
                Toast.makeText(this, getString(R.string.contract_assets_bad_quantity, label), Toast.LENGTH_LONG).show();
                return;
            }
            items.add(new ContractAsset(label, quantity, false));
            seen.add(StandardContractAssets.key(label));
        }

        for (View row : customRows) {
            EditText nameInput = row.findViewById(R.id.customAssetName);
            EditText quantityInput = row.findViewById(R.id.customAssetQuantity);
            String name = StandardContractAssets.cleanName(nameInput.getText().toString());
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, R.string.contract_assets_blank_name, Toast.LENGTH_LONG).show();
                return;
            }
            if (name.length() > StandardContractAssets.MAX_NAME_LENGTH) {
                Toast.makeText(this, R.string.contract_assets_name_too_long, Toast.LENGTH_LONG).show();
                return;
            }
            String canonical = StandardContractAssets.canonical(name);
            if (canonical != null) {
                Toast.makeText(this, getString(R.string.contract_assets_standard_exists, canonical), Toast.LENGTH_LONG).show();
                return;
            }
            String key = StandardContractAssets.key(name);
            if (!seen.add(key)) {
                Toast.makeText(this, R.string.contract_assets_duplicate, Toast.LENGTH_LONG).show();
                return;
            }
            Integer quantity = StandardContractAssets.parseQuantity(quantityInput.getText().toString());
            if (quantity == null) {
                Toast.makeText(this, getString(R.string.contract_assets_bad_quantity, name), Toast.LENGTH_LONG).show();
                return;
            }
            items.add(new ContractAsset(name, quantity, true));
        }

        saving = true;
        saveButton.setEnabled(false);
        ContractAssetsRepository.save(this, contractId, contractName, items, (success, error) -> runOnUiThread(() -> {
            saving = false;
            if (isFinishing()) return;
            if (!success) {
                saveButton.setEnabled(true);
                int message = error != null && isDenied(error)
                        ? R.string.contract_assets_denied
                        : R.string.contract_assets_save_failed;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.contract_assets_saved, Toast.LENGTH_SHORT).show();
            finish();
        }));
    }

    private static boolean isDenied(@Nullable Exception error) {
        if (error == null || error.getMessage() == null) return false;
        return error.getMessage().toLowerCase(Locale.UK).contains("permission");
    }
}
