// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.os.Looper;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;
import java.util.List;

public final class AdapterUtils {
    public static <T, V> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                @NonNull SimpleArrayMap<T, V> baseList,
                                                @Nullable SimpleArrayMap<T, V> newList) {
        int previousCount = baseList.size();
        baseList.clear();
        if (newList != null) {
            baseList.putAll(newList);
        }
        int currentCount = baseList.size();
        notifyDataSetChanged(adapter, previousCount, currentCount);
    }

    public static <T, V> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                @IntRange(from = 0) int startIndex,
                                                @NonNull SimpleArrayMap<T, V> baseList,
                                                @Nullable SimpleArrayMap<T, V> newList) {
        int previousCount = baseList.size();
        baseList.clear();
        if (newList != null) {
            baseList.putAll(newList);
        }
        int currentCount = baseList.size();
        notifyDataSetChanged(adapter, startIndex, previousCount, currentCount);
    }

    public static <T> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                @NonNull List<T> baseList,
                                                @Nullable Collection<T> newList) {
        int previousCount = baseList.size();
        baseList.clear();
        if (newList != null) {
            baseList.addAll(newList);
        }
        int currentCount = baseList.size();
        notifyDataSetChanged(adapter, previousCount, currentCount);
    }

    public static <T> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                @IntRange(from = 0) int startIndex,
                                                @NonNull List<T> baseList,
                                                @Nullable Collection<T> newList) {
        int previousCount = baseList.size();
        baseList.clear();
        if (newList != null) {
            baseList.addAll(newList);
        }
        int currentCount = baseList.size();
        notifyDataSetChanged(adapter, startIndex, previousCount, currentCount);
    }

    public static void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter, int previousCount,
                                            int currentCount) {
        notifyDataSetChanged(adapter, 0, previousCount, currentCount);
    }


    public static void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                            @IntRange(from = 0) int startIndex,
                                            @IntRange(from = 1) int previousCount,
                                            @IntRange(from = 1) int currentCount) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            // Main thread is required
            throw new RuntimeException("Must be called on the UI thread");
        }
        if (previousCount > currentCount) {
            // Some values are removed
            if (currentCount > 0) {
                adapter.notifyItemRangeChanged(startIndex, currentCount);
            }
            adapter.notifyItemRangeRemoved(currentCount + startIndex, previousCount - currentCount);
        } else if (previousCount < currentCount) {
            // Some values are added
            if (previousCount > 0) {
                adapter.notifyItemRangeChanged(startIndex, previousCount);
            }
            adapter.notifyItemRangeInserted(previousCount + startIndex, currentCount - previousCount);
        } else if (previousCount > 0) {
            // No values are added or removed
            adapter.notifyItemRangeChanged(startIndex, previousCount);
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