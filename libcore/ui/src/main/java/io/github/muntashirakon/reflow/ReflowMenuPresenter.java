// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.reflow;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuPresenter;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.view.menu.SubMenuBuilder;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.internal.ParcelableSparseArray;

// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
public class ReflowMenuPresenter implements MenuPresenter {
    private ReflowMenuView menuView;
    private boolean updateSuspended = false;
    private int id;

    public void setMenuView(@NonNull ReflowMenuView menuView) {
        this.menuView = menuView;
    }

    @Override
    public void initForMenu(@NonNull Context context, @NonNull MenuBuilder menu) {
        menuView.initialize(menu);
    }

    @Override
    @Nullable
    public MenuView getMenuView(@Nullable ViewGroup root) {
        return menuView;
    }

    @Override
    public void updateMenuView(boolean cleared) {
        if (updateSuspended) {
            return;
        }
        if (cleared) {
            menuView.buildMenuView();
        } else {
            menuView.updateMenuView();
        }
    }

    @Override
    public void setCallback(@Nullable Callback cb) {
    }

    @Override
    public boolean onSubMenuSelected(@Nullable SubMenuBuilder subMenu) {
        return false;
    }

    @Override
    public void onCloseMenu(@Nullable MenuBuilder menu, boolean allMenusAreClosing) {
    }

    @Override
    public boolean flagActionItems() {
        return false;
    }

    @Override
    public boolean expandItemActionView(@Nullable MenuBuilder menu, @Nullable MenuItemImpl item) {
        return false;
    }

    @Override
    public boolean collapseItemActionView(@Nullable MenuBuilder menu, @Nullable MenuItemImpl item) {
        return false;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getId() {
        return id;
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    @NonNull
    @Override
    public Parcelable onSaveInstanceState() {
        SavedState savedState = new SavedState();
        savedState.selectedItemId = menuView.getSelectedItemId();
        savedState.badgeSavedStates =
                BadgeUtils.createParcelableBadgeStates(menuView.getBadgeDrawables());
        return savedState;
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    @Override
    public void onRestoreInstanceState(@NonNull Parcelable state) {
        if (state instanceof SavedState) {
            menuView.tryRestoreSelectedItemId(((SavedState) state).selectedItemId);
            SparseArray<BadgeDrawable> badgeDrawables = BadgeUtils.createBadgeDrawablesFromSavedStates(
                    menuView.getContext(), ((SavedState) state).badgeSavedStates);
            menuView.setBadgeDrawables(badgeDrawables);
        }
    }

    public void setUpdateSuspended(boolean updateSuspended) {
        this.updateSuspended = updateSuspended;
    }

    static class SavedState implements Parcelable {
        int selectedItemId;
        @Nullable
        ParcelableSparseArray badgeSavedStates;

        SavedState() {
        }

        SavedState(@NonNull Parcel in) {
            selectedItemId = in.readInt();
            badgeSavedStates = in.readParcelable(getClass().getClassLoader());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            out.writeInt(selectedItemId);
            out.writeParcelable(badgeSavedStates, /* parcelableFlags= */ 0);
        }

        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    @NonNull
                    @Override
                    public SavedState createFromParcel(@NonNull Parcel in) {
                        return new SavedState(in);
                    }

                    @NonNull
                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}