// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public final class AdapterUtils {
    public static final Object STUB = new Object();
    public static final Object PAYLOAD_HIGHLIGHT_CHANGED = new Object();

    public static boolean isStartingSearch(@Nullable String oldQuery, @Nullable String newQuery) {
        return TextUtils.isEmpty(oldQuery) && !TextUtils.isEmpty(newQuery);
    }

    public static boolean isClearingSearch(@Nullable String oldQuery, @Nullable String newQuery) {
        return !TextUtils.isEmpty(oldQuery) && TextUtils.isEmpty(newQuery);
    }

    public static void setVisible(@NonNull View v, boolean visible) {
        if (visible && v.getVisibility() != View.VISIBLE) {
            v.setVisibility(View.VISIBLE);
        } else if (!visible && v.getVisibility() != View.GONE) {
            v.setVisibility(View.GONE);
        }
    }

    public static <VH extends RecyclerView.ViewHolder> void fixTextSelectionInView(@NonNull VH holder) {
        fixTextSelectionInView(holder.itemView);
    }

    private static void fixTextSelectionInView(@Nullable View view) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            // Apply the enabled toggle workaround
            tv.setEnabled(false);
            tv.setEnabled(true);
        } else if (view instanceof ViewGroup) {
            // If it's a ViewGroup, recurse into children
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                fixTextSelectionInView(vg.getChildAt(i));
            }
        }
    }
}