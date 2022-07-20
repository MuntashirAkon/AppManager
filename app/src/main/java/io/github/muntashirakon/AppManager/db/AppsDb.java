// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.db;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.db.dao.AppDao;
import io.github.muntashirakon.AppManager.db.dao.BackupDao;
import io.github.muntashirakon.AppManager.db.dao.FileHashDao;
import io.github.muntashirakon.AppManager.db.dao.LogFilterDao;
import io.github.muntashirakon.AppManager.db.entity.App;
import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.db.entity.FileHash;
import io.github.muntashirakon.AppManager.db.entity.LogFilter;

@Database(entities = {App.class, LogFilter.class, FileHash.class, Backup.class}, version = 1)
public abstract class AppsDb extends RoomDatabase {
    private static AppsDb sAppsDb;

    public static AppsDb getInstance() {
        if (sAppsDb == null) {
            sAppsDb = Room.databaseBuilder(AppManager.getContext(), AppsDb.class, "apps.db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return sAppsDb;
    }

    public abstract AppDao appDao();

    public abstract BackupDao backupDao();

    public abstract LogFilterDao logFilterDao();

    public abstract FileHashDao fileHashDao();
}
