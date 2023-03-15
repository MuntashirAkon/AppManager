// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

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

import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandleHidden;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewDialogFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;

public class PackageInstallerActivity extends BaseActivity implements WhatsNewDialogFragment.InstallInterface {
    public static final String TAG = PackageInstallerActivity.class.getSimpleName();

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.setData(uri);
        return intent;
    }

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, int apkFileKey) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.putExtra(EXTRA_APK_FILE_KEY, apkFileKey);
        return intent;
    }

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, @NonNull String packageName) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.putExtra(EXTRA_INSTALL_EXISTING, true);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        return intent;
    }

    public static final String EXTRA_APK_FILE_KEY = "key";
    public static final String EXTRA_INSTALL_EXISTING = "install_existing";
    public static final String EXTRA_PACKAGE_NAME = "pkg";
    public static final String ACTION_PACKAGE_INSTALLED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_INSTALLED";

    private int sessionId = -1;
    @Nullable
    private ApkQueueItem currentItem;
    private String packageName;
    /**
     * Whether this activity is currently dealing with an apk
     */
    private boolean isDealingWithApk = false;
    private int actionName;
    @UserIdInt
    private int lastUserId;
    private FragmentManager fm;
    private AlertDialog progressDialog;
    @Nullable
    private AlertDialog installProgressDialog;
    private PackageInstallerViewModel model;
    @Nullable
    private PackageInstallerService service;
    private final Queue<ApkQueueItem> apkQueue = new LinkedList<>();
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

    private final AccessibilityMultiplexer multiplexer = AccessibilityMultiplexer.getInstance();
    private final StoragePermission storagePermission = StoragePermission.init(this);
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
    public boolean getTransparentBackground() {
        return true;
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        final Intent intent = getIntent();
        if (intent == null) {
            triggerCancel();
            return;
        }
        Log.d(TAG, "On create, intent: " + intent);
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
        synchronized (apkQueue) {
            apkQueue.addAll(ApkQueueItem.fromIntent(intent));
        }
        int apkFileKey = intent.getIntExtra(EXTRA_APK_FILE_KEY, -1);
        if (apkFileKey != -1) {
            synchronized (apkQueue) {
                apkQueue.add(new ApkQueueItem(apkFileKey));
            }
        }
        goToNext();
        model.packageInfoLiveData().observe(this, newPackageInfo -> {
            progressDialog.dismiss();
            if (newPackageInfo == null) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string._undefined)
                        .setIcon(R.drawable.ic_get_app)
                        .setMessage(R.string.failed_to_fetch_package_info)
                        .setCancelable(false)
                        .setNegativeButton(R.string.close, (dialog, which) -> {
                            dialog.dismiss();
                            goToNext();
                        })
                        .create();
                triggerCancel();
                return;
            }
            // TODO: Resolve dependencies
            displayInitialPrompt(newPackageInfo, model.getInstalledPackageInfo());
        });
        model.packageUninstalledLiveData().observe(this, success -> {
            if (success) {
                install();
            } else {
                getInstallationFinishedDialog(model.getPackageName(), getString(R.string.failed_to_uninstall_app),
                        null, false).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            unbindService(serviceConnection);
        }
        unsetInstallFinishedListener();
        super.onDestroy();
    }

    private void displayInitialPrompt(@NonNull PackageInfo newPackageInfo, @Nullable PackageInfo installedPackageInfo) {
        if (installedPackageInfo == null) {
            // App not installed or data not cleared
            actionName = R.string.install;
            if (model.getApkFile().isSplit() || !Ops.isPrivileged()) {
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
            long installedVersionCode = PackageInfoCompat.getLongVersionCode(installedPackageInfo);
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
                displayWhatsNewDialog();
            }
        }
    }

    @UiThread
    private void displayWhatsNewDialog() {
        if (!Prefs.Installer.displayChanges()) {
            if (!Ops.isPrivileged()) {
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
        if (model.getApkFile().hasObb() && !Ops.isPrivileged()) {
            // Need to request permissions if not given
            storagePermission.request(granted -> {
                if (granted) launchInstaller();
            });
        } else launchInstaller();
    }

    @UiThread
    private void launchInstaller() {
        if (model.getApkFile().isSplit()) {
            SplitApkChooser.getNewInstance(
                    model.getApkFileKey(),
                    model.getNewPackageInfo().applicationInfo,
                    getVersionInfoWithTrackers(model.getNewPackageInfo()),
                    getString(actionName),
                    new SplitApkChooser.OnTriggerInstallInterface() {
                        @Override
                        public void triggerInstall() {
                            launchInstallService();
                        }

                        @Override
                        public void triggerCancel() {
                            PackageInstallerActivity.this.triggerCancel();
                        }
                    }
            ).show(fm, SplitApkChooser.TAG);
        } else {
            launchInstallService();
        }
    }

    @UiThread
    private void launchInstallService() {
        // Select user
        if (Ops.isPrivileged() && Prefs.Installer.displayUsers()) {
            List<UserInfo> users = model.getUsers();
            if (users != null && users.size() > 1) {
                String[] userNames = new String[users.size() + 1];
                Integer[] userHandles = new Integer[users.size() + 1];
                userNames[0] = getString(R.string.backup_all_users);
                userHandles[0] = UserHandleHidden.USER_ALL;
                int i = 1;
                for (UserInfo info : users) {
                    userNames[i] = info.name == null ? String.valueOf(info.id) : info.name;
                    userHandles[i] = info.id;
                    ++i;
                }
                new SearchableSingleChoiceDialogBuilder<>(this, userHandles, userNames)
                        .setCancelable(false)
                        .setTitle(R.string.select_user)
                        .setSelectionIndex(0)
                        .setPositiveButton(R.string.ok, (dialog, which, selectedUserId) ->
                                doLaunchInstallerService(Objects.requireNonNull(selectedUserId)))
                        .setNegativeButton(R.string.cancel, (dialog, which, selectedUserId) -> triggerCancel())
                        .show();
                return;
            }
        }
        doLaunchInstallerService(UserHandleHidden.myUserId());
    }

    private void doLaunchInstallerService(@UserIdInt int userId) {
        lastUserId = userId == UserHandleHidden.USER_ALL ? UserHandleHidden.myUserId() : userId;
        boolean canDisplayNotification = Utils.canDisplayNotification(this);
        boolean alwaysOnBackground = canDisplayNotification && Prefs.Installer.installInBackground();
        Intent intent = new Intent(this, PackageInstallerService.class);
        intent.putExtra(PackageInstallerService.EXTRA_QUEUE_ITEM, currentItem);
        // We have to get an ApkFile instance in advance because of the queue management i.e. if this activity is closed
        // before the ApkFile in the queue is accessed, it will throw an IllegalArgumentException as the ApkFile under
        // the key is unavailable by the time it calls it.
        ApkFile.getInAdvance(model.getApkFileKey());
        if (!Ops.isPrivileged()) {
            // For no-root, use accessibility service if enabled
            multiplexer.enableInstall(true);
        }
        ContextCompat.startForegroundService(this, intent);
        if (!alwaysOnBackground && service != null) {
            setInstallFinishedListener();
            installProgressDialog = getInstallationProgressDialog(canDisplayNotification);
            installProgressDialog.show();
        } else {
            unsetInstallFinishedListener();
            // For some reason, the service is empty
            // Install next app instead
            goToNext();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "New intent called: " + intent.toString());
        // Check for action first
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
            Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            try {
                if (packageName == null || confirmIntent == null) throw new Exception("Empty confirmation intent.");
                Log.d(TAG, "Requesting user confirmation for package " + packageName);
                confirmIntentLauncher.launch(confirmIntent);
            } catch (Exception e) {
                e.printStackTrace();
                PackageInstallerCompat.sendCompletedBroadcast(packageName, PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM, sessionId);
                if (!hasNext() && !isDealingWithApk) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            }
            return;
        }
        // New APK files added
        synchronized (apkQueue) {
            apkQueue.addAll(ApkQueueItem.fromIntent(intent));
        }
        UIUtils.displayShortToast(R.string.added_to_queue);
    }

    @UiThread
    @Override
    public void triggerInstall() {
        long installedVersionCode;
        if (model.getInstalledPackageInfo() != null) {
            installedVersionCode = PackageInfoCompat.getLongVersionCode(model.getInstalledPackageInfo());
        } else installedVersionCode = 0L;
        long thisVersionCode = PackageInfoCompat.getLongVersionCode(model.getNewPackageInfo());
        if (installedVersionCode > thisVersionCode && !Ops.isPrivileged()) {
            // Need to uninstall and install again
            SpannableStringBuilder builder = new SpannableStringBuilder()
                    .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                    .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                    .append("\n\n");

            new MaterialAlertDialogBuilder(PackageInstallerActivity.this)
                    .setCustomTitle(getDialogTitle(PackageInstallerActivity.this, model.getAppLabel(),
                            model.getAppIcon(), getVersionInfoWithTrackers(model.getNewPackageInfo())))
                    .setMessage(builder)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        // Uninstall and then install again
                        reinstall();
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> triggerCancel())
                    .setCancelable(false)
                    .show();
            return;
        }
        if (!model.isSignatureDifferent()) {
            // Signature is either matched or the app isn't installed
            install();
            return;
        }
        // Signature is different
        ApplicationInfo info = model.getInstalledPackageInfo().applicationInfo;  // Installed package info is never null here.
        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            // Cannot reinstall a system app with a different signature
            getInstallationFinishedDialog(model.getPackageName(),
                    getString(R.string.app_signing_signature_mismatch_for_system_apps), null, false).show();
            return;
        }
        if (info.publicSourceDir == null || !new File(info.publicSourceDir).exists()) {
            // Cannot reinstall an uninstalled app
            getInstallationFinishedDialog(model.getPackageName(),
                    getString(R.string.app_signing_signature_mismatch_for_data_only_app), null, false).show();
            return;
        }
        // Offer user to uninstall and then install the app again
        SpannableStringBuilder builder = new SpannableStringBuilder()
                .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                .append("\n\n");
        int start = builder.length();
        builder.append(getText(R.string.app_signing_install_without_data_loss));
        builder.setSpan(new RelativeSizeSpan(0.8f), start, builder.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        new MaterialAlertDialogBuilder(PackageInstallerActivity.this)
                .setCustomTitle(getDialogTitle(PackageInstallerActivity.this, model.getAppLabel(),
                        model.getAppIcon(), getVersionInfoWithTrackers(model.getNewPackageInfo())))
                .setMessage(builder)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    // Uninstall and then install again
                    reinstall();
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

    private void reinstall() {
        if (!Ops.isPrivileged()) {
            multiplexer.enableUninstall(true);
        }
        model.uninstallPackage();
    }

    /**
     * Closes the current APK and start the next
     */
    private void goToNext() {
        multiplexer.enableInstall(false);
        multiplexer.enableUninstall(false);
        if (hasNext()) {
            isDealingWithApk = true;
            progressDialog.show();
            synchronized (apkQueue) {
                currentItem = Objects.requireNonNull(apkQueue.poll());
                model.getPackageInfo(currentItem);
            }
        } else {
            isDealingWithApk = false;
            finish();
        }
    }

    private boolean hasNext() {
        synchronized (apkQueue) {
            return !apkQueue.isEmpty();
        }
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
    public AlertDialog getInstallationFinishedDialog(String packageName, int result, @Nullable String blockingPackage,
                                                     @Nullable String statusMessage) {
        return getInstallationFinishedDialog(packageName, getStringFromStatus(result, blockingPackage), statusMessage,
                result == STATUS_SUCCESS);
    }

    @NonNull
    public AlertDialog getInstallationFinishedDialog(String packageName, CharSequence message,
                                                     @Nullable String statusMessage, boolean displayOpenAndAppInfo) {
        View view = getLayoutInflater().inflate(io.github.muntashirakon.ui.R.layout.dialog_scrollable_text_view, null);
        view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
        TextView tv = view.findViewById(android.R.id.content);
        SpannableStringBuilder ssb = new SpannableStringBuilder(message);
        if (statusMessage != null) {
            ssb.append("\n\n").append(UIUtils.getItalicString(statusMessage));
        }
        tv.setText(ssb);
        AtomicReference<AlertDialog> dialogAtomicReference = new AtomicReference<>();
        DialogTitleBuilder title = new DialogTitleBuilder(this)
                .setTitle(model.getAppLabel())
                .setSubtitle(getVersionInfoWithTrackers(model.getNewPackageInfo()))
                .setStartIcon(model.getAppIcon());
        if (displayOpenAndAppInfo) {
            title.setEndIcon(io.github.muntashirakon.ui.R.drawable.ic_information, v -> {
                Intent appDetailsIntent = AppDetailsActivity.getIntent(this, packageName, lastUserId, true);
                appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(appDetailsIntent);
                if (!hasNext() && dialogAtomicReference.get() != null) {
                    dialogAtomicReference.get().dismiss();
                    goToNext();
                }
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
                    try {
                        startActivity(intent);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast(th.getMessage());
                    }
                    goToNext();
                });
            }
        }
        dialogAtomicReference.set(builder.create());
        return dialogAtomicReference.get();
    }

    @NonNull
    public AlertDialog getParsingProgressDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_progress2, null);
        TextView tv = view.findViewById(android.R.id.text1);
        tv.setText(R.string.staging_apk_files);
        return new MaterialAlertDialogBuilder(this)
                .setTitle(R.string._undefined)
                .setIcon(R.drawable.ic_get_app)
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
            service.setOnInstallFinished((packageName, status, blockingPackage, statusMessage) -> {
                if (isFinishing()) return;
                if (installProgressDialog != null) {
                    installProgressDialog.hide();
                }
                getInstallationFinishedDialog(packageName, status, blockingPackage, statusMessage).show();
            });
        }
    }

    public void unsetInstallFinishedListener() {
        if (service != null) {
            service.setOnInstallFinished(null);
        }
    }
}

