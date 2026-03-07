package com.grpc.grpc.core;

import android.app.Activity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/**
 * Central guard to prevent accidental left/right navigation swipes while editing forms.
 * Vertical scrolling is not affected because callers only consult this for horizontal swipe actions.
 */
public final class HorizontalSwipeGuard {
    private HorizontalSwipeGuard() {}

    /**
     * Block horizontal swipe navigation when:
     * - any text input is focused (user is actively typing), OR
     * - any text input contains text (form has content; avoid accidental navigation loss).
     */
    public static boolean shouldBlock(Activity activity) {
        if (activity == null) return false;
        try {
            View root = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
            if (root == null) return false;
            return hasFocusedEditText(root) || hasAnyNonEmptyEditText(root);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasFocusedEditText(View v) {
        if (v instanceof EditText) {
            return v.hasFocus();
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasFocusedEditText(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    private static boolean hasAnyNonEmptyEditText(View v) {
        if (v instanceof EditText) {
            CharSequence t = ((EditText) v).getText();
            return !TextUtils.isEmpty(t);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasAnyNonEmptyEditText(g.getChildAt(i))) return true;
            }
        }
        return false;
    }
}

