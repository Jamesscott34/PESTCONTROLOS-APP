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
import com.grpc.grpc.stock.model.StockRequest;
import com.grpc.grpc.stock.util.StockDates;

import java.util.List;

public class StockRequestAdapter extends ArrayAdapter<StockRequest> {

    private final boolean showRequester;

    public StockRequestAdapter(@NonNull Context context, @NonNull List<StockRequest> requests, boolean showRequester) {
        super(context, 0, requests);
        this.showRequester = showRequester;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = LayoutInflater.from(getContext()).inflate(R.layout.item_stock_request, parent, false);
        }
        StockRequest request = getItem(position);
        TextView date = row.findViewById(R.id.stockRequestDate);
        TextView meta = row.findViewById(R.id.stockRequestMeta);
        TextView status = row.findViewById(R.id.stockRequestStatus);
        if (request == null) return row;

        String when = StockDates.format(request.createdAtMillis);
        if (showRequester) {
            date.setText(request.displayName());
            meta.setText(when + " · " + itemCount(request));
        } else {
            date.setText(when.isEmpty() ? request.displayName() : when);
            meta.setText(itemCount(request));
        }
        status.setText(StockDates.statusLabel(getContext(), request.status));
        return row;
    }

    @NonNull
    private String itemCount(@NonNull StockRequest request) {
        int count = request.items.size();
        if (count == 1) return getContext().getString(R.string.stock_item_count_one);
        return getContext().getString(R.string.stock_item_count_many, count);
    }
}
