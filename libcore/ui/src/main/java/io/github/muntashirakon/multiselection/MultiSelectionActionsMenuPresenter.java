// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.multiselection;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.view.menu.MenuPresenter;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.view.menu.SubMenuBuilder;

// Copyright 2020 The Android Open Source Project
@SuppressLint("RestrictedApi")
class MultiSelectionActionsMenuPresenter implements MenuPresenter {
    private MultiSelectionActionsView mMenuView;
    private boolean mUpdateSuspended = false;
    private int mId;

    public void setMenuView(@NonNull MultiSelectionActionsView menuView) {
        mMenuView = menuView;
    }

    @Override
    public void initForMenu(@NonNull Context context, @NonNull MenuBuilder menu) {
        mMenuView.initialize(menu);
    }

    @Override
    @Nullable
    public MenuView getMenuView(@Nullable ViewGroup root) {
        return mMenuView;
    }

    @Override
    public void updateMenuView(boolean cleared) {
        if (mUpdateSuspended) {
            return;
        }
        if (cleared) {
            mMenuView.buildMenuView();
        } else {
            mMenuView.updateMenuView();
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
        mId = id;
    }

    @Override
    public int getId() {
        return mId;
    }


    @NonNull
    @Override
    public Parcelable onSaveInstanceState() {
        return new SavedState();
    }

    @Override
    public void onRestoreInstanceState(@NonNull Parcelable state) {
    }

    public void setUpdateSuspended(boolean updateSuspended) {
        mUpdateSuspended = updateSuspended;
    }

    static class SavedState implements Parcelable {
        SavedState() {
        }

        SavedState(@NonNull Parcel in) {
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
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