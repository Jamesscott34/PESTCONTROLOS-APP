package com.grpc.grpc.maps.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.grpc.grpc.R;
import com.grpc.grpc.core.FirestorePaths;
import com.grpc.grpc.core.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapsPlaceholderActivity extends AppCompatActivity {
    private String userName;
    private String contractId;
    private String companyName;
    private String address;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps_placeholder);

        userName = getIntent().getStringExtra("USER_NAME");
        contractId = getIntent().getStringExtra("CONTRACT_ID");
        companyName = getIntent().getStringExtra("COMPANY_NAME");
        address = getIntent().getStringExtra("ADDRESS");

        TextView titleText = findViewById(R.id.mapsTitleText);
        TextView descriptionText = findViewById(R.id.mapsDescriptionText);
        TextView contractNameText = findViewById(R.id.contractNameText);
        TextView contractAddressText = findViewById(R.id.contractAddressText);
        TextView contractIdText = findViewById(R.id.contractIdText);
        Button primaryButton = findViewById(R.id.mapsPrimaryButton);
        Button secondaryButton = findViewById(R.id.mapsSecondaryButton);
        Button tertiaryButton = findViewById(R.id.mapsTertiaryButton);
        Button backButton = findViewById(R.id.backButton);
        boolean canCreateMaps = SessionManager.canMap(this);

        boolean hasContract = hasContract();
        titleText.setText(hasContract ? "Contract Maps" : "Maps");
        descriptionText.setText(hasContract
                ? "View saved maps for this contract or create a new contract map."
                : "Create a map, create a contract map, or view saved maps.");

        contractNameText.setText(safe(companyName, "N/A"));
        contractAddressText.setText(safe(address, "N/A"));
        contractIdText.setText(safe(contractId, "No contract selected"));

        if (hasContract) {
            primaryButton.setText("View Contract Maps");
            secondaryButton.setText("Create Contract Map");
            tertiaryButton.setVisibility(android.view.View.GONE);

            primaryButton.setOnClickListener(v -> openMapsList(false));
            secondaryButton.setOnClickListener(v -> {
                if (!canCreateMaps) {
                    Toast.makeText(this, "You do not have permission to create maps.", Toast.LENGTH_SHORT).show();
                    return;
                }
                openEditor(contractId, companyName, address);
            });
        } else {
            primaryButton.setText("Create a Map");
            secondaryButton.setText("Create a Contract Map");
            tertiaryButton.setText("View Maps");
            tertiaryButton.setVisibility(android.view.View.VISIBLE);

            primaryButton.setOnClickListener(v -> {
                if (!canCreateMaps) {
                    Toast.makeText(this, "You do not have permission to create maps.", Toast.LENGTH_SHORT).show();
                    return;
                }
                openEditor(null, null, null);
            });
            secondaryButton.setOnClickListener(v -> {
                if (!canCreateMaps) {
                    Toast.makeText(this, "You do not have permission to create maps.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showContractPickerAndCreate();
            });
            tertiaryButton.setOnClickListener(v -> showViewMapsChoice());
        }

        if (!canCreateMaps) {
            descriptionText.setText(hasContract
                    ? "View saved maps for this contract. Map creation requires canMap = true."
                    : "View saved maps. Map creation requires canMap = true.");
        }

        backButton.setOnClickListener(v -> finish());
    }

    private boolean hasContract() {
        return contractId != null && !contractId.trim().isEmpty();
    }

    private void openEditor(String selectedContractId, String selectedCompanyName, String selectedAddress) {
        Intent intent = new Intent(this, SiteMapEditorActivity.class);
        intent.putExtra("USER_NAME", userName);
        if (selectedContractId != null && !selectedContractId.trim().isEmpty()) {
            intent.putExtra("CONTRACT_ID", selectedContractId);
            intent.putExtra("COMPANY_NAME", selectedCompanyName);
            intent.putExtra("ADDRESS", selectedAddress);
        }
        startActivity(intent);
    }

    private void openMapsList(boolean localOnly) {
        Intent intent = new Intent(this, MapsListActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra(MapsListActivity.EXTRA_LOCAL_ONLY, localOnly);
        if (!localOnly && hasContract()) {
            intent.putExtra("CONTRACT_ID", contractId);
            intent.putExtra("COMPANY_NAME", companyName);
            intent.putExtra("ADDRESS", address);
        }
        startActivity(intent);
    }

    private void showContractPickerAndCreate() {
        showContractPicker(false);
    }

    private void showContractPickerAndView() {
        showContractPicker(true);
    }

    private void showContractPicker(boolean forViewing) {
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("Loading contracts")
                .setMessage("Please wait...")
                .setCancelable(false)
                .show();

        loadAccessibleContracts((contracts, errorMessage) -> {
            loading.dismiss();
            if (errorMessage != null) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                return;
            }
            if (contracts.isEmpty()) {
                Toast.makeText(this, "No contracts available for maps.", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] labels = new String[contracts.size()];
            for (int i = 0; i < contracts.size(); i++) {
                ContractChoice contract = contracts.get(i);
                labels[i] = contract.companyName + " - " + contract.address;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select contract")
                    .setItems(labels, (dialog, which) -> {
                        ContractChoice selected = contracts.get(which);
                        if (forViewing) {
                            openContractMapsList(selected.contractId, selected.companyName, selected.address);
                        } else {
                            openEditor(selected.contractId, selected.companyName, selected.address);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void showViewMapsChoice() {
        new AlertDialog.Builder(this)
                .setTitle("View Maps")
                .setItems(new CharSequence[]{"Saved Maps", "Contract Maps"}, (dialog, which) -> {
                    if (which == 0) {
                        openMapsList(true);
                    } else {
                        showContractPickerAndView();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openContractMapsList(String selectedContractId, String selectedCompanyName, String selectedAddress) {
        Intent intent = new Intent(this, MapsListActivity.class);
        intent.putExtra("USER_NAME", userName);
        intent.putExtra(MapsListActivity.EXTRA_LOCAL_ONLY, false);
        intent.putExtra("CONTRACT_ID", selectedContractId);
        intent.putExtra("COMPANY_NAME", selectedCompanyName);
        intent.putExtra("ADDRESS", selectedAddress);
        startActivity(intent);
    }

    private void loadAccessibleContracts(ContractsCallback callback) {
        String contractKey = SessionManager.getContractKey(this);
        boolean isAdmin = SessionManager.isAdmin(this) || SessionManager.canViewAllContracts(this);

        if (!isAdmin && (contractKey == null || contractKey.trim().isEmpty())) {
            callback.onLoaded(new ArrayList<>(), "No contract key found for this user.");
            return;
        }

        if (isAdmin) {
            FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.CONTRACTS)
                    .get()
                    .addOnSuccessListener(snapshot -> callback.onLoaded(toChoices(snapshot.getDocuments()), null))
                    .addOnFailureListener(e -> callback.onLoaded(new ArrayList<>(), "Failed to load contracts: " + e.getMessage()));
        } else {
            String keyFilter = contractKey.trim().toLowerCase(Locale.getDefault());
            FirebaseFirestore.getInstance()
                    .collection(FirestorePaths.CONTRACTS)
                    .whereEqualTo("assignedTech", keyFilter)
                    .get()
                    .addOnSuccessListener(snapshot -> callback.onLoaded(toChoices(snapshot.getDocuments()), null))
                    .addOnFailureListener(e -> callback.onLoaded(new ArrayList<>(), "Failed to load contracts: " + e.getMessage()));
        }
    }

    private List<ContractChoice> toChoices(List<? extends com.google.firebase.firestore.DocumentSnapshot> docs) {
        List<ContractChoice> choices = new ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
            Map<String, Object> data = doc.getData();
            if (data == null) continue;
            String id = doc.getId();
            String name = data.get("name") != null ? String.valueOf(data.get("name")) : "Unknown contract";
            String addressValue = data.get("address") != null ? String.valueOf(data.get("address")) : "No address";
            choices.add(new ContractChoice(id, name, addressValue));
        }
        Collections.sort(choices, (a, b) -> a.companyName.compareToIgnoreCase(b.companyName));
        return choices;
    }

    private String safe(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
    }

    private interface ContractsCallback {
        void onLoaded(List<ContractChoice> contracts, String errorMessage);
    }

    private static final class ContractChoice {
        final String contractId;
        final String companyName;
        final String address;

        ContractChoice(String contractId, String companyName, String address) {
            this.contractId = contractId;
            this.companyName = companyName;
            this.address = address;
        }
    }
}
