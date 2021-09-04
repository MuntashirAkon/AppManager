// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getDialogTitle;

public class PackageInstallerActivity extends BaseActivity implements WhatsNewDialogFragment.InstallInterface {
    public static final String EXTRA_APK_FILE_KEY = "EXTRA_APK_FILE_KEY";
    public static final String ACTION_PACKAGE_INSTALLED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_INSTALLED";

    private int sessionId = -1;
    private String packageName;
    private final ActivityResultLauncher<Intent> confirmIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // User did some interaction and the installer screen is closed now
                Intent broadcastIntent = new Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_END);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                getApplicationContext().sendBroadcast(broadcastIntent);
                if (!hasNext()) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            });

    @SuppressWarnings("FieldCanBeLocal") // Cannot be local
    @Nullable
    private List<Uri> apkUris;
    @Nullable
    private Iterator<Uri> uriIterator;
    @Nullable
    private String mimeType;
    private int actionName;
    private FragmentManager fm;
    private AlertDialog progressDialog;
    private PackageInstallerViewModel model;
    private final StoragePermission storagePermission = StoragePermission.init(this);
    private final ActivityResultLauncher<Intent> uninstallIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    // No need for user handle since it is only applicable for the current user (no-root)
                    getPackageManager().getPackageInfo(model.getPackageName(), 0);
                    // The package is still installed meaning that the app uninstall wasn't successful
                    UIUtils.displayLongToast(R.string.failed_to_install_package_name, model.getAppLabel());
                    triggerCancel();
                } catch (PackageManager.NameNotFoundException e) {
                    install();
                }
            });

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        final Intent intent = getIntent();
        if (intent == null) {
            triggerCancel();
            return;
        }
        Log.d("PIA", "On create, intent: " + intent);
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            onNewIntent(intent);
            return;
        }
        model = new ViewModelProvider(this).get(PackageInstallerViewModel.class);
        progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading));
        fm = getSupportFragmentManager();
        progressDialog.show();
        mimeType = intent.getType();
        apkUris = IntentCompat.getDataUris(intent);
        if (apkUris != null) uriIterator = apkUris.listIterator();
        int apkFileKey = intent.getIntExtra(EXTRA_APK_FILE_KEY, -1);
        if (apkFileKey != -1) {
            model.getPackageInfo(apkFileKey);
            // If URIs are supplied, they will also be read
        } else {
            // Only URIs may be supplied
            goToNext();
        }
        model.packageInfoLiveData().observe(this, newPackageInfo -> {
            progressDialog.dismiss();
            if (newPackageInfo == null) {
                UIUtils.displayLongToast(R.string.failed_to_fetch_package_info);
                triggerCancel();
                return;
            }
            if (model.getInstalledPackageInfo() == null) {
                // App not installed or data not cleared
                actionName = R.string.install;
                if (model.getApkFile().isSplit() || !AppPref.isRootOrAdbEnabled()) {
                    install();
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setCancelable(false)
                            .setCustomTitle(getDialogTitle(this, model.getAppLabel(), model.getAppIcon(),
                                    getVersionInfoWithTrackers(newPackageInfo)))
                            .setMessage(R.string.install_app_message)
                            .setPositiveButton(R.string.install, (dialog, which) -> install())
                            .setNegativeButton(R.string.cancel, (dialog, which) -> triggerCancel())
                            .show();
                }
            } else {
                // App is installed or the app is uninstalled without clearing data or the app is uninstalled,
                // but it's a system app
                long installedVersionCode = PackageInfoCompat.getLongVersionCode(model.getInstalledPackageInfo());
                long thisVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo);
                if (installedVersionCode < thisVersionCode) {
                    // Needs update
                    actionName = R.string.update;
                    displayWhatsNewDialog();
                } else if (installedVersionCode == thisVersionCode) {
                    // Issue reinstall
                    actionName = R.string.reinstall;
                    displayWhatsNewDialog();
                } else {
                    // Downgrade
                    actionName = R.string.downgrade;
                    if (AppPref.isRootOrAdbEnabled()) {
                        displayWhatsNewDialog();
                    } else {
                        UIUtils.displayLongToast(R.string.downgrade_not_possible);
                        triggerCancel();
                    }
                }
            }
        });
    }

    @UiThread
    private void displayWhatsNewDialog() {
        if (!AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_CHANGES_BOOL)) {
            if (!AppPref.isRootOrAdbEnabled()) {
                triggerInstall();
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setCancelable(false)
                    .setCustomTitle(getDialogTitle(this, model.getAppLabel(), model.getAppIcon(),
                            getVersionInfoWithTrackers(model.getNewPackageInfo())))
                    .setMessage(R.string.install_app_message)
                    .setPositiveButton(actionName, (dialog, which) -> triggerInstall())
                    .setNegativeButton(R.string.cancel, (dialog, which) -> triggerCancel())
                    .show();
            return;
        }
        Bundle args = new Bundle();
        args.putParcelable(WhatsNewDialogFragment.ARG_NEW_PKG_INFO, model.getNewPackageInfo());
        args.putParcelable(WhatsNewDialogFragment.ARG_OLD_PKG_INFO, model.getInstalledPackageInfo());
        args.putString(WhatsNewDialogFragment.ARG_INSTALL_NAME, getString(actionName));
        WhatsNewDialogFragment dialogFragment = new WhatsNewDialogFragment();
        dialogFragment.setCancelable(false);
        dialogFragment.setArguments(args);
        dialogFragment.setOnTriggerInstall(this);
        dialogFragment.show(getSupportFragmentManager(), WhatsNewDialogFragment.TAG);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.clear();
        super.onSaveInstanceState(outState);
    }

    @UiThread
    private void install() {
        if (model.getApkFile().hasObb() && !AppPref.isRootOrAdbEnabled()) {
            // Need to request permissions if not given
            storagePermission.request(granted -> {
                if (granted) launchInstaller();
            });
        } else launchInstaller();
    }

    @UiThread
    private void launchInstaller() {
        if (model.getApkFile().isSplit()) {
            SplitApkChooser splitApkChooser = new SplitApkChooser();
            Bundle args = new Bundle();
            args.putInt(SplitApkChooser.EXTRA_APK_FILE_KEY, model.getApkFileKey());
            args.putString(SplitApkChooser.EXTRA_ACTION_NAME, getString(actionName));
            args.putParcelable(SplitApkChooser.EXTRA_APP_INFO, model.getNewPackageInfo().applicationInfo);
            args.putString(SplitApkChooser.EXTRA_VERSION_INFO, getVersionInfoWithTrackers(model.getNewPackageInfo()));
            splitApkChooser.setArguments(args);
            splitApkChooser.setCancelable(false);
            splitApkChooser.setOnTriggerInstall(new SplitApkChooser.InstallInterface() {
                @Override
                public void triggerInstall() {
                    launchInstallService();
                }

                @Override
                public void triggerCancel() {
                    PackageInstallerActivity.this.triggerCancel();
                }
            });
            splitApkChooser.show(fm, SplitApkChooser.TAG);
        } else {
            launchInstallService();
        }
    }

    private void launchInstallService() {
        // Select user
        if (AppPref.isRootOrAdbEnabled() && AppPref.getBoolean(AppPref.PrefKey.PREF_INSTALLER_DISPLAY_USERS_BOOL)) {
            List<UserInfo> users = model.getUsers();
            if (users != null && users.size() > 1) {
                String[] userNames = new String[users.size() + 1];
                int[] userHandles = new int[users.size() + 1];
                userNames[0] = getString(R.string.backup_all_users);
                userHandles[0] = UserHandleHidden.USER_ALL;
                int i = 1;
                for (UserInfo info : users) {
                    userNames[i] = info.name == null ? String.valueOf(info.id) : info.name;
                    userHandles[i] = info.id;
                    ++i;
                }
                AtomicInteger userHandle = new AtomicInteger(UserHandleHidden.USER_ALL);
                new MaterialAlertDialogBuilder(this)
                        .setCancelable(false)
                        .setTitle(R.string.select_user)
                        .setSingleChoiceItems(userNames, 0, (dialog, which) ->
                                userHandle.set(userHandles[which]))
                        .setPositiveButton(R.string.ok, (dialog, which) -> doLaunchInstallerService(userHandle.get()))
                        .setNegativeButton(R.string.cancel, (dialog, which) -> triggerCancel())
                        .show();
                return;
            }
        }
        doLaunchInstallerService(UserHandleHidden.myUserId());
    }

    private void doLaunchInstallerService(int userHandle) {
        Intent intent = new Intent(this, PackageInstallerService.class);
        intent.putExtra(PackageInstallerService.EXTRA_APK_FILE_KEY, model.getApkFileKey());
        intent.putExtra(PackageInstallerService.EXTRA_APP_LABEL, model.getAppLabel());
        intent.putExtra(PackageInstallerService.EXTRA_USER_ID, userHandle);
        intent.putExtra(PackageInstallerService.EXTRA_CLOSE_APK_FILE, model.isCloseApkFile());
        ContextCompat.startForegroundService(this, intent);
        model.setCloseApkFile(false);
        triggerCancel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("PIA", "New intent called: " + intent.toString());
        // Check for action first
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            try {
                if (packageName == null || confirmIntent == null) throw new Exception("Empty confirmation intent.");
                Log.d("PIA", "Requesting user confirmation for package " + packageName);
                confirmIntentLauncher.launch(confirmIntent);
            } catch (Exception e) {
                e.printStackTrace();
                PackageInstallerCompat.sendCompletedBroadcast(packageName, PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM, sessionId);
                if (!hasNext()) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            }
        }
    }

    @UiThread
    @Override
    public void triggerInstall() {
        if (!model.isSignatureDifferent()) {
            // Signature is either matched or the app isn't installed
            install();
            return;
        }
        // Signature is different
        ApplicationInfo info = model.getInstalledPackageInfo().applicationInfo;  // Installed package info is never null here.
        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            // Cannot reinstall a system app with a different signature
            UIUtils.displayLongToast(R.string.app_signing_signature_mismatch_for_system_apps);
            triggerCancel();
            return;
        }
        if (!new File(info.publicSourceDir).exists()) {
            // Cannot reinstall an uninstalled app
            UIUtils.displayLongToast(R.string.app_signing_signature_mismatch_for_data_only_app);
            triggerCancel();
            return;
        }
        // Offer user to uninstall and then install the app again
        SpannableStringBuilder builder = new SpannableStringBuilder()
                .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                .append("\n\n");
        int start = builder.length();
        builder.append(getText(R.string.app_signing_install_without_data_loss));
        builder.setSpan(new RelativeSizeSpan(0.8f), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        new MaterialAlertDialogBuilder(PackageInstallerActivity.this)
                .setCustomTitle(getDialogTitle(PackageInstallerActivity.this, model.getAppLabel(),
                        model.getAppIcon(), getVersionInfoWithTrackers(model.getNewPackageInfo())))
                .setMessage(builder)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    // Uninstall and then install again
                    if (AppPref.isRootOrAdbEnabled()) {
                        // User must be all
                        try {
                            PackageInstallerCompat.uninstall(model.getPackageName(),
                                    UserHandleHidden.USER_ALL, false);
                            install();
                        } catch (Exception e) {
                            e.printStackTrace();
                            UIUtils.displayLongToast(R.string.failed_to_uninstall, model.getAppLabel());
                            triggerCancel();
                        }
                    } else {
                        // Uninstall using service, not guaranteed to work
                        // since it only uninstalls for the current user
                        Intent intent = new Intent(Intent.ACTION_DELETE);
                        intent.setData(Uri.parse("package:" + model.getPackageName()));
                        uninstallIntentLauncher.launch(intent);
                    }
                })
                .setNegativeButton(R.string.no, (dialog, which) -> triggerCancel())
                .setNeutralButton(R.string.only_install, (dialog, which) -> install())
                .setCancelable(false)
                .show();
    }

    @Override
    public void triggerCancel() {
        goToNext();
    }

    /**
     * Closes the current APK and start the next
     */
    private void goToNext() {
        if (hasNext()) {
            //noinspection ConstantConditions Checked earlier
            model.getPackageInfo(uriIterator.next(), mimeType);
        } else {
            finish();
        }
    }

    private boolean hasNext() {
        return uriIterator != null && uriIterator.hasNext();
    }

    @NonNull
    private String getVersionInfoWithTrackers(@NonNull final PackageInfo newPackageInfo) {
        Resources res = getApplication().getResources();
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo);
        String newVersionName = newPackageInfo.versionName;
        int trackers = model.getTrackerCount();
        StringBuilder sb = new StringBuilder(res.getString(R.string.version_name_with_code, newVersionName, newVersionCode));
        if (trackers > 0) {
            sb.append(", ").append(res.getQuantityString(R.plurals.no_of_trackers, trackers, trackers));
        }
        return sb.toString();
    }
}

