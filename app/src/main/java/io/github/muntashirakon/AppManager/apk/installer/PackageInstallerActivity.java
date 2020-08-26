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

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;

public class PackageInstallerActivity extends AppCompatActivity {
    private ApkFile apkFile;
    private PackageManager mPackageManager;

    private int flagSigningInfo;
    private int flagDisabledComponents;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        final Uri apkUri = intent.getData();
        if (apkUri == null) {
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            flagSigningInfo = PackageManager.GET_SIGNING_CERTIFICATES;
        else flagSigningInfo = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            flagDisabledComponents = PackageManager.MATCH_DISABLED_COMPONENTS;
        else flagDisabledComponents = PackageManager.GET_DISABLED_COMPONENTS;
        mPackageManager = getPackageManager();
        new Thread(() -> {
            try {
                apkFile = new ApkFile(apkUri);
                PackageInfo packageInfo = getPackageInfo();
                PackageInfo installedPackageInfo = null;
                try {
                    installedPackageInfo = getInstalledPackageInfo(packageInfo.packageName);
                } catch (PackageManager.NameNotFoundException ignore) {
                }
                String appLabel = mPackageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                Drawable appIcon = mPackageManager.getApplicationIcon(packageInfo.applicationInfo);
                if (installedPackageInfo == null) {
                    // App not installed
                    if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
                        runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                                .setCancelable(false)
                                .setTitle(appLabel)
                                .setIcon(appIcon)
                                .setMessage(R.string.install_app_message)
                                .setPositiveButton(R.string.install, (dialog, which) -> install(appLabel))
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                                .show());
                    } else install(appLabel);
                } else {
                    // App is installed
                    long installedVersionCode = PackageUtils.getVersionCode(installedPackageInfo);
                    long thisVersionCode = PackageUtils.getVersionCode(packageInfo);
                    if (installedVersionCode < thisVersionCode) {  // FIXME: Check for signature
                        // Needs update
                        Bundle args = new Bundle();
                        args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, packageInfo);
                        args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, installedPackageInfo);
                        WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnTriggerInstall(() -> install(appLabel));
                        runOnUiThread(() -> dialogFragment.show(getSupportFragmentManager(), WhatsNewDialogFragment.TAG));
                    } else if (installedVersionCode == thisVersionCode) {
                        // Issue reinstall
                        if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
                            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                                    .setCancelable(false)
                                    .setTitle(appLabel)
                                    .setIcon(appIcon)
                                    .setMessage(R.string.reinstall_app_message)
                                    .setPositiveButton(R.string.reinstall, (dialog, which) -> install(appLabel))
                                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                                    .show());
                        } else install(appLabel);
                    } else {
                        // TODO: Add option to downgrade
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Downgrade is not currently possible in App Manager.", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(this::finish);
            }
        }).start();
    }

    @NonNull
    private PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException {
        int flagSigningInfo = PackageManager.GET_SIGNATURES;
        String apkPath = apkFile.getBaseEntry().source.getAbsolutePath();
        PackageInfo packageInfo = mPackageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null)
            throw new PackageManager.NameNotFoundException("Package cannot be parsed.");
        packageInfo.applicationInfo.sourceDir = apkPath;
        packageInfo.applicationInfo.publicSourceDir = apkPath;
        return packageInfo;
    }

    @NonNull
    private PackageInfo getInstalledPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null)
            throw new PackageManager.NameNotFoundException("Package not found.");
        return packageInfo;
    }

    @NonNull
    private File[] getApkFiles() {
        List<ApkFile.Entry> entries = apkFile.getEntries();
        File[] apkFiles = new File[entries.size()];
        for (int i = 0; i < entries.size(); ++i) {
            apkFiles[i] = entries.get(i).source;
        }
        return apkFiles;
    }

    private void install(String appLabel) {
        new Thread(() -> {
            try {
                PackageUtils.installApkCompat(appLabel, getApkFiles());
                runOnUiThread(this::finish);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, getString(R.string.failed_to_install_package_name, appLabel), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (apkFile != null) apkFile.close();
        super.onDestroy();
    }
}
