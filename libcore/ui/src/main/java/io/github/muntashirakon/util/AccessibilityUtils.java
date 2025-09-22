// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AccessibilityUtils {
    public static void requestParentAccessibilityFocus(@NonNull View view) {
        requestAccessibilityFocus(view.getParentForAccessibility());
    }

    public static void requestParentAccessibilityFocus(@NonNull View view, long delayMillis) {
        requestAccessibilityFocus(view.getParentForAccessibility(), delayMillis);
    }

    @SuppressLint("AccessibilityFocus")
    public static <T> void requestAccessibilityFocus(@Nullable T anyView) {
        if (anyView instanceof View) {
            View view = (View) anyView;
            view.post(() -> view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        }
    }

    @SuppressLint("AccessibilityFocus")
    public static <T> void requestAccessibilityFocus(@Nullable T anyView, long delayMillis) {
        if (anyView instanceof View) {
            View view = (View) anyView;
            view.postDelayed(() ->
                    view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null), delayMillis);
        }
    }
}
