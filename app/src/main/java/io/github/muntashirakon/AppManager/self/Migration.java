// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.github.muntashirakon.AppManager.utils.ThreadUtils;

class Migration {
    private final List<MigrationTask> mMigrationTasks = new ArrayList<>();

    public void addTask(@NonNull MigrationTask migrationTask) {
        mMigrationTasks.add(migrationTask);
    }

    public void migrate(long fromVersion) {
        if (fromVersion == 0) {
            // This is a new version, no migration needed
            return;
        }
        List<MigrationTask> migrationTasks = mMigrationTasks.stream()
                // Any tasks with toVersion > fromVersion hasn't been run yet
                .filter(task -> task.toVersion > fromVersion)
                // Migration performed in ascending order
                .sorted(Comparator.comparingLong((MigrationTask task) -> task.fromVersion)
                        .thenComparingLong(task -> task.toVersion))
                .collect(Collectors.toList());
        for (MigrationTask migrationTask : migrationTasks) {
            if (migrationTask.mainThread) {
                ThreadUtils.postOnMainThread(migrationTask);
            } else migrationTask.run();
        }
    }
}
