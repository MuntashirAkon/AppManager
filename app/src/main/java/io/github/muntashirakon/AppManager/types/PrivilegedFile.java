/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.types;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class PrivilegedFile extends File {
    public PrivilegedFile(@NonNull String pathname) {
        super(pathname);
    }

    public PrivilegedFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
    }

    public PrivilegedFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
    }

    @Override
    public boolean delete() {
        boolean isDeleted = false;
        try {
            isDeleted = super.delete();
        } catch (SecurityException ignore) {
        }
        if (!isDeleted && AppPref.isRootOrAdbEnabled()) {
            RunnerUtils.deleteFile(getAbsolutePath(), false);
            return !RunnerUtils.fileExists(getAbsolutePath());
        }
        return isDeleted;
    }

    public boolean forceDelete() {
        boolean isDeleted = false;
        try {
            isDeleted = super.delete();
        } catch (SecurityException ignore) {
        }
        if (!isDeleted && AppPref.isRootOrAdbEnabled()) {
            RunnerUtils.deleteFile(getAbsolutePath(), true);
            return !RunnerUtils.fileExists(getAbsolutePath());
        }
        return isDeleted;
    }

    @Override
    public boolean exists() {
        boolean isExists = false;
        try {
            isExists = super.exists();
        } catch (SecurityException ignore) {
        }
        if (!isExists && AppPref.isRootOrAdbEnabled()) {
            return RunnerUtils.fileExists(getAbsolutePath());
        }
        return isExists;
    }

    @Override
    public boolean mkdir() {
        boolean isCreated = false;
        try {
            isCreated = super.mkdir();
        } catch (SecurityException ignore) {
        }
        if (!isCreated && AppPref.isRootOrAdbEnabled()) {
            return RunnerUtils.mkdir(getAbsolutePath());
        }
        return isCreated;
    }

    @Override
    public boolean mkdirs() {
        boolean isCreated = false;
        try {
            isCreated = super.mkdirs();
        } catch (SecurityException ignore) {
        }
        if (!isCreated && AppPref.isRootOrAdbEnabled()) {
            return RunnerUtils.mkdirs(getAbsolutePath());
        }
        return isCreated;
    }

    @Override
    public boolean renameTo(@NonNull File dest) {
        boolean isRenamed = false;
        try {
            isRenamed = super.renameTo(dest);
        } catch (SecurityException ignore) {
        }
        if (!isRenamed && AppPref.isRootOrAdbEnabled()) {
            return RunnerUtils.mv(this, dest);
        }
        return isRenamed;
    }
}
