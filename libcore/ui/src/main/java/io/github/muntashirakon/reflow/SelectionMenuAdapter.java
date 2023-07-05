// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.reflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
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

import io.github.muntashirakon.widget.RecyclerView;

@SuppressLint("RestrictedApi")
public class SelectionMenuAdapter extends RecyclerView.Adapter<SelectionMenuAdapter.MenuItemHolder> implements MenuView {
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
    private static final int[] DISABLED_STATE_SET = {-android.R.attr.state_enabled};
    private static int[] EMPTY_STATE_SET;
    private static final int ITEM_POOL_SIZE = 5;

    private final Context mContext;
    @NonNull
    private final SparseArray<View.OnTouchListener> onTouchListeners = new SparseArray<>(ITEM_POOL_SIZE);

    @Nullable
    private ColorStateList itemIconTint;
    @Dimension
    private int itemIconSize;
    private ColorStateList itemTextColorFromUser;
    @Nullable
    private final ColorStateList itemTextColorDefault;
    @StyleRes
    private int itemTextAppearanceInactive;
    @StyleRes
    private int itemTextAppearanceActive;
    private Drawable itemBackground;
    private int itemBackgroundRes;
    private boolean itemActiveBackgroundEnabled;

    private ReflowMenuPresenter presenter;
    private MenuBuilder menu;
    private int selectedItemId = 0;
    private int selectedItemPosition = 0;

    public SelectionMenuAdapter(Context context, int[] EMPTY_STATE_SET) {
        SelectionMenuAdapter.EMPTY_STATE_SET = EMPTY_STATE_SET;
        mContext = context;
        itemTextColorDefault = createDefaultColorStateList(android.R.attr.textColorSecondary);
    }

    public void setPresenter(ReflowMenuPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void initialize(@NonNull MenuBuilder menu) {
        this.menu = menu;
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
        itemIconTint = tint;
        notifyDataSetChanged();
    }

    /**
     * Returns the tint which is applied for the menu item labels.
     *
     * @return the ColorStateList that is used to tint menu items' icons
     */
    @Nullable
    public ColorStateList getIconTintList() {
        return itemIconTint;
    }

    /**
     * Sets the size to provide for the menu item icons.
     *
     * <p>For best image resolution, use an icon with the same size set in this method.
     *
     * @param iconSize the size to provide for the menu item icons in pixels
     */
    public void setItemIconSize(@Dimension int iconSize) {
        this.itemIconSize = iconSize;
        notifyDataSetChanged();
    }

    /**
     * Returns the size in pixels provided for the menu item icons.
     */
    @Dimension
    public int getItemIconSize() {
        return itemIconSize;
    }

    /**
     * Sets the text color to be used for the menu item labels.
     *
     * @param color the ColorStateList used for menu item labels
     */
    public void setItemTextColor(@Nullable ColorStateList color) {
        itemTextColorFromUser = color;
        notifyDataSetChanged();
    }

    /**
     * Returns the text color used for menu item labels.
     *
     * @return the ColorStateList used for menu items labels
     */
    @Nullable
    public ColorStateList getItemTextColor() {
        return itemTextColorFromUser;
    }

    /**
     * Sets the text appearance to be used for inactive menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for inactive menu item labels
     */
    public void setItemTextAppearanceInactive(@StyleRes int textAppearanceRes) {
        this.itemTextAppearanceInactive = textAppearanceRes;
        notifyDataSetChanged();
    }

    /**
     * Returns the text appearance used for inactive menu item labels.
     *
     * @return the text appearance ID used for inactive menu item labels
     */
    @StyleRes
    public int getItemTextAppearanceInactive() {
        return itemTextAppearanceInactive;
    }

    /**
     * Sets the text appearance to be used for the active menu item label.
     *
     * @param textAppearanceRes the text appearance ID used for the active menu item label
     */
    public void setItemTextAppearanceActive(@StyleRes int textAppearanceRes) {
        this.itemTextAppearanceActive = textAppearanceRes;
        notifyDataSetChanged();
    }

    /**
     * Returns the text appearance used for the active menu item label.
     *
     * @return the text appearance ID used for the active menu item label
     */
    @StyleRes
    public int getItemTextAppearanceActive() {
        return itemTextAppearanceActive;
    }

    /**
     * Sets the resource ID to be used for item backgrounds.
     *
     * @param background the resource ID of the background
     */
    public void setItemBackgroundRes(int background) {
        itemBackgroundRes = background;
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
        return itemBackgroundRes;
    }


    /**
     * Sets the drawable to be used for item backgrounds.
     *
     * @param background the drawable of the background
     */
    public void setItemBackground(@Nullable Drawable background) {
        itemBackground = background;
        notifyDataSetChanged();
    }

    /**
     * Returns the drawable for the background of the menu items.
     *
     * @return the drawable for the background
     */
    @Nullable
    public Drawable getItemBackground() {
        return itemBackground;
    }

    /**
     * Returns whether or not an active background is enabled for the navigation bar.
     *
     * @return true if the active background is enabled.
     */
    public boolean getItemActiveBackgroundEnabled() {
        return itemActiveBackgroundEnabled;
    }

    /**
     * Set whether or not an active background is enabled for the navigation bar.
     *
     * @param enabled true if an active background should be shown.
     */
    public void setItemActiveBackgroundEnabled(boolean enabled) {
        this.itemActiveBackgroundEnabled = enabled;
        notifyDataSetChanged();
    }

    /**
     * Sets an {@link android.view.View.OnTouchListener} for the item view associated with the
     * provided {@code menuItemId}.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setItemOnTouchListener(int menuItemId, @Nullable View.OnTouchListener onTouchListener) {
        if (onTouchListener == null) {
            onTouchListeners.remove(menuItemId);
        } else {
            onTouchListeners.put(menuItemId, onTouchListener);
        }
        if (menu != null) {
            for (int i = 0; i < menu.size(); ++i) {
                MenuItemImpl menuItem = (MenuItemImpl) menu.getItem(i);
                if (menuItem.getItemId() == menuItemId) {
                    notifyItemChanged(i);
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void buildMenuView() {
        if (menu.size() == 0) {
            selectedItemId = 0;
            selectedItemPosition = 0;
            return;
        }

        for (int i = 0; i < menu.size(); i++) {
            presenter.setUpdateSuspended(true);
            MenuItemImpl menuItem = (MenuItemImpl) menu.getItem(i);
            menuItem.setCheckable(true);
            presenter.setUpdateSuspended(false);
            if (selectedItemId != Menu.NONE && menuItem.getItemId() == selectedItemId) {
                selectedItemPosition = i;
            }
        }
        selectedItemPosition = Math.min(menu.size() - 1, selectedItemPosition);
        menu.getItem(selectedItemPosition).setChecked(true);

        notifyDataSetChanged();
    }

    public void updateMenuView() {
        if (menu == null) {
            return;
        }

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.isChecked()) {
                selectedItemId = item.getItemId();
                selectedItemPosition = i;
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    private ReflowMenuItemView getNewItem() {
        ReflowMenuItemView item = new ReflowMenuItemView(mContext);
        item.setIconTintList(itemIconTint);
        item.setIconSize(itemIconSize);
        // Set the text color the default, then look for another text color in order of precedence.
        item.setTextColor(itemTextColorDefault);
        item.setTextAppearanceInactive(itemTextAppearanceInactive);
        item.setTextAppearanceActive(itemTextAppearanceActive);
        item.setTextColor(itemTextColorFromUser);
        item.setActiveBackgroundEnabled(itemActiveBackgroundEnabled);
        if (itemBackground != null) {
            item.setItemBackground(itemBackground);
        } else {
            item.setItemBackground(itemBackgroundRes);
        }
        return item;
    }

    public int getSelectedItemId() {
        return selectedItemId;
    }

    void tryRestoreSelectedItemId(int itemId) {
        final int size = menu.size();
        for (int i = 0; i < size; i++) {
            MenuItem item = menu.getItem(i);
            if (itemId == item.getItemId()) {
                selectedItemId = itemId;
                selectedItemPosition = i;
                item.setChecked(true);
                break;
            }
        }
    }

    @NonNull
    @Override
    public MenuItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MenuItemHolder(getNewItem());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull MenuItemHolder holder, int position) {
        MenuItemImpl menuItem = (MenuItemImpl) menu.getItem(position);
        ReflowMenuItemView menuItemView = holder.itemView;
        presenter.setUpdateSuspended(true);
        menuItemView.initialize(menuItem, 0);
        presenter.setUpdateSuspended(false);
        menuItemView.setItemPosition(position);
        int itemId = menuItem.getItemId();
        menuItemView.setOnTouchListener(onTouchListeners.get(itemId));
        menuItemView.setOnClickListener(v -> {
            if (!menu.performItemAction(menuItem, presenter, 0)) {
                menuItem.setChecked(true);
                notifyItemChanged(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return menu != null ? menu.size() : 0;
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
