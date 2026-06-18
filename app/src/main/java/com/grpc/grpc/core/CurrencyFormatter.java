package com.grpc.grpc.core;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Central formatting for user-visible euro amounts. Keep calculations numeric and
 * apply this only when showing values on screen or in generated documents.
 */
public final class CurrencyFormatter {

    private static final Locale EURO_LOCALE = new Locale("en", "IE");
    private static final Currency EURO = Currency.getInstance("EUR");

    private CurrencyFormatter() {
    }

    public static String formatEuro(double amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(EURO_LOCALE);
        formatter.setCurrency(EURO);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(amount);
    }
}
