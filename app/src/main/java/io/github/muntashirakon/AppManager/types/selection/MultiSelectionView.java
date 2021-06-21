// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types.selection;

import android.annotation.SuppressLint;
import android.content.Context;
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
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.transition.MaterialSharedAxis;

import java.lang.reflect.Field;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.types.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.AppManager.types.reflow.SelectionActionsView;
import io.github.muntashirakon.AppManager.utils.UIUtils;

@SuppressLint("RestrictedApi")
public class MultiSelectionView extends MaterialCardView {
    public interface OnSelectionChangeListener {
        void onSelectionChange(int selectionCount);
    }

    private final SelectionActionsView selectionActionsView;
    private final View cancelSelectionView;
    private final MaterialCheckBox selectAllView;
    private final TextView selectionCounter;
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

    public MultiSelectionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.view_selection_panel, this, true);
        selectionActionsView = findViewById(R.id.selection_actions);
        cancelSelectionView = findViewById(R.id.action_cancel);
        selectAllView = findViewById(R.id.action_select_all);
        selectionCounter = findViewById(R.id.selection_counter);

        // Ensure we are using the correctly themed context rather than the context that was passed in.
        context = getContext();

        // Custom attributes
        TintTypedArray attributes = ThemeEnforcement.obtainTintedStyledAttributes(context, attrs,
                R.styleable.MultiSelectionView, defStyleAttr, R.style.Widget_MaterialComponents_CardView);

        // Set styles
        @Px
        int smallSize = getResources().getDimensionPixelSize(R.dimen.padding_small);
        setContentPadding(smallSize, 0, smallSize, smallSize);
        setUseCompatPadding(true);
        setCardElevation(UIUtils.dpToPx(context, 2));
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
        // Set layout params
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof MarginLayoutParams) {
            ((MarginLayoutParams) params).leftMargin = smallSize;
            ((MarginLayoutParams) params).rightMargin = smallSize;
        }
        try {
            Field gravity = params.getClass().getField("gravity");
            gravity.set(params, Gravity.BOTTOM);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
        }
        setLayoutParams(params);
    }

    public Menu getMenu() {
        return selectionActionsView.getMenu();
    }


    public void setAdapter(@NonNull Adapter<?> adapter) {
        this.adapter = adapter;
        // Set listeners
        cancelSelectionView.setOnClickListener(v -> {
            adapter.cancelSelection();
            hide();
        });
        selectAllView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                if (isChecked) adapter.selectAll();
                else adapter.clearSelections();
            }
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
            adapter.setSelectionBottomMargin(getHeight());
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
            adapter.setSelectionBottomMargin(-1);
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
        selectAllView.setChecked(adapter.areAllSelected());
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChange(selectionCount);
        }
    }

    public abstract static class Adapter<VH extends ViewHolder> extends RecyclerView.Adapter<VH> {
        private interface OnSelectionChangeListener {
            void onSelectionChange();
        }

        @Px
        private int selectionBottomMargin;
        @Nullable
        private OnSelectionChangeListener selectionChangeListener;
        private boolean isInSelectionMode;

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

        public final void toggleSelection(int position) {
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

        public final void selectAll() {
            for (int position = 0; position < getItemCount(); ++position) {
                select(position);
            }
            notifyItemRangeChanged(0, getItemCount(), null);
            notifySelectionChange();
        }

        public final void selectRange(int firstPosition, int secondPosition) {
            int beginPosition = Math.min(firstPosition, secondPosition);
            int endPosition = Math.max(firstPosition, secondPosition);
            for (int position = beginPosition; position <= endPosition; ++position) {
                select(position);
            }
            notifyItemRangeChanged(beginPosition, endPosition - beginPosition + 1);
            notifySelectionChange();
        }

        private void setOnSelectionChangeListener(@Nullable OnSelectionChangeListener listener) {
            selectionChangeListener = listener;
        }

        /**
         * @param selectionBottomMargin Set {@code -1} to reset
         */
        @UiThread
        private void setSelectionBottomMargin(@Px int selectionBottomMargin) {
            this.selectionBottomMargin = selectionBottomMargin;
            // Last view has to be reset
            notifyItemChanged(getItemCount() - 1);
        }

        @CallSuper
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // Set selection background
            if (isSelected(position)) {
                holder.itemView.setBackgroundResource(R.drawable.item_highlight);
            }
            // Last item must have extended padding
            if (selectionBottomMargin != -1 && position == getItemCount() - 1) {
                holder.setBottomMargin(selectionBottomMargin);
            } else holder.resetBottomMargin();
        }
    }

    public abstract static class ViewHolder extends RecyclerView.ViewHolder {
        @Px
        private final int defaultBottomMargin;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            defaultBottomMargin = ((RecyclerView.LayoutParams) itemView.getLayoutParams()).bottomMargin;
        }

        void setBottomMargin(@Px int bottomMargin) {
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) itemView.getLayoutParams();
            layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, bottomMargin);
        }

        void resetBottomMargin() {
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) itemView.getLayoutParams();
            if (layoutParams.bottomMargin != defaultBottomMargin) {
                layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, defaultBottomMargin);
            }
        }
    }
}
