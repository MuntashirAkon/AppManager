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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.FragmentManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagDisabledComponents;
import static io.github.muntashirakon.AppManager.utils.PackageUtils.flagSigningInfo;

public class PackageInstallerActivity extends BaseActivity {
    public static final String EXTRA_APK_FILE_KEY = "EXTRA_APK_FILE_KEY";
    public static final String ACTION_PACKAGE_INSTALLED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_INSTALLED";

    private ApkFile apkFile;
    private String appLabel;
    private PackageInfo packageInfo;
    private PackageInfo installedPackageInfo;
    private String actionName;
    private PackageManager mPackageManager;
    private FragmentManager fm;
    private String packageName;
    private boolean isSignatureDifferent = false;
    private int sessionId = -1;
    private boolean closeApkFile = true;
    private int apkFileKey;
    private final StoragePermission storagePermission = StoragePermission.init(this);
    private final ActivityResultLauncher<Intent> confirmIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // User did some interaction and the installer screen is closed now
                Intent broadcastIntent = new Intent(AMPackageInstaller.ACTION_INSTALL_INTERACTION_END);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                getApplicationContext().sendBroadcast(broadcastIntent);
                finish();
            });
    private final ActivityResultLauncher<Intent> uninstallIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    // No need for user handle since it is only applicable for the current user (no-root)
                    getPackageManager().getPackageInfo(packageName, 0);
                    // The package is still installed meaning that the app uninstall wasn't successful
                    Toast.makeText(this, getString(R.string.failed_to_install_package_name, appLabel), Toast.LENGTH_SHORT).show();
                } catch (PackageManager.NameNotFoundException e) {
                    install();
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
        Log.d("PIA", "On create, intent: " + intent.toString());
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            onNewIntent(intent);
            return;
        }
        final Uri apkUri = intent.getData();
        apkFileKey = intent.getIntExtra(EXTRA_APK_FILE_KEY, -1);
        if (apkUri == null && apkFileKey == -1) {
            finish();
            return;
        }
        final AlertDialog progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading));
        mPackageManager = getPackageManager();
        fm = getSupportFragmentManager();
        progressDialog.show();
        new Thread(() -> {
            try {
                if (apkUri != null) {
                    apkFileKey = ApkFile.createInstance(apkUri, intent.getType());
                } else {
                    closeApkFile = false;  // Internal request, don't close the ApkFile
                }
                apkFile = ApkFile.getInstance(apkFileKey);
                packageInfo = getPackageInfo();
                packageName = packageInfo.packageName;
                try {
                    installedPackageInfo = getInstalledPackageInfo(packageName);
                } catch (PackageManager.NameNotFoundException ignore) {
                }
                appLabel = mPackageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                Drawable appIcon = mPackageManager.getApplicationIcon(packageInfo.applicationInfo);
                runOnUiThread(() -> {
                    if (!isDestroyed()) progressDialog.dismiss();
                });
                if (installedPackageInfo == null) {
                    // App not installed
                    actionName = getString(R.string.install);
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
                } else {
                    // App is installed
                    long installedVersionCode = PackageInfoCompat.getLongVersionCode(installedPackageInfo);
                    long thisVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
                    isSignatureDifferent = PackageUtils.isSignatureDifferent(packageInfo, installedPackageInfo);
                    if (installedVersionCode < thisVersionCode) {
                        // Needs update
                        actionName = getString(R.string.update);
                        displayWhatsNewDialog();
                    } else if (installedVersionCode == thisVersionCode) {
                        // Issue reinstall
                        actionName = getString(R.string.reinstall);
                        if (isSignatureDifferent) {
                            // Display what's new dialog
                            displayWhatsNewDialog();
                        } else {
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
                        }
                    } else {
                        actionName = getString(R.string.downgrade);
                        if (AppPref.isRootOrAdbEnabled()) {
                            displayWhatsNewDialog();
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(this, R.string.downgrade_not_possible, Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    }
                }
            } catch (ApkFile.ApkFileException | PackageManager.NameNotFoundException | IOException e) {
                Log.e("PIA", "Could not fetch package info.", e);
                runOnUiThread(this::finish);
            }
        }).start();
    }

//    @NonNull
//    private String getVersionInfoWithTrackers() {
//        long newVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
//        String newVersionName = packageInfo.versionName;
//        int trackers = ComponentUtils.getTrackerComponentsForPackageInfo(packageInfo).size();
//        StringBuilder sb = new StringBuilder(getString(R.string.version_name_with_code, newVersionName, newVersionCode));
//        if (trackers > 0) {
//            sb.append(", ").append(getResources().getQuantityString(R.plurals.no_of_trackers, trackers, trackers));
//        }
//        return sb.toString();
//    }

    @NonNull
    private PackageInfo getPackageInfo() throws PackageManager.NameNotFoundException, IOException {
        String apkPath = apkFile.getBaseEntry().getSignedFile(this).getAbsolutePath();
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

    @WorkerThread
    private void displayWhatsNewDialog() {
        Bundle args = new Bundle();
        args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, packageInfo);
        args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, installedPackageInfo);
        args.putString(WhatsNewDialogFragment.ARG_INSTALL_NAME, actionName);
        WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
        dialogFragment.setCancelable(false);
        dialogFragment.setArguments(args);
        dialogFragment.setOnTriggerInstall(new WhatsNewDialogFragment.InstallInterface() {
            @Override
            public void triggerInstall() {
                if (isSignatureDifferent) {
                    // Signature is different, offer to uninstall and then install apk
                    // only if the app is not a system app
                    // TODO(8/10/20): Handle apps uninstalled with DONT_DELETE_DATA flag
                    ApplicationInfo info = installedPackageInfo.applicationInfo;  // Installed package info is never null here.
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        Toast.makeText(PackageInstallerActivity.this,
                                R.string.signature_mismatch_for_system_apps,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SpannableStringBuilder builder = new SpannableStringBuilder()
                            .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                            .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                            .append("\n\n");
                    CharSequence only_install_message = getText(R.string.install_without_data_loss);
                    int start = builder.length();
                    builder.append(only_install_message);
                    builder.setSpan(new RelativeSizeSpan(0.8f), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    new MaterialAlertDialogBuilder(PackageInstallerActivity.this)
                            .setIcon(getPackageManager().getApplicationIcon(info))
                            .setTitle(appLabel)
                            .setMessage(builder)
                            .setPositiveButton(R.string.yes, (dialog, which) -> {
                                // Uninstall and then install again
                                if (AppPref.isRootOrAdbEnabled()) {
                                    // User must be all
                                    RunnerUtils.uninstallPackageWithData(packageName, RunnerUtils.USER_ALL);
                                    install();
                                } else {
                                    // Uninstall using service, not guaranteed to work
                                    // since it only uninstalls for the current user
                                    Intent intent = new Intent(Intent.ACTION_DELETE);
                                    intent.setData(Uri.parse("package:" + packageName));
                                    uninstallIntentLauncher.launch(intent);
                                }
                            })
                            .setNegativeButton(R.string.no, (dialog, which) -> finish())
                            .setNeutralButton(R.string.only_install, (dialog, which) -> install())
                            .show();
                } else {
                    // Signature is either matched or the app isn't installed
                    install();
                }
            }

            @Override
            public void triggerCancel() {
                finish();
            }
        });
        runOnUiThread(() -> {
            if (!isFinishing()) {
                dialogFragment.show(getSupportFragmentManager(), WhatsNewDialogFragment.TAG);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.clear();
        super.onSaveInstanceState(outState);
    }

    private void install() {
        if (apkFile.hasObb() && !AppPref.isRootOrAdbEnabled()) {
            // Need to request permissions if not given
            storagePermission.request(granted -> {
                if (granted) launchInstaller();
            });
        } else launchInstaller();
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
        // Select user
        if (AppPref.isRootOrAdbEnabled() && (boolean) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_USERS_BOOL)) {
            new Thread(() -> {
                // Init local server first
                LocalServer.getInstance();
                List<UserInfo> users = Users.getUsers();
                if (users != null && users.size() > 1) {
                    String[] userNames = new String[users.size() + 1];
                    int[] userHandles = new int[users.size() + 1];
                    userNames[0] = getString(R.string.backup_all_users);
                    userHandles[0] = RunnerUtils.USER_ALL;
                    int i = 1;
                    for (UserInfo info : users) {
                        userNames[i] = info.name == null ? String.valueOf(info.id) : info.name;
                        userHandles[i] = info.id;
                        ++i;
                    }
                    AtomicInteger userHandle = new AtomicInteger(RunnerUtils.USER_ALL);
                    runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                            .setCancelable(false)
                            .setTitle(R.string.select_user)
                            .setSingleChoiceItems(userNames, 0, (dialog, which) ->
                                    userHandle.set(userHandles[which]))
                            .setPositiveButton(R.string.ok, (dialog, which) ->
                                    doLaunchInstallerService(userHandle.get()))
                            .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                            .show());
                } else runOnUiThread(() -> doLaunchInstallerService(Users.getCurrentUserHandle()));
            }).start();
        } else doLaunchInstallerService(Users.getCurrentUserHandle());
    }

    private void doLaunchInstallerService(int userHandle) {
        Intent intent = new Intent(this, PackageInstallerService.class);
        intent.putExtra(PackageInstallerService.EXTRA_APK_FILE_KEY, apkFileKey);
        intent.putExtra(PackageInstallerService.EXTRA_APP_LABEL, appLabel);
        intent.putExtra(PackageInstallerService.EXTRA_USER_ID, userHandle);
        intent.putExtra(PackageInstallerService.EXTRA_CLOSE_APK_FILE, closeApkFile);
        ContextCompat.startForegroundService(this, intent);
        closeApkFile = false;
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("PIA", "New intent called: " + intent.toString());
        // Check for action first
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            try {
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
                if (confirmIntent == null)
                    throw new Exception("Empty confirmation intent.");
                Log.d("PIA", "Requesting user confirmation for package " + packageName);
                confirmIntentLauncher.launch(confirmIntent);
            } catch (Exception e) {
                e.printStackTrace();
                AMPackageInstaller.sendCompletedBroadcast(packageName, AMPackageInstaller.STATUS_FAILURE_INCOMPATIBLE_ROM, sessionId);
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (closeApkFile && apkFile != null) apkFile.close();
        super.onDestroy();
    }
}
