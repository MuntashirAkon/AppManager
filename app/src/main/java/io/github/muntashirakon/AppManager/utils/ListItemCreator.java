// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.button.MaterialButton;

import io.github.muntashirakon.AppManager.R;

public class ListItemCreator {
    private static final int EMPTY = -1;

    private final LinearLayoutCompat mListContainer;
    private final LayoutInflater mLayoutInflater;

    public View listItem;
    public TextView itemTitle;
    public TextView itemSubtitle;
    public ImageView itemIcon;
    public MaterialButton itemOpen;

    public ListItemCreator(@NonNull Activity activity, @IdRes int resIdMenuContainer) {
        mListContainer = activity.findViewById(resIdMenuContainer);
        mListContainer.removeAllViews();
        mLayoutInflater = activity.getLayoutInflater();
    }

    public View addItemWithTitleSubtitle(CharSequence title, CharSequence subtitle) {
        return addItemWithTitleSubtitle(title, subtitle, EMPTY);
    }

    public View addItemWithTitleSubtitle(CharSequence title, CharSequence subtitle, int resIdIcon) {
        return addItemWithIconTitleSubtitle(title, subtitle, resIdIcon);
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
                                              @Nullable CharSequence subtitle, int resIdIcon) {
        listItem = mLayoutInflater.inflate(R.layout.item_icon_title_subtitle, mListContainer, false);
        // Item title
        itemTitle = listItem.findViewById(R.id.item_title);
        itemTitle.setText(title);
        // Item subtitle
        itemSubtitle = listItem.findViewById(R.id.item_subtitle);
        if (subtitle != null) itemSubtitle.setText(subtitle);
        else itemSubtitle.setVisibility(View.GONE);
        // Item icon
        itemIcon = listItem.findViewById(R.id.item_icon);
        if (resIdIcon != EMPTY) itemIcon.setImageResource(resIdIcon);
        else itemIcon.setVisibility(View.GONE);
        // Remove open with button if not requested
        itemOpen = listItem.findViewById(R.id.item_open);
        itemOpen.setVisibility(View.GONE);
        // Add new menu to the container
        mListContainer.addView(listItem);
        return listItem;
    }
}
