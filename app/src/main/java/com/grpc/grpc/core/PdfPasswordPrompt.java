package com.grpc.grpc.core;

import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Small helper to prompt for a PDF owner password when "Password protect" is enabled.
 */
public final class PdfPasswordPrompt {
    private PdfPasswordPrompt() {}

    public interface Callback {
        void onPassword(@Nullable String password);
    }

    public static void prompt(AppCompatActivity activity, Callback cb) {
        if (activity == null) {
            if (cb != null) cb.onPassword(null);
            return;
        }

        EditText input = new EditText(activity);
        input.setHint("Enter password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(activity)
                .setTitle("Password protect PDF")
                .setMessage("Enter a password to protect this PDF.")
                .setView(input)
                .setPositiveButton("Generate", (d, which) -> {
                    String pw = input.getText() != null ? input.getText().toString().trim() : "";
                    if (pw.isEmpty()) {
                        if (cb != null) cb.onPassword(null);
                    } else {
                        if (cb != null) cb.onPassword(pw);
                    }
                })
                .setNegativeButton("Cancel", (d, which) -> {
                    if (cb != null) cb.onPassword(null);
                })
                .show();
    }
}

