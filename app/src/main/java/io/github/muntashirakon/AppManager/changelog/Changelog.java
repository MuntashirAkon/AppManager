// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.changelog;

import androidx.annotation.NonNull;

import java.util.LinkedList;

// Copyright 2013 Gabriele Mariotti <gabri.mariotti@gmail.com>
// Copyright 2022 Muntashir Al-Islam
public class Changelog {
    @NonNull
    private final LinkedList<ChangelogItem> changelogItems;

    private boolean bulletedList;

    public Changelog() {
        changelogItems = new LinkedList<>();
    }

    public void addItem(@NonNull ChangelogItem row) {
        changelogItems.add(row);
    }

    /**
     * Clear all rows
     */
    public void clearAllRows() {
        changelogItems.clear();
    }

    public boolean isBulletedList() {
        return bulletedList;
    }

    public void setBulletedList(boolean bulletedList) {
        this.bulletedList = bulletedList;
    }

    public LinkedList<ChangelogItem> getChangelogItems() {
        return changelogItems;
    }
}