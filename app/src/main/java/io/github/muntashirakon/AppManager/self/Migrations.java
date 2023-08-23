// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.annotation.SuppressLint;

import androidx.annotation.WorkerThread;

import java.io.File;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Paths;

@SuppressLint("StaticFieldLeak")
public class Migrations {
    public static final String TAG = Migrations.class.getSimpleName();

    private static final MigrationTask MIGRATE_FROM_ALL_VERSION_TO_3_0_0 = new MigrationTask(-1) {
        @Override
        public void run() {
            Log.d(TAG, "Running MIGRATE_FROM_ALL_VERSION_TO_3_0_0 from (%d-%d) to %d", fromVersionAtLeast, fromVersionAtMost, toVersion);
            // Delete am database, am.jar
            File internalFilesDir = ContextUtils.getDeContext(context).getFilesDir().getParentFile();
            File[] paths = new File[]{
                    ServerConfig.getDestJarFile(),
                    new File(internalFilesDir, "main.jar"),
                    new File(internalFilesDir, "run_server.sh"),
                    context.getDatabasePath("am"),
                    context.getDatabasePath("am-shm"),
                    context.getDatabasePath("am-wal"),
            };
            for (File path : paths) {
                FileUtils.deleteSilently(path);
            }
            // Delete old cache dir (removed in v2.6.4 (394))
            File oldCacheDir = context.getExternalFilesDir("cache");
            Paths.get(oldCacheDir).delete();
            // Disable Internet feature by default
            FeatureController.getInstance().modifyState(FeatureController.FEAT_INTERNET, false);
        }
    };

    private static final MigrationTask MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0 = new MigrationTask(403, 406) {
        @Override
        public void run() {
            Log.d(TAG, "Running MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0 from (%d-%d) to %d", fromVersionAtLeast, fromVersionAtMost, toVersion);
            // Migrate DB
            File newAppsDb = context.getDatabasePath("apps.db");
            if (!newAppsDb.exists()) {
                File oldAppsDb = new File(FileUtils.getCachePath(), "apps.db");
                if (oldAppsDb.exists()) {
                    oldAppsDb.renameTo(newAppsDb);
                }
            }
        }
    };

    private static final Migration migration;

    static {
        migration = new Migration();
        migration.addTask(MIGRATE_FROM_ALL_VERSION_TO_3_0_0);
        migration.addTask(MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0);
    }

    @WorkerThread
    public static void startMigration(long fromVersion, long toVersion) {
        migration.migrate(fromVersion, toVersion);
    }
}
