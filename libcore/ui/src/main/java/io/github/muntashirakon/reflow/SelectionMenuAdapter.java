// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.reflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuView;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.widget.RecyclerView;

@SuppressLint({"RestrictedApi", "NotifyDataSetChanged"})
class SelectionMenuAdapter extends RecyclerView.Adapter<SelectionMenuAdapter.MenuItemHolder> implements MenuView {
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
    private static final int[] DISABLED_STATE_SET = {-android.R.attr.state_enabled};
    private static int[] EMPTY_STATE_SET;
    private static final int ITEM_POOL_SIZE = 5;

    private final Context mContext;
    @NonNull
    private final SparseArray<View.OnTouchListener> mOnTouchListeners = new SparseArray<>(ITEM_POOL_SIZE);

    @Nullable
    private ColorStateList mItemIconTint;
    @Dimension
    private int mItemIconSize;
    private ColorStateList mItemTextColorFromUser;
    @Nullable
    private final ColorStateList mItemTextColorDefault;
    @StyleRes
    private int mItemTextAppearanceInactive;
    @StyleRes
    private int mItemTextAppearanceActive;
    private Drawable mItemBackground;
    private int mItemBackgroundRes;
    private boolean mItemActiveBackgroundEnabled;

    private ReflowMenuPresenter mPresenter;
    private MenuBuilder mMenu;
    private final List<MenuItemImpl> mVisibleMenuItems = new ArrayList<>();

    public SelectionMenuAdapter(Context context, int[] EMPTY_STATE_SET) {
        SelectionMenuAdapter.EMPTY_STATE_SET = EMPTY_STATE_SET;
        mContext = context;
        mItemTextColorDefault = createDefaultColorStateList(android.R.attr.textColorSecondary);
    }

    public void setPresenter(ReflowMenuPresenter presenter) {
        mPresenter = presenter;
    }

    @Override
    public void initialize(@NonNull MenuBuilder menu) {
        mMenu = menu;
    }

    @Override
    public int getWindowAnimations() {
        return 0;
    }

    /**
     * Sets the tint which is applied to the menu items' icons.
     *
     * @param tint the tint to apply
     */
    public void setIconTintList(@Nullable ColorStateList tint) {
        mItemIconTint = tint;
        notifyDataSetChanged();
    }

    /**
     * Returns the tint which is applied for the menu item labels.
     *
     * @return the ColorStateList that is used to tint menu items' icons
     */
    @Nullable
    public ColorStateList getIconTintList() {
        return mItemIconTint;
    }

    /**
     * Sets the size to provide for the menu item icons.
     *
     * <p>For best image resolution, use an icon with the same size set in this method.
     *
     * @param iconSize the size to provide for the menu item icons in pixels
     */
    public void setItemIconSize(@Dimension int iconSize) {
        mItemIconSize = iconSize;
        notifyDataSetChanged();
    }

    /**
     * Returns the size in pixels provided for the menu item icons.
     */
    @Dimension
    public int getItemIconSize() {
        return mItemIconSize;
    }

    /**
     * Sets the text color to be used for the menu item labels.
     *
     * @param color the ColorStateList used for menu item labels
     */
    public void setItemTextColor(@Nullable ColorStateList color) {
        mItemTextColorFromUser = color;
        notifyDataSetChanged();
    }

    /**
     * Returns the text color used for menu item labels.
     *
     * @return the ColorStateList used for menu items labels
     */
    @Nullable
    public ColorStateList getItemTextColor() {
        return mItemTextColorFromUser;
    }

    /**
     * Sets the text appearance to be used for inactive menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for inactive menu item labels
     */
    public void setItemTextAppearanceInactive(@StyleRes int textAppearanceRes) {
        mItemTextAppearanceInactive = textAppearanceRes;
        notifyDataSetChanged();
    }

    /**
     * Returns the text appearance used for inactive menu item labels.
     *
     * @return the text appearance ID used for inactive menu item labels
     */
    @StyleRes
    public int getItemTextAppearanceInactive() {
        return mItemTextAppearanceInactive;
    }

    /**
     * Sets the text appearance to be used for the active menu item label.
     *
     * @param textAppearanceRes the text appearance ID used for the active menu item label
     */
    public void setItemTextAppearanceActive(@StyleRes int textAppearanceRes) {
        mItemTextAppearanceActive = textAppearanceRes;
        notifyDataSetChanged();
    }

    /**
     * Returns the text appearance used for the active menu item label.
     *
     * @return the text appearance ID used for the active menu item label
     */
    @StyleRes
    public int getItemTextAppearanceActive() {
        return mItemTextAppearanceActive;
    }

    /**
     * Sets the resource ID to be used for item backgrounds.
     *
     * @param background the resource ID of the background
     */
    public void setItemBackgroundRes(int background) {
        mItemBackgroundRes = background;
        notifyDataSetChanged();
    }

    /**
     * Returns the resource ID for the background of the menu items.
     *
     * @return the resource ID for the background
     * @deprecated Use {@link #getItemBackground()} instead.
     */
    @Deprecated
    public int getItemBackgroundRes() {
        return mItemBackgroundRes;
    }


    /**
     * Sets the drawable to be used for item backgrounds.
     *
     * @param background the drawable of the background
     */
    public void setItemBackground(@Nullable Drawable background) {
        mItemBackground = background;
        notifyDataSetChanged();
    }

    /**
     * Returns the drawable for the background of the menu items.
     *
     * @return the drawable for the background
     */
    @Nullable
    public Drawable getItemBackground() {
        return mItemBackground;
    }

    /**
     * Returns whether or not an active background is enabled for the navigation bar.
     *
     * @return true if the active background is enabled.
     */
    public boolean getItemActiveBackgroundEnabled() {
        return mItemActiveBackgroundEnabled;
    }

    /**
     * Set whether or not an active background is enabled for the navigation bar.
     *
     * @param enabled true if an active background should be shown.
     */
    public void setItemActiveBackgroundEnabled(boolean enabled) {
        mItemActiveBackgroundEnabled = enabled;
        notifyDataSetChanged();
    }

    /**
     * Sets an {@link android.view.View.OnTouchListener} for the item view associated with the
     * provided {@code menuItemId}.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setItemOnTouchListener(int menuItemId, @Nullable View.OnTouchListener onTouchListener) {
        if (onTouchListener == null) {
            mOnTouchListeners.remove(menuItemId);
        } else {
            mOnTouchListeners.put(menuItemId, onTouchListener);
        }
        notifyDataSetChanged();
    }

    public void buildMenuView() {
        updateMenuView();
    }

    public void updateMenuView() {
        mVisibleMenuItems.clear();
        if (mMenu != null) {
            for (int i = 0; i < mMenu.size(); ++i) {
                MenuItemImpl menuItem = (MenuItemImpl) mMenu.getItem(i);
                if (menuItem.isVisible()) {
                    mVisibleMenuItems.add(menuItem);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    private ReflowMenuItemView getNewItem() {
        ReflowMenuItemView item = new ReflowMenuItemView(mContext);
        item.setIconTintList(mItemIconTint);
        item.setIconSize(mItemIconSize);
        // Set the text color the default, then look for another text color in order of precedence.
        item.setTextColor(mItemTextColorDefault);
        item.setTextAppearanceInactive(mItemTextAppearanceInactive);
        item.setTextAppearanceActive(mItemTextAppearanceActive);
        item.setTextColor(mItemTextColorFromUser);
        item.setActiveBackgroundEnabled(mItemActiveBackgroundEnabled);
        if (mItemBackground != null) {
            item.setItemBackground(mItemBackground);
        } else {
            item.setItemBackground(mItemBackgroundRes);
        }
        return item;
    }

    @NonNull
    @Override
    public MenuItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MenuItemHolder(getNewItem());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull MenuItemHolder holder, int position) {
        MenuItemImpl menuItem = mVisibleMenuItems.get(position);
        ReflowMenuItemView menuItemView = holder.itemView;
        menuItemView.initialize(menuItem, 0);
        int itemId = menuItem.getItemId();
        menuItemView.setOnTouchListener(mOnTouchListeners.get(itemId));
        menuItemView.setOnClickListener(v -> mMenu.performItemAction(menuItem, mPresenter, 0));
    }

    @Override
    public int getItemCount() {
        return mVisibleMenuItems.size();
    }

    @Nullable
    public ColorStateList createDefaultColorStateList(int baseColorThemeAttr) {
        final TypedValue value = new TypedValue();
        if (!mContext.getTheme().resolveAttribute(baseColorThemeAttr, value, true)) {
            return null;
        }
        ColorStateList baseColor = AppCompatResources.getColorStateList(mContext, value.resourceId);
        if (!mContext
                .getTheme()
                .resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true)) {
            return null;
        }
        int colorPrimary = value.data;
        int defaultColor = baseColor.getDefaultColor();
        return new ColorStateList(
                new int[][]{DISABLED_STATE_SET, CHECKED_STATE_SET, EMPTY_STATE_SET},
                new int[]{
                        baseColor.getColorForState(DISABLED_STATE_SET, defaultColor), colorPrimary, defaultColor
                });
    }

    public static class MenuItemHolder extends RecyclerView.ViewHolder {
        final ReflowMenuItemView itemView;

        public MenuItemHolder(@NonNull ReflowMenuItemView itemView) {
            super(itemView);
            this.itemView = itemView;
        }
    }
}
