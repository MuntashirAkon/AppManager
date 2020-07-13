package io.github.muntashirakon.AppManager.utils;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.R;

import androidx.cardview.widget.CardView;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class ListItemCreator {
    private Activity mActivity;
    private LinearLayout mListContainer;
    private LayoutInflater mLayoutInflater;
    private static final int EMPTY = -1;

    public static final float CLICKABLE = 469.0f;
    public static final float SELECTABLE = 708.0f;
    public static final float NO_ACTION = 243.0f;

    public View list_item;
    public TextView item_title;
    public TextView item_subtitle;
    public ImageView item_icon;
    public ImageButton item_open;

    /**
     * Constructor.
     *
     * @param activity Caller activity
     * @param resIdListContainer Resource ID where menu items will be added
     */
    public ListItemCreator(@NonNull Activity activity, @IdRes int resIdListContainer) {
        mListContainer = activity.findViewById(resIdListContainer);
        mLayoutInflater = activity.getLayoutInflater();
        mActivity = activity;
    }

    /**
     * Constructor.
     *
     * @param activity Caller activity
     * @param resIdMenuContainer Resource ID where menu items will be added
     */
    public ListItemCreator(@NonNull Activity activity, @IdRes int resIdMenuContainer, boolean removeAllChildren) {
        mListContainer = activity.findViewById(resIdMenuContainer);
        if(removeAllChildren) mListContainer.removeAllViews();
        mLayoutInflater = activity.getLayoutInflater();
        mActivity = activity;
    }

    public void addDivider() {
        View divider = mLayoutInflater.inflate(R.layout.item_divider_horizontal, mListContainer, false);
        mListContainer.addView(divider);
    }

    public View addInlineItem(@NonNull CharSequence title, @NonNull CharSequence subtitle) {
        list_item = mLayoutInflater.inflate(R.layout.item_title_subtitle_inline, mListContainer, false);
        // Item Title
        item_title = list_item.findViewById(R.id.item_title);
        item_title.setText(title);
        // Item Subtitle
        item_subtitle = list_item.findViewById(R.id.item_subtitle);
        item_subtitle.setText(subtitle);
        // Add new menu to the container
        mListContainer.addView(list_item);
        return list_item;
    }

    /**
     * Create a menu item with only title
     * @param title Title
     * @return The menu item is returned which can be used for other purpose
     */
    public View addItemWithTitle(CharSequence title) {
        return addItemWithTitle(title, NO_ACTION);
    }

    public View addItemWithTitle(CharSequence title, float isClickableOrSelectable) {
        return addItemWithTitleSubtitle(title, null, isClickableOrSelectable);
    }

    public View addItemWithTitle(CharSequence title, boolean isAllCaps) {
        return addItemWithIconTitleSubtitle(title, null, EMPTY, false, false, isAllCaps);
    }

    public View addItemWithTitle(CharSequence title, float isClickableOrSelectable, boolean isAllCaps) {
        return addItemWithIconTitleSubtitle(title, null, EMPTY, isClickableOrSelectable == CLICKABLE, isClickableOrSelectable == SELECTABLE, isAllCaps);
    }

    public View addItemWithTitleSubtitle(CharSequence title, CharSequence subtitle) {
        return addItemWithTitleSubtitle(title, subtitle, EMPTY);
    }

    public View addItemWithTitleSubtitle(CharSequence title, CharSequence subtitle, float isClickableOrSelectable) {
        return addItemWithIconTitleSubtitle(title, subtitle, EMPTY, isClickableOrSelectable);
    }

    public View addItemWithTitleSubtitle(CharSequence title, CharSequence subtitle, int resIdIcon) {
        return addItemWithIconTitleSubtitle(title, subtitle, resIdIcon, true, false, false);
    }

    public View addItemWithIconTitleSubtitle(CharSequence title, CharSequence subtitle, int resIdIcon, float isClickableOrSelectable) {
        return addItemWithIconTitleSubtitle(title, subtitle, resIdIcon, isClickableOrSelectable == CLICKABLE, isClickableOrSelectable == SELECTABLE, false);
    }

    public void setOpen(@NonNull View.OnClickListener onClickListener) {
        item_open.setVisibility(View.VISIBLE);
        item_open.setOnClickListener(onClickListener);
    }
    /**
     * Add a menu item to the main menu container.
     *
     * @param title Title
     * @param subtitle Subtitle (null to remove it)
     * @param resIdIcon Resource ID for icon (ListItemCreator.EMPTY to leave it empty)
     * @return The menu item is returned which can be used for other purpose
     */
    private View addItemWithIconTitleSubtitle(@NonNull CharSequence title,
                                              @Nullable CharSequence subtitle, int resIdIcon,
                                              boolean isClickable, boolean isSelectable,
                                              boolean isAllCaps) {
        list_item = mLayoutInflater.inflate(R.layout.item_icon_title_subtitle, mListContainer, false);
        // Item Title
        item_title = list_item.findViewById(R.id.item_title);
        item_title.setText(title);
        // Make title all caps if requested
        if (isAllCaps) {
            item_title.setAllCaps(true);
            item_title.setTextSize(12f);  // FIXME: Use a dimen value
        }
        // Item Subtitle
        item_subtitle = list_item.findViewById(R.id.item_subtitle);
        if (subtitle != null) item_subtitle.setText(subtitle);
        else item_subtitle.setVisibility(View.GONE);
        // Set selectable if requested
        if (isSelectable) item_subtitle.setTextIsSelectable(true);
        // Item Icon
        item_icon = list_item.findViewById(R.id.item_icon);
        if (resIdIcon != EMPTY) item_icon.setImageResource(resIdIcon);
        // Remove open with button if not requested
        item_open = list_item.findViewById(R.id.item_open);
        item_open.setVisibility(View.GONE);
        // Reduce padding if both subtitle and icon is empty
        if (subtitle == null && resIdIcon == EMPTY) {
            int padding_small = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_small);
            int padding_very_small = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_very_small);
            int padding_medium = mActivity.getResources().getDimensionPixelOffset(R.dimen.padding_medium);
            LinearLayout item_layout = list_item.findViewById(R.id.item_layout);
            item_layout.setPadding(padding_medium, padding_small, padding_medium, padding_very_small);
            item_title.setTypeface(Typeface.DEFAULT);
        }
        // Disable animations if not clickable
        if (!isClickable) {
            CardView item_view = list_item.findViewById(R.id.item_view);
            item_view.setClickable(false);
            item_view.setFocusable(false);
        }
        // Add new menu to the container
        mListContainer.addView(list_item);
        return list_item;
    }
}
