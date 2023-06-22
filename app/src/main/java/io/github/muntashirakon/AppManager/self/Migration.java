// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class Migration {
    private final ArrayMap<Long, List<MigrationTask>> mMigrationTaskList = new ArrayMap<>();

    public void addTask(@NonNull MigrationTask migrationTask) {
        List<MigrationTask> migrationTasks = mMigrationTaskList.get(migrationTask.toVersion);
        if (migrationTasks == null) {
            migrationTasks = new ArrayList<>();
            mMigrationTaskList.put(migrationTask.toVersion, migrationTasks);
        }
        migrationTasks.add(migrationTask);
    }

    public void migrate(long fromVersion, long toVersion) {
        List<MigrationTask> migrationTasks = mMigrationTaskList.get(toVersion);
        if (migrationTasks == null) {
            return;
        }
        for (MigrationTask migrationTask : migrationTasks) {
            if (!migrationTask.shouldRunMigration(fromVersion)) {
                continue;
            }
            if (migrationTask.mainThread) {
                ThreadUtils.postOnMainThread(migrationTask);
            } else migrationTask.run();
        }
    }
}
