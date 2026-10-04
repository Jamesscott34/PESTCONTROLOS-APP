package com.grpc.grpc.stock.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.grpc.grpc.R;
import com.grpc.grpc.stock.model.StockRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StockDates {

    private StockDates() {
    }

    @NonNull
    public static String format(long millis) {
        if (millis <= 0L) return "";
        return new SimpleDateFormat("dd/MM/yyyy", Locale.UK).format(new Date(millis));
    }

    @NonNull
    public static String statusLabel(@NonNull Context context, @NonNull String status) {
        if (StockRequest.STATUS_PENDING.equals(status)) return context.getString(R.string.stock_status_pending);
        if (StockRequest.STATUS_READY.equals(status)) return context.getString(R.string.stock_status_ready);
        if (StockRequest.STATUS_RETRIEVED.equals(status)) return context.getString(R.string.stock_status_retrieved);
        return status;
    }
}
