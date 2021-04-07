/*
 * Copyright (C) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.details.info;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

class ListItem {
    @IntDef(value = {
            LIST_ITEM_GROUP_BEGIN,
            LIST_ITEM_GROUP_END,
            LIST_ITEM_REGULAR,
            LIST_ITEM_INLINE
    })
    @interface ListItemType {
    }

    static final int LIST_ITEM_GROUP_BEGIN = 0;  // Group header
    static final int LIST_ITEM_GROUP_END = 1;  // Group divider
    static final int LIST_ITEM_REGULAR = 2;
    static final int LIST_ITEM_INLINE = 3;

    @IntDef(flag = true, value = {
            LIST_ITEM_FLAG_SELECTABLE,
            LIST_ITEM_FLAG_MONOSPACE
    })
    @interface ListItemFlag {
    }

    static final int LIST_ITEM_FLAG_SELECTABLE = 1;
    static final int LIST_ITEM_FLAG_MONOSPACE = 1 << 1;

    @ListItemType
    int type;
    @ListItemFlag
    int flags = 0;
    CharSequence title;
    CharSequence subtitle;
    @DrawableRes
    int actionIcon = 0;
    View.OnClickListener actionListener;

    @NonNull
    static ListItem getGroupHeader(CharSequence title) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_GROUP_BEGIN;
        listItem.title = title;
        return listItem;
    }

    @NonNull
    static ListItem getGroupDivider() {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_GROUP_END;
        return listItem;
    }

    @NonNull
    static ListItem getInlineItem(CharSequence title, CharSequence subtitle) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_INLINE;
        listItem.title = title;
        listItem.subtitle = subtitle;
        return listItem;
    }

    @NonNull
    static ListItem getRegularItem(CharSequence title, CharSequence subtitle) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_REGULAR;
        listItem.title = title;
        listItem.subtitle = subtitle;
        return listItem;
    }

    @NonNull
    static ListItem getSelectableRegularItem(CharSequence title, CharSequence subtitle) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_REGULAR;
        listItem.flags |= LIST_ITEM_FLAG_SELECTABLE;
        listItem.title = title;
        listItem.subtitle = subtitle;
        return listItem;
    }

    @NonNull
    static ListItem getSelectableRegularItem(CharSequence title, CharSequence subtitle, View.OnClickListener actionListener) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_REGULAR;
        listItem.flags |= LIST_ITEM_FLAG_SELECTABLE;
        listItem.title = title;
        listItem.subtitle = subtitle;
        listItem.actionListener = actionListener;
        return listItem;
    }

    @NonNull
    @Override
    public String toString() {
        return "ListItem{" +
                "type=" + type +
                ", flags=" + flags +
                ", title='" + title + '\'' +
                ", subtitle='" + subtitle + '\'' +
                '}';
    }
}