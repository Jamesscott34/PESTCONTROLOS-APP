package com.grpc.grpc.stock.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.grpc.grpc.R;
import com.grpc.grpc.stock.model.StockUserSummary;

import java.util.List;

public class StockUserAdapter extends ArrayAdapter<StockUserSummary> {

    public StockUserAdapter(@NonNull Context context, @NonNull List<StockUserSummary> users) {
        super(context, 0, users);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = LayoutInflater.from(getContext()).inflate(R.layout.item_stock_user, parent, false);
        }
        StockUserSummary user = getItem(position);
        TextView name = row.findViewById(R.id.stockUserName);
        TextView pending = row.findViewById(R.id.stockUserPending);
        if (user == null) return row;
        name.setText(user.name);
        pending.setText(getContext().getString(R.string.stock_pending_count, user.pendingCount));
        return row;
    }
}
