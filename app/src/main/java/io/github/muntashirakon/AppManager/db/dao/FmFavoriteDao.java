// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.github.muntashirakon.AppManager.db.entity.FmFavorite;

@Dao
public interface FmFavoriteDao {
    @Query("SELECT * FROM fm_favorite")
    List<FmFavorite> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(@NonNull FmFavorite fmFavorite);

    @Query("UPDATE fm_favorite SET name = :newName WHERE id = :id")
    void rename(long id, @NonNull String newName);

    @Query("DELETE FROM fm_favorite WHERE id = :id")
    void delete(long id);
}
