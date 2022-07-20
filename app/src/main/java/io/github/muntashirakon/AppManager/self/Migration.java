// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

class Migration {
    private final ArrayMap<Long, List<MigrationTask>> migrationTaskList = new ArrayMap<>();

    public void addTask(@NonNull MigrationTask migrationTask) {
        List<MigrationTask> migrationTasks = migrationTaskList.get(migrationTask.toVersion);
        if (migrationTasks == null) {
            migrationTasks = new ArrayList<>();
            migrationTaskList.put(migrationTask.toVersion, migrationTasks);
        }
        migrationTasks.add(migrationTask);
    }

    public void migrate(long fromVersion, long toVersion) {
        List<MigrationTask> migrationTasks = migrationTaskList.get(toVersion);
        if (migrationTasks == null) {
            return;
        }
        for (MigrationTask migrationTask : migrationTasks) {
            if (!migrationTask.shouldRunMigration(fromVersion)) {
                continue;
            }
            if (migrationTask.mainThread) {
                UiThreadHandler.run(migrationTask);
            } else migrationTask.run();
        }
    }
}
