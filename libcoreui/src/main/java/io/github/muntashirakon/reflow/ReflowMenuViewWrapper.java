// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.reflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.AttrRes;
import androidx.annotation.DimenRes;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.TintTypedArray;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.internal.ViewUtils;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.MaterialShapeUtils;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.libcoreui.R;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

/**
 * <p>The bar contents can be populated by specifying a menu resource file. Each menu item title,
 * icon and enabled state will be used for displaying navigation bar items. Menu items can also be
 * used for programmatically selecting which destination is currently active. It can be done using
 * {@code MenuItem#setChecked(true)}
 */
// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public abstract class ReflowMenuViewWrapper extends LinearLayoutCompat {

    /**
     * Label behaves as "labeled" when there are 3 items or less, or "selected" when there are 4 items
     * or more.
     */
    public static final int LABEL_VISIBILITY_AUTO = -1;

    /**
     * Label is shown on the selected navigation item.
     */
    public static final int LABEL_VISIBILITY_SELECTED = 0;

    /**
     * Label is shown on all navigation items.
     */
    public static final int LABEL_VISIBILITY_LABELED = 1;

    /**
     * Label is not shown on any navigation items.
     */
    public static final int LABEL_VISIBILITY_UNLABELED = 2;

    /**
     * Menu Label visibility mode enum for component provide an implementation of navigation bar view.
     *
     * <p>The label visibility mode determines whether to show or hide labels in the navigation items.
     * Setting the label visibility mode to {@link ReflowMenuViewWrapper#LABEL_VISIBILITY_SELECTED} sets
     * the label to only show when selected, setting it to {@link
     * ReflowMenuViewWrapper#LABEL_VISIBILITY_LABELED} sets the label to always show, and {@link
     * ReflowMenuViewWrapper#LABEL_VISIBILITY_UNLABELED} sets the label to never show.
     *
     * <p>Setting the label visibility mode to {@link ReflowMenuViewWrapper#LABEL_VISIBILITY_AUTO} sets
     * the label to behave as "labeled" when there are 3 items or less, or "selected" when there are 4
     * items or more.
     */
    @IntDef(value = {
            LABEL_VISIBILITY_AUTO,
            LABEL_VISIBILITY_SELECTED,
            LABEL_VISIBILITY_LABELED,
            LABEL_VISIBILITY_UNLABELED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LabelVisibility {
    }

    private static final int MENU_PRESENTER_ID = 1;

    @NonNull
    private final ReflowMenu menu;
    @NonNull
    private final ReflowMenuView menuView;
    @NonNull
    private final ReflowMenuPresenter presenter = new ReflowMenuPresenter();
    private MenuInflater menuInflater;

    private OnItemSelectedListener selectedListener;
    private OnItemReselectedListener reselectedListener;

    public ReflowMenuViewWrapper(
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
        this.menu = new ReflowMenu(context, this.getClass(), getMaxItemCount());

        // Create the menu view.
        menuView = createNavigationBarMenuView(context);

        presenter.setMenuView(menuView);
        presenter.setId(MENU_PRESENTER_ID);
        menuView.setPresenter(presenter);
        this.menu.addMenuPresenter(presenter);
        presenter.initForMenu(getContext(), this.menu);

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemIconTint)) {
            menuView.setIconTintList(attributes.getColorStateList(R.styleable.ReflowMenuViewWrapper_itemIconTint));
        } else {
            menuView.setIconTintList(menuView.createDefaultColorStateList(android.R.attr.textColorSecondary));
        }

        setItemIconSize(attributes.getDimensionPixelSize(
                R.styleable.ReflowMenuViewWrapper_itemIconSize,
                getResources().getDimensionPixelSize(R.dimen.mtrl_navigation_bar_item_default_icon_size)));

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

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemPaddingTop)) {
            setItemPaddingTop(
                    attributes.getDimensionPixelSize(R.styleable.ReflowMenuViewWrapper_itemPaddingTop, 0));
        }

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_itemPaddingBottom)) {
            setItemPaddingBottom(
                    attributes.getDimensionPixelSize(R.styleable.ReflowMenuViewWrapper_itemPaddingBottom, 0));
        }

        if (attributes.hasValue(R.styleable.ReflowMenuViewWrapper_elevation)) {
            setElevation(attributes.getDimensionPixelSize(R.styleable.ReflowMenuViewWrapper_elevation, 0));
        }

        ColorStateList backgroundTint = MaterialResources.getColorStateList(
                context, attributes, R.styleable.ReflowMenuViewWrapper_backgroundTint);
        DrawableCompat.setTintList(getBackground().mutate(), backgroundTint);

        setLabelVisibilityMode(attributes.getInteger(R.styleable.ReflowMenuViewWrapper_labelVisibilityMode,
                ReflowMenuViewWrapper.LABEL_VISIBILITY_AUTO));

        int itemBackground = attributes.getResourceId(R.styleable.ReflowMenuViewWrapper_itemBackground, 0);
        if (itemBackground != 0) {
            menuView.setItemBackgroundRes(itemBackground);
        }

        int activeIndicatorStyleResId =
                attributes.getResourceId(R.styleable.ReflowMenuViewWrapper_itemActiveIndicatorStyle, 0);

        if (activeIndicatorStyleResId != 0) {
            setItemActiveIndicatorEnabled(true);

            @SuppressLint("CustomViewStyleable") TypedArray activeIndicatorAttributes =
                    context.obtainStyledAttributes(activeIndicatorStyleResId, R.styleable.ReflowMenuViewActiveIndicator);

            int itemActiveIndicatorWidth = activeIndicatorAttributes.getDimensionPixelSize(
                    R.styleable.ReflowMenuViewActiveIndicator_android_width, 0);
            setItemActiveIndicatorWidth(itemActiveIndicatorWidth);

            int itemActiveIndicatorHeight = activeIndicatorAttributes.getDimensionPixelSize(
                    R.styleable.ReflowMenuViewActiveIndicator_android_height, 0);
            setItemActiveIndicatorHeight(itemActiveIndicatorHeight);

            int itemActiveIndicatorMarginHorizontal = activeIndicatorAttributes.getDimensionPixelOffset(
                    R.styleable.ReflowMenuViewActiveIndicator_marginHorizontal, 0);
            setItemActiveIndicatorMarginHorizontal(itemActiveIndicatorMarginHorizontal);

            ColorStateList itemActiveIndicatorColor = MaterialResources.getColorStateList(
                    context, activeIndicatorAttributes, R.styleable.ReflowMenuViewActiveIndicator_android_color);
            setItemActiveIndicatorColor(itemActiveIndicatorColor);

            int shapeAppearanceResId = activeIndicatorAttributes.getResourceId(
                    R.styleable.ReflowMenuViewActiveIndicator_shapeAppearance, 0);
            ShapeAppearanceModel itemActiveIndicatorShapeAppearance = ShapeAppearanceModel.builder(
                    context, shapeAppearanceResId, 0).build();
            setItemActiveIndicatorShapeAppearance(itemActiveIndicatorShapeAppearance);

            activeIndicatorAttributes.recycle();
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

        applyWindowInsets();
    }

    private void applyWindowInsets() {
        ViewUtils.doOnApplyWindowInsets(this, (view, insets, initialPadding) -> {
            // Window insets may add additional padding, e.g., to dodge the system navigation bar
            initialPadding.bottom += insets.getSystemWindowInsetBottom();

            boolean isRtl = ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL;
            int systemWindowInsetLeft = insets.getSystemWindowInsetLeft();
            int systemWindowInsetRight = insets.getSystemWindowInsetRight();
            initialPadding.start += isRtl ? systemWindowInsetRight : systemWindowInsetLeft;
            initialPadding.end += isRtl ? systemWindowInsetLeft : systemWindowInsetRight;
            initialPadding.applyToView(view);
            return insets;
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
        return menuView;
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
        return menuView.getIconTintList();
    }

    /**
     * Set the tint which is applied to our menu items' icons.
     *
     * @param tint the tint to apply.
     * @see R.styleable#ReflowMenuViewWrapper_itemIconTint
     */
    public void setItemIconTintList(@Nullable ColorStateList tint) {
        menuView.setIconTintList(tint);
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
        menuView.setItemIconSize(iconSize);
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
        return menuView.getItemIconSize();
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
        return menuView.getItemTextColor();
    }

    /**
     * Set the colors to use for the different states (normal, selected, focused, etc.) of the menu
     * item text.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemTextColor
     * @see #getItemTextColor()
     */
    public void setItemTextColor(@Nullable ColorStateList textColor) {
        menuView.setItemTextColor(textColor);
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
        return menuView.getItemBackgroundRes();
    }

    /**
     * Set the background of our menu items to the given resource.
     *
     * @param resId The identifier of the resource.
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     */
    public void setItemBackgroundResource(@DrawableRes int resId) {
        menuView.setItemBackgroundRes(resId);
    }

    /**
     * Returns the background drawable of the menu items.
     *
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     * @see #setItemBackground(Drawable)
     */
    @Nullable
    public Drawable getItemBackground() {
        return menuView.getItemBackground();
    }

    /**
     * Set the background of our menu items to the given drawable.
     *
     * @param background The drawable for the background.
     * @see R.styleable#ReflowMenuViewWrapper_itemBackground
     */
    public void setItemBackground(@Nullable Drawable background) {
        menuView.setItemBackground(background);
    }

    /**
     * Get the distance from the top of an item's icon/active indicator to the top of the navigation
     * bar item.
     */
    @Px
    public int getItemPaddingTop() {
        return menuView.getItemPaddingTop();
    }

    /**
     * Set the distance from the top of an items icon/active indicator to the top of the navigation
     * bar item.
     */
    public void setItemPaddingTop(@Px int paddingTop) {
        menuView.setItemPaddingTop(paddingTop);
    }

    /**
     * Get the distance from the bottom of an item's label to the bottom of the navigation bar item.
     */
    @Px
    public int getItemPaddingBottom() {
        return menuView.getItemPaddingBottom();
    }

    /**
     * Set the distance from the bottom of an item's label to the bottom of the navigation bar item.
     */
    public void setItemPaddingBottom(@Px int paddingBottom) {
        menuView.setItemPaddingBottom(paddingBottom);
    }

    /**
     * Get whether or not a selected item should show an active indicator.
     *
     * @return true if an active indicator will be shown when an item is selected.
     */
    public boolean isItemActiveIndicatorEnabled() {
        return menuView.getItemActiveIndicatorEnabled();
    }

    /**
     * Set whether a selected item should show an active indicator.
     *
     * @param enabled true if a selected item should show an active indicator.
     */
    public void setItemActiveIndicatorEnabled(boolean enabled) {
        menuView.setItemActiveIndicatorEnabled(enabled);
    }

    /**
     * Get whether or not a selected item should show an active background.
     *
     * @return true if an active background will be shown when an item is selected.
     */
    public boolean isItemActiveBackgroundEnabled() {
        return menuView.getItemActiveBackgroundEnabled();
    }

    /**
     * Set whether a selected item should show an active background.
     *
     * @param enabled true if a selected item should show an active background.
     */
    public void setItemActiveBackgroundEnabled(boolean enabled) {
        menuView.setItemActiveBackgroundEnabled(enabled);
    }

    /**
     * Get the width of an item's active indicator.
     *
     * @return The width, in pixels, of a menu item's active indicator.
     */
    @Px
    public int getItemActiveIndicatorWidth() {
        return menuView.getItemActiveIndicatorWidth();
    }

    /**
     * Set the width of an item's active indicator.
     *
     * @param width The width, in pixels, of the menu item's active indicator.
     */
    public void setItemActiveIndicatorWidth(@Px int width) {
        menuView.setItemActiveIndicatorWidth(width);
    }

    /**
     * Get the width of an item's active indicator.
     *
     * @return The width, in pixels, of a menu item's active indicator.
     */
    @Px
    public int getItemActiveIndicatorHeight() {
        return menuView.getItemActiveIndicatorHeight();
    }

    /**
     * Set the height of an item's active indicator.
     *
     * @param height The height, in pixels, of the menu item's active indicator.
     */
    public void setItemActiveIndicatorHeight(@Px int height) {
        menuView.setItemActiveIndicatorHeight(height);
    }

    /**
     * Get the margin that will be maintained at the start and end of the active indicator away from
     * the edges of its parent container.
     *
     * @return The horizontal margin, in pixels.
     */
    @Px
    public int getItemActiveIndicatorMarginHorizontal() {
        return menuView.getItemActiveIndicatorMarginHorizontal();
    }

    /**
     * Set the horizontal margin that will be maintained at the start and end of the active indicator,
     * making sure the indicator remains the given distance from the edge of its parent container.
     *
     * @param horizontalMargin The horizontal margin, in pixels.
     */
    public void setItemActiveIndicatorMarginHorizontal(@Px int horizontalMargin) {
        menuView.setItemActiveIndicatorMarginHorizontal(horizontalMargin);
    }

    /**
     * Get the {@link ShapeAppearanceModel} of the active indicator drawable.
     *
     * @return The {@link ShapeAppearanceModel} of the active indicator drawable.
     */
    @Nullable
    public ShapeAppearanceModel getItemActiveIndicatorShapeAppearance() {
        return menuView.getItemActiveIndicatorShapeAppearance();
    }

    /**
     * Set the {@link ShapeAppearanceModel} of the active indicator drawable.
     *
     * @param shapeAppearance The {@link ShapeAppearanceModel} of the active indicator drawable.
     */
    public void setItemActiveIndicatorShapeAppearance(
            @Nullable ShapeAppearanceModel shapeAppearance) {
        menuView.setItemActiveIndicatorShapeAppearance(shapeAppearance);
    }

    /**
     * Get the color of the active indicator drawable.
     *
     * @return A {@link ColorStateList} used as the color of the active indicator.
     */
    @Nullable
    public ColorStateList getItemActiveIndicatorColor() {
        return menuView.getItemActiveIndicatorColor();
    }

    /**
     * Set the {@link ColorStateList} of the active indicator drawable.
     *
     * @param csl The {@link ColorStateList} used as the color of the active indicator.
     */
    public void setItemActiveIndicatorColor(@Nullable ColorStateList csl) {
        menuView.setItemActiveIndicatorColor(csl);
    }

    /**
     * Returns the currently selected menu item ID, or zero if there is no menu.
     *
     * @see #setSelectedItemId(int)
     */
    @IdRes
    public int getSelectedItemId() {
        return menuView.getSelectedItemId();
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
     * Sets the navigation items' label visibility mode.
     *
     * <p>The label is either always shown, never shown, or only shown when activated. Also supports
     * "auto" mode, which uses the item count to determine whether to show or hide the label.
     *
     * @param labelVisibilityMode mode which decides whether or not the label should be shown. Can be
     *                            one of {@link ReflowMenuViewWrapper#LABEL_VISIBILITY_AUTO}, {@link
     *                            ReflowMenuViewWrapper#LABEL_VISIBILITY_SELECTED}, {@link
     *                            ReflowMenuViewWrapper#LABEL_VISIBILITY_LABELED}, or {@link
     *                            ReflowMenuViewWrapper#LABEL_VISIBILITY_UNLABELED}
     * @see R.styleable#ReflowMenuViewWrapper_labelVisibilityMode
     * @see #getLabelVisibilityMode()
     */
    public void setLabelVisibilityMode(@LabelVisibility int labelVisibilityMode) {
        if (menuView.getLabelVisibilityMode() != labelVisibilityMode) {
            menuView.setLabelVisibilityMode(labelVisibilityMode);
            presenter.updateMenuView(false);
        }
    }

    /**
     * Returns the current label visibility mode used by this {@link ReflowMenuViewWrapper}.
     *
     * @see R.styleable#ReflowMenuViewWrapper_labelVisibilityMode
     * @see #setLabelVisibilityMode(int)
     */
    @ReflowMenuViewWrapper.LabelVisibility
    public int getLabelVisibilityMode() {
        return menuView.getLabelVisibilityMode();
    }

    /**
     * Sets the text appearance to be used for inactive menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for inactive menu item labels
     */
    public void setItemTextAppearanceInactive(@StyleRes int textAppearanceRes) {
        menuView.setItemTextAppearanceInactive(textAppearanceRes);
    }

    /**
     * Returns the text appearance used for inactive menu item labels.
     *
     * @return the text appearance ID used for inactive menu item labels
     */
    @StyleRes
    public int getItemTextAppearanceInactive() {
        return menuView.getItemTextAppearanceInactive();
    }

    /**
     * Sets the text appearance to be used for the menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for menu item labels
     */
    public void setItemTextAppearanceActive(@StyleRes int textAppearanceRes) {
        menuView.setItemTextAppearanceActive(textAppearanceRes);
    }

    /**
     * Returns the text appearance used for the active menu item label.
     *
     * @return the text appearance ID used for the active menu item label
     */
    @StyleRes
    public int getItemTextAppearanceActive() {
        return menuView.getItemTextAppearanceActive();
    }

    /**
     * Sets an {@link android.view.View.OnTouchListener} for the item view associated with the
     * provided {@code menuItemId}.
     */
    public void setItemOnTouchListener(int menuItemId, @Nullable OnTouchListener onTouchListener) {
        menuView.setItemOnTouchListener(menuItemId, onTouchListener);
    }

    /**
     * Returns an instance of {@link BadgeDrawable} associated with {@code menuItemId}, null if none
     * was initialized.
     *
     * @param menuItemId Id of the menu item.
     * @return an instance of BadgeDrawable associated with {@code menuItemId} or null.
     * @see #getOrCreateBadge(int)
     */
    @Nullable
    public BadgeDrawable getBadge(int menuItemId) {
        return menuView.getBadge(menuItemId);
    }

    /**
     * Creates an instance of {@link BadgeDrawable} associated with {@code menuItemId} if none exists.
     * Initializes (if needed) and returns the associated instance of {@link BadgeDrawable} associated
     * with {@code menuItemId}.
     *
     * @param menuItemId Id of the menu item.
     * @return an instance of BadgeDrawable associated with {@code menuItemId}.
     */
    @NonNull
    public BadgeDrawable getOrCreateBadge(int menuItemId) {
        return menuView.getOrCreateBadge(menuItemId);
    }

    /**
     * Removes the {@link BadgeDrawable} associated with {@code menuItemId}. Do nothing if none
     * exists. Consider changing the visibility of the {@link BadgeDrawable} if you only want to hide
     * it temporarily.
     *
     * @param menuItemId Id of the menu item.
     */
    public void removeBadge(int menuItemId) {
        menuView.removeBadge(menuItemId);
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

    /**
     * Returns the maximum number of items that can be shown in ReflowMenuViewWrapper.
     */
    public abstract int getMaxItemCount();

    /**
     * Returns reference to a newly created {@link ReflowMenuView}
     */
    @NonNull
    protected abstract ReflowMenuView createNavigationBarMenuView(@NonNull Context context);

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