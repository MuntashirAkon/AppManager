package io.github.muntashirakon.AppManager.utils;

import android.app.Activity;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ContextThemeWrapper;

import io.github.muntashirakon.AppManager.R;

import androidx.cardview.widget.CardView;

public class MenuItemCreator {
    private Activity mActivity;
    private LinearLayout mMenuContainer;
    private LayoutInflater mLayoutInflater;
    private static final int EMPTY = -1;

    public static final float CLICKABLE = 469.0f;
    public static final float SELECTABLE = 708.0f;
    public static final float NO_ACTION = 243.0f;
    public static final int NO_COLOR = -1;

    public int titleColor = NO_COLOR;
    public int subtitleColor = NO_COLOR;
    public int iconColor = NO_COLOR;

    /**
     * Constructor.
     *
     * @param activity Caller activity
     * @param resIdMenuContainer Resource ID where menu items will be added
     */
    public MenuItemCreator(Activity activity, int resIdMenuContainer) {
        mMenuContainer = activity.findViewById(resIdMenuContainer);
        mLayoutInflater = activity.getLayoutInflater();
        mActivity = activity;
    }

    public View addDivider() {
        View divider = mLayoutInflater.inflate(R.layout.item_divider_horizontal, mMenuContainer, false);
        mMenuContainer.addView(divider);
        return divider;
    }

    /**
     * Create a menu item with only title
     * @param title Title
     * @return The menu item is returned which can be used for other purpose
     */
    public View addMenuItemWithIconTitleSubtitle(CharSequence title) {
        return addMenuItemWithIconTitleSubtitle(title, NO_ACTION);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, float isClickableOrSelectable) {
        return addMenuItemWithIconTitleSubtitle(title, null, isClickableOrSelectable);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, boolean isAllCaps) {
        return addMenuItemWithIconTitleSubtitle(title, null, EMPTY, false, false, isAllCaps);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, float isClickableOrSelectable, boolean isAllCaps) {
        return addMenuItemWithIconTitleSubtitle(title, null, EMPTY, isClickableOrSelectable == CLICKABLE, isClickableOrSelectable == SELECTABLE, isAllCaps);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, CharSequence subtitle) {
        return addMenuItemWithIconTitleSubtitle(title, subtitle, EMPTY);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, CharSequence subtitle, float isClickableOrSelectable) {
        return addMenuItemWithIconTitleSubtitle(title, subtitle, EMPTY, isClickableOrSelectable);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, CharSequence subtitle, int resIdIcon) {
        return addMenuItemWithIconTitleSubtitle(title, subtitle, resIdIcon, true, false, false);
    }

    public View addMenuItemWithIconTitleSubtitle(CharSequence title, CharSequence subtitle, int resIdIcon, float isClickableOrSelectable) {
        return addMenuItemWithIconTitleSubtitle(title, subtitle, resIdIcon, isClickableOrSelectable == CLICKABLE, isClickableOrSelectable == SELECTABLE, false);
    }
    /**
     * Add a menu item to the main menu container.
     *
     * @param title Title
     * @param subtitle Subtitle (null to remove it)
     * @param resIdIcon Resource ID for icon (MenuItemCreator.EMPTY to leave it empty)
     * @return The menu item is returned which can be used for other purpose
     */
    private View addMenuItemWithIconTitleSubtitle(CharSequence title, CharSequence subtitle, int resIdIcon, boolean isClickable, boolean isSelectable, boolean isAllCaps) {
        View menu_item = mLayoutInflater.inflate(R.layout.item_icon_title_subtitle, mMenuContainer, false);
        // Item Title
        TextView item_title = menu_item.findViewById(R.id.item_title);
        item_title.setText(title);
        // Make title all caps if requested
        if (isAllCaps) {
            item_title.setAllCaps(true);
        }
        // Item Subtitle
        TextView item_subtitle = menu_item.findViewById(R.id.item_subtitle);
        if (subtitle != null) {
            item_subtitle.setText(subtitle);
        } else {  // Remove subtitle
            item_subtitle.setVisibility(View.GONE);
        }
        // Set selectable if requested
        if (isSelectable) {
            item_subtitle.setTextIsSelectable(true);
        }
        // Item Icon
        ImageView item_icon = menu_item.findViewById(R.id.item_icon);
        if (resIdIcon != EMPTY) {
            item_icon.setImageResource(resIdIcon);
//           item_icon.setColorFilter(color);
        }
        // Reduce padding if both subtitle and icon is empty
        if (subtitle == null && resIdIcon == EMPTY) {
            int padding_small = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_small);
            int padding_very_small = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_very_small);
            int padding_medium = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_medium);
            LinearLayout item_layout = menu_item.findViewById(R.id.item_layout);
            item_layout.setPadding(padding_medium, padding_small, padding_medium, padding_very_small);
        }
        // Disable animations if not clickable
        if (!isClickable) {
            CardView item_view = menu_item.findViewById(R.id.item_view);
            item_view.setClickable(false);
            item_view.setFocusable(false);
        }
        // Set colors
        if (titleColor != NO_COLOR) item_title.setTextColor(titleColor);
        if (subtitleColor != NO_COLOR) item_subtitle.setTextColor(subtitleColor);
        if (iconColor != NO_COLOR) item_icon.setColorFilter(iconColor);
        // Add new menu to the container
        mMenuContainer.addView(menu_item);
        return menu_item;
    }

    public int getAccentColor(){
        return getSystemColor(android.R.attr.colorAccent);
    }

    public int getSystemColor(int resAttrColor) { // Ex. android.R.attr.colorPrimary
        // Get accent color
        TypedValue typedValue = new TypedValue();
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(mActivity,
                android.R.style.Theme_DeviceDefault);
        contextThemeWrapper.getTheme().resolveAttribute(resAttrColor,
                typedValue, true);
        return typedValue.data;
    }
}
