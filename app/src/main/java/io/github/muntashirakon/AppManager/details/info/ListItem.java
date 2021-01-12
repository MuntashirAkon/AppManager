package io.github.muntashirakon.AppManager.details.info;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

public class ListItem {
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
    String title;
    String subtitle;
    @DrawableRes
    int icon = 0;
    @DrawableRes
    int actionIcon = 0;
    View.OnClickListener actionListener;

    @NonNull
    static ListItem getGroupHeader(String title) {
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
    static ListItem getInlineItem(String title, String subtitle) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_INLINE;
        listItem.title = title;
        listItem.subtitle = subtitle;
        return listItem;
    }

    @NonNull
    static ListItem getRegularItem(String title, String subtitle) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_REGULAR;
        listItem.title = title;
        listItem.subtitle = subtitle;
        return listItem;
    }

    @NonNull
    static ListItem getSelectableRegularItem(String title, String subtitle) {
        ListItem listItem = new ListItem();
        listItem.type = LIST_ITEM_REGULAR;
        listItem.flags |= LIST_ITEM_FLAG_SELECTABLE;
        listItem.title = title;
        listItem.subtitle = subtitle;
        return listItem;
    }

    @NonNull
    static ListItem getSelectableRegularItem(String title, String subtitle, View.OnClickListener actionListener) {
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