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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.utils.IOUtils;

public final class PackageInstallerNoRoot implements IPackageInstaller {
    public static final String TAG = "SAI";

    private static PackageInstallerNoRoot INSTANCE;

    public static PackageInstallerNoRoot getInstance() {
        if (INSTANCE == null) INSTANCE = new PackageInstallerNoRoot();
        return INSTANCE;
    }

    private PackageInstaller packageInstaller;
    private PackageInstaller.Session session;

    private PackageInstallerNoRoot() {
    }

    @Override
    public boolean installMultiple(File[] apkFiles) {
        Context context = AppManager.getContext();
        packageInstaller = context.getPackageManager().getPackageInstaller();
        // Clean old sessions
        cleanOldSessions();
        // Create install session
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER);
        int sessionId;
        try {
            sessionId = packageInstaller.createSession(sessionParams);
        } catch (IOException e) {
            Log.e(TAG, "InstallMultiple: Failed to create install session.", e);
            return false;
        }
        try {
            session = packageInstaller.openSession(sessionId);
        } catch (IOException e) {
            Log.e(TAG, "InstallMultiple: Failed to open install session.", e);
            return false;
        }
        // Write apk files
        for (File apkFile : apkFiles) {
            try (InputStream apkInputStream = new FileInputStream(apkFile);
                 OutputStream apkOutputStream = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
                IOUtils.copy(apkInputStream, apkOutputStream);
                session.fsync(apkOutputStream);
            } catch (IOException e) {
                Log.e(TAG, "InstallMultiple: Cannot access copy files to session.", e);
                return abandon();
            } catch (SecurityException e) {
                Log.e(TAG, "InstallMultiple: Cannot access apk files.", e);
                return abandon();
            }
        }
        // Commit
        Intent callbackIntent = new Intent(context, PackageInstallerService.class);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, callbackIntent, 0);
        session.commit(pendingIntent.getIntentSender());
        return true;
    }

    private void cleanOldSessions() {
        for (PackageInstaller.SessionInfo sessionInfo : packageInstaller.getMySessions()) {
            try {
                packageInstaller.abandonSession(sessionInfo.getSessionId());
            } catch (Exception e) {
                Log.w(TAG, "CleanOldSessions: Unable to abandon session", e);
            }
        }
    }

    private boolean abandon() {
        if (session != null) session.close();
        return false;
    }
}
