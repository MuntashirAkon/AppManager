// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;

@Dao
public interface PermissionOverrideDao {
    @Nullable
    @Query("SELECT * FROM permission_override WHERE package_name = :packageName AND user_id = :userId AND permission_name = :permissionName LIMIT 1")
    PermissionOverride get(String packageName, int userId, String permissionName);

    @Query("SELECT * FROM permission_override WHERE package_name = :packageName AND user_id = :userId")
    List<PermissionOverride> getForPackage(String packageName, int userId);

    @Query("SELECT * FROM permission_override")
    List<PermissionOverride> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PermissionOverride override);

    @Query("DELETE FROM permission_override WHERE package_name = :packageName AND user_id = :userId AND permission_name = :permissionName")
    void delete(String packageName, int userId, String permissionName);

    @Query("DELETE FROM permission_override WHERE package_name = :packageName AND user_id = :userId")
    void deleteForPackage(String packageName, int userId);
}
