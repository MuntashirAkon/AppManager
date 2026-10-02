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
import io.github.muntashirakon.AppManager.db.dao.FmDirectorySizeDao;
import io.github.muntashirakon.AppManager.db.dao.FmDirectorySortDao;
import io.github.muntashirakon.AppManager.db.dao.FreezeTypeDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.dao.OpHistoryDao;
import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao;
import io.github.muntashirakon.AppManager.db.dao.VtFileDao;
import io.github.muntashirakon.AppManager.db.dao.VtFileSourceDao;
import io.github.muntashirakon.AppManager.db.dao.VtScanAttemptDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.FmFavorite;
import io.github.muntashirakon.AppManager.db.entity.FmDirectorySize;
import io.github.muntashirakon.AppManager.db.entity.FmDirectorySort;
import io.github.muntashirakon.AppManager.db.entity.FreezeType;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;
import io.github.muntashirakon.AppManager.db.entity.OpHistory;
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;
import io.github.muntashirakon.AppManager.db.entity.VtFile;
import io.github.muntashirakon.AppManager.db.entity.VtFileSource;
import io.github.muntashirakon.AppManager.db.entity.VtScanAttempt;
import io.github.muntashirakon.AppManager.utils.ContextUtils;

@Database(entities = {App.class, LogFilter.class, Backup.class, OpHistory.class, FmFavorite.class, FreezeType.class,
        PermissionOverride.class, FmDirectorySort.class, FmDirectorySize.class, VtFile.class, VtFileSource.class,
        VtScanAttempt.class}, version = 11)
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
            db.execSQL("CREATE TABLE IF NOT EXISTS `permission_override` (`package_name` TEXT NOT NULL, `user_id` INTEGER NOT NULL, `permission_name` TEXT NOT NULL, `desired_granted` INTEGER NOT NULL, `controller` TEXT NOT NULL, `sync_status` INTEGER NOT NULL, `sync_time` INTEGER NOT NULL, PRIMARY KEY(`package_name`, `user_id`, `permission_name`))");
        }
    };
    public static final Migration M_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `fm_directory_sort` (`directory_key` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `reverse_sort` INTEGER NOT NULL, `last_used_at` INTEGER NOT NULL, PRIMARY KEY(`directory_key`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `fm_directory_size` (`directory_key` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `calculated_at` INTEGER NOT NULL, `last_used_at` INTEGER NOT NULL, PRIMARY KEY(`directory_key`))");
        }
    };

    public static final Migration M_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `vt_file` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sha256` TEXT NOT NULL, " +
                    "`md5` TEXT, " +
                    "`sha1` TEXT, " +
                    "`display_name` TEXT, " +
                    "`mime_type` TEXT, " +
                    "`size_bytes` INTEGER NOT NULL, " +
                    "`latest_report_json` TEXT, " +
                    "`latest_detected` INTEGER, " +
                    "`latest_total` INTEGER, " +
                    "`latest_analysis_id` TEXT, " +
                    "`permalink` TEXT, " +
                    "`latest_analysis_at` INTEGER NOT NULL, " +
                    "`local_analysis_json` TEXT, " +
                    "`last_accessed_at` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vt_file_sha256` ON `vt_file` (`sha256`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `vt_file_source` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`file_id` INTEGER NOT NULL, " +
                    "`source_uri` TEXT NOT NULL, " +
                    "`display_name` TEXT, " +
                    "`mime_type` TEXT, " +
                    "`last_seen_at` INTEGER NOT NULL, " +
                    "`is_readable` INTEGER NOT NULL, " +
                    "`source_type` TEXT, " +
                    "FOREIGN KEY(`file_id`) REFERENCES `vt_file`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vt_file_source_file_id` ON `vt_file_source` (`file_id`)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vt_file_source_file_uri` ON `vt_file_source` (`file_id`, `source_uri`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `vt_scan_attempt` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`file_id` INTEGER NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`analysis_id` TEXT, " +
                    "`permalink` TEXT, " +
                    "`report_json` TEXT, " +
                    "`detected` INTEGER, " +
                    "`total` INTEGER, " +
                    "`error_code` TEXT, " +
                    "`error_message` TEXT, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`started_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "`completed_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`file_id`) REFERENCES `vt_file`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vt_scan_attempt_file_id` ON `vt_scan_attempt` (`file_id`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vt_scan_attempt_status` ON `vt_scan_attempt` (`status`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vt_scan_attempt_updated_at` ON `vt_scan_attempt` (`updated_at`)");
        }
    };

    public static final Migration M_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `vt_file` ADD COLUMN `latest_read_at` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `vt_scan_attempt` ADD COLUMN `read_at` INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppsDb getInstance() {
        if (sAppsDb == null) {
            sAppsDb = Room.databaseBuilder(ContextUtils.getContext(), AppsDb.class, "apps.db")
                    .addMigrations(M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8, M_8_9, M_9_10, M_10_11)
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

    public abstract FmDirectorySortDao fmDirectorySortDao();

    public abstract FmDirectorySizeDao fmDirectorySizeDao();

    public abstract FreezeTypeDao freezeTypeDao();

    public abstract PermissionOverrideDao permissionOverrideDao();

    public abstract VtFileDao vtFileDao();

    public abstract VtFileSourceDao vtFileSourceDao();

    public abstract VtScanAttemptDao vtScanAttemptDao();
}
