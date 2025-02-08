// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Query;

import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.utils.FreezeUtils;

@Dao
public interface FreezeTypeDao {
    @Nullable
    @Query("SELECT * FROM freeze_type WHERE package_name = :packageName LIMIT 1")
    FreezeType get(@NonNull String packageName);

    @Query("INSERT INTO freeze_type (package_name, type) VALUES (:packageName, :type)")
    void insert(@NonNull String packageName, @FreezeUtils.FreezeType int type);
}
