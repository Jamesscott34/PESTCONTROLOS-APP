package com.grpc.grpc.reports.ui;



import android.app.Activity;

import android.text.Editable;

import android.view.LayoutInflater;

import android.view.View;

import android.widget.ArrayAdapter;

import android.widget.AutoCompleteTextView;

import android.widget.Button;

import android.widget.EditText;

import android.widget.Filter;

import android.widget.ImageButton;

import android.widget.LinearLayout;

import android.widget.TextView;

import android.widget.Toast;



import androidx.annotation.NonNull;

import androidx.annotation.Nullable;



import com.grpc.grpc.R;

import com.grpc.grpc.reports.data.ProductListsLoader;

import com.grpc.grpc.reports.model.ProductUsageItem;

import com.grpc.grpc.reports.util.PrepProductsFormatter;

import com.grpc.grpc.reports.util.PrepProductsSerializer;



import java.util.ArrayList;

import java.util.List;

import java.util.Locale;



/**

 * UI controller for prep / products section (Service Report &amp; Action Form).

 */

public class PrepProductsSectionController {



    private final Activity activity;

    private final View root;

    private final AutoCompleteTextView productInput;

    private final EditText quantityInput;

    private final EditText batchInput;

    private final EditText locationInput;

    private final Button addButton;

    private final LinearLayout itemsList;

    private final EditText legacyPrepInput;



    private final List<String> rodenticideNames;

    private final List<String> insecticideNames;

    private final List<String> materialNames;

    private final List<String> allProductNames;

    private final List<ProductUsageItem> items = new ArrayList<>();



    @Nullable

    private Integer editingIndex;



    public PrepProductsSectionController(@NonNull Activity activity, int sectionViewId) {

        this.activity = activity;

        this.root = activity.findViewById(sectionViewId);

        productInput = root.findViewById(R.id.prepProductNameInput);

        quantityInput = root.findViewById(R.id.prepQuantityInput);

        batchInput = root.findViewById(R.id.prepBatchNumberInput);

        locationInput = root.findViewById(R.id.prepLocationInput);

        addButton = root.findViewById(R.id.prepAddProductButton);

        itemsList = root.findViewById(R.id.prepItemsList);

        legacyPrepInput = root.findViewById(R.id.prepInput);



        ProductListsLoader.ProductLists lists = ProductListsLoader.load(activity);

        rodenticideNames = lists != null ? lists.rodenticides : new ArrayList<>();

        insecticideNames = lists != null ? lists.insecticides : new ArrayList<>();

        materialNames = lists != null ? lists.materials : new ArrayList<>();

        allProductNames = lists != null ? lists.allNames : new ArrayList<>();



        setupProductAutocomplete();

        addButton.setOnClickListener(v -> onAddOrUpdateProduct());

        refreshList();

    }



    @NonNull

    public EditText getLegacyPrepInput() {

        return legacyPrepInput;

    }



    @NonNull

    public List<ProductUsageItem> getProducts() {

        return new ArrayList<>(items);

    }



    public void setProducts(@Nullable List<ProductUsageItem> products) {

        items.clear();

        if (products != null) {

            items.addAll(products);

        }

        editingIndex = null;

        addButton.setText("Add Product");

        clearEntryForm();

        refreshList();

    }



    public void loadFromPrepColumn(@Nullable String prepColumn) {

        if (PrepProductsSerializer.isStructuredPrepColumn(prepColumn)) {

            setProducts(PrepProductsSerializer.fromJson(prepColumn));

            setLegacyPrepText("");

        } else {

            clear();

            setLegacyPrepText(PrepProductsSerializer.legacyPlainFromPrepColumn(prepColumn));

        }

    }



    public void clear() {

        items.clear();

        editingIndex = null;

        addButton.setText("Add Product");

        clearEntryForm();

        refreshList();

        legacyPrepInput.setText("");

    }



    public void setLegacyPrepText(@Nullable String text) {

        legacyPrepInput.setText(text != null ? text : "");

    }



    @NonNull

    public String getLegacyPrepText() {

        return legacyPrepInput.getText() != null ? legacyPrepInput.getText().toString().trim() : "";

    }



    private void setupProductAutocomplete() {

        productInput.setThreshold(1);

        CaseInsensitiveFilterAdapter adapter = new CaseInsensitiveFilterAdapter(

                activity, android.R.layout.simple_dropdown_item_1line, allProductNames);

        productInput.setAdapter(adapter);

    }



    private void onAddOrUpdateProduct() {

        String productName = productInput.getText() != null ? productInput.getText().toString().trim() : "";

        if (productName.isEmpty()) {

            Toast.makeText(activity, "Please enter a product or material name", Toast.LENGTH_SHORT).show();

            return;

        }



        ProductUsageItem item = new ProductUsageItem(

                inferType(productName),

                productName,

                null,

                emptyToNull(quantityInput.getText()),

                emptyToNull(batchInput.getText()),

                emptyToNull(locationInput.getText())

        );



        if (editingIndex != null && editingIndex >= 0 && editingIndex < items.size()) {

            items.set(editingIndex, item);

            editingIndex = null;

            addButton.setText("Add Product");

        } else {

            items.add(item);

        }

        clearEntryForm();

        refreshList();

    }



    private void clearEntryForm() {

        productInput.setText("");

        quantityInput.setText("");

        batchInput.setText("");

        locationInput.setText("");

    }



    private void refreshList() {

        itemsList.removeAllViews();

        for (int i = 0; i < items.size(); i++) {

            itemsList.addView(createRowView(items.get(i), i));

        }

    }



    @NonNull

    private View createRowView(@NonNull ProductUsageItem item, int index) {

        View row = LayoutInflater.from(activity).inflate(R.layout.item_prep_product_row, null);

        TextView summary = row.findViewById(R.id.prepProductRowSummary);

        summary.setText(PrepProductsFormatter.formatRowLine(item));

        ImageButton editBtn = row.findViewById(R.id.prepProductRowEdit);

        ImageButton removeBtn = row.findViewById(R.id.prepProductRowRemove);

        editBtn.setOnClickListener(v -> startEdit(index));

        removeBtn.setOnClickListener(v -> {

            if (index >= 0 && index < items.size()) {

                if (editingIndex != null && editingIndex == index) {

                    editingIndex = null;

                    addButton.setText("Add Product");

                    clearEntryForm();

                } else if (editingIndex != null && editingIndex > index) {

                    editingIndex = editingIndex - 1;

                }

                items.remove(index);

                refreshList();

            }

        });

        return row;

    }



    private void startEdit(int index) {

        if (index < 0 || index >= items.size()) return;

        ProductUsageItem item = items.get(index);

        editingIndex = index;

        addButton.setText("Update Product");

        productInput.setText(item.getResolvedProductName());

        quantityInput.setText(item.getQuantity() != null ? item.getQuantity() : "");

        batchInput.setText(item.getBatchNumber() != null ? item.getBatchNumber() : "");

        locationInput.setText(item.getLocation() != null ? item.getLocation() : "");

    }



    @NonNull

    private String inferType(@NonNull String name) {

        if (containsIgnoreCase(rodenticideNames, name)) {

            return ProductUsageItem.TYPE_RODENTICIDE;

        }

        if (containsIgnoreCase(insecticideNames, name)) {

            return ProductUsageItem.TYPE_INSECTICIDE;

        }

        if (containsIgnoreCase(materialNames, name)) {

            return ProductUsageItem.TYPE_MATERIAL;

        }

        return ProductUsageItem.TYPE_CUSTOM;

    }



    private static boolean containsIgnoreCase(@NonNull List<String> names, @NonNull String value) {

        for (String name : names) {

            if (name.equalsIgnoreCase(value)) {

                return true;

            }

        }

        return false;

    }



    @Nullable

    private static String emptyToNull(@Nullable Editable editable) {

        if (editable == null) return null;

        String t = editable.toString().trim();

        return t.isEmpty() ? null : t;

    }



    /** Autocomplete adapter with case-insensitive prefix/contains filtering. */

    private static final class CaseInsensitiveFilterAdapter extends ArrayAdapter<String> {



        private final List<String> allItems;

        private List<String> filtered = new ArrayList<>();



        CaseInsensitiveFilterAdapter(Activity context, int resource, List<String> objects) {

            super(context, resource, new ArrayList<>());

            allItems = objects != null ? new ArrayList<>(objects) : new ArrayList<>();

            filtered.addAll(allItems);

            addAll(filtered);

        }



        @NonNull

        @Override

        public Filter getFilter() {

            return new Filter() {

                @Override

                protected FilterResults performFiltering(CharSequence constraint) {

                    FilterResults results = new FilterResults();

                    if (constraint == null || constraint.length() == 0) {

                        results.values = new ArrayList<>(allItems);

                        results.count = allItems.size();

                        return results;

                    }

                    String query = constraint.toString().trim().toLowerCase(Locale.ROOT);

                    List<String> matches = new ArrayList<>();

                    for (String item : allItems) {

                        if (item.toLowerCase(Locale.ROOT).contains(query)) {

                            matches.add(item);

                        }

                    }

                    results.values = matches;

                    results.count = matches.size();

                    return results;

                }



                @Override

                protected void publishResults(CharSequence constraint, FilterResults results) {

                    filtered.clear();

                    if (results != null && results.values instanceof List) {

                        //noinspection unchecked

                        filtered.addAll((List<String>) results.values);

                    }

                    clear();

                    addAll(filtered);

                    notifyDataSetChanged();

                }

            };

        }

    }

}

