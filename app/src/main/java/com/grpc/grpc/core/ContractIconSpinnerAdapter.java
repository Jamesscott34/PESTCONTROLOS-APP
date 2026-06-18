package com.grpc.grpc.core;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grpc.grpc.R;

import java.util.List;

public class ContractIconSpinnerAdapter<T> extends ArrayAdapter<T> {

    private final LayoutInflater inflater;

    public ContractIconSpinnerAdapter(@NonNull Context context, @NonNull List<T> items) {
        super(context, 0, items);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bind(position, convertView, parent, false);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bind(position, convertView, parent, true);
    }

    private View bind(int position, @Nullable View convertView, @NonNull ViewGroup parent, boolean dropdown) {
        int layout = dropdown ? R.layout.spinner_contract_icon_dropdown_item : R.layout.spinner_contract_icon_item;
        View view = convertView != null ? convertView : inflater.inflate(layout, parent, false);
        TextView text = view.findViewById(R.id.contractItemText);
        T item = getItem(position);
        text.setText(item != null ? item.toString() : "");
        return view;
    }
}
