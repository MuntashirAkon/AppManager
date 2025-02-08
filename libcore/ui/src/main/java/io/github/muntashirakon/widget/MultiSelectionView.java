// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.AttrRes;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.TintTypedArray;
import androidx.core.os.ParcelCompat;
import androidx.core.util.ObjectsCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.customview.view.AbsSavedState;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.transition.MaterialSharedAxis;

import java.lang.reflect.Field;
import java.util.Locale;

import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

@SuppressLint("RestrictedApi")
public class MultiSelectionView extends MaterialCardView implements OnApplyWindowInsetsListener {
    public interface OnSelectionChangeListener {
        /**
         * Called when the number of selections has changed or an update is required internally or via
         * {@link #updateCounter(boolean)}.
         *
         * @param selectionCount Present selection count
         * @return {@code true} if it's necessary to update the visibility of menu items, or {@code false} otherwise.
         */
        @UiThread
        boolean onSelectionChange(int selectionCount);
    }

    private final MultiSelectionActionsView mSelectionActionsView;
    private final View mDivider;
    private final View mCancelSelectionView;
    private final CheckBox mSelectAllView;
    private final TextView mSelectionCounter;
    @Px
    private final int mHorizontalMargin;
    @Px
    private final int mBottomMargin;
    @Px
    private final int mMaxHeight;
    @Px
    private final int mTitleHeight;

    @Px
    private int mCurrentHeight;
    @Px
    private int mSelectionBottomPadding;
    private boolean mInSelectionMode = false;
    @Nullable
    private Adapter<?> mAdapter;
    @Nullable
    private OnSelectionChangeListener mSelectionChangeListener;
    @Nullable
    private WindowInsetsCompat mLastInsets;

    public MultiSelectionView(Context context) {
        this(context, null);
    }

    public MultiSelectionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    @SuppressLint("ClickableViewAccessibility")
    public MultiSelectionView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Ensure we are using the correctly themed context rather than the context that was passed in.
        context = getContext();

        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_selection_panel, this, true);
        mSelectionActionsView = findViewById(R.id.selection_actions);
        mCancelSelectionView = findViewById(R.id.action_cancel);
        mSelectAllView = findViewById(R.id.action_select_all);
        mSelectionCounter = findViewById(R.id.selection_counter);
        mDivider = findViewById(R.id.divider);

        // Set heights
        mMaxHeight = UiUtils.dpToPx(context, 36 + 1 + 75); // This is a pessimistic approximation, not a real height
        mTitleHeight = UiUtils.dpToPx(context, 48);
        mCurrentHeight = mMaxHeight;

        // Clicking on counter maximizes/minimizes the selection actions
        mSelectionCounter.setOnClickListener((v) -> {
            Adapter.OnLayoutChangeListener listener;
            if (mAdapter != null) {
                listener = mAdapter.getLayoutChangeListener();
                mAdapter.setOnLayoutChangeListener(null);
            } else listener = null;
            if (mCurrentHeight == mTitleHeight) {
                // Minimized mode
                maximize();
            } else minimize();
            if (mAdapter != null) {
                mAdapter.setOnLayoutChangeListener(listener);
            }
        });

        // Custom attributes
        TintTypedArray attributes = ThemeEnforcement.obtainTintedStyledAttributes(context, attrs,
                R.styleable.MultiSelectionView, defStyleAttr, com.google.android.material.R.style.Widget_MaterialComponents_CardView);

        // Set styles
        @Px
        int smallSize = getResources().getDimensionPixelSize(R.dimen.padding_small);
        setPreventCornerOverlap(false);
        setCardElevation(UiUtils.dpToPx(context, 8));

        mHorizontalMargin = smallSize;
        mBottomMargin = getResources().getDimensionPixelSize(R.dimen.padding_very_small);

        if (attributes.hasValue(R.styleable.MultiSelectionView_menu)) {
            mSelectionActionsView.inflateMenu(attributes.getResourceId(R.styleable.MultiSelectionView_menu, 0));
        }

        attributes.recycle();

        ViewCompat.setOnApplyWindowInsetsListener(this, this);
    }

    static class SavedState extends AbsSavedState {
        int currentHeight;
        int selectionBottomPadding;
        int selectionBottomPaddingMinimum;
        boolean inSelectionMode;

        SavedState(@NonNull Parcelable superState) {
            super(superState);
        }

        public SavedState(@NonNull Parcel source, @Nullable ClassLoader loader) {
            super(source, loader);
            currentHeight = source.readInt();
            selectionBottomPadding = source.readInt();
            selectionBottomPaddingMinimum = source.readInt();
            inSelectionMode = ParcelCompat.readBoolean(source);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(currentHeight);
            dest.writeInt(selectionBottomPadding);
            dest.writeInt(selectionBottomPaddingMinimum);
            ParcelCompat.writeBoolean(dest, inSelectionMode);
        }

        @NonNull
        @Override
        public String toString() {
            return "SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + "currentHeight=" + currentHeight
                    + " selectionBottomPadding=" + selectionBottomPadding
                    + " selectionBottomPaddingMinimum=" + selectionBottomPaddingMinimum +
                    '}';
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        if (superState == null) {
            return null;
        }
        SavedState ss = new SavedState(superState);
        ss.currentHeight = mCurrentHeight;
        ss.selectionBottomPadding = mSelectionBottomPadding;
        ss.inSelectionMode = mInSelectionMode;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState ss = (SavedState) state;
            super.onRestoreInstanceState(ss.getSuperState());
            mCurrentHeight = ss.currentHeight;
            mSelectionBottomPadding = ss.selectionBottomPadding;
            mInSelectionMode = ss.inSelectionMode;
        } else super.onRestoreInstanceState(state);
        if (mInSelectionMode) {
            show();
            updateCounter(false);
        } else {
            updateCounter(true);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Set layout params
        updateMarginAndPosition();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        ViewGroup.MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        mSelectionBottomPadding = getHeight() + lp.topMargin + lp.bottomMargin + UiUtils.dpToPx(getContext(), 5);
        if (mAdapter != null) {
            mAdapter.setSelectionBottomPadding(mSelectionBottomPadding);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(mCurrentHeight, MeasureSpec.AT_MOST));
    }

    @Override
    @NonNull
    public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        WindowInsetsCompat newInsets = null;
        if (getFitsSystemWindows()) {
            newInsets = insets;
        }
        if (!ObjectsCompat.equals(mLastInsets, newInsets)) {
            mLastInsets = newInsets;
            updateMarginAndPosition();
            requestLayout();
        }
        return insets;
    }

    @NonNull
    public Menu getMenu() {
        return mSelectionActionsView.getMenu();
    }

    @Px
    public int getHorizontalMargin() {
        return mHorizontalMargin;
    }

    @Px
    public int getBottomMargin() {
        return mBottomMargin;
    }

    @Px
    public int getSelectionBottomPadding() {
        return mSelectionBottomPadding;
    }

    public void setAdapter(@NonNull Adapter<?> adapter) {
        mAdapter = adapter;
        // Set listeners
        adapter.setOnLayoutChangeListener((v, rect, oldRect) -> toggleSelectionActions(rect.height()));
        mCancelSelectionView.setOnClickListener(v -> {
            adapter.cancelSelection();
            hide();
        });
        mSelectAllView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) adapter.selectAll();
            else adapter.deselectAll();
        });
        adapter.setOnSelectionChangeListener(() -> updateCounter(false));
    }

    @UiThread
    public void show() {
        Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
        TransitionManager.beginDelayedTransition(this, sharedAxis);
        setVisibility(VISIBLE);
        ViewGroup.MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        mSelectionBottomPadding = getHeight() + lp.topMargin + lp.bottomMargin;
        mInSelectionMode = true;
        if (mAdapter != null) {
            mAdapter.setInSelectionMode(true);
            mAdapter.setSelectionBottomPadding(mSelectionBottomPadding);
        }
    }

    public void cancel() {
        mCancelSelectionView.performClick();
    }

    @SuppressWarnings("deprecation")
    @UiThread
    public void hide() {
        Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, false);
        TransitionManager.beginDelayedTransition(this, sharedAxis);
        setVisibility(GONE);
        mSelectionBottomPadding = 0;
        mInSelectionMode = false;
        if (mAdapter != null) {
            //noinspection PointlessNullCheck
            if (mAdapter.mRecyclerView != null
                    && mAdapter.mRecyclerView.getFitsSystemWindows()
                    && mLastInsets != null) {
                mSelectionBottomPadding += mLastInsets.getSystemWindowInsetBottom();
            }
            mAdapter.setInSelectionMode(false);
            mAdapter.setSelectionBottomPadding(mSelectionBottomPadding);
        }
    }

    public void setOnItemSelectedListener(MultiSelectionActionsView.OnItemSelectedListener listener) {
        mSelectionActionsView.setOnItemSelectedListener(listener);
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener selectionChangeListener) {
        mSelectionChangeListener = selectionChangeListener;
    }

    @SuppressLint("SetTextI18n")
    @UiThread
    public void updateCounter(boolean hideOnEmpty) {
        if (mAdapter == null) {
            hide();
            return;
        }
        int selectionCount = mAdapter.getSelectedItemCount();
        if (selectionCount <= 0 && hideOnEmpty) {
            if (getVisibility() != GONE) hide();
            if (mSelectionChangeListener != null && mSelectionChangeListener.onSelectionChange(0)) {
                mSelectionActionsView.updateMenuView();
            }
            return;
        }
        if (selectionCount > 0) {
            if (getVisibility() != VISIBLE) show();
        }
        mSelectionCounter.setText(String.format(Locale.getDefault(), "%d/%d", selectionCount, mAdapter.getTotalItemCount()));
        mSelectAllView.setChecked(mAdapter.areAllSelected(), false);
        if (mSelectionChangeListener != null && mSelectionChangeListener.onSelectionChange(selectionCount)) {
            mSelectionActionsView.updateMenuView();
        }
        if (!mAdapter.isInSelectionMode()) {
            // Special check to avoid displaying the selection panel on resizing the view
            hide();
        }
    }

    private void toggleSelectionActions(int recyclerViewHeight) {
        if (mMaxHeight * 2 > recyclerViewHeight) {
            minimize();
        } else {
            maximize();
        }
    }

    private void minimize() {
        mCurrentHeight = mTitleHeight;
        mSelectionActionsView.setVisibility(GONE);
        mDivider.setVisibility(GONE);
        requestLayout();
    }

    private void maximize() {
        mCurrentHeight = mMaxHeight;
        mSelectionActionsView.setVisibility(VISIBLE);
        mDivider.setVisibility(VISIBLE);
        requestLayout();
    }

    @SuppressWarnings("deprecation")
    private void updateMarginAndPosition() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof MarginLayoutParams) {
            int totalLeftMargin = mHorizontalMargin;
            int totalRightMargin = mHorizontalMargin;
            int totalBottomMargin = mBottomMargin;
            if (ViewCompat.getFitsSystemWindows(this) && mLastInsets != null) {
                totalLeftMargin += mLastInsets.getSystemWindowInsetLeft();
                totalRightMargin += mLastInsets.getSystemWindowInsetRight();
                totalBottomMargin += mLastInsets.getSystemWindowInsetBottom();
            }
            ((MarginLayoutParams) params).leftMargin = totalLeftMargin;
            ((MarginLayoutParams) params).rightMargin = totalRightMargin;
            ((MarginLayoutParams) params).bottomMargin = totalBottomMargin;
        }
        try {
            Field gravity = params.getClass().getField("gravity");
            gravity.set(params, Gravity.BOTTOM);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
        }
        setLayoutParams(params);
    }

    public abstract static class Adapter<VH extends ViewHolder> extends RecyclerView.Adapter<VH> implements View.OnLayoutChangeListener {
        private interface OnSelectionChangeListener {
            @UiThread
            void onSelectionChange();
        }

        private interface OnLayoutChangeListener {
            @UiThread
            void onLayoutChange(RecyclerView v, Rect rect, Rect oldRect);
        }

        @Nullable
        private OnSelectionChangeListener mSelectionChangeListener;
        @Nullable
        private OnLayoutChangeListener mLayoutChangeListener;
        private boolean mIsInSelectionMode;
        @Nullable
        private RecyclerView mRecyclerView;
        private int mDefaultBottomPadding;

        public Adapter() {
            setHasStableIds(true);
        }

        @AnyThread
        public abstract long getItemId(int position);

        @UiThread
        protected abstract void select(int position);

        @UiThread
        protected abstract void deselect(int position);

        @AnyThread
        protected abstract boolean isSelected(int position);

        /**
         * Cancel the selection process. This should clear all the selected items that may not be displayed in the
         * {@link RecyclerView} due to filtering, etc.
         */
        @UiThread
        @CallSuper
        protected void cancelSelection() {
            deselectAll();
        }

        @AnyThread
        protected abstract int getSelectedItemCount();

        @AnyThread
        protected abstract int getTotalItemCount();

        @AnyThread
        public final boolean isInSelectionMode() {
            return mIsInSelectionMode;
        }

        @AnyThread
        public final boolean areAllSelected() {
            for (int position = 0; position < getItemCount(); ++position) {
                if (!isSelected(position)) return false;
            }
            return true;
        }

        @UiThread
        public final void notifySelectionChange() {
            if (mSelectionChangeListener != null) mSelectionChangeListener.onSelectionChange();
        }

        @AnyThread
        public final void setInSelectionMode(boolean inSelectionMode) {
            mIsInSelectionMode = inSelectionMode;
        }

        @UiThread
        @CallSuper
        public void toggleSelection(int position) {
            if (isSelected(position)) {
                deselect(position);
                notifyItemChanged(position);
                notifySelectionChange();
            } else {
                select(position);
                notifySelectionChange();
                notifyItemChanged(position);
            }
        }

        @UiThread
        @CallSuper
        public void selectAll() {
            for (int position = 0; position < getItemCount(); ++position) {
                select(position);
            }
            notifySelectionChange();
            notifyItemRangeChanged(0, getItemCount(), null);
        }

        @UiThread
        @CallSuper
        public void deselectAll() {
            for (int position = 0; position < getItemCount(); ++position) {
                if (isSelected(position)) {
                    deselect(position);
                    notifyItemChanged(position);
                }
            }
            notifySelectionChange();
        }

        @UiThread
        @CallSuper
        public void selectRange(int firstPosition, int secondPosition) {
            int beginPosition = Math.min(firstPosition, secondPosition);
            int endPosition = Math.max(firstPosition, secondPosition);
            for (int position = beginPosition; position <= endPosition; ++position) {
                select(position);
            }
            notifySelectionChange();
            notifyItemRangeChanged(beginPosition, endPosition - beginPosition + 1);
        }

        @Override
        public final void onLayoutChange(View v, int left, int top, int right, int bottom,
                                         int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (mLayoutChangeListener == null) return;
            Rect rect = new Rect(left, top, right, bottom);
            Rect oldRect = new Rect(oldLeft, oldTop, oldRight, oldBottom);
            if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
                mLayoutChangeListener.onLayoutChange(mRecyclerView, rect, oldRect);
            }
        }

        @AnyThread
        private void setOnSelectionChangeListener(@Nullable OnSelectionChangeListener listener) {
            mSelectionChangeListener = listener;
        }

        @AnyThread
        private void setOnLayoutChangeListener(@Nullable OnLayoutChangeListener listener) {
            mLayoutChangeListener = listener;
        }

        @AnyThread
        @Nullable
        private OnLayoutChangeListener getLayoutChangeListener() {
            return mLayoutChangeListener;
        }

        /**
         * @param selectionBottomPadding Set {@code 0} to reset
         */
        @UiThread
        private void setSelectionBottomPadding(@Px int selectionBottomPadding) {
            if (mRecyclerView == null) return;
            if (mRecyclerView.getClipToPadding()) {
                // Clip to padding must be disabled
                mRecyclerView.setClipToPadding(false);
            }
            mRecyclerView.setPadding(mRecyclerView.getPaddingLeft(), mRecyclerView.getPaddingTop(),
                    mRecyclerView.getPaddingRight(), selectionBottomPadding == 0 ? mDefaultBottomPadding
                            : selectionBottomPadding);
        }

        @CallSuper
        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            mRecyclerView = recyclerView;
            mDefaultBottomPadding = recyclerView.getPaddingBottom();
            recyclerView.addOnLayoutChangeListener(this);
        }

        @CallSuper
        @Override
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            recyclerView.removeOnLayoutChangeListener(this);
            mRecyclerView = null;
        }

        @CallSuper
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // Set focus right to select all
            holder.itemView.setNextFocusRightId(R.id.action_select_all);
            // Set selection background
            boolean isSelected = isSelected(position);
            if (holder.itemView instanceof MaterialCardView) {
                MaterialCardView cardView = (MaterialCardView) holder.itemView;
                if (cardView.isCheckable()) {
                    cardView.setChecked(isSelected);
                } else if (isSelected) {
                    throw new UnsupportedOperationException("Card is not checkable");
                }
            } else if (isSelected) {
                holder.itemView.setBackgroundResource(R.drawable.item_highlight);
            }
        }
    }

    public abstract static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
