// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.info;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.github.muntashirakon.AppManager.R;

class ListItem {
    @IntDef(value = {
            LIST_ITEM_GROUP_BEGIN,
            LIST_ITEM_REGULAR,
            LIST_ITEM_REGULAR_ACTION,
            LIST_ITEM_INLINE
    })
    @interface ListItemType {
    }

    static final int LIST_ITEM_GROUP_BEGIN = 0;  // Group header
    static final int LIST_ITEM_REGULAR = 1;
    static final int LIST_ITEM_REGULAR_ACTION = 2;
    static final int LIST_ITEM_INLINE = 3;

    @ListItemType
    public final int type;

    @Nullable
    private CharSequence mTitle;
    @Nullable
    private CharSequence mSubtitle;
    @DrawableRes
    private int mActionIconRes;
    @StringRes
    private int mActionContentDescriptionRes;
    @Nullable
    private CharSequence mActionContentDescription;
    @Nullable
    private View.OnClickListener mOnActionClickListener;
    private boolean mIsSelectable;
    private boolean mIsMonospace;

    @NonNull
    public static ListItem newGroupStart(@Nullable CharSequence header) {
        ListItem listItem = new ListItem(LIST_ITEM_GROUP_BEGIN);
        listItem.mTitle = header;
        return listItem;
    }

    @NonNull
    public static ListItem newInlineItem(@Nullable CharSequence title, @Nullable CharSequence subtitle) {
        ListItem listItem = new ListItem(LIST_ITEM_INLINE);
        listItem.mTitle = title;
        listItem.mSubtitle = subtitle;
        return listItem;
    }

    @NonNull
    public static ListItem newRegularItem(@Nullable CharSequence title, @Nullable CharSequence subtitle) {
        ListItem listItem = new ListItem(LIST_ITEM_REGULAR);
        listItem.mTitle = title;
        listItem.mSubtitle = subtitle;
        return listItem;
    }

    @NonNull
    public static ListItem newSelectableRegularItem(@Nullable CharSequence title, @Nullable CharSequence subtitle) {
        ListItem listItem = new ListItem(LIST_ITEM_REGULAR);
        listItem.mIsSelectable = true;
        listItem.mTitle = title;
        listItem.mSubtitle = subtitle;
        return listItem;
    }

    @NonNull
    public static ListItem newSelectableRegularItem(@Nullable CharSequence title,
                                                    @Nullable CharSequence subtitle,
                                                    @Nullable View.OnClickListener actionListener) {
        ListItem listItem = new ListItem(LIST_ITEM_REGULAR_ACTION);
        listItem.mIsSelectable = true;
        listItem.mTitle = title;
        listItem.mSubtitle = subtitle;
        listItem.mActionIconRes = R.drawable.ic_open_in_new;
        listItem.mOnActionClickListener = actionListener;
        return listItem;
    }

    public ListItem(int listType) {
        this.type = listType;
    }

    @Nullable
    public CharSequence getTitle() {
        return mTitle;
    }

    public void setTitle(@Nullable CharSequence title) {
        this.mTitle = title;
    }

    @Nullable
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    public void setSubtitle(@Nullable CharSequence subtitle) {
        this.mSubtitle = subtitle;
    }

    @DrawableRes
    public int getActionIconRes() {
        return mActionIconRes;
    }

    public void setActionIcon(@DrawableRes int actionIcon) {
        this.mActionIconRes = actionIcon;
    }

    @Nullable
    public View.OnClickListener getOnActionClickListener() {
        return mOnActionClickListener;
    }

    public void setOnActionClickListener(@Nullable View.OnClickListener onActionClickListener) {
        this.mOnActionClickListener = onActionClickListener;
    }

    @StringRes
    public int getActionContentDescriptionRes() {
        return mActionContentDescriptionRes;
    }

    @Nullable
    public CharSequence getActionContentDescription() {
        return mActionContentDescription;
    }

    public void setActionContentDescription(@StringRes int contentDescriptionRes) {
        this.mActionContentDescriptionRes = contentDescriptionRes;
    }

    public void setActionContentDescription(@Nullable CharSequence contentDescription) {
        this.mActionContentDescription = contentDescription;
    }

    public boolean isMonospace() {
        return mIsMonospace;
    }

    public void setMonospace(boolean monospace) {
        mIsMonospace = monospace;
    }

    public boolean isSelectable() {
        return mIsSelectable;
    }

    public void setSelectable(boolean selectable) {
        mIsSelectable = selectable;
    }

    @NonNull
    @Override
    public String toString() {
        return "ListItem{" +
                "type=" + type +
                ", title='" + mTitle + '\'' +
                ", subtitle='" + mSubtitle + '\'' +
                '}';
    }
}