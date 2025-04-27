// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.util;

import android.os.Looper;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

public final class AdapterUtils {
    public static final Object STUB = new Object();

    private static class SimpleListDiffCallback<T> extends DiffUtil.Callback {
        private final List<T> mOldList;
        private final List<T> mNewList;
        private final int mStartPosition;

        private SimpleListDiffCallback(@NonNull List<T> oldList, @Nullable List<T> newList) {
            mOldList = oldList;
            mNewList = newList;
            mStartPosition = 0;
        }


        private SimpleListDiffCallback(@NonNull List<T> oldList, @Nullable List<T> newList, int startPosition) {
            mOldList = oldList;
            mNewList = newList;
            mStartPosition = startPosition;
        }

        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return (mNewList != null ? mNewList.size() : 0) + mStartPosition;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (newItemPosition < mStartPosition) {
                // Both values are null
                return true;
            }
            if (mNewList == null) {
                return false;
            }
            return Objects.equals(mOldList.get(oldItemPosition), mNewList.get(newItemPosition - mStartPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (newItemPosition < mStartPosition) {
                // Both values are null
                return true;
            }
            return false;
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            return AdapterUtils.STUB;
        }
    }

    private static class SimpleArrayMapDiffCallback<K, V> extends DiffUtil.Callback {
        private final SimpleArrayMap<K, V> mOldList;
        private final SimpleArrayMap<K, V> mNewList;

        private SimpleArrayMapDiffCallback(@NonNull SimpleArrayMap<K, V> oldList, @Nullable SimpleArrayMap<K, V> newList) {
            mOldList = oldList;
            mNewList = newList;
        }

        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList != null ? mNewList.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (mNewList == null) {
                return false;
            }
            return Objects.equals(mOldList.keyAt(oldItemPosition), mNewList.keyAt(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return false;
        }

        @Nullable
        @Override
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            return AdapterUtils.STUB;
        }
    }

    public static <T, V> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                   @NonNull SimpleArrayMap<T, V> baseList,
                                                   @Nullable SimpleArrayMap<T, V> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new SimpleArrayMapDiffCallback<>(baseList, newList));
        baseList.clear();
        if (newList != null) {
            baseList.putAll(newList);
        }
        result.dispatchUpdatesTo(adapter);
    }

    public static <T> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                @NonNull List<T> baseList,
                                                @Nullable List<T> newList) {
        notifyDataSetChanged(adapter, 0, baseList, newList);
    }

    public static <T> void notifyDataSetChanged(@NonNull RecyclerView.Adapter<?> adapter,
                                                @IntRange(from = 0) int startIndex,
                                                @NonNull List<T> baseList,
                                                @Nullable List<T> newList) {
        // base list always has placeholders < startIndex, newList do not. So, it is necessary to
        // offset the placeholders during comparison.
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new SimpleListDiffCallback<>(baseList, newList, startIndex));
        baseList.clear();
        // Add |startIndex| no. of null as placeholders
        for (int i = 0; i < startIndex; ++i) {
            baseList.add(null);
        }
        if (newList != null) {
            baseList.addAll(newList);
        }
        // When dispatching updates, null items are never updated in partial update.
        result.dispatchUpdatesTo(adapter);
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
                adapter.notifyItemRangeChanged(0, currentCount, STUB);
            }
            adapter.notifyItemRangeRemoved(currentCount + 0, previousCount - currentCount);
        } else if (previousCount < currentCount) {
            // Some values are added
            if (previousCount > 0) {
                adapter.notifyItemRangeChanged(0, previousCount, STUB);
            }
            adapter.notifyItemRangeInserted(previousCount + 0, currentCount - previousCount);
        } else if (previousCount > 0) {
            // No values are added or removed
            adapter.notifyItemRangeChanged(0, previousCount, STUB);
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