// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.TintTypedArray;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.transition.MaterialSharedAxis;

import java.lang.reflect.Field;

import io.github.muntashirakon.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

import static com.google.android.material.R.style.Widget_MaterialComponents_CardView;

@SuppressLint("RestrictedApi")
public class MultiSelectionView extends MaterialCardView {
    public interface OnSelectionChangeListener {
        void onSelectionChange(int selectionCount);
    }

    private final SelectionActionsView selectionActionsView;
    private final View divider;
    private final MaxHeightScrollView selectionActionsContainer;
    private final View cancelSelectionView;
    private final CheckBox selectAllView;
    private final TextView selectionCounter;
    @Px
    private final int maxHeight;
    @Px
    private final int titleHeight;
    @Px
    private int currentHeight;
    @Nullable
    private Adapter<?> adapter;
    @Nullable
    private OnSelectionChangeListener selectionChangeListener;

    public MultiSelectionView(Context context) {
        this(context, null);
    }

    public MultiSelectionView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.materialCardViewStyle);
    }

    @SuppressLint("ClickableViewAccessibility")
    public MultiSelectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Ensure we are using the correctly themed context rather than the context that was passed in.
        context = getContext();

        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_selection_panel, this, true);
        selectionActionsView = findViewById(R.id.selection_actions);
        selectionActionsContainer = findViewById(R.id.selection_actions_container);
        cancelSelectionView = findViewById(R.id.action_cancel);
        selectAllView = findViewById(R.id.action_select_all);
        selectionCounter = findViewById(R.id.selection_counter);
        divider = findViewById(R.id.divider);

        // Set heights
        maxHeight = UiUtils.dpToPx(getContext(), 48 + 1 + 116);
        titleHeight = UiUtils.dpToPx(getContext(), 48);
        currentHeight = maxHeight;

        // Clicking on counter maximizes/minimizes the selection actions
        selectionCounter.setOnClickListener((v) -> {
            Adapter.OnLayoutChangeListener listener;
            if (adapter != null) {
                listener = adapter.getLayoutChangeListener();
                adapter.setOnLayoutChangeListener(null);
            } else listener = null;
            if (currentHeight == titleHeight) {
                // Minimized mode
                maximize();
            } else minimize();
            if (adapter != null) {
                adapter.setOnLayoutChangeListener(listener);
            }
        });

        // Custom attributes
        TintTypedArray attributes = ThemeEnforcement.obtainTintedStyledAttributes(context, attrs,
                R.styleable.MultiSelectionView, defStyleAttr, Widget_MaterialComponents_CardView);

        // Set styles
        @Px
        int smallSize = getResources().getDimensionPixelSize(R.dimen.padding_small);
        setCardElevation(UiUtils.dpToPx(context, 2));
        setPreventCornerOverlap(false);
        setRadius(smallSize);

        if (attributes.hasValue(R.styleable.MultiSelectionView_menu)) {
            selectionActionsView.inflateMenu(attributes.getResourceId(R.styleable.MultiSelectionView_menu, 0));
        }

        selectionActionsView.setItemActiveIndicatorEnabled(false);

        attributes.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        @Px
        int smallSize = getResources().getDimensionPixelSize(R.dimen.padding_small);
        int smallerSize = getResources().getDimensionPixelSize(R.dimen.padding_very_small);
        // Set layout params
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof MarginLayoutParams) {
            ((MarginLayoutParams) params).leftMargin = smallSize;
            ((MarginLayoutParams) params).rightMargin = smallSize;
            ((MarginLayoutParams) params).bottomMargin = smallerSize;
        }
        try {
            Field gravity = params.getClass().getField("gravity");
            gravity.set(params, Gravity.BOTTOM);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
        }
        setLayoutParams(params);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (adapter != null) {
            ViewGroup.MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
            adapter.setSelectionBottomPadding(getHeight() + lp.topMargin + lp.bottomMargin + UiUtils.dpToPx(getContext(), 5));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(currentHeight, MeasureSpec.AT_MOST));
    }

    @NonNull
    public Menu getMenu() {
        return selectionActionsView.getMenu();
    }

    public void setAdapter(@NonNull Adapter<?> adapter) {
        this.adapter = adapter;
        // Set listeners
        adapter.setOnLayoutChangeListener((v, rect, oldRect) -> toggleSelectionActions(rect.height()));
        cancelSelectionView.setOnClickListener(v -> {
            adapter.cancelSelection();
            hide();
        });
        selectAllView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) adapter.selectAll();
            else adapter.clearSelections();
        });
        adapter.setOnSelectionChangeListener(() -> updateCounter(false));
    }

    @UiThread
    public void show() {
        Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, true);
        TransitionManager.beginDelayedTransition(this, sharedAxis);
        setVisibility(VISIBLE);
        if (adapter != null) {
            adapter.setInSelectionMode(true);
            ViewGroup.MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
            adapter.setSelectionBottomPadding(getHeight() + lp.topMargin + lp.bottomMargin);
        }
    }

    public void cancel() {
        cancelSelectionView.performClick();
    }

    @UiThread
    public void hide() {
        Transition sharedAxis = new MaterialSharedAxis(MaterialSharedAxis.Y, false);
        TransitionManager.beginDelayedTransition(this, sharedAxis);
        setVisibility(GONE);
        if (adapter != null) {
            adapter.setInSelectionMode(false);
            adapter.setSelectionBottomPadding(0);
        }
    }

    public void setOnItemSelectedListener(ReflowMenuViewWrapper.OnItemSelectedListener listener) {
        selectionActionsView.setOnItemSelectedListener(listener);
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener selectionChangeListener) {
        this.selectionChangeListener = selectionChangeListener;
    }

    @SuppressLint("SetTextI18n")
    @UiThread
    public void updateCounter(boolean hideOnEmpty) {
        if (adapter == null) {
            hide();
            return;
        }
        int selectionCount = adapter.getSelectedItemCount();
        if (selectionCount <= 0 && hideOnEmpty) {
            if (getVisibility() != GONE) hide();
            return;
        }
        if (selectionCount > 0) {
            if (getVisibility() != VISIBLE) show();
        }
        selectionCounter.setText(selectionCount + "/" + adapter.getTotalItemCount());
        selectAllView.setChecked(adapter.areAllSelected(), false);
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChange(selectionCount);
        }
        if (!adapter.isInSelectionMode()) {
            // Special check to avoid displaying the selection panel on resizing the view
            hide();
        }
    }

    private void toggleSelectionActions(int recyclerViewHeight) {
        if (maxHeight * 2 > recyclerViewHeight) {
            minimize();
        } else {
            maximize();
        }
    }

    private void minimize() {
        currentHeight = titleHeight;
        selectionActionsContainer.setVisibility(GONE);
        divider.setVisibility(GONE);
        requestLayout();
    }

    private void maximize() {
        currentHeight = maxHeight;
        selectionActionsContainer.setVisibility(VISIBLE);
        divider.setVisibility(VISIBLE);
        requestLayout();
    }

    public abstract static class Adapter<VH extends ViewHolder> extends RecyclerView.Adapter<VH> implements View.OnLayoutChangeListener {
        private interface OnSelectionChangeListener {
            void onSelectionChange();
        }

        private interface OnLayoutChangeListener {
            void onLayoutChange(RecyclerView v, Rect rect, Rect oldRect);
        }

        @Nullable
        private OnSelectionChangeListener selectionChangeListener;
        @Nullable
        private OnLayoutChangeListener layoutChangeListener;
        private boolean isInSelectionMode;
        @Nullable
        private RecyclerView recyclerView;

        public Adapter() {
            setHasStableIds(true);
        }

        public abstract long getItemId(int position);

        protected abstract void select(int position);

        protected abstract void deselect(int position);

        protected abstract boolean isSelected(int position);

        protected abstract void cancelSelection();

        protected abstract int getSelectedItemCount();

        protected abstract int getTotalItemCount();

        public final boolean isInSelectionMode() {
            return isInSelectionMode;
        }

        public final boolean areAllSelected() {
            for (int position = 0; position < getItemCount(); ++position) {
                if (!isSelected(position)) return false;
            }
            return true;
        }

        public final void notifySelectionChange() {
            if (selectionChangeListener != null) selectionChangeListener.onSelectionChange();
        }

        public final void setInSelectionMode(boolean inSelectionMode) {
            isInSelectionMode = inSelectionMode;
        }

        @CallSuper
        public void toggleSelection(int position) {
            if (isSelected(position)) {
                deselect(position);
            } else select(position);
            notifyItemChanged(position);
            notifySelectionChange();
        }

        @CallSuper
        public void clearSelections() {
            notifySelectionChange();
        }

        @CallSuper
        public void selectAll() {
            for (int position = 0; position < getItemCount(); ++position) {
                select(position);
            }
            notifyItemRangeChanged(0, getItemCount(), null);
            notifySelectionChange();
        }

        @CallSuper
        public void selectRange(int firstPosition, int secondPosition) {
            int beginPosition = Math.min(firstPosition, secondPosition);
            int endPosition = Math.max(firstPosition, secondPosition);
            for (int position = beginPosition; position <= endPosition; ++position) {
                select(position);
            }
            notifyItemRangeChanged(beginPosition, endPosition - beginPosition + 1);
            notifySelectionChange();
        }

        @Override
        public final void onLayoutChange(View v, int left, int top, int right, int bottom,
                                         int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (layoutChangeListener == null) return;
            Rect rect = new Rect(left, top, right, bottom);
            Rect oldRect = new Rect(oldLeft, oldTop, oldRight, oldBottom);
            if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
                layoutChangeListener.onLayoutChange(recyclerView, rect, oldRect);
            }
        }

        private void setOnSelectionChangeListener(@Nullable OnSelectionChangeListener listener) {
            selectionChangeListener = listener;
        }

        private void setOnLayoutChangeListener(@Nullable OnLayoutChangeListener listener) {
            layoutChangeListener = listener;
        }

        @Nullable
        public OnLayoutChangeListener getLayoutChangeListener() {
            return layoutChangeListener;
        }

        /**
         * @param selectionBottomPadding Set {@code 0} to reset
         */
        @UiThread
        private void setSelectionBottomPadding(@Px int selectionBottomPadding) {
            if (recyclerView == null) return;
            if (recyclerView.getClipToPadding()) {
                // Clip to padding must be disabled
                recyclerView.setClipToPadding(false);
            }
            recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(), selectionBottomPadding);
        }

        @CallSuper
        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
            this.recyclerView = recyclerView;
            recyclerView.addOnLayoutChangeListener(this);
        }

        @CallSuper
        @Override
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            super.onDetachedFromRecyclerView(recyclerView);
            recyclerView.removeOnLayoutChangeListener(this);
            this.recyclerView = null;
        }

        @CallSuper
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // Set focus
            holder.itemView.setNextFocusRightId(R.id.action_select_all);
            // Set selection background
            if (isSelected(position)) {
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
