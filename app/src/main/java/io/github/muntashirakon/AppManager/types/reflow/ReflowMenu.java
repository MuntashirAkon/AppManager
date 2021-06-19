// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types.reflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MenuItem;
import android.view.SubMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;

/**
 * Provides a {@link MenuBuilder} that can be used to build a menu that can be placed inside
 * navigation bar view such as the bottom navigation menu or the navigation rail view. This
 * implementation of the menu builder prevents the addition of submenus to the primary destinations.
 */
// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public final class ReflowMenu extends MenuBuilder {
    @NonNull
    private final Class<?> viewClass;
    private final int maxItemCount;

    public ReflowMenu(
            @NonNull Context context, @NonNull Class<?> viewClass, int maxItemCount) {
        super(context);
        this.viewClass = viewClass;
        this.maxItemCount = maxItemCount;
    }

    /**
     * Returns the maximum number of items that can be shown in ReflowMenu.
     */
    public int getMaxItemCount() {
        return maxItemCount;
    }

    @NonNull
    @Override
    public SubMenu addSubMenu(int group, int id, int categoryOrder, @NonNull CharSequence title) {
        throw new UnsupportedOperationException(
                viewClass.getSimpleName() + " does not support submenus");
    }

    @Override
    @NonNull
    protected MenuItem addInternal(
            int group, int id, int categoryOrder, @NonNull CharSequence title) {
        if (size() + 1 > maxItemCount) {
            String viewClassName = viewClass.getSimpleName();
            throw new IllegalArgumentException(
                    "Maximum number of items supported by "
                            + viewClassName
                            + " is "
                            + maxItemCount
                            + ". Limit can be checked with "
                            + viewClassName
                            + "#getMaxItemCount()");
        }
        stopDispatchingItemsChanged();
        final MenuItem item = super.addInternal(group, id, categoryOrder, title);
        if (item instanceof MenuItemImpl) {
            ((MenuItemImpl) item).setExclusiveCheckable(true);
        }
        startDispatchingItemsChanged();
        return item;
    }
}