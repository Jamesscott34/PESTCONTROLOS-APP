package com.grpc.grpc.jobs.ui;

import android.app.Activity;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

/**
 * Shared FollowUpDate dialog. Saves a scheduled date only.
 * It does not create a visit or change job status.
 */
public final class JobWorkFollowUpDates {

    public interface DateChosen {
        void onDate(String normalized);
    }

    private JobWorkFollowUpDates() {
    }

    public static void show(Activity activity, @Nullable String currentValue, DateChosen onDate) {
        EditText input = new EditText(activity);
        input.setHint("dd/MM/yyyy or dd/MM/yy [HH:mm or 930] or N/A");
        if (currentValue != null && !currentValue.trim().isEmpty()) {
            input.setText(currentValue.trim());
            input.setSelection(input.getText().length());
        }
        new AlertDialog.Builder(activity)
                .setTitle("Next visit")
                .setMessage("This saves the scheduled date only. It does not mark a follow-up visit or change the job status.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String rawInput = input.getText().toString().trim();
                    if (rawInput.equalsIgnoreCase("N/A")) {
                        onDate.onDate("N/A");
                        return;
                    }
                    String normalized = normalize(rawInput);
                    if (normalized == null) {
                        Toast.makeText(activity, "Invalid format. Use dd/MM/yyyy or 09/02/26 or dd/MM/yy HH:mm", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onDate.onDate(normalized);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Accepts dd/MM/yyyy, dd/MM/yy, and optional time. Returns dd/MM/yyyy [HH:mm] or null. */
    public static String normalize(String input) {
        if (input == null) return null;
        input = input.trim();

        if (input.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
            return input;
        }
        if (input.matches("^\\d{2}/\\d{2}/\\d{2}$")) {
            return expandTwoDigitYear(input);
        }
        if (input.matches("^\\d{2}/\\d{2}/\\d{4}\\s\\d{1,2}:\\d{2}$")) {
            return input;
        }
        if (input.matches("^\\d{2}/\\d{2}/\\d{2}\\s\\d{1,2}:\\d{2}$")) {
            String[] parts = input.split("\\s");
            String dateExpanded = expandTwoDigitYear(parts[0]);
            if (dateExpanded != null) return dateExpanded + " " + parts[1];
        }
        if (input.matches("^\\d{2}/\\d{2}/\\d{4}\\s\\d{3,4}$")) {
            try {
                String[] parts = input.split("\\s");
                return parts[0] + " " + clock(parts[1]);
            } catch (Exception e) {
                return null;
            }
        }
        if (input.matches("^\\d{2}/\\d{2}/\\d{2}\\s\\d{3,4}$")) {
            try {
                String[] parts = input.split("\\s");
                String dateExpanded = expandTwoDigitYear(parts[0]);
                if (dateExpanded == null) return null;
                return dateExpanded + " " + clock(parts[1]);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static String clock(String timeRaw) {
        if (timeRaw.length() == 3) timeRaw = "0" + timeRaw;
        return timeRaw.substring(0, 2) + ":" + timeRaw.substring(2, 4);
    }

    private static String expandTwoDigitYear(String ddMMyy) {
        if (ddMMyy == null || !ddMMyy.matches("^\\d{2}/\\d{2}/\\d{2}$")) return null;
        try {
            String[] parts = ddMMyy.split("/");
            int yy = Integer.parseInt(parts[2]);
            int fullYear = yy >= 0 && yy <= 99 ? (2000 + yy) : yy;
            return parts[0] + "/" + parts[1] + "/" + fullYear;
        } catch (Exception e) {
            return null;
        }
    }
}
