package com.grpc.grpc;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView adapter for the Bird Quotation "Materials Required" table.
 * Allows editing material name, quantity, and unit price per row and removing rows.
 */
public class BirdMaterialsAdapter extends RecyclerView.Adapter<BirdMaterialsAdapter.MaterialViewHolder> {

    private final List<BirdMaterialItem> items;

    public BirdMaterialsAdapter(List<BirdMaterialItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public MaterialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bird_material, parent, false);
        return new MaterialViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MaterialViewHolder holder, int position) {
        BirdMaterialItem item = items.get(position);

        // Avoid triggering watchers while setting text
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class MaterialViewHolder extends RecyclerView.ViewHolder {

        final EditText materialNameInput;
        final EditText quantityInput;
        final EditText unitPriceInput;
        final ImageButton removeButton;

        private TextWatcher nameWatcher;
        private TextWatcher quantityWatcher;
        private TextWatcher priceWatcher;

        MaterialViewHolder(@NonNull View itemView) {
            super(itemView);
            materialNameInput = itemView.findViewById(R.id.materialNameInput);
            quantityInput = itemView.findViewById(R.id.quantityInput);
            unitPriceInput = itemView.findViewById(R.id.unitPriceInput);
            removeButton = itemView.findViewById(R.id.removeMaterialButton);
        }

        void bind(BirdMaterialItem item) {
            // Detach previous watchers to avoid duplicate callbacks on recycle
            if (nameWatcher != null) materialNameInput.removeTextChangedListener(nameWatcher);
            if (quantityWatcher != null) quantityInput.removeTextChangedListener(quantityWatcher);
            if (priceWatcher != null) unitPriceInput.removeTextChangedListener(priceWatcher);

            materialNameInput.setText(item.getMaterialName());
            quantityInput.setText(item.getQuantityDisplay());
            unitPriceInput.setText(item.getUnitPrice() != 0.0 ? String.valueOf(item.getUnitPrice()) : "");

            nameWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    item.setMaterialName(s != null ? s.toString().trim() : "");
                }
            };
            materialNameInput.addTextChangedListener(nameWatcher);

            quantityWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    String raw = s != null ? s.toString().trim() : "";
                    item.setQuantityText(raw);
                    int q = 0;
                    try {
                        if (raw.length() > 0) {
                            q = Integer.parseInt(raw);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    item.setQuantity(q);
                }
            };
            quantityInput.addTextChangedListener(quantityWatcher);

            priceWatcher = new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    double p = 0.0;
                    try {
                        if (s != null && s.length() > 0) {
                            p = Double.parseDouble(s.toString().trim());
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    item.setUnitPrice(p);
                }
            };
            unitPriceInput.addTextChangedListener(priceWatcher);

            removeButton.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < items.size()) {
                    items.remove(position);
                    notifyItemRemoved(position);
                }
            });
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public abstract void afterTextChanged(Editable s);
    }
}

