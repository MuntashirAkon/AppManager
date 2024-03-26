// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.List;

public final class AdapterUtils {
    public static <T> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter, @NonNull List<T> baseList,
                                                @Nullable Collection<T> newList) {
        int previousCount = baseList.size();
        baseList.clear();
        if (newList != null) {
            baseList.addAll(newList);
        }
        int currentCount = baseList.size();
        notifyDataSetChanged(adapter, previousCount, currentCount);
    }

    public static void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter, int previousCount,
                                            int currentCount) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            // Main thread is required
            throw new RuntimeException("Must be called on the UI thread");
        }
        if (previousCount > currentCount) {
            // Some values are removed
            if (currentCount > 0) {
                adapter.notifyItemRangeChanged(0, currentCount);
            }
            adapter.notifyItemRangeRemoved(currentCount, previousCount - currentCount);
        } else if (previousCount < currentCount) {
            // Some values are added
            if (previousCount > 0) {
                adapter.notifyItemRangeChanged(0, previousCount);
            }
            adapter.notifyItemRangeInserted(previousCount, currentCount - previousCount);
        } else if (previousCount > 0) {
            // No values are added or removed
            adapter.notifyItemRangeChanged(0, previousCount);
        }
    }

    public static void setVisible(@NonNull View v, boolean visible) {
        if (visible && v.getVisibility() != View.VISIBLE) {
            v.setVisibility(View.VISIBLE);
        } else if (!visible && v.getVisibility() != View.GONE) {
            v.setVisibility(View.GONE);
        }
    }
}