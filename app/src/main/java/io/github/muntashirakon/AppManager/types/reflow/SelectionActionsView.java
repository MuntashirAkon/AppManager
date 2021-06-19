// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types.reflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.R;
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior;

/**
 * Represents a standard bottom navigation bar for application. It is an implementation of <a
 * href="https://material.google.com/components/bottom-navigation.html">material design bottom
 * navigation</a>.
 *
 * <p>Bottom navigation bars make it easy for users to explore and switch between top-level views in
 * a single tap. They should be used when an application has three to five top-level destinations.
 *
 * <p>The bar can disappear on scroll, based on {@link
 * com.google.android.material.behavior.HideBottomViewOnScrollBehavior}, when it is placed within a
 * {@link CoordinatorLayout} and one of the children within the {@link CoordinatorLayout} is
 * scrolled. This behavior is only set if the {@code layout_behavior} property is set to {@link
 * HideBottomViewOnScrollBehavior}.
 *
 * <p>The bar contents can be populated by specifying a menu resource file. Each menu item title,
 * icon and enabled state will be used for displaying bottom navigation bar items. Menu items can
 * also be used for programmatically selecting which destination is currently active. It can be done
 * using {@code MenuItem#setChecked(true)}
 *
 * <pre>
 * layout resource file:
 * &lt;io.github.muntashirakon.AppManager.types.reflow.SelectionActionsView
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:app="http://schema.android.com/apk/res/res-auto"
 *     android:id="@+id/navigation"
 *     android:layout_width="match_parent"
 *     android:layout_height="56dp"
 *     android:layout_gravity="start"
 *     app:menu="@menu/my_navigation_items" /&gt;
 *
 * res/menu/my_navigation_items.xml:
 * &lt;menu xmlns:android="http://schemas.android.com/apk/res/android"&gt;
 *     &lt;item android:id="@+id/action_search"
 *          android:title="@string/menu_search"
 *          android:icon="@drawable/ic_search" /&gt;
 *     &lt;item android:id="@+id/action_settings"
 *          android:title="@string/menu_settings"
 *          android:icon="@drawable/ic_add" /&gt;
 *     &lt;item android:id="@+id/action_navigation"
 *          android:title="@string/menu_navigation"
 *          android:icon="@drawable/ic_action_navigation_menu" /&gt;
 * &lt;/menu&gt;
 * </pre>
 */
// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public final class SelectionActionsView extends ReflowMenuViewWrapper {
    public SelectionActionsView(@NonNull Context context) {
        this(context, null);
    }

    public SelectionActionsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.bottomNavigationStyle);
    }

    public SelectionActionsView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Widget_Design_BottomNavigationView);
    }

    public SelectionActionsView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public int getMaxItemCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    @NonNull
    protected ReflowMenuView createNavigationBarMenuView(@NonNull Context context) {
        ReflowMenuView view = new SelectionActionMenuView(context);
        LinearLayoutCompat.LayoutParams params =
                new LinearLayoutCompat.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
        return view;
    }

    private static class SelectionActionMenuView extends ReflowMenuView {
        public SelectionActionMenuView(@NonNull Context context) {
            super(context);
        }

        @Override
        protected boolean isShifting(int labelVisibilityMode, int childCount) {
            return false;
        }

        @NonNull
        @Override
        protected ReflowMenuItemView createNavigationBarItemView(@NonNull Context context) {
            return new ReflowMenuItemView(context);
        }
    }
}