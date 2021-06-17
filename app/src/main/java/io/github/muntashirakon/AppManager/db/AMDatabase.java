// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.BackupDao;
import io.github.muntashirakon.AppManager.db.dao.FileHashDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.FileHash;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;

@Database(entities = {App.class, LogFilter.class, FileHash.class, Backup.class}, version = 5)
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

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS backup (package_name TEXT NOT NULL, " +
                    "backup_name TEXT NOT NULL, label TEXT, version_name TEXT, version_code INTEGER, " +
                    "is_system INTEGER, has_splits INTEGER, has_rules INTEGER, backup_time INTEGER, crypto TEXT, " +
                    "meta_version INTEGER, flags INTEGER, user_id INTEGER, tar_type TEXT, has_key_store INTEGER, " +
                    "installer_app TEXT, PRIMARY KEY (package_name, backup_name))");
        }
    };

    public abstract AppDao appDao();

    public abstract BackupDao backupDao();

    public abstract LogFilterDao logFilterDao();

    public abstract FileHashDao fileHashDao();
}
