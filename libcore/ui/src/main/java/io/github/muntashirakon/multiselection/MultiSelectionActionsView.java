// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.multiselection;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.DimenRes;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TintTypedArray;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.customview.view.AbsSavedState;

import com.google.android.material.internal.ThemeEnforcement;
import com.google.android.material.resources.MaterialResources;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.MaterialShapeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

@SuppressLint({"RestrictedApi"})
@SuppressWarnings({"unused"})
public class MultiSelectionActionsView extends LinearLayoutCompat implements MenuView {
    private static final int MENU_PRESENTER_ID = 1;
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
    private static final int[] DISABLED_STATE_SET = {-android.R.attr.state_enabled};

    /**
     * Listener for handling selection events on options.
     */
    public interface OnItemSelectedListener {

        /**
         * Called when an item in the options menu is selected.
         *
         * @param item The selected item
         * @return Whether the menu item selection was handled.
         */
        boolean onNavigationItemSelected(@NonNull MenuItem item);
    }

    @NonNull
    private final MultiSelectionActionsMenu mMenu;
    private final List<MenuItemImpl> mVisibleMenuItems = new ArrayList<>();

    @NonNull
    private final MultiSelectionActionsMenuPresenter mPresenter = new MultiSelectionActionsMenuPresenter();
    private MenuInflater mMenuInflater;
    private MenuBuilder mMenuBuilder;
    @Nullable
    private OnItemSelectedListener mSelectedListener;

    @Nullable
    private final ColorStateList mItemTextColorDefault;
    @Nullable
    private ColorStateList mItemIconTint;
    @Dimension
    private int mItemIconSize;
    @Nullable
    private ColorStateList mItemTextColorFromUser;
    @StyleRes
    private int mItemTextAppearanceInactive;
    @StyleRes
    private int mItemTextAppearanceActive;
    @Nullable
    private Drawable mItemBackground;
    private int mItemBackgroundRes;
    private boolean mItemActiveBackgroundEnabled;

    public MultiSelectionActionsView(@NonNull Context context) {
        this(context, null);
    }

    public MultiSelectionActionsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.multiSelectionActionsView);
    }

    private MultiSelectionActionsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Widget_AppTheme_MultiSelectionActionsView);
    }

    private MultiSelectionActionsView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr,
                                     @StyleRes int defStyleRes) {
        super(wrap(context, attrs, defStyleAttr, defStyleRes), attrs, defStyleAttr);
        context = getContext();

        mItemTextColorDefault = createDefaultColorStateList(android.R.attr.textColorSecondary);

        TintTypedArray attributes = ThemeEnforcement.obtainTintedStyledAttributes(
                context,
                attrs,
                R.styleable.MultiSelectionActionsView,
                defStyleAttr,
                defStyleRes,
                R.styleable.MultiSelectionActionsView_itemTextAppearanceInactive,
                R.styleable.MultiSelectionActionsView_itemTextAppearanceActive);

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemIconTint)) {
            mItemIconTint = attributes.getColorStateList(R.styleable.MultiSelectionActionsView_itemIconTint);
        } else {
            mItemIconTint = createDefaultColorStateList(android.R.attr.textColorSecondary);
        }

        mItemIconSize = attributes.getDimensionPixelSize(R.styleable.MultiSelectionActionsView_itemIconSize,
                getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_navigation_bar_item_default_icon_size));

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemTextAppearanceInactive)) {
            mItemTextAppearanceInactive = attributes.getResourceId(
                    R.styleable.MultiSelectionActionsView_itemTextAppearanceInactive, 0);
        }

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemTextAppearanceActive)) {
            mItemTextAppearanceActive = attributes.getResourceId(
                    R.styleable.MultiSelectionActionsView_itemTextAppearanceActive, 0);
        }

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_itemTextColor)) {
            mItemTextColorFromUser = attributes.getColorStateList(R.styleable.MultiSelectionActionsView_itemTextColor);
        }

        if (getBackground() == null || getBackground() instanceof ColorDrawable) {
            // Add a MaterialShapeDrawable as background that supports tinting in every API level.
            ViewCompat.setBackground(this, createMaterialShapeDrawableBackground(context));
        }

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_elevation)) {
            setElevation(attributes.getDimensionPixelSize(R.styleable.MultiSelectionActionsView_elevation, 0));
        }

        ColorStateList backgroundTint = MaterialResources.getColorStateList(
                context, attributes, R.styleable.MultiSelectionActionsView_backgroundTint);
        DrawableCompat.setTintList(getBackground().mutate(), backgroundTint);

        int itemBackground = attributes.getResourceId(R.styleable.MultiSelectionActionsView_itemBackground, 0);
        if (itemBackground != 0) {
            mItemBackgroundRes = itemBackground;
        }

        setOrientation(HORIZONTAL);
        mPresenter.setMenuView(this);
        mPresenter.setId(MENU_PRESENTER_ID);
        mMenu = new MultiSelectionActionsMenu(context);
        mMenu.addMenuPresenter(mPresenter);
        mPresenter.initForMenu(context, mMenu);

        if (attributes.hasValue(R.styleable.MultiSelectionActionsView_menu)) {
            inflateMenu(attributes.getResourceId(R.styleable.MultiSelectionActionsView_menu, 0));
        }

        attributes.recycle();

        mMenu.setCallback(new MenuBuilder.Callback() {
            @Override
            public boolean onMenuItemSelected(@NonNull MenuBuilder menu, @NonNull MenuItem item) {
                return mSelectedListener != null && !mSelectedListener.onNavigationItemSelected(item);
            }

            @Override
            public void onMenuModeChange(@NonNull MenuBuilder menu) {
            }
        });
    }

    @Override
    public void initialize(MenuBuilder menu) {
        mMenuBuilder = menu;
    }

    @Override
    public int getWindowAnimations() {
        return 0;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            renderItems();
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
     * @see R.styleable#MultiSelectionActionsView_elevation
     */
    @Override
    public void setElevation(float elevation) {
        super.setElevation(elevation);
        MaterialShapeUtils.setElevation(this, elevation);
    }

    @NonNull
    public MultiSelectionActionsMenu getMenu() {
        return mMenu;
    }

    /**
     * Set a listener that will be notified when an option is selected.
     *
     * @param listener The listener to notify
     */
    public void setOnItemSelectedListener(@Nullable OnItemSelectedListener listener) {
        mSelectedListener = listener;
    }

    /**
     * Returns the tint which is applied to our menu items' icons.
     *
     * @see R.styleable#MultiSelectionActionsView_itemIconTint
     * @see #setItemIconTintList(ColorStateList)
     */
    @Nullable
    public ColorStateList getItemIconTintList() {
        return mItemIconTint;
    }

    /**
     * Set the tint which is applied to our menu items' icons.
     *
     * @param tint the tint to apply.
     * @see R.styleable#MultiSelectionActionsView_itemIconTint
     */
    public void setItemIconTintList(@Nullable ColorStateList tint) {
        mItemIconTint = tint;
        renderItems();
    }

    /**
     * Set the size to provide for the menu item icons.
     *
     * <p>For best image resolution, use an icon with the same size set in this method.
     *
     * @param iconSize the size in pixels to provide for the menu item icons
     * @see R.styleable#MultiSelectionActionsView_itemIconSize
     */
    public void setItemIconSize(@Dimension int iconSize) {
        mItemIconSize = iconSize;
        renderItems();
    }

    /**
     * Set the size to provide for the menu item icons using a resource ID.
     *
     * <p>For best image resolution, use an icon with the same size set in this method.
     *
     * @param iconSizeRes the resource ID for the size to provide for the menu item icons
     * @see R.styleable#MultiSelectionActionsView_itemIconSize
     */
    public void setItemIconSizeRes(@DimenRes int iconSizeRes) {
        setItemIconSize(getResources().getDimensionPixelSize(iconSizeRes));
    }

    /**
     * Returns the size provided for the menu item icons in pixels.
     *
     * @see R.styleable#MultiSelectionActionsView_itemIconSize
     * @see #setItemIconSize(int)
     */
    @Dimension
    public int getItemIconSize() {
        return mItemIconSize;
    }

    /**
     * Returns colors used for the different states (normal, selected, focused, etc.) of the menu item
     * text.
     *
     * @return the ColorStateList of colors used for the different states of the menu items text.
     * @see R.styleable#MultiSelectionActionsView_itemTextColor
     * @see #setItemTextColor(ColorStateList)
     */
    @Nullable
    public ColorStateList getItemTextColor() {
        return mItemTextColorFromUser;
    }

    /**
     * Set the colors to use for the different states (normal, selected, focused, etc.) of the menu
     * item text.
     *
     * @see R.styleable#MultiSelectionActionsView_itemTextColor
     * @see #getItemTextColor()
     */
    public void setItemTextColor(@Nullable ColorStateList textColor) {
        mItemTextColorFromUser = textColor;
        renderItems();
    }

    /**
     * Returns the background resource of the menu items.
     *
     * @see R.styleable#MultiSelectionActionsView_itemBackground
     * @see #setItemBackgroundResource(int)
     * @deprecated Use {@link #getItemBackground()} instead.
     */
    @Deprecated
    @DrawableRes
    public int getItemBackgroundResource() {
        return mItemBackgroundRes;
    }

    /**
     * Set the background of our menu items to the given resource.
     *
     * @param resId The identifier of the resource.
     * @see R.styleable#MultiSelectionActionsView_itemBackground
     */
    public void setItemBackgroundResource(@DrawableRes int resId) {
        mItemBackgroundRes = resId;
        renderItems();
    }

    /**
     * Returns the background drawable of the menu items.
     *
     * @see R.styleable#MultiSelectionActionsView_itemBackground
     * @see #setItemBackground(Drawable)
     */
    @Nullable
    public Drawable getItemBackground() {
        return mItemBackground;
    }

    /**
     * Set the background of our menu items to the given drawable.
     *
     * @param background The drawable for the background.
     * @see R.styleable#MultiSelectionActionsView_itemBackground
     */
    public void setItemBackground(@Nullable Drawable background) {
        mItemBackground = background;
        renderItems();
    }

    /**
     * Get whether or not a selected item should show an active background.
     *
     * @return true if an active background will be shown when an item is selected.
     */
    public boolean isItemActiveBackgroundEnabled() {
        return mItemActiveBackgroundEnabled;
    }

    /**
     * Set whether a selected item should show an active background.
     *
     * @param enabled true if a selected item should show an active background.
     */
    public void setItemActiveBackgroundEnabled(boolean enabled) {
        mItemActiveBackgroundEnabled = enabled;
        renderItems();
    }

    /**
     * Sets the text appearance to be used for inactive menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for inactive menu item labels
     */
    public void setItemTextAppearanceInactive(@StyleRes int textAppearanceRes) {
        mItemTextAppearanceInactive = textAppearanceRes;
        renderItems();
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
     * Sets the text appearance to be used for the menu item labels.
     *
     * @param textAppearanceRes the text appearance ID used for menu item labels
     */
    public void setItemTextAppearanceActive(@StyleRes int textAppearanceRes) {
        mItemTextAppearanceActive = textAppearanceRes;
        renderItems();
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
     * Inflate a menu resource into this navigation view.
     *
     * <p>Existing items in the menu will not be modified or removed.
     *
     * @param resId ID of a menu resource to inflate
     */
    public void inflateMenu(int resId) {
        mPresenter.setUpdateSuspended(true);
        getMenuInflater().inflate(resId, mMenu);
        mPresenter.setUpdateSuspended(false);
        mPresenter.updateMenuView(true);
    }

    public void updateMenuView() {
        if (mMenuBuilder == null) {
            return;
        }
        mVisibleMenuItems.clear();
        mVisibleMenuItems.addAll(mMenuBuilder.getVisibleItems());
        renderItems();
    }

    void buildMenuView() {
        updateMenuView();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void renderItems() {
        if (getWidth() == 0) {
            return;
        }

        boolean wasLayoutRequested = isLayoutRequested();
        int widthDp = UiUtils.pxToDp(getContext(), getWidth());
        int minButtonWidthDp = 80;
        int maxButtons = widthDp / minButtonWidthDp;
        int usableButtonCount = mVisibleMenuItems.size() <= maxButtons ? mVisibleMenuItems.size() : maxButtons - 1;
        int allocatedWidth = getWidth() / maxButtons;

        List<MenuItemImpl> renderableItems = mVisibleMenuItems.subList(0, usableButtonCount);
        List<MenuItemImpl> overflowItems = renderableItems.size() < mVisibleMenuItems.size()
                ? mVisibleMenuItems.subList(usableButtonCount, mVisibleMenuItems.size())
                : Collections.emptyList();

        removeAllViews();

        for (MenuItemImpl menuItem : renderableItems) {
            addNewOptionItemFromMenuItem(menuItem, allocatedWidth);
        }

        if (!overflowItems.isEmpty()) {
            ReflowMenuItemView overflowView = getNewOptionItem();
            addView(overflowView, new LinearLayoutCompat.LayoutParams(allocatedWidth, ViewGroup.LayoutParams.MATCH_PARENT));
            // init
            CharSequence tooltipText = getContext().getString(com.google.android.material.R.string.abc_action_menu_overflow_description);
            overflowView.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.ic_more_horiz));
            overflowView.setTitle(tooltipText);
            overflowView.setContentDescription(tooltipText);
            // Avoid calling tooltip for L and M devices because long pressing twice may freeze devices.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                TooltipCompat.setTooltipText(this, tooltipText);
            }
            overflowView.setOnClickListener(v -> {
                PopupMenu overflowMenu = new PopupMenu(getContext(), overflowView);
                overflowMenu.setForceShowIcon(true);
                Menu menu = overflowMenu.getMenu();
                for (int i = overflowItems.size() - 1; i >= 0; --i) {
                    MenuItemImpl menuItem = overflowItems.get(i);
                    addMenuFromMenuItem(menu, menuItem);
                }
                overflowMenu.show();
            });
        }
        if (wasLayoutRequested) {
            post(this::requestLayout);
        }
    }

    private void addMenuFromMenuItem(@NonNull Menu menu, @NonNull MenuItemImpl menuItem) {
        MenuItem newMenuItem = menu.add(menuItem.getItemId())
                .setCheckable(menuItem.isCheckable())
                .setChecked(menuItem.isChecked())
                .setEnabled(menuItem.isEnabled())
                .setIcon(menuItem.getIcon())
                .setTitle(menuItem.getTitle());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !TextUtils.isEmpty(menuItem.getContentDescription())) {
            newMenuItem.setContentDescription(menuItem.getContentDescription());
            CharSequence tooltip = !TextUtils.isEmpty(menuItem.getTooltipText())
                    ? menuItem.getTooltipText()
                    : menuItem.getTitle();
            newMenuItem.setTooltipText(tooltip);
        }
        newMenuItem.setOnMenuItemClickListener(item -> mMenu.performItemAction(menuItem, mPresenter, 0));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addNewOptionItemFromMenuItem(@NonNull MenuItemImpl menuItem, int width) {
        ReflowMenuItemView item = getNewOptionItem();
        item.initialize(menuItem);
        item.setOnClickListener(v -> mMenu.performItemAction(menuItem, mPresenter, 0));
        addView(item, new LinearLayoutCompat.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    private ReflowMenuItemView getNewOptionItem() {
        ReflowMenuItemView item = new ReflowMenuItemView(getContext());
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

    private MenuInflater getMenuInflater() {
        if (mMenuInflater == null) {
            mMenuInflater = new SupportMenuInflater(getContext());
        }
        return mMenuInflater;
    }

    @Nullable
    public ColorStateList createDefaultColorStateList(int baseColorThemeAttr) {
        final TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(baseColorThemeAttr, value, true)) {
            return null;
        }
        ColorStateList baseColor = AppCompatResources.getColorStateList(getContext(), value.resourceId);
        if (!getContext()
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
    @NonNull
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.menuPresenterState = new Bundle();
        mMenu.savePresenterStates(savedState.menuPresenterState);
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
        mMenu.restorePresenterStates(savedState.menuPresenterState);
    }

    static class SavedState extends AbsSavedState {
        @Nullable
        Bundle menuPresenterState;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(@NonNull Parcel source, @Nullable ClassLoader loader) {
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

        private void readFromParcel(@NonNull Parcel in, @Nullable ClassLoader loader) {
            menuPresenterState = in.readBundle(loader);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @NonNull
            @Override
            public SavedState createFromParcel(@NonNull Parcel in, @Nullable ClassLoader loader) {
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
