package com.grpc.grpc.contracts;

import androidx.annotation.Nullable;

import java.util.Locale;

/** Substring or loose in-order match (e.g. "buks" → "Bucks Head"). */
public final class ContractSearch {

    private ContractSearch() {}

    public static boolean matches(
            @Nullable String name,
            @Nullable String email,
            @Nullable String address,
            @Nullable String id,
            String query
    ) {
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;

        StringBuilder haystack = new StringBuilder();
        append(haystack, name);
        append(haystack, email);
        append(haystack, address);
        append(haystack, id);
        if (haystack.length() == 0) return false;

        String hay = haystack.toString().toLowerCase(Locale.ROOT);
        if (hay.contains(q)) return true;

        int from = 0;
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            int idx = hay.indexOf(ch, from);
            if (idx == -1) return false;
            from = idx + 1;
        }
        return true;
    }

    private static void append(StringBuilder sb, @Nullable String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(trimmed);
    }
}
