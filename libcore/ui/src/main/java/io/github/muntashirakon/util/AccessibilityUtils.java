// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;

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

    public static void setAccessibilityHeading(@NonNull View view, boolean enable) {
        if (view instanceof TextView) {
            ViewCompat.setAccessibilityHeading(view, enable);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setHeading(enable && info.getContentDescription() != null);
                }
            });
        }
    }

    public static void popupMenuToAccessibleOptions(@NonNull View view, @NonNull PopupMenu popupMenu) {
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                // Add each PopupMenu item as an AccessibilityAction
                for (int i = 0; i < popupMenu.getMenu().size(); i++) {
                    MenuItem item = popupMenu.getMenu().getItem(i);
                    if (item.isVisible() && item.isEnabled()) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                                item.getItemId(),
                                item.getTitle()
                        ));
                    }
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, Bundle args) {
                MenuItem menuItem = popupMenu.getMenu().findItem(action);
                if (menuItem != null) {
                    // Invoke the corresponding PopupMenu action programmatically
                    popupMenu.getMenu().performIdentifierAction(action, 0);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }
}
