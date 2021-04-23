/*
 * Copyright (c) 2021 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.FileHashDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.FileHash;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;

@Database(entities = {App.class, LogFilter.class, FileHash.class}, version = 4)
public abstract class AMDatabase extends RoomDatabase {
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE app ADD COLUMN tracker_count INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS log_filter (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_name ON log_filter (name)");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS file_hash (path TEXT NOT NULL PRIMARY KEY, hash TEXT)");
        }
    };

    public abstract AppDao appDao();

    public abstract LogFilterDao logFilterDao();

    public abstract FileHashDao fileHashDao();
}
