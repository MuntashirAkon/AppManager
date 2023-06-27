// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import io.github.muntashirakon.io.Path;

public class FmTasks {
    // It's okay to use a singleton class since we won't be following application lifecycle.
    // If the app instance is destroyed, tasks will cease to exist.
    private static final FmTasks sInstance = new FmTasks();

    public static FmTasks getInstance() {
        return sInstance;
    }

    private final Queue<FmTask> taskList = new LinkedList<>();

    public void enqueue(FmTask fmTask) {
        // Currently, we only allow a single task. So, clear the queue first.
        taskList.clear();
        taskList.add(fmTask);
    }

    @Nullable
    public FmTask peek() {
        return taskList.peek();
    }

    @Nullable
    public FmTask dequeue() {
        FmTask task = peek();
        if (task != null && task.type == FmTask.TYPE_COPY) {
            // Copy is allowed multiple times but others aren't
            return task;
        }
        return taskList.poll();
    }

    public boolean isEmpty() {
        return taskList.isEmpty();
    }

    public static class FmTask {
        @IntDef({TYPE_COPY, TYPE_CUT})
        @Retention(RetentionPolicy.SOURCE)
        public @interface TaskType {
        }

        public static final int TYPE_COPY = 0;
        public static final int TYPE_CUT = 1;

        @TaskType
        public final int type;
        public final long timestamp;
        public final List<Path> files;

        private int mFlags;

        public FmTask(@TaskType int type, List<Path> files) {
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.files = new ArrayList<>(files);
        }

        public boolean canPaste() {
            return type == TYPE_COPY || type == TYPE_CUT;
        }

        public void addFlag(int flag) {
            mFlags |= flag;
        }

        public void removeFlag(int flag) {
            mFlags &= ~flag;
        }

        public boolean hasFlag(int flag) {
            return (mFlags & flag) != 0;
        }
    }
}
