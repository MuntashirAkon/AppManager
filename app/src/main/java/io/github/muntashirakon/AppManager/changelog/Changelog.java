// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import androidx.annotation.NonNull;

import java.util.LinkedList;

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class Changelog {
    @NonNull
    private final LinkedList<ChangelogItem> mChangelogItems;

    private boolean mBulletedList;

    public Changelog() {
        mChangelogItems = new LinkedList<>();
    }

    public void addItem(@NonNull ChangelogItem row) {
        mChangelogItems.add(row);
    }

    /**
     * Clear all rows
     */
    public void clearAllRows() {
        mChangelogItems.clear();
    }

    public boolean isBulletedList() {
        return mBulletedList;
    }

    public void setBulletedList(boolean bulletedList) {
        mBulletedList = bulletedList;
    }

    public LinkedList<ChangelogItem> getChangelogItems() {
        return mChangelogItems;
    }
}