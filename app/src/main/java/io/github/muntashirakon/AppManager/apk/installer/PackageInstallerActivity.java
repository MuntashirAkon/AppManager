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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class PackageInstallerActivity extends BaseActivity {
    public static final String EXTRA_APK_FILE_KEY = "EXTRA_APK_FILE_KEY";

    private ApkFile apkFile;
    private String appLabel;
    private PackageInfo packageInfo;
    private String actionName;
    private PackageManager mPackageManager;
    FragmentManager fm;
    private boolean closeApkFile = true;
    private int apkFileKey;
    private ActivityResultLauncher<String[]> permInstallWithObb = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Utils.getExternalStoragePermissions(this) == null) {
                    launchInstaller();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        final Uri apkUri = intent.getData();
        apkFileKey = intent.getIntExtra(EXTRA_APK_FILE_KEY, -1);
        if (apkUri == null && apkFileKey == -1) {
            finish();
            return;
        }
        mPackageManager = getPackageManager();
        fm = getSupportFragmentManager();
        new Thread(() -> {
            try {
                if (apkUri != null) {
                    apkFileKey = ApkFile.createInstance(apkUri);
                } else {
                    closeApkFile = false;  // Internal request, don't close the ApkFile
                }
                apkFile = ApkFile.getInstance(apkFileKey);
                packageInfo = getPackageInfo();
                PackageInfo installedPackageInfo = null;
                try {
                    installedPackageInfo = getInstalledPackageInfo(packageInfo.packageName);
                } catch (PackageManager.NameNotFoundException ignore) {
                }
                appLabel = mPackageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                Drawable appIcon = mPackageManager.getApplicationIcon(packageInfo.applicationInfo);
                if (installedPackageInfo == null) {
                    // App not installed
                    actionName = getString(R.string.install);
                    if (AppPref.isRootOrAdbEnabled()) {
                        if (apkFile.isSplit()) {
                            install();
                        } else {
                            runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                                    .setCancelable(false)
                                    .setTitle(appLabel)
                                    .setIcon(appIcon)
                                    .setMessage(R.string.install_app_message)
                                    .setPositiveButton(R.string.install, (dialog, which) -> install())
                                    .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                                    .show());
                        }
                    } else install();
                } else {
                    // App is installed
                    long installedVersionCode = PackageUtils.getVersionCode(installedPackageInfo);
                    long thisVersionCode = PackageUtils.getVersionCode(packageInfo);
                    if (installedVersionCode < thisVersionCode) {  // FIXME: Check for signature
                        // Needs update
                        actionName = getString(R.string.update);
                        Bundle args = new Bundle();
                        args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, packageInfo);
                        args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, installedPackageInfo);
                        args.putString(WhatsNewDialogFragment.ARG_INSTALL_NAME, actionName);
                        WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
                        dialogFragment.setCancelable(false);
                        dialogFragment.setArguments(args);
                        dialogFragment.setOnTriggerInstall(
                                new WhatsNewDialogFragment.InstallInterface() {
                                    @Override
                                    public void triggerInstall() {
                                        install();
                                    }

                                    @Override
                                    public void triggerCancel() {
                                        finish();
                                    }
                                });
                        runOnUiThread(() -> dialogFragment.show(fm, WhatsNewDialogFragment.TAG));
                    } else if (installedVersionCode == thisVersionCode) {
                        // Issue reinstall
                        actionName = getString(R.string.reinstall);
                        if (AppPref.isRootOrAdbEnabled()) {
                            if (apkFile.isSplit()) {
                                install();
                            } else {
                                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                                        .setCancelable(false)
                                        .setTitle(appLabel)
                                        .setIcon(appIcon)
                                        .setMessage(R.string.reinstall_app_message)
                                        .setPositiveButton(R.string.reinstall, (dialog, which) -> install())
                                        .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                                        .show());
                            }
                        } else install();
                    } else {
                        actionName = getString(R.string.downgrade);
                        if (AppPref.isRootOrAdbEnabled()) {
                            Bundle args = new Bundle();
                            args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, packageInfo);
                            args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, installedPackageInfo);
                            args.putString(WhatsNewDialogFragment.ARG_INSTALL_NAME, actionName);
                            WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
                            dialogFragment.setCancelable(false);
                            dialogFragment.setArguments(args);
                            dialogFragment.setOnTriggerInstall(
                                    new WhatsNewDialogFragment.InstallInterface() {
                                        @Override
                                        public void triggerInstall() {
                                            install();
                                        }

                                        @Override
                                        public void triggerCancel() {
                                            finish();
                                        }
                                    });
                            runOnUiThread(() -> dialogFragment.show(fm, WhatsNewDialogFragment.TAG));
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.downgrade_not_possible, Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    }
                }
            } catch (ApkFile.ApkFileException | PackageManager.NameNotFoundException | IOException e) {
                e.printStackTrace();
                runOnUiThread(this::finish);
            }
        }).start();
    }

    @NonNull
    private PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException, IOException {
        String apkPath = apkFile.getBaseEntry().getCachedFile().getAbsolutePath();
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = mPackageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                | flagDisabledComponents | PackageManager.GET_SIGNATURES | PackageManager.GET_CONFIGURATIONS
                | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null)
            throw new PackageManager.NameNotFoundException("Package cannot be parsed.");
        packageInfo.applicationInfo.sourceDir = apkPath;
        packageInfo.applicationInfo.publicSourceDir = apkPath;
        return packageInfo;
    }

    @NonNull
    private PackageInfo getInstalledPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        @SuppressLint("WrongConstant")
        PackageInfo packageInfo = mPackageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS
                | PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS
                | flagDisabledComponents | flagSigningInfo | PackageManager.GET_CONFIGURATIONS
                | PackageManager.GET_SHARED_LIBRARY_FILES);
        if (packageInfo == null)
            throw new PackageManager.NameNotFoundException("Package not found.");
        return packageInfo;
    }

    private void install() {
        if (apkFile.hasObb() && !AppPref.isRootOrAdbEnabled()) {
            // Need to request permissions if not given
            String[] permissions = Utils.getExternalStoragePermissions(this);
            if (permissions != null) {
                permInstallWithObb.launch(permissions);
                return;
            }
        }
        launchInstaller();
    }

    private void launchInstaller() {
        if (apkFile.isSplit()) {
            SplitApkChooser splitApkChooser = new SplitApkChooser();
            Bundle args = new Bundle();
            args.putInt(SplitApkChooser.EXTRA_APK_FILE_KEY, apkFileKey);
            args.putString(SplitApkChooser.EXTRA_ACTION_NAME, actionName);
            args.putParcelable(SplitApkChooser.EXTRA_APP_INFO, packageInfo.applicationInfo);
            splitApkChooser.setArguments(args);
            splitApkChooser.setCancelable(false);
            splitApkChooser.setOnTriggerInstall(new SplitApkChooser.InstallInterface() {
                @Override
                public void triggerInstall() {
                    launchInstallService();
                }

                @Override
                public void triggerCancel() {
                    PackageInstallerActivity.this.finish();
                }
            });
            splitApkChooser.show(fm, SplitApkChooser.TAG);
        } else {
            launchInstallService();
        }
    }

    private void launchInstallService() {
        Intent intent = new Intent(this, AMPackageInstallerService.class);
        intent.putExtra(AMPackageInstallerService.EXTRA_APK_FILE_KEY, apkFileKey);
        intent.putExtra(AMPackageInstallerService.EXTRA_APP_LABEL, appLabel);
        intent.putExtra(AMPackageInstallerService.EXTRA_CLOSE_APK_FILE, closeApkFile);
        ContextCompat.startForegroundService(AppManager.getContext(), intent);
        closeApkFile = false;
        finish();
    }

    @Override
    protected void onDestroy() {
        if (closeApkFile && apkFile != null) apkFile.close();
        super.onDestroy();
    }
}
