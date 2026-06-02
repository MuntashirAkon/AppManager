// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class RecyclerView extends androidx.recyclerview.widget.RecyclerView {
    public static class AdapterDataChangedObserver extends AdapterDataObserver {
        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            onChanged();
        }
    }

    private View mEmptyView;
    final private AdapterDataObserver mObserver = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            checkIfEmpty();
        }
    };

    public RecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public RecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.recyclerview.R.attr.recyclerViewStyle);
    }

    public RecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecyclerView);
        boolean fastScrollEnabled = a.getBoolean(R.styleable.RecyclerView_fastScrollerEnabled, false);
        a.recycle();

        if (fastScrollEnabled) {
            new FastScrollerBuilder(this).useMd2Style().build();
        }
        UiUtils.applyWindowInsetsAsPaddingNoTop(this);
    }

    void checkIfEmpty() {
        if (isInEditMode()) {
            return;
        }
        if (mEmptyView != null && getAdapter() != null) {
            boolean emptyViewVisible = getAdapter().getItemCount() == 0;
            mEmptyView.setVisibility(emptyViewVisible ? VISIBLE : GONE);
            setVisibility(emptyViewVisible ? GONE : VISIBLE);
        }
    }

    @UiThread
    @Override
    public void setAdapter(@Nullable androidx.recyclerview.widget.RecyclerView.Adapter adapter) {
        @SuppressWarnings("rawtypes")
        androidx.recyclerview.widget.RecyclerView.Adapter oldAdapter = getAdapter();
        if (oldAdapter != null) {
            oldAdapter.unregisterAdapterDataObserver(mObserver);
        }
        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerAdapterDataObserver(mObserver);
        }
        checkIfEmpty();
    }

    public void setEmptyView(View emptyView) {
        mEmptyView = emptyView;
        checkIfEmpty();
    }

    public void setSelection(int position) {
        LayoutManager layoutManager = getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            linearLayoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    public abstract static class ListAdapter<T, VH extends ViewHolder> extends androidx.recyclerview.widget.ListAdapter<T, VH> {
        @Nullable
        private androidx.recyclerview.widget.RecyclerView mRecyclerView;
        @Nullable
        private Parcelable mPreFilterScrollState;

        protected ListAdapter(@NonNull DiffUtil.ItemCallback<T> diffCallback) {
            super(diffCallback);
        }

        protected ListAdapter(@NonNull AsyncDifferConfig<T> config) {
            super(config);
        }

        @CallSuper
        @Override
        public void onAttachedToRecyclerView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            mRecyclerView = recyclerView;
        }

        @CallSuper
        @Override
        public void onDetachedFromRecyclerView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            mRecyclerView = null;
        }

        @CallSuper
        @Override
        public void onViewAttachedToWindow(@NonNull VH holder) {
            super.onViewAttachedToWindow(holder);
            AdapterUtils.fixTextSelectionInView(holder);
        }

        /**
         * Modified submitList that handles structural viewport anchoring.
         *
         * @param list         The new filtered or unfiltered list
         * @param saveState    True if the user is just starting to type a search query
         * @param restoreState True if the user just cleared the search query
         */
        public void submitListWithScrollState(@Nullable List<T> list, boolean saveState, boolean restoreState) {
            androidx.recyclerview.widget.RecyclerView.LayoutManager layoutManager = mRecyclerView != null ? mRecyclerView.getLayoutManager() : null;
            // Save historical state if entering a search
            if (saveState && layoutManager != null) {
                mPreFilterScrollState = layoutManager.onSaveInstanceState();
            }
            // Check if the user is currently at the exact top of the list
            boolean isCurrentlyAtTop;
            if (layoutManager instanceof LinearLayoutManager) {
                int firstVisibleItem = ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
                // Check offset to ensure they haven't scrolled down slightly
                View topView = layoutManager.findViewByPosition(firstVisibleItem);
                int topOffset = topView != null ? topView.getTop() : 0;
                isCurrentlyAtTop = (firstVisibleItem == 0 && topOffset >= 0);
            } else isCurrentlyAtTop = false;
            // Submit the diff
            super.submitList(list, () -> {
                if (layoutManager != null) {
                    if (isCurrentlyAtTop) {
                        // The user was at the top.
                        layoutManager.scrollToPosition(0);
                        // If they were also clearing a search, discard the historical state
                        if (restoreState) {
                            mPreFilterScrollState = null;
                        }
                    } else if (restoreState && mPreFilterScrollState != null) {
                        // Restore them to their deep historical position
                        layoutManager.onRestoreInstanceState(mPreFilterScrollState);
                        mPreFilterScrollState = null;
                    } // else do nothing
                }
            });
        }
    }
}
