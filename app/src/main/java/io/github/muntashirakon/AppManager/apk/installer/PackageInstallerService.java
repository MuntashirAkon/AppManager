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

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.logs.Log;

public class PackageInstallerService extends Service {
    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                try {
                    Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmationIntent == null)
                        throw new Exception("Empty confirmation intent.");
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirmationIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                    AMPackageInstaller.sendCompletedBroadcast(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME), AMPackageInstaller.STATUS_FAILURE_INCOMPATIBLE_ROM);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                AMPackageInstaller.sendCompletedBroadcast(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME), AMPackageInstaller.STATUS_SUCCESS);
                break;
            default:
                Intent broadcastIntent = new Intent(AMPackageInstaller.ACTION_INSTALL_COMPLETED);
                broadcastIntent.putExtra(AMPackageInstaller.EXTRA_PACKAGE_NAME, intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME));
                broadcastIntent.putExtra(AMPackageInstaller.EXTRA_OTHER_PACKAGE_NAME, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME));
                broadcastIntent.putExtra(AMPackageInstaller.EXTRA_STATUS, status);
                getApplication().sendBroadcast(broadcastIntent);
                Log.e("PIS", "" + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                break;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
