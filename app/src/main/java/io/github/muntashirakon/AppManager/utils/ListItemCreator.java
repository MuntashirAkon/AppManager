/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.utils;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import io.github.muntashirakon.AppManager.R;

public class ListItemCreator {
    private LinearLayoutCompat mListContainer;
    private LayoutInflater mLayoutInflater;
    private static final int EMPTY = -1;

    public View list_item;
    public TextView item_title;
    public TextView item_subtitle;
    public ImageView item_icon;
    public ImageButton item_open;

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
        list_item = mLayoutInflater.inflate(R.layout.item_icon_title_subtitle, mListContainer, false);
        // Item title
        item_title = list_item.findViewById(R.id.item_title);
        item_title.setText(title);
        // Item subtitle
        item_subtitle = list_item.findViewById(R.id.item_subtitle);
        if (subtitle != null) item_subtitle.setText(subtitle);
        else item_subtitle.setVisibility(View.GONE);
        // Item icon
        item_icon = list_item.findViewById(R.id.item_icon);
        if (resIdIcon != EMPTY) item_icon.setImageResource(resIdIcon);
        // Remove open with button if not requested
        item_open = list_item.findViewById(R.id.item_open);
        item_open.setVisibility(View.GONE);
        // Add new menu to the container
        mListContainer.addView(list_item);
        return list_item;
    }
}
