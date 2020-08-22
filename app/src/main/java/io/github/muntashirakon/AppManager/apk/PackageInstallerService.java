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

package io.github.muntashirakon.AppManager.apk;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class PackageInstallerService extends Service {
    @Override
    public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                try {
                    Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmationIntent == null) throw new Exception("Empty confirmation intent.");
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirmationIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), R.string.installer_error_lidl_rom, Toast.LENGTH_LONG).show();
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Toast.makeText(getApplicationContext(), getString(R.string.package_name_is_installed_successfully, PackageUtils.getPackageLabel(getPackageManager(), intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME))), Toast.LENGTH_LONG).show();
                break;
            default:
                Toast.makeText(getApplicationContext(), getErrorString(status, intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME)), Toast.LENGTH_LONG).show();
                break;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    public String getErrorString(int status, String blockingPackage) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return getString(R.string.installer_error_aborted);
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                String blocker = getString(R.string.installer_error_blocked_device);
                if (blockingPackage != null) {
                    blocker = PackageUtils.getPackageLabel(getPackageManager(), blockingPackage);
                }
                return getString(R.string.installer_error_blocked, blocker);
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return getString(R.string.installer_error_conflict);
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return getString(R.string.installer_error_incompatible);
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return getString(R.string.installer_error_bad_apks);
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return getString(R.string.installer_error_storage);
        }
        return getString(R.string.installer_error_generic);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
