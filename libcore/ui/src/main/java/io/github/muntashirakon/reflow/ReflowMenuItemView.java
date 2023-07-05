// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.reflow;

import static java.lang.Math.max;

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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuView;
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

import com.google.android.material.card.MaterialCardView;

import io.github.muntashirakon.ui.R;

// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public final class ReflowMenuItemView extends MaterialCardView implements MenuView.ItemView {
    private static final int INVALID_ITEM_POSITION = -1;
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    private final ImageView icon;
    private final TextView label;
    private int itemPosition = INVALID_ITEM_POSITION;

    @Nullable
    private MenuItemImpl itemData;

    @Nullable
    private ColorStateList iconTint;
    @Nullable
    private Drawable originalIconDrawable;
    @Nullable
    private Drawable wrappedIconDrawable;

    private boolean activeBackgroundEnabled = false;

    public ReflowMenuItemView(Context context) {
        this(context, null);
    }

    public ReflowMenuItemView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public ReflowMenuItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Inflate layout
        LayoutInflater.from(context).inflate(R.layout.item_reflow_menu, this, true);
        icon = findViewById(R.id.icon);
        label = findViewById(R.id.label);

        ViewCompat.setImportantForAccessibility(label, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        LinearLayoutCompat.LayoutParams labelGroupParams = (LinearLayoutCompat.LayoutParams) label.getLayoutParams();
        int labelWidth = labelGroupParams.leftMargin + label.getMeasuredWidth() + labelGroupParams.rightMargin;

        return max(getSuggestedIconWidth(), labelWidth);
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        LinearLayoutCompat.LayoutParams labelGroupParams = (LinearLayoutCompat.LayoutParams) label.getLayoutParams();
        return getSuggestedIconHeight()
                + labelGroupParams.topMargin
                + label.getMeasuredHeight()
                + labelGroupParams.bottomMargin;
    }

    @Override
    public void initialize(@NonNull MenuItemImpl itemData, int menuType) {
        this.itemData = itemData;
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

    /**
     * If this item's layout contains a container which holds the icon and active indicator, return
     * the container. Otherwise, return the icon image view.
     * <p>
     * This is needed for clients who subclass this view and set their own item layout resource which
     * might not container an icon container or active indicator view.
     */
    private View getIconOrContainer() {
        return icon;
    }

    public void setItemPosition(int position) {
        itemPosition = position;
    }

    public int getItemPosition() {
        return itemPosition;
    }

    @Override
    @Nullable
    public MenuItemImpl getItemData() {
        return itemData;
    }

    @Override
    public void setTitle(@Nullable CharSequence title) {
        label.setText(title);
        if (itemData == null || TextUtils.isEmpty(itemData.getContentDescription())) {
            setContentDescription(title);
        }

        CharSequence tooltipText = itemData == null || TextUtils.isEmpty(itemData.getTooltipText())
                ? title
                : itemData.getTooltipText();
        // Avoid calling tooltip for L and M devices because long pressing twice may freeze devices.
        if (VERSION.SDK_INT > VERSION_CODES.M) {
            TooltipCompat.setTooltipText(this, tooltipText);
        }
    }

    @Override
    public void setCheckable(boolean checkable) {
        refreshDrawableState();
    }

    @Override
    public void setChecked(boolean checked) {
        label.setPivotX(label.getWidth() / 2F);
        label.setPivotY(label.getBaseline());

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
        label.setEnabled(enabled);
        icon.setEnabled(enabled);

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
        if (activeBackgroundEnabled && itemData != null && itemData.isCheckable() && itemData.isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    public void setShortcut(boolean showShortcut, char shortcutKey) {
    }

    @Override
    public void setIcon(@Nullable Drawable iconDrawable) {
        if (iconDrawable == originalIconDrawable) {
            return;
        }

        // Save the original icon to check if it has changed in future calls of this method.
        originalIconDrawable = iconDrawable;
        if (iconDrawable != null) {
            Drawable.ConstantState state = iconDrawable.getConstantState();
            iconDrawable = DrawableCompat.wrap(state == null ? iconDrawable : state.newDrawable()).mutate();
            wrappedIconDrawable = iconDrawable;
            if (iconTint != null) {
                DrawableCompat.setTintList(wrappedIconDrawable, iconTint);
            }
        }
        this.icon.setImageDrawable(iconDrawable);
    }

    @Override
    public boolean prefersCondensedTitle() {
        return false;
    }

    @Override
    public boolean showsIcon() {
        return true;
    }

    public void setIconTintList(@Nullable ColorStateList tint) {
        iconTint = tint;
        if (itemData != null && wrappedIconDrawable != null) {
            DrawableCompat.setTintList(wrappedIconDrawable, iconTint);
            wrappedIconDrawable.invalidateSelf();
        }
    }

    public void setIconSize(int iconSize) {
        LinearLayoutCompat.LayoutParams iconParams = (LinearLayoutCompat.LayoutParams) icon.getLayoutParams();
        iconParams.width = iconSize;
        iconParams.height = iconSize;
        icon.setLayoutParams(iconParams);
    }

    public void setTextAppearanceInactive(@StyleRes int inactiveTextAppearance) {
        TextViewCompat.setTextAppearance(label, inactiveTextAppearance);
    }

    public void setTextAppearanceActive(@StyleRes int activeTextAppearance) {
        TextViewCompat.setTextAppearance(label, activeTextAppearance);
    }

    public void setTextColor(@Nullable ColorStateList color) {
        if (color != null) {
            label.setTextColor(color);
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
        this.activeBackgroundEnabled = enabled;
        requestLayout();
    }

    private int getSuggestedIconWidth() {
        LinearLayoutCompat.LayoutParams iconContainerParams = (LinearLayoutCompat.LayoutParams) getIconOrContainer().getLayoutParams();
        return iconContainerParams.leftMargin
                + icon.getMeasuredWidth()
                + iconContainerParams.rightMargin;
    }

    private int getSuggestedIconHeight() {
        LinearLayoutCompat.LayoutParams iconContainerParams = (LinearLayoutCompat.LayoutParams) getIconOrContainer().getLayoutParams();
        return iconContainerParams.topMargin + icon.getMeasuredWidth();
    }
}