package com.grpc.grpc.stock.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.grpc.grpc.BuildConfig;
import com.grpc.grpc.R;
import com.grpc.grpc.core.DemoFirebaseExpiryHelper;
import com.grpc.grpc.core.SessionManager;
import com.grpc.grpc.stock.adapter.StockProductAutoCompleteAdapter;
import com.grpc.grpc.stock.data.StockRepository;
import com.grpc.grpc.stock.model.StockRequestItem;
import com.grpc.grpc.stock.util.StockProductTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Build one stock request with one or more items. Submitted requests are not edited here.
 */
public class StockRequestActivity extends AppCompatActivity {

    private final List<StockRequestItem> items = new ArrayList<>();
    private AutoCompleteTextView productInput;
    private EditText quantityInput;
    private EditText notesInput;
    private LinearLayout itemsContainer;
    private Button submitButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (BuildConfig.IS_OFFLINE) {
            Toast.makeText(this, R.string.stock_unavailable_offline, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (DemoFirebaseExpiryHelper.finishIfBlocked(this)) return;

        setContentView(R.layout.activity_stock_request);
        productInput = findViewById(R.id.stockProductInput);
        quantityInput = findViewById(R.id.stockQuantityInput);
        notesInput = findViewById(R.id.stockNotesInput);
        itemsContainer = findViewById(R.id.stockItemsContainer);
        submitButton = findViewById(R.id.buttonSubmitStockRequest);
        Button addButton = findViewById(R.id.buttonAddStockItem);

        productInput.setThreshold(1);
        productInput.setAdapter(new StockProductAutoCompleteAdapter(this, StockProductTypes.suggestionNames(this)));
        addButton.setOnClickListener(v -> addItem());
        submitButton.setEnabled(false);
        submitButton.setOnClickListener(v -> submit());

        SessionManager.ensureLoaded(this, session -> runOnUiThread(() -> {
            if (session == null || (!session.isTech && !session.isAdmin)) {
                Toast.makeText(this, R.string.stock_not_signed_in, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            submitButton.setEnabled(true);
        }));
    }

    private void addItem() {
        String name = StockProductTypes.normalizeName(
                productInput.getText() != null ? productInput.getText().toString() : "");
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.stock_need_product, Toast.LENGTH_SHORT).show();
            return;
        }
        String quantity = quantityInput.getText() != null ? quantityInput.getText().toString().trim() : "";
        if (quantity.isEmpty()) {
            Toast.makeText(this, R.string.stock_need_quantity, Toast.LENGTH_SHORT).show();
            return;
        }
        if (quantity.length() > 80) {
            Toast.makeText(this, R.string.stock_quantity_too_long, Toast.LENGTH_SHORT).show();
            return;
        }
        if (items.size() >= 30) {
            Toast.makeText(this, R.string.stock_too_many_items, Toast.LENGTH_SHORT).show();
            return;
        }
        items.add(new StockRequestItem(name, quantity, StockProductTypes.resolve(this, name)));
        productInput.setText("");
        quantityInput.setText("");
        renderItems();
    }

    private void renderItems() {
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < items.size(); i++) {
            StockRequestItem item = items.get(i);
            View row = inflater.inflate(R.layout.item_stock_request_line, itemsContainer, false);
            TextView summary = row.findViewById(R.id.stockLineSummary);
            summary.setText(getString(R.string.stock_line_summary, item.getProductName(), item.getQuantity()));
            int index = i;
            row.findViewById(R.id.buttonRemoveStockLine).setOnClickListener(v -> {
                if (index >= 0 && index < items.size()) {
                    items.remove(index);
                    renderItems();
                }
            });
            itemsContainer.addView(row);
        }
    }

    private void submit() {
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.stock_need_items, Toast.LENGTH_SHORT).show();
            return;
        }
        String notes = notesInput.getText() != null ? notesInput.getText().toString().trim() : "";
        if (notes.length() > 500) {
            Toast.makeText(this, R.string.stock_notes_too_long, Toast.LENGTH_SHORT).show();
            return;
        }
        submitButton.setEnabled(false);
        StockRepository.submitRequest(this, new ArrayList<>(items), notes, (success, message) -> runOnUiThread(() -> {
            submitButton.setEnabled(true);
            if (!success) {
                Toast.makeText(this, message != null ? message : getString(R.string.stock_save_failed), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.stock_submitted, Toast.LENGTH_SHORT).show();
            finish();
        }));
    }
}
