package com.grpc.grpc.stock.adapter;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Case-insensitive contains filter for stock product suggestions.
 * Typed text that is not in the list can still be submitted as a manual item.
 */
public class StockProductAutoCompleteAdapter extends ArrayAdapter<String> {

    private final List<String> allItems;
    private final List<String> filtered = new ArrayList<>();

    public StockProductAutoCompleteAdapter(@NonNull Context context, @NonNull List<String> products) {
        super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        allItems = new ArrayList<>(products);
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
                    if (item != null && item.toLowerCase(Locale.ROOT).contains(query)) {
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
                    for (Object value : (List<?>) results.values) {
                        if (value instanceof String) filtered.add((String) value);
                    }
                }
                clear();
                addAll(filtered);
                notifyDataSetChanged();
            }
        };
    }

    @Nullable
    @Override
    public String getItem(int position) {
        if (position < 0 || position >= filtered.size()) return null;
        return filtered.get(position);
    }

    @Override
    public int getCount() {
        return filtered.size();
    }
}
