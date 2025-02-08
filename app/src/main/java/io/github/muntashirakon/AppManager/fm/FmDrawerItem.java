// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class FmDrawerItem {
    public static final int ITEM_TYPE_LABEL = 0;
    public static final int ITEM_TYPE_FAVORITE = 1;
    public static final int ITEM_TYPE_LOCATION = 2;
    public static final int ITEM_TYPE_TAG = 3;

    @IntDef({ITEM_TYPE_LABEL, ITEM_TYPE_FAVORITE, ITEM_TYPE_LOCATION, ITEM_TYPE_TAG})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DrawerItemType {
    }

    public final long id;
    @NonNull
    public final String name;
    @Nullable
    public final FmActivity.Options options;
    @DrawerItemType
    public final int type;
    @DrawableRes
    public int iconRes;
    @Nullable
    public Drawable icon;
    @ColorInt
    public int color;

    public FmDrawerItem(long id, @NonNull String name, @Nullable FmActivity.Options options, @DrawerItemType int type) {
        this.id = id;
        this.name = name;
        this.options = options;
        this.type = type;
    }
}
