package com.grpc.grpc.contracts.util;

import java.util.Locale;

/**
 * The five standard contract asset names. Custom names stay on the contract only.
 */
public final class StandardContractAssets {

    public static final String EXTERNAL = "External";
    public static final String INTERNAL = "Internal";
    public static final String FLY_UNIT = "Fly Unit";
    public static final String INSECT_MONITOR = "Insect Monitor";
    public static final String MOTH_POT = "Moth Pot";

    public static final String[] NAMES = {
            EXTERNAL, INTERNAL, FLY_UNIT, INSECT_MONITOR, MOTH_POT
    };

    public static final int MAX_QUANTITY = 99999;
    public static final int MAX_NAME_LENGTH = 80;
    public static final int MAX_CUSTOM = 20;

    private StandardContractAssets() {}

    public static String displayPlural(String canonicalName) {
        if (EXTERNAL.equals(canonicalName)) return "Externals";
        if (INTERNAL.equals(canonicalName)) return "Internals";
        if (FLY_UNIT.equals(canonicalName)) return "Fly Units";
        if (INSECT_MONITOR.equals(canonicalName)) return "Insect Monitors";
        if (MOTH_POT.equals(canonicalName)) return "Moth Pots";
        return canonicalName == null ? "" : canonicalName;
    }

    public static String canonical(String name) {
        String key = key(name);
        for (String standard : NAMES) {
            if (key(standard).equals(key) || key(displayPlural(standard)).equals(key)) {
                return standard;
            }
        }
        return null;
    }

    public static boolean isStandard(String name) {
        return canonical(name) != null;
    }

    public static String cleanName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ");
    }

    public static String key(String name) {
        return cleanName(name).toLowerCase(Locale.UK);
    }

    /** Whole number from 0 to {@link #MAX_QUANTITY}, or null when the text is not allowed. */
    public static Integer parseQuantity(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (!text.matches("\\d{1,6}")) return null;
        try {
            long value = Long.parseLong(text);
            if (value < 0 || value > MAX_QUANTITY) return null;
            return (int) value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
