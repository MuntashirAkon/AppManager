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

package io.github.muntashirakon.AppManager.apk.installer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.apk.ApkFile;

public abstract class AMPackageInstaller {
    public static final String EXTRA_STATUS = "EXTRA_STATUS";
    public static final String EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME";
    public static final String EXTRA_OTHER_PACKAGE_NAME = "EXTRA_OTHER_PACKAGE_NAME";

    public static final String ACTION_INSTALL_STARTED = BuildConfig.APPLICATION_ID + ".action.INSTALL_STARTED";
    public static final String ACTION_INSTALL_COMPLETED = BuildConfig.APPLICATION_ID + ".action.INSTALL_COMPLETED";

    /**
     * See {@link PackageInstaller#STATUS_SUCCESS}
     */
    public static final int STATUS_SUCCESS = PackageInstaller.STATUS_SUCCESS;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_ABORTED}
     */
    public static final int STATUS_FAILURE_ABORTED = PackageInstaller.STATUS_FAILURE_ABORTED;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_BLOCKED}
     */
    public static final int STATUS_FAILURE_BLOCKED = PackageInstaller.STATUS_FAILURE_BLOCKED;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_CONFLICT}
     */
    public static final int STATUS_FAILURE_CONFLICT = PackageInstaller.STATUS_FAILURE_CONFLICT;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_INCOMPATIBLE}
     */
    public static final int STATUS_FAILURE_INCOMPATIBLE = PackageInstaller.STATUS_FAILURE_INCOMPATIBLE;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_INVALID}
     */
    public static final int STATUS_FAILURE_INVALID = PackageInstaller.STATUS_FAILURE_INVALID;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_STORAGE}
     */
    public static final int STATUS_FAILURE_STORAGE = PackageInstaller.STATUS_FAILURE_STORAGE;
    // Custom status
    /**
     * The operation failed because the apk file(s) are not accessible.
     */
    public static final int STATUS_FAILURE_SECURITY = -2;
    /**
     * The operation failed because it failed to create an installer session.
     */
    public static final int STATUS_FAILURE_SESSION_CREATE = -3;
    /**
     * The operation failed because it failed to write apk files to session.
     */
    public static final int STATUS_FAILURE_SESSION_WRITE = -4;
    /**
     * The operation failed because it could not commit the installer session.
     */
    public static final int STATUS_FAILURE_SESSION_COMMIT = -5;
    /**
     * The operation failed because it could not abandon the installer session. This is a redundant
     * failure.
     */
    public static final int STATUS_FAILURE_SESSION_ABANDON = -6;
    /**
     * The operation failed because the current ROM is incompatible with PackageInstaller
     */
    public static final int STATUS_FAILURE_INCOMPATIBLE_ROM = -7;

    @SuppressLint("StaticFieldLeak")
    protected static final Context context = AppManager.getContext();

    public abstract boolean install(@NonNull ApkFile apkFile);

    public abstract boolean install(@NonNull File[] apkFiles, String packageName);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    abstract boolean openSession();

    abstract boolean abandon();

    abstract boolean commit();

    static void sendStartedBroadcast(String packageName) {
        Intent broadcastIntent = new Intent(ACTION_INSTALL_STARTED);
        broadcastIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        context.sendBroadcast(broadcastIntent);
    }

    static void sendCompletedBroadcast(String packageName, int status) {
        Intent broadcastIntent = new Intent(ACTION_INSTALL_COMPLETED);
        broadcastIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.putExtra(EXTRA_STATUS, status);
        context.sendBroadcast(broadcastIntent);
    }
}
