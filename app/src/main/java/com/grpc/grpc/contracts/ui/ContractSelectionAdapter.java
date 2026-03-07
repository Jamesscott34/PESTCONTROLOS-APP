package com.grpc.grpc.contracts.ui;

import com.grpc.grpc.core.*;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom adapter for contract selection dialog
 * Provides better visual containers and live search filtering
 */
public class ContractSelectionAdapter extends ArrayAdapter<String> {

    private List<String> originalList;
    private List<String> filteredList;
    private Context context;

    public ContractSelectionAdapter(Context context, List<String> contracts) {
        super(context, 0, contracts);
        this.context = context;
        this.originalList = new ArrayList<>(contracts);
        this.filteredList = new ArrayList<>(contracts);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
        }

        String contract = getItem(position);
        if (contract != null) {
            TextView textView = convertView.findViewById(android.R.id.text1);
            textView.setText(contract);
            textView.setPadding(16, 12, 16, 12);
            textView.setTextSize(14);
        }

        return convertView;
    }

    /**
     * Filter contracts based on search query
     * Supports live search with partial matching
     */
    public void filter(String query) {
        filteredList.clear();

        if (query == null || query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (String contract : originalList) {
                // Split contract string to get name and address
                String[] parts = contract.split("\n");
                String contractName = parts[0].toLowerCase();
                String contractAddress = parts.length > 1 ? parts[1].toLowerCase() : "";

                // Check if name or address contains the search query
                if (contractName.contains(lowerQuery) || contractAddress.contains(lowerQuery)) {
                    filteredList.add(contract);
                }
            }
        }

        clear();
        addAll(filteredList);
        notifyDataSetChanged();
    }

    /**
     * Get the original contract name from the display string
     */
    public String getContractName(int position) {
        String contract = getItem(position);
        if (contract != null) {
            String[] parts = contract.split("\n");
            return parts[0];
        }
        return "";
    }

    /**
     * Get the original contract address from the display string
     */
    public String getContractAddress(int position) {
        String contract = getItem(position);
        if (contract != null) {
            String[] parts = contract.split("\n");
            return parts.length > 1 ? parts[1].replace("📍 ", "") : "";
        }
        return "";
    }
}
