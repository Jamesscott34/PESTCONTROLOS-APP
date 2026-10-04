package com.grpc.grpc.contracts.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only asset quantities for one contract. Zero standard quantities stay hidden.
 */
public class ContractAssetsActivity extends AppCompatActivity {

    public static final String EXTRA_CONTRACT_ID = "CONTRACT_ID";
    public static final String EXTRA_CONTRACT_NAME = "CONTRACT_NAME";

    private String contractId = "";
    private String contractName = "";
    private boolean accessChecked;
    private boolean accessRequestStarted;

    private TextView nameView;
    private TextView emptyView;
    private LinearLayout list;
    private Button editButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.contract_assets_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_contract_assets);
        nameView = findViewById(R.id.assetsContractName);
        emptyView = findViewById(R.id.assetsEmpty);
        list = findViewById(R.id.assetsList);
        editButton = findViewById(R.id.buttonEditAssets);
        editButton.setEnabled(false);

        contractId = getIntent().getStringExtra(EXTRA_CONTRACT_ID);
        contractName = getIntent().getStringExtra(EXTRA_CONTRACT_NAME);
        if (contractId == null) contractId = "";
        if (contractName == null) contractName = "";
        contractId = contractId.trim();
        nameView.setText(contractName);

        editButton.setOnClickListener(v -> openEditor());

        if (contractId.isEmpty()) {
            Toast.makeText(this, R.string.contract_assets_missing, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BuildConfig.IS_OFFLINE || contractId.isEmpty()) return;
        if (!accessChecked) {
            SessionManager.ensureLoaded(this, session -> runOnUiThread(this::checkAccess));
        } else {
            loadAssets();
        }
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
                nameView.setText(contractName);
            }
            accessChecked = true;
            editButton.setEnabled(true);
            loadAssets();
        }));
    }

    private void loadAssets() {
        ContractAssetsRepository.load(contractId, (exists, items, error) -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (error != null) {
                Toast.makeText(this, R.string.contract_assets_load_failed, Toast.LENGTH_LONG).show();
                return;
            }
            showItems(items);
        }));
    }

    private void showItems(List<ContractAsset> items) {
        list.removeAllViews();
        Map<String, Integer> standardQty = new LinkedHashMap<>();
        for (String name : StandardContractAssets.NAMES) {
            standardQty.put(name, 0);
        }
        List<ContractAsset> customs = new ArrayList<>();
        if (items != null) {
            for (ContractAsset item : items) {
                String canonical = StandardContractAssets.canonical(item.name);
                if (canonical != null) {
                    standardQty.put(canonical, item.quantity);
                } else {
                    customs.add(item);
                }
            }
        }

        int shown = 0;
        for (Map.Entry<String, Integer> entry : standardQty.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            addRow(StandardContractAssets.displayPlural(entry.getKey()), entry.getValue());
            shown++;
        }
        for (ContractAsset custom : customs) {
            addRow(custom.name, custom.quantity);
            shown++;
        }

        boolean empty = shown == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        editButton.setText(R.string.contract_assets_edit);
    }

    private void addRow(String name, int quantity) {
        View row = getLayoutInflater().inflate(R.layout.item_contract_asset, list, false);
        TextView nameView = row.findViewById(R.id.assetName);
        TextView quantityView = row.findViewById(R.id.assetQuantity);
        nameView.setText(name);
        quantityView.setText(String.valueOf(quantity));
        list.addView(row);
    }

    private void openEditor() {
        Intent intent = new Intent(this, ContractAssetsEditActivity.class);
        intent.putExtra(EXTRA_CONTRACT_ID, contractId);
        intent.putExtra(EXTRA_CONTRACT_NAME, contractName);
        startActivity(intent);
    }
}
