package com.grpc.grpc.stock.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * ISO week identity for the weekly stock check, for example 2026-W40.
 * Week boundaries follow the device time zone.
 */
public final class StockWeekHelper {

    private StockWeekHelper() {
    }

    public static String currentWeekKey() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.get(WeekFields.ISO.weekBasedYear());
        return String.format(Locale.US, "%04d-W%02d", year, week);
    }

    public static String documentId(String uid, String weekKey) {
        return (uid != null ? uid : "") + "_" + (weekKey != null ? weekKey : "");
    }
}
