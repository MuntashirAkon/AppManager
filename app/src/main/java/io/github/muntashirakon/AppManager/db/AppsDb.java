// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.BackupDao;
import io.github.muntashirakon.AppManager.db.dao.FmFavoriteDao;
import io.github.muntashirakon.AppManager.db.dao.FreezeTypeDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.dao.OpHistoryDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@Database(entities = {App.class, LogFilter.class, Backup.class, OpHistory.class, FmFavorite.class, FreezeType.class, ArchivedApp.class}, version = 8)
public abstract class AppsDb extends RoomDatabase {
    private static AppsDb sAppsDb;

    public static final Migration M_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `op_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `time` INTEGER NOT NULL, `data` TEXT NOT NULL, `status` TEXT NOT NULL, `extra` TEXT)");
        }
    };
    public static final Migration M_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `fm_favorite` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `uri` TEXT NOT NULL, `init_uri` TEXT, `options` INTEGER NOT NULL, `order` INTEGER NOT NULL, `type` INTEGER NOT NULL)");
        }
    };
    public static final Migration M_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `freeze_type` (`package_name` TEXT NOT NULL, `type` INTEGER NOT NULL, PRIMARY KEY(`package_name`))");
        }
    };
    public static final Migration M_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `app` ADD COLUMN `is_only_data_installed` INTEGER NOT NULL DEFAULT 0");
        }
    };
    public static final Migration M_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS `file_hash`");
        }
    };

    public static final Migration M_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `archived_apps` (`package_name` TEXT NOT NULL, `app_name` TEXT, `archive_timestamp` INTEGER NOT NULL, PRIMARY KEY(`package_name`))");
        }
    };

    public static AppsDb getInstance() {
        if (sAppsDb == null) {
            sAppsDb = Room.databaseBuilder(ContextUtils.getContext(), AppsDb.class, "apps.db")
                    .addMigrations(M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
            try {
                sAppsDb.appDao().getAll();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        return sAppsDb;
    }

    public abstract AppDao appDao();

    public abstract BackupDao backupDao();

    public abstract LogFilterDao logFilterDao();

    public abstract OpHistoryDao opHistoryDao();

    public abstract FmFavoriteDao fmFavoriteDao();

    public abstract FreezeTypeDao freezeTypeDao();

    public abstract ArchivedAppDao archivedAppDao();
}
