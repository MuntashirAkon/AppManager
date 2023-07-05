// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.reflow;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.DimenRes;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.TintTypedArray;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.MaterialShapeUtils;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

/**
 * <p>The bar contents can be populated by specifying a menu resource file. Each menu item title,
 * icon and enabled state will be used for displaying navigation bar items. Menu items can also be
 * used for programmatically selecting which destination is currently active. It can be done using
 * {@code MenuItem#setChecked(true)}
 */
// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public class SelectionActionsView extends LinearLayoutCompat {
    private static final int MENU_PRESENTER_ID = 1;
    private static final int MIN_COLUMN_WIDTH_DP = 80;
    private static final int DEFAULT_COLUMN_SIZE = 5;

    @NonNull
    private final ReflowMenu menu;
    @NonNull
    private final RecyclerView menuView;
    private final SelectionMenuAdapter menuAdapter;
    @NonNull
    private final ReflowMenuPresenter presenter = new ReflowMenuPresenter();
    private MenuInflater menuInflater;

    private OnItemSelectedListener selectedListener;
    private OnItemReselectedListener reselectedListener;

    public SelectionActionsView(Context context) {
        this(context, null);
    }

    public SelectionActionsView(Context context, AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.bottomNavigationStyle);
    }

    public SelectionActionsView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        this(context, attrs, defStyleAttr, com.google.android.material.R.style.Widget_Design_BottomNavigationView);
    }

    public SelectionActionsView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr,
            @StyleRes int defStyleRes) {
        super(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr);

        // Ensure we are using the correctly themed context rather than the context that was passed in.
        context = getContext();

        /* Custom attributes */
        TintTypedArray attributes =
                ThemeEnforcement.obtainTintedStyledAttributes(
                        context,
                        attrs,
                        R.styleable.ReflowMenuViewWrapper,
                        defStyleAttr,
                        defStyleRes,
                        R.styleable.ReflowMenuViewWrapper_itemTextAppearanceInactive,
                        R.styleable.ReflowMenuViewWrapper_itemTextAppearanceActive);

        // Create the menu.
        this.menu = new ReflowMenu(context, this.getClass());

        // Create the menu view.
        menuView = createNavigationBarMenuView(context);
        menuView.setLayoutManager(new GridLayoutManager(context, UiUtils.getColumnCount(this, MIN_COLUMN_WIDTH_DP, DEFAULT_COLUMN_SIZE)));
        menuAdapter = new SelectionMenuAdapter(context, View.EMPTY_STATE_SET);
        menuView.setAdapter(menuAdapter);

        presenter.setMenuView(menuAdapter);
        presenter.setId(MENU_PRESENTER_ID);
        menuAdapter.setPresenter(presenter);
        this.menu.addMenuPresenter(presenter);
        presenter.initForMenu(getContext(), this.menu);

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemIconTint)) {
            menuAdapter.setIconTintList(attributes.getColorStateList(R.styleable.ReflowMenuViewWrapper_itemIconTint));
        } else {
            menuAdapter.setIconTintList(menuAdapter.createDefaultColorStateList(android.R.attr.textColorSecondary));
        }

        setItemIconSize(attributes.getDimensionPixelSize(
                R.styleable.ReflowMenuViewWrapper_itemIconSize,
                getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_navigation_bar_item_default_icon_size)));

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemTextAppearanceInactive)) {
            setItemTextAppearanceInactive(
                    attributes.getResourceId(R.styleable.ReflowMenuViewWrapper_itemTextAppearanceInactive, 0));
        }

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemTextAppearanceActive)) {
            setItemTextAppearanceActive(
                    attributes.getResourceId(R.styleable.ReflowMenuViewWrapper_itemTextAppearanceActive, 0));
        }

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemTextColor)) {
            setItemTextColor(attributes.getColorStateList(R.styleable.ReflowMenuViewWrapper_itemTextColor));
        }

        if (getBackground() == null || getBackground() instanceof ColorDrawable) {
            // Add a MaterialShapeDrawable as background that supports tinting in every API level.
            ViewCompat.setBackground(this, createMaterialShapeDrawableBackground(context));
        }

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_elevation)) {
            setElevation(attributes.getDimensionPixelSize(R.styleable.ReflowMenuViewWrapper_elevation, 0));
        }

        ColorStateList backgroundTint = MaterialResources.getColorStateList(
                context, attributes, R.styleable.ReflowMenuViewWrapper_backgroundTint);
        DrawableCompat.setTintList(getBackground().mutate(), backgroundTint);

        int itemBackground = attributes.getResourceId(R.styleable.ReflowMenuViewWrapper_itemBackground, 0);
        if (itemBackground != 0) {
            menuAdapter.setItemBackgroundRes(itemBackground);
        }

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_menu)) {
            inflateMenu(attributes.getResourceId(R.styleable.ReflowMenuViewWrapper_menu, 0));
        }

        attributes.recycle();

        addView(menuView);

        this.menu.setCallback(
                new MenuBuilder.Callback() {
                    @Override
                    public boolean onMenuItemSelected(@NonNull MenuBuilder menu, @NonNull MenuItem item) {
                        if (reselectedListener != null && item.getItemId() == getSelectedItemId()) {
                            reselectedListener.onNavigationItemReselected(item);
                            return true; // item is already selected
                        }
                        return selectedListener != null && !selectedListener.onNavigationItemSelected(item);
                    }

                    @Override
                    public void onMenuModeChange(@NonNull MenuBuilder menu) {
                    }
                });
    }

    @NonNull
    private MaterialShapeDrawable createMaterialShapeDrawableBackground(Context context) {
        MaterialShapeDrawable materialShapeDrawable = new MaterialShapeDrawable();
        Drawable originalBackground = getBackground();
        if (originalBackground instanceof ColorDrawable) {
            materialShapeDrawable.setFillColor(ColorStateList.valueOf(((ColorDrawable) originalBackground).getColor()));
        }
        materialShapeDrawable.initializeElevationOverlay(context);
        return materialShapeDrawable;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            menuView.setLayoutManager(new GridLayoutManager(getContext(), UiUtils.getColumnCount(this, MIN_COLUMN_WIDTH_DP, DEFAULT_COLUMN_SIZE)));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        MaterialShapeUtils.setParentAbsoluteElevation(this);
    }

    /**
     * Sets the base elevation of this view, in pixels.
     *
     * @see R.styleable#ReflowMenuViewWrapper_elevation
     */
    @Override
    public void setElevation(float elevation) {
        super.setElevation(elevation);
        MaterialShapeUtils.setElevation(this, elevation);
    }

    /**
     * Set a listener that will be notified when a navigation item is selected. This listener will
     * also be notified when the currently selected item is reselected, unless an {@link
     * OnItemReselectedListener} has also been set.
     *
     * @param listener The listener to notify
     * @see #setOnItemReselectedListener(OnItemReselectedListener)
     */
    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener listener) {
        selectedListener = listener;
    }

    /**
     * Set a listener that will be notified when the currently selected navigation item is reselected.
     * This does not require an {@link OnItemSelectedListener} to be set.
     *
     * @param listener The listener to notify
     * @see #setOnItemSelectedListener(OnItemSelectedListener)
     */
    public void setOnItemReselectedListener(@Nullable OnItemReselectedListener listener) {
        reselectedListener = listener;
    }

    /**
     * Returns the {@link Menu} instance associated with this navigation bar.
     */
    @NonNull
    public Menu getMenu() {
        return menu;
    }

    /**
     * Returns the {@link MenuView} instance associated with this navigation bar.
     */
    @NonNull
    public MenuView getMenuView() {
        return menuAdapter;
    }

    /**
     * Inflate a menu resource into this navigation view.
     *
     * <p>Existing items in the menu will not be modified or removed.
     *
     * @param resId ID of a menu resource to inflate
     */
    public void inflateMenu(int resId) {
        presenter.setUpdateSuspended(true);
        getMenuInflater().inflate(resId, menu);
        presenter.setUpdateSuspended(false);
        presenter.updateMenuView(true);
    }

    /**
     * Returns the tint which is applied to our menu items' icons.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemIconTint
     * @see #setItemIconTintList(ColorStateList)
     */
    @Nullable
    public ColorStateList getItemIconTintList() {
        return menuAdapter.getIconTintList();
    }

    /**
     * Set the tint which is applied to our menu items' icons.
     *
     * @param tint the tint to apply.
     * @see R.styleable#ReflowMenuViewWrapper_itemIconTint
     */
    public void setItemIconTintList(@Nullable ColorStateList tint) {
        menuAdapter.setIconTintList(tint);
    }

    /**
     * Set the size to provide for the menu item icons.
     *
     * <p>For best image resolution, use an icon with the same size set in this method.
     *
     * @param iconSize the size in pixels to provide for the menu item icons
     * @see R.styleable#ReflowMenuViewWrapper_itemIconSize
     */
    public void setItemIconSize(@Dimension int iconSize) {
        menuAdapter.setItemIconSize(iconSize);
    }

    /**
     * Set the size to provide for the menu item icons using a resource ID.
     *
     * <p>For best image resolution, use an icon with the same size set in this method.
     *
     * @param iconSizeRes the resource ID for the size to provide for the menu item icons
     * @see R.styleable#ReflowMenuViewWrapper_itemIconSize
     */
    public void setItemIconSizeRes(@DimenRes int iconSizeRes) {
        setItemIconSize(getResources().getDimensionPixelSize(iconSizeRes));
    }

    /**
     * Returns the size provided for the menu item icons in pixels.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemIconSize
     * @see #setItemIconSize(int)
     */
    @Dimension
    public int getItemIconSize() {
        return menuAdapter.getItemIconSize();
    }

    /**
     * Returns colors used for the different states (normal, selected, focused, etc.) of the menu item
     * text.
     *
     * @return the ColorStateList of colors used for the different states of the menu items text.
     * @see R.styleable#ReflowMenuViewWrapper_itemTextColor
     * @see #setItemTextColor(ColorStateList)
     */
    @Nullable
    public ColorStateList getItemTextColor() {
        return menuAdapter.getItemTextColor();
    }

    /**
     * Set the colors to use for the different states (normal, selected, focused, etc.) of the menu
     * item text.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemTextColor
     * @see #getItemTextColor()
     */
    public void setItemTextColor(@Nullable ColorStateList textColor) {
        menuAdapter.setItemTextColor(textColor);
    }

    /**
     * Returns the background resource of the menu items.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     * @see #setItemBackgroundResource(int)
     * @deprecated Use {@link #getItemBackground()} instead.
     */
    @Deprecated
    @DrawableRes
    public int getItemBackgroundResource() {
        return menuAdapter.getItemBackgroundRes();
    }

    /**
     * Set the background of our menu items to the given resource.
     *
     * @param resId The identifier of the resource.
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     */
    public void setItemBackgroundResource(@DrawableRes int resId) {
        menuAdapter.setItemBackgroundRes(resId);
    }

    /**
     * Returns the background drawable of the menu items.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     * @see #setItemBackground(Drawable)
     */
    @Nullable
    public Drawable getItemBackground() {
        return menuAdapter.getItemBackground();
    }

    /**
     * Set the background of our menu items to the given drawable.
     *
     * @param background The drawable for the background.
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     */
    public void setItemBackground(@Nullable Drawable background) {
        menuAdapter.setItemBackground(background);
    }

    /**
     * Get whether or not a selected item should show an active background.
     *
     * @return true if an active background will be shown when an item is selected.
     */
    public boolean isItemActiveBackgroundEnabled() {
        return menuAdapter.getItemActiveBackgroundEnabled();
    }

    /**
     * Set whether a selected item should show an active background.
     *
     * @param enabled true if a selected item should show an active background.
     */
    public void setItemActiveBackgroundEnabled(boolean enabled) {
        menuAdapter.setItemActiveBackgroundEnabled(enabled);
    }

    /**
     * Returns the currently selected menu item ID, or zero if there is no menu.
     *
     * @see #setSelectedItemId(int)
     */
    @IdRes
    public int getSelectedItemId() {
        return menuAdapter.getSelectedItemId();
    }

    /**
     * Set the selected menu item ID. This behaves the same as tapping on an item.
     *
     * @param itemId The menu item ID. If no item has this ID, the current selection is unchanged.
     * @see #getSelectedItemId()
     */
    public void setSelectedItemId(@IdRes int itemId) {
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            if (!menu.performItemAction(item, presenter, 0)) {
                item.setChecked(true);
            }
        }
    }

    /**
     * Sets the text appearance to be used for inactive menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for inactive menu item labels
     */
    public void setItemTextAppearanceInactive(@StyleRes int textAppearanceRes) {
        menuAdapter.setItemTextAppearanceInactive(textAppearanceRes);
    }

    /**
     * Returns the text appearance used for inactive menu item labels.
     *
     * @return the text appearance ID used for inactive menu item labels
     */
    @StyleRes
    public int getItemTextAppearanceInactive() {
        return menuAdapter.getItemTextAppearanceInactive();
    }

    /**
     * Sets the text appearance to be used for the menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for menu item labels
     */
    public void setItemTextAppearanceActive(@StyleRes int textAppearanceRes) {
        menuAdapter.setItemTextAppearanceActive(textAppearanceRes);
    }

    /**
     * Returns the text appearance used for the active menu item label.
     *
     * @return the text appearance ID used for the active menu item label
     */
    @StyleRes
    public int getItemTextAppearanceActive() {
        return menuAdapter.getItemTextAppearanceActive();
    }

    /**
     * Sets an {@link android.view.View.OnTouchListener} for the item view associated with the
     * provided {@code menuItemId}.
     */
    public void setItemOnTouchListener(int menuItemId, @Nullable OnTouchListener onTouchListener) {
        menuAdapter.setItemOnTouchListener(menuItemId, onTouchListener);
    }

    /**
     * Listener for handling selection events on navigation items.
     */
    public interface OnItemSelectedListener {

        /**
         * Called when an item in the navigation menu is selected.
         *
         * @param item The selected item
         * @return true to display the item as the selected item and false if the item should not be
         * selected. Consider setting non-selectable items as disabled preemptively to make them
         * appear non-interactive.
         */
        boolean onNavigationItemSelected(@NonNull MenuItem item);
    }

    /**
     * Listener for handling reselection events on navigation items.
     */
    public interface OnItemReselectedListener {

        /**
         * Called when the currently selected item in the navigation menu is selected again.
         *
         * @param item The selected item
         */
        void onNavigationItemReselected(@NonNull MenuItem item);
    }

    @NonNull
    protected RecyclerView createNavigationBarMenuView(@NonNull Context context) {
        RecyclerView view = new RecyclerView(context);
        LinearLayoutCompat.LayoutParams params =
                new LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
        return view;
    }

    private MenuInflater getMenuInflater() {
        if (menuInflater == null) {
            menuInflater = new SupportMenuInflater(getContext());
        }
        return menuInflater;
    }

    @NonNull
    protected ReflowMenuPresenter getPresenter() {
        return presenter;
    }

    @Override
    @NonNull
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.menuPresenterState = new Bundle();
        menu.savePresenterStates(savedState.menuPresenterState);
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(@Nullable Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        menu.restorePresenterStates(savedState.menuPresenterState);
    }

    static class SavedState extends AbsSavedState {
        @Nullable
        Bundle menuPresenterState;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(@NonNull Parcel source, ClassLoader loader) {
            super(source, loader);
            if (loader == null) {
                loader = getClass().getClassLoader();
            }
            readFromParcel(source, loader);
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeBundle(menuPresenterState);
        }

        private void readFromParcel(@NonNull Parcel in, ClassLoader loader) {
            menuPresenterState = in.readBundle(loader);
        }

        public static final Creator<SavedState> CREATOR =
                new ClassLoaderCreator<SavedState>() {
                    @NonNull
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @NonNull
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in) {
                        return new SavedState(in, null);
                    }

                    @NonNull
                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}