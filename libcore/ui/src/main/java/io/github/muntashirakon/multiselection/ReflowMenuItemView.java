// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.multiselection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.PointerIconCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import androidx.core.widget.TextViewCompat;

import io.github.muntashirakon.ui.R;

// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public final class ReflowMenuItemView extends FrameLayout {
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    private final ImageView mIcon;
    private final TextView mLabel;

    @Nullable
    private MenuItemImpl mItemData;

    @Nullable
    private ColorStateList mIconTint;
    @Nullable
    private Drawable mOriginalIconDrawable;
    @Nullable
    private Drawable mWrappedIconDrawable;

    private boolean mActiveBackgroundEnabled = false;

    public ReflowMenuItemView(Context context) {
        this(context, null);
    }

    public ReflowMenuItemView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.item_reflow_menu, this, true);
        mIcon = findViewById(R.id.icon);
        mLabel = findViewById(R.id.label);

        ViewCompat.setImportantForAccessibility(mLabel, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
    }

    public void initialize(@NonNull MenuItemImpl itemData) {
        mItemData = itemData;
        setCheckable(itemData.isCheckable());
        setChecked(itemData.isChecked());
        setEnabled(itemData.isEnabled());
        setIcon(itemData.getIcon());
        setTitle(itemData.getTitle());
        setId(itemData.getItemId());
        if (!TextUtils.isEmpty(itemData.getContentDescription())) {
            setContentDescription(itemData.getContentDescription());
        }

        CharSequence tooltipText = !TextUtils.isEmpty(itemData.getTooltipText())
                ? itemData.getTooltipText()
                : itemData.getTitle();

        // Avoid calling tooltip for L and M devices because long pressing twice may freeze devices.
        if (VERSION.SDK_INT > VERSION_CODES.M) {
            TooltipCompat.setTooltipText(this, tooltipText);
        }
        setVisibility(itemData.isVisible() ? View.VISIBLE : View.GONE);
    }

    public void setTitle(@Nullable CharSequence title) {
        mLabel.setText(title);
        if (mItemData == null || TextUtils.isEmpty(mItemData.getContentDescription())) {
            setContentDescription(title);
        }

        CharSequence tooltipText = mItemData == null || TextUtils.isEmpty(mItemData.getTooltipText())
                ? title
                : mItemData.getTooltipText();
        // Avoid calling tooltip for L and M devices because long pressing twice may freeze devices.
        if (VERSION.SDK_INT > VERSION_CODES.M) {
            TooltipCompat.setTooltipText(this, tooltipText);
        }
    }

    public void setCheckable(boolean checkable) {
        refreshDrawableState();
    }

    public void setChecked(boolean checked) {
        mLabel.setPivotX(mLabel.getWidth() / 2F);
        mLabel.setPivotY(mLabel.getBaseline());

        refreshDrawableState();

        // Set the item as selected to send an AccessibilityEvent.TYPE_VIEW_SELECTED from View, so that
        // the item is read out as selected.
        setSelected(checked);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        AccessibilityNodeInfoCompat infoCompat = AccessibilityNodeInfoCompat.wrap(info);
        infoCompat.setCollectionItemInfo(
                CollectionItemInfoCompat.obtain(
                        /* rowIndex= */ 0,
                        /* rowSpan= */ 1,
                        /* columnIndex= */ getItemVisiblePosition(),
                        /* columnSpan= */ 1,
                        /* heading= */ false,
                        /* selected= */ isSelected()));
        if (isSelected()) {
            infoCompat.setClickable(false);
            infoCompat.removeAction(AccessibilityActionCompat.ACTION_CLICK);
        }
        infoCompat.setRoleDescription(getResources().getString(com.google.android.material.R.string.item_view_role_description));
    }

    /**
     * Iterate through all the preceding bottom navigating items to determine this item's visible
     * position.
     *
     * @return This item's visible position in a bottom navigation.
     */
    private int getItemVisiblePosition() {
        ViewGroup parent = (ViewGroup) getParent();
        int index = parent.indexOfChild(this);
        int visiblePosition = 0;
        for (int i = 0; i < index; i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ReflowMenuItemView && child.getVisibility() == View.VISIBLE) {
                visiblePosition++;
            }
        }
        return visiblePosition;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mLabel.setEnabled(enabled);
        mIcon.setEnabled(enabled);

        if (enabled) {
            ViewCompat.setPointerIcon(this, PointerIconCompat.getSystemIcon(getContext(), PointerIconCompat.TYPE_HAND));
        } else {
            ViewCompat.setPointerIcon(this, null);
        }
    }

    @Override
    @NonNull
    public int[] onCreateDrawableState(final int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mActiveBackgroundEnabled && mItemData != null && mItemData.isCheckable() && mItemData.isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    public void setIcon(@Nullable Drawable iconDrawable) {
        if (iconDrawable == mOriginalIconDrawable) {
            return;
        }

        // Save the original icon to check if it has changed in future calls of this method.
        mOriginalIconDrawable = iconDrawable;
        if (iconDrawable != null) {
            Drawable.ConstantState state = iconDrawable.getConstantState();
            iconDrawable = DrawableCompat.wrap(state == null ? iconDrawable : state.newDrawable()).mutate();
            mWrappedIconDrawable = iconDrawable;
            if (mIconTint != null) {
                DrawableCompat.setTintList(mWrappedIconDrawable, mIconTint);
            }
        }
        mIcon.setImageDrawable(iconDrawable);
    }

    public void setIconTintList(@Nullable ColorStateList tint) {
        mIconTint = tint;
        if (mItemData != null && mWrappedIconDrawable != null) {
            DrawableCompat.setTintList(mWrappedIconDrawable, mIconTint);
            mWrappedIconDrawable.invalidateSelf();
        }
    }

    public void setIconSize(int iconSize) {
        LinearLayoutCompat.LayoutParams iconParams = (LinearLayoutCompat.LayoutParams) mIcon.getLayoutParams();
        iconParams.width = iconSize;
        iconParams.height = iconSize;
        mIcon.setLayoutParams(iconParams);
    }

    public void setTextAppearanceInactive(@StyleRes int inactiveTextAppearance) {
        TextViewCompat.setTextAppearance(mLabel, inactiveTextAppearance);
    }

    public void setTextAppearanceActive(@StyleRes int activeTextAppearance) {
        TextViewCompat.setTextAppearance(mLabel, activeTextAppearance);
    }

    public void setTextColor(@Nullable ColorStateList color) {
        if (color != null) {
            mLabel.setTextColor(color);
        }
    }

    public void setItemBackground(int background) {
        Drawable backgroundDrawable = background == 0 ? null : ContextCompat.getDrawable(getContext(), background);
        setItemBackground(backgroundDrawable);
    }

    public void setItemBackground(@Nullable Drawable background) {
        if (background != null && background.getConstantState() != null) {
            background = background.getConstantState().newDrawable().mutate();
        }
        ViewCompat.setBackground(this, background);
    }

    /**
     * Set whether or not this item should show an active indicator when checked.
     */
    public void setActiveBackgroundEnabled(boolean enabled) {
        mActiveBackgroundEnabled = enabled;
        requestLayout();
    }
}
