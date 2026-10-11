// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.intercept.HistoryEditText;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.FeatureController;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.FileUtils;
import io.github.muntashirakon.io.Paths;

@SuppressLint("StaticFieldLeak")
public class Migrations {
    public static final String TAG = Migrations.class.getSimpleName();

    public static final int FROM_VERSION_CURRENT = 0;

    private static final MigrationTask MIGRATE_FROM_ALL_VERSION_TO_3_0_0 = new MigrationTask(409, 410) {
        @Override
        public void run() {
            Log.d(TAG, "Running MIGRATE_FROM_ALL_VERSION_TO_3_0_0 from %d to %d", fromVersion, toVersion);
            // Delete am database, am.jar
            File internalFilesDir = ContextUtils.getDeContext(context).getFilesDir().getParentFile();
            File[] paths = new File[]{
                    new File(internalFilesDir, "am.jar"),
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

    private static final MigrationTask MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0 = new MigrationTask(406, 410) {
        @Override
        public void run() {
            Log.d(TAG, "Running MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0 from %d to %d", fromVersion, toVersion);
            // Clear DB
            AppsDb.getInstance().backupDao().deleteAll();
        }
    };

    private static final MigrationTask MIGRATE_FROM_4_0_5_TO_4_0_6 = new MigrationTask(445, 446) {
        @Override
        public void run() {
            Log.d(TAG, "Running MIGRATE_FROM_4_0_5_TO_4_0_6 from %d to %d", fromVersion, toVersion);
            //  DB
            File newAppsDb = context.getDatabasePath("apps.db");
            if (!newAppsDb.exists()) {
                File oldAppsDb = new File(FileUtils.getCachePath(), "apps.db");
                if (oldAppsDb.exists()) {
                    oldAppsDb.renameTo(newAppsDb);
                }
            }
        }
    };

    private static final MigrationTask MIGRATE_FROM_451_TO_452 = new MigrationTask(451, 452) {
        @Override
        public void run() {
            Log.d(TAG, "Running MIGRATE_FROM_451_TO_452 from %d to %d", fromVersion, toVersion);
            SharedPreferences legacyPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences historyPreferences = context.getSharedPreferences(
                    HistoryEditText.HISTORY_PREFS_NAME, Context.MODE_PRIVATE);
            Map<String, ?> legacyValues = legacyPreferences.getAll();
            SharedPreferences.Editor historyEditor = historyPreferences.edit();
            SharedPreferences.Editor legacyEditor = legacyPreferences.edit();
            for (int i = 0; i < 6; ++i) {
                String key = HistoryEditText.HISTORY_PREFIX + i;
                Object value = legacyValues.get(key);
                if (value instanceof String) {
                    historyEditor.putString(key, (String) value);
                    legacyEditor.remove(key);
                }
            }
            historyEditor.apply();
            legacyEditor.apply();
        }
    };

    private static final Migration migration;

    static {
        migration = new Migration();
        migration.addTask(MIGRATE_FROM_ALL_VERSION_TO_3_0_0);
        migration.addTask(MIGRATE_FROM_3_0_0_RC01_RC04_TO_3_0_0);
        migration.addTask(MIGRATE_FROM_4_0_5_TO_4_0_6);
        migration.addTask(MIGRATE_FROM_451_TO_452);
    }

    @WorkerThread
    public static void startMigration(long fromVersion) {
        migration.migrate(fromVersion);
    }
}
