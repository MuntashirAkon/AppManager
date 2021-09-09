// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;

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
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;

import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_ABORTED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_BLOCKED;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_CONFLICT;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_INVALID;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SECURITY;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_ABANDON;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_COMMIT;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_CREATE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_SESSION_WRITE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_FAILURE_STORAGE;
import static io.github.muntashirakon.AppManager.apk.installer.PackageInstallerCompat.STATUS_SUCCESS;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getDialogTitle;

public class PackageInstallerActivity extends BaseActivity implements WhatsNewDialogFragment.InstallInterface {
    public static final String EXTRA_APK_FILE_KEY = "EXTRA_APK_FILE_KEY";
    public static final String ACTION_PACKAGE_INSTALLED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_INSTALLED";

    private int sessionId = -1;
    private String packageName;
    /**
     * Whether this activity is currently dealing with an apk
     */
    private boolean isDealingWithApk = false;
    private final ActivityResultLauncher<Intent> confirmIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // User did some interaction and the installer screen is closed now
                Intent broadcastIntent = new Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_END);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
                getApplicationContext().sendBroadcast(broadcastIntent);
                if (!hasNext() && !isDealingWithApk) {
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
    @Nullable
    private AlertDialog installProgressDialog;
    private PackageInstallerViewModel model;
    @Nullable
    private PackageInstallerService service;
    private final StoragePermission storagePermission = StoragePermission.init(this);
    private final ActivityResultLauncher<Intent> uninstallIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    // No need for user handle since it is only applicable for the current user (no-root)
                    getPackageManager().getPackageInfo(model.getPackageName(), 0);
                    // The package is still installed meaning that the app uninstall wasn't successful
                    getInstallationFinishedDialog(model.getPackageName(), STATUS_FAILURE_CONFLICT, null).show();
                } catch (PackageManager.NameNotFoundException e) {
                    install();
                }
            });
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PackageInstallerActivity.this.service = ((ForegroundService.Binder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

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
        if (!bindService(
                new Intent(this, PackageInstallerService.class),
                serviceConnection,
                BIND_AUTO_CREATE)) {
            throw new RuntimeException("Unable to bind PackageInstallerService");
        }
        progressDialog = getParsingProgressDialog();
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
                        getInstallationFinishedDialog(
                                model.getPackageName(),
                                getString(R.string.downgrade_not_possible),
                                false).show();
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            unbindService(serviceConnection);
        }
        super.onDestroy();
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
        args.putString(WhatsNewDialogFragment.ARG_VERSION_INFO, getVersionInfoWithTrackers(model.getNewPackageInfo()));
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
                userHandles[0] = Users.USER_ALL;
                int i = 1;
                for (UserInfo info : users) {
                    userNames[i] = info.name == null ? String.valueOf(info.id) : info.name;
                    userHandles[i] = info.id;
                    ++i;
                }
                AtomicInteger userHandle = new AtomicInteger(Users.USER_ALL);
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
        doLaunchInstallerService(Users.myUserId());
    }

    private void doLaunchInstallerService(int userHandle) {
        Intent intent = new Intent(this, PackageInstallerService.class);
        intent.putExtra(PackageInstallerService.EXTRA_APK_FILE_KEY, model.getApkFileKey());
        intent.putExtra(PackageInstallerService.EXTRA_APP_LABEL, model.getAppLabel());
        intent.putExtra(PackageInstallerService.EXTRA_USER_ID, userHandle);
        intent.putExtra(PackageInstallerService.EXTRA_CLOSE_APK_FILE, model.isCloseApkFile());
        ContextCompat.startForegroundService(this, intent);
        model.setCloseApkFile(false);
        setInstallFinishedListener();
        if (service != null) {
            installProgressDialog = getInstallationProgressDialog(Utils.canDisplayNotification(this));
            installProgressDialog.show();
        } else {
            // For some reason, the service is empty
            // Install next app instead
            goToNext();
        }
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
                if (!hasNext() && !isDealingWithApk) {
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
            getInstallationFinishedDialog(
                    model.getPackageName(),
                    getString(R.string.app_signing_signature_mismatch_for_system_apps),
                    false).show();
            return;
        }
        if (info.publicSourceDir == null || !new File(info.publicSourceDir).exists()) {
            // Cannot reinstall an uninstalled app
            getInstallationFinishedDialog(
                    model.getPackageName(),
                    getString(R.string.app_signing_signature_mismatch_for_data_only_app),
                    false).show();
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
                                    Users.USER_ALL, false);
                            install();
                        } catch (Exception e) {
                            e.printStackTrace();
                            getInstallationFinishedDialog(
                                    model.getPackageName(),
                                    getString(R.string.failed_to_uninstall_app),
                                    false).show();
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
            isDealingWithApk = true;
            progressDialog.show();
            //noinspection ConstantConditions Checked earlier
            model.getPackageInfo(uriIterator.next(), mimeType);
        } else {
            isDealingWithApk = false;
            finish();
        }
    }

    private boolean hasNext() {
        return uriIterator != null && uriIterator.hasNext();
    }

    @NonNull
    private String getVersionInfoWithTrackers(@NonNull final PackageInfo newPackageInfo) {
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo);
        String newVersionName = newPackageInfo.versionName;
        int trackers = model.getTrackerCount();
        StringBuilder sb = new StringBuilder(getString(R.string.version_name_with_code, newVersionName, newVersionCode));
        if (trackers > 0) {
            sb.append(", ").append(getResources().getQuantityString(R.plurals.no_of_trackers, trackers, trackers));
        }
        return sb.toString();
    }

    @NonNull
    public AlertDialog getInstallationProgressDialog(boolean enableBackgroundService) {
        View view = getLayoutInflater().inflate(R.layout.dialog_progress2, null);
        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText(R.string.install_in_progress);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(getDialogTitle(this, model.getAppLabel(), model.getAppIcon(),
                        getVersionInfoWithTrackers(model.getNewPackageInfo())))
                .setCancelable(false)
                .setView(view);
        if (enableBackgroundService) {
            builder.setPositiveButton(R.string.background, (dialog, which) -> {
                unsetInstallFinishedListener();
                dialog.dismiss();
                goToNext();
            });
        }
        return builder.create();
    }

    @NonNull
    public AlertDialog getInstallationFinishedDialog(String packageName, int result, @Nullable String blockingPackage) {
        return getInstallationFinishedDialog(
                packageName,
                getStringFromStatus(result, blockingPackage),
                result == STATUS_SUCCESS);
    }

    @NonNull
    public AlertDialog getInstallationFinishedDialog(String packageName,
                                                     CharSequence message,
                                                     boolean displayOpenAndAppInfo) {
        View view = getLayoutInflater().inflate(R.layout.dialog_scrollable_text_view, null);
        view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
        TextView tv = view.findViewById(android.R.id.content);
        tv.setText(message);
        DialogTitleBuilder title = new DialogTitleBuilder(this)
                .setTitle(model.getAppLabel())
                .setSubtitle(getVersionInfoWithTrackers(model.getNewPackageInfo()))
                .setStartIcon(model.getAppIcon());
        if (displayOpenAndAppInfo) {
            title.setEndIcon(R.drawable.ic_info_outline_black_24dp, v -> {
                Intent appDetailsIntent = new Intent(this, AppDetailsActivity.class);
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
                // FIXME: 9/9/21 Use the first user ID instead of the current user ID
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, Users.myUserId());
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_BACK_TO_MAIN, true);
                appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(appDetailsIntent);
            });
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(title.build())
                .setView(view)
                .setCancelable(false)
                .setNegativeButton(hasNext() ? R.string.next : R.string.close, (dialog, which) -> {
                    dialog.dismiss();
                    goToNext();
                });
        if (displayOpenAndAppInfo) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                builder.setPositiveButton(R.string.open, (dialog, which) -> {
                    dialog.dismiss();
                    startActivity(intent);
                    goToNext();
                });
            }
        }
        return builder.create();
    }

    @NonNull
    public AlertDialog getParsingProgressDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_progress2, null);
        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText(R.string.staging_apk_files);
        return new MaterialAlertDialogBuilder(this)
                .setTitle(R.string._undefined)
                .setIcon(R.drawable.ic_baseline_get_app_24)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    goToNext();
                })
                .setView(view)
                .create();
    }

    @NonNull
    private String getStringFromStatus(@PackageInstallerCompat.Status int status,
                                       @Nullable String blockingPackage) {
        switch (status) {
            case STATUS_SUCCESS:
                return getString(R.string.installer_app_installed);
            case STATUS_FAILURE_ABORTED:
                return getString(R.string.installer_error_aborted);
            case STATUS_FAILURE_BLOCKED:
                String blocker = getString(R.string.installer_error_blocked_device);
                if (blockingPackage != null) {
                    blocker = PackageUtils.getPackageLabel(getPackageManager(), blockingPackage);
                }
                return getString(R.string.installer_error_blocked, blocker);
            case STATUS_FAILURE_CONFLICT:
                return getString(R.string.installer_error_conflict);
            case STATUS_FAILURE_INCOMPATIBLE:
                return getString(R.string.installer_error_incompatible);
            case STATUS_FAILURE_INVALID:
                return getString(R.string.installer_error_bad_apks);
            case STATUS_FAILURE_STORAGE:
                return getString(R.string.installer_error_storage);
            case STATUS_FAILURE_SECURITY:
                return getString(R.string.installer_error_security);
            case STATUS_FAILURE_SESSION_CREATE:
                return getString(R.string.installer_error_session_create);
            case STATUS_FAILURE_SESSION_WRITE:
                return getString(R.string.installer_error_session_write);
            case STATUS_FAILURE_SESSION_COMMIT:
                return getString(R.string.installer_error_session_commit);
            case STATUS_FAILURE_SESSION_ABANDON:
                return getString(R.string.installer_error_session_abandon);
            case STATUS_FAILURE_INCOMPATIBLE_ROM:
                return getString(R.string.installer_error_lidl_rom);
        }
        return getString(R.string.installer_error_generic);
    }

    public void setInstallFinishedListener() {
        if (service != null) {
            service.setOnInstallFinished((packageName, status, blockingPackage) -> {
                if (isFinishing()) return;
                if (installProgressDialog != null) {
                    installProgressDialog.hide();
                }
                getInstallationFinishedDialog(packageName, status, blockingPackage).show();
            });
        }
    }

    public void unsetInstallFinishedListener() {
        if (service != null) {
            service.setOnInstallFinished(null);
        }
    }
}

