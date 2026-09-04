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

import android.Manifest;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.accessibility.AccessibilityMultiplexer;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.CachedApkSource;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerViewModel.PackageParseResult;
import io.github.muntashirakon.AppManager.apk.splitapk.SplitApkChooser;
import io.github.muntashirakon.AppManager.apk.whatsnew.WhatsNewFragment;
import io.github.muntashirakon.AppManager.compat.ApplicationInfoCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.types.ForegroundService;
import io.github.muntashirakon.AppManager.utils.PackageUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

/**
 * Activity that manages installing and confirming package installation. Actual installation is done by
 * {@link PackageInstallerService}.
 * <p>
 * How the installer works:
 * <ol>
 * <li>When the installation of a package is requested, it is either stored in queue or loaded directly if the queue is
 * empty.
 * <li>Then, it is checked whether there's an already installed package by the same name. If there exists any, the user
 * is offered to reinstall, upgrade or downgrade the package depending on the features supported by the present mode of
 * operation. Otherwise, the user is asked to confirm installation. Before doing so, however, a changelog may be
 * listed if it is enabled in settings.
 * <li>Next, if it is a split app, the user is asked to choose the splits to be installed. Otherwise, the installer
 * proceeds to the next phase directly.
 * <li>If display options is enabled, the options are displayed so that the user can tweak the present installer.
 * Otherwise, the installed proceeds to the next phase.
 * <li>Installer takes necessary steps to launch a installer service to initiate the installation.
 * </ol>
 */
public class PackageInstallerActivity extends BaseActivity implements InstallerDialogHelper.OnClickButtonsListener {
    public static final String TAG = PackageInstallerActivity.class.getSimpleName();

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, @NonNull Uri uri) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.setData(uri);
        return intent;
    }

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, ApkSource apkSource) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        IntentCompat.putWrappedParcelableExtra(intent, EXTRA_APK_FILE_LINK, apkSource);
        return intent;
    }

    @NonNull
    public static Intent getLaunchableInstance(@NonNull Context context, @NonNull String packageName) {
        Intent intent = new Intent(context, PackageInstallerActivity.class);
        intent.setData(Uri.parse("package:" + packageName));
        return intent;
    }

    private static final String EXTRA_APK_FILE_LINK = "link";
    private static final String STATE_SESSION_ID = "session_id";
    private static final String STATE_PACKAGE_NAME = "package_name";
    private static final String STATE_OPERATION_ID = "operation_id";
    public static final String ACTION_PACKAGE_INSTALLED = BuildConfig.APPLICATION_ID + ".action.PACKAGE_INSTALLED";

    private int mSessionId = -1;
    @Nullable
    private ApkQueueItem mCurrentItem;
    private String mPackageName;
    private String mOperationId;
    /**
     * Whether this activity is currently dealing with an apk
     */
    private boolean mIsDealingWithApk = false;
    private InstallerDialogHelper mDialogHelper;
    private PackageInstallerViewModel mModel;
    @Nullable
    private PackageInstallerService mService;
    private boolean mServiceBound;
    private InstallerDialogFragment mInstallerDialogFragment;
    private boolean initiated = false;
    private final PackageInstallerService.OnInstallFinished mInstallFinishedListener =
            installResult -> {
                if (isFinishing()) return;
                mModel.setInstallResult(installResult);
                showInstallationFinishedDialog(installResult.getPackageName(), installResult.getStatus(),
                        installResult.getBlockingPackage(), installResult.getStatusMessage());
            };
    private final View.OnClickListener mAppInfoClickListener = v -> {
        assert mCurrentItem != null;
        try {
            ApkSource apkSource = mCurrentItem.getApkSource();
            if (apkSource == null) {
                apkSource = getCurrentPackage().getApkSource();
            }
            Intent appDetailsIntent = AppDetailsActivity.getIntent(this, apkSource, true);
            appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(appDetailsIntent);
        } finally {
            // We cannot trigger cancel here because the cached file will be deleted
            goToNext();
        }
    };
    private InstallerOptions mInstallerOptions;
    private final ActivityResultLauncher<Intent> mConfirmIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                // User did some interaction and the installer screen is closed now
                Intent broadcastIntent = new Intent(PackageInstallerCompat.ACTION_INSTALL_INTERACTION_END);
                broadcastIntent.setPackage(getPackageName());
                broadcastIntent.putExtra(PackageInstallerCompat.EXTRA_OPERATION_ID, mOperationId);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, mPackageName);
                broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, mSessionId);
                getApplicationContext().sendBroadcast(broadcastIntent);
                if (!hasNext() && !mIsDealingWithApk) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            });

    private final AccessibilityMultiplexer mMultiplexer = AccessibilityMultiplexer.getInstance();
    private final StoragePermission mStoragePermission = StoragePermission.init(this);
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((ForegroundService.Binder) service).getService();
            if (mModel != null && mModel.isCurrentItemSubmitted()) {
                ApkQueueItem currentItem = mModel.getCurrentQueueItem();
                if (currentItem == null) {
                    return;
                }
                mCurrentItem = currentItem;
                if (mService.hasOperation(currentItem.getOperationId())) {
                    setInstallFinishedListener();
                } else {
                    String packageName = currentItem.getPackageName();
                    mModel.setInstallResult(new PackageInstallResult(currentItem.getOperationId(),
                            packageName != null ? packageName : "Unknown Package",
                            STATUS_FAILURE_ABORTED, null, null));
                    if (mDialogHelper != null) {
                        PackageInstallResult installResult = Objects.requireNonNull(mModel.getInstallResult());
                        showInstallationFinishedDialog(installResult.getPackageName(), installResult.getStatus(),
                                installResult.getBlockingPackage(), installResult.getStatusMessage());
                    }
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
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
        if (savedInstanceState != null) {
            mSessionId = savedInstanceState.getInt(STATE_SESSION_ID, -1);
            mPackageName = savedInstanceState.getString(STATE_PACKAGE_NAME);
            mOperationId = savedInstanceState.getString(STATE_OPERATION_ID);
        }
        Log.d(TAG, "On create, intent: %s", intent);
        mModel = new ViewModelProvider(this).get(PackageInstallerViewModel.class);
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            onNewIntent(intent);
            return;
        }
        if (!bindService(
                new Intent(this, PackageInstallerService.class), mServiceConnection, BIND_AUTO_CREATE)) {
            throw new RuntimeException("Unable to bind PackageInstallerService");
        }
        mServiceBound = true;
        mInstallerOptions = mModel.getInstallerOptions();
        getSupportFragmentManager().setFragmentResultListener(
                InstallerOptionsFragment.REQUEST_KEY, this, (requestKey, result) -> {
                    InstallerOptions options = InstallerOptionsFragment.getResult(result);
                    if (options != null) {
                        mModel.updateInstallerOptions(options);
                        mInstallerOptions = mModel.getInstallerOptions();
                    }
                });
        if (!mModel.isQueueInitialized()) {
            mModel.initializeQueue(getQueueItems(intent));
        }
        // Init fragment before observing retained LiveData.
        Fragment restoredFragment = getSupportFragmentManager()
                .findFragmentByTag(InstallerDialogFragment.TAG);
        mInstallerDialogFragment = restoredFragment instanceof InstallerDialogFragment
                ? (InstallerDialogFragment) restoredFragment
                : new InstallerDialogFragment();
        mInstallerDialogFragment.setCancelable(false);
        mInstallerDialogFragment.setFragmentStartedCallback(this::init);
        if (!mInstallerDialogFragment.isAdded()) {
            mInstallerDialogFragment.showNow(getSupportFragmentManager(), InstallerDialogFragment.TAG);
        }
        mModel.packageParseResultLiveData().observe(this, packageResult -> {
            if (mDialogHelper == null || mCurrentItem == null || mModel.isCurrentItemSubmitted()) {
                return;
            }
            if (packageResult == null) {
                mDialogHelper.showParseFailedDialog(v -> triggerCancel());
                return;
            }
            if (!mCurrentItem.getOperationId().equals(packageResult.getOperationId())) {
                return;
            }
            // TODO: Resolve dependencies
            mDialogHelper.onParseSuccess(packageResult.getAppLabel(), getVersionInfoWithTrackers(packageResult),
                    packageResult.getAppIcon(), v -> displayInstallerOptions());
            displayChangesOrInstallationPrompt();
        });
        mModel.packageUninstalledLiveData().observe(this, success -> {
            if (mModel.isCurrentItemSubmitted()) {
                return;
            }
            if (success) {
                install();
            } else {
                showInstallationFinishedDialog(getCurrentPackage().getPackageName(), getString(R.string.failed_to_uninstall_app),
                        null, false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        unsetInstallFinishedListener();
        if (mServiceBound) {
            unbindService(mServiceConnection);
            mServiceBound = false;
        }
        mService = null;
        super.onDestroy();
    }

    private void init(@NonNull InstallerDialogFragment fragment, @NonNull AlertDialog dialog) {
        // Make sure that it's only initiated once
        if (initiated) {
            return;
        }
        initiated = true;
        mDialogHelper = new InstallerDialogHelper(fragment, dialog);
        mDialogHelper.initProgress(v -> triggerCancel());
        resumeOrStartQueue();
    }

    private void resumeOrStartQueue() {
        mCurrentItem = mModel.getCurrentQueueItem();
        if (mCurrentItem == null) {
            startNextQueueItem();
            return;
        }
        mIsDealingWithApk = true;
        PackageInstallResult installResult = mModel.getInstallResult();
        if (installResult != null) {
            showInstallationFinishedDialog(installResult.getPackageName(), installResult.getStatus(),
                    installResult.getBlockingPackage(), installResult.getStatusMessage());
        } else if (mModel.isCurrentItemSubmitted()) {
            showInstallProgress();
            setInstallFinishedListener();
        } else if (mModel.getCurrentPackage() == null) {
            mModel.getPackageInfo(mCurrentItem);
        }
    }

    @UiThread
    private void displayChangesOrInstallationPrompt() {
        // This dialog either calls triggerInstall() or triggerCancel()
        boolean displayChanges;
        PackageParseResult currentPackage = getCurrentPackage();
        PackageInfo installedPackageInfo = currentPackage.getInstalledPackageInfo();
        int actionRes;
        if (installedPackageInfo == null) {
            // App not installed or data not cleared
            displayChanges = false;
            actionRes = R.string.install;
        } else {
            // App is installed or the app is uninstalled without clearing data, or the app is uninstalled,
            // but it's a system app
            long installedVersionCode = PackageInfoCompat.getLongVersionCode(installedPackageInfo);
            long thisVersionCode = PackageInfoCompat.getLongVersionCode(currentPackage.getNewPackageInfo());
            displayChanges = Prefs.Installer.displayChanges();
            if (installedVersionCode < thisVersionCode) {
                // Needs update
                actionRes = R.string.update;
            } else if (installedVersionCode == thisVersionCode) {
                // Issue reinstall
                actionRes = R.string.reinstall;
            } else {
                // Downgrade
                actionRes = R.string.downgrade;
            }
        }
        if (displayChanges) {
            WhatsNewFragment dialogFragment = WhatsNewFragment.getInstance(currentPackage.getNewPackageInfo(),
                    currentPackage.getInstalledPackageInfo());
            mDialogHelper.showWhatsNewDialog(actionRes, dialogFragment, new InstallerDialogHelper.OnClickButtonsListener() {
                @Override
                public void triggerInstall() {
                    displayInstallationPrompt(actionRes, true);
                }

                @Override
                public void triggerCancel() {
                    PackageInstallerActivity.this.triggerCancel();
                }
            }, mAppInfoClickListener);
            return;
        }
        displayInstallationPrompt(actionRes, false);
    }

    private void displayInstallationPrompt(int actionRes, boolean splitOnly) {
        PackageParseResult currentPackage = getCurrentPackage();
        if (currentPackage.getApkFile().isSplit()) {
            SplitApkChooser fragment = SplitApkChooser.getNewInstance(
                    getVersionInfoWithTrackers(currentPackage), getString(actionRes));
            mDialogHelper.showApkChooserDialog(actionRes, fragment, this, mAppInfoClickListener);
            return;
        }
        if (!splitOnly) {
            // In unprivileged mode, a dialog is generated by the system. But we need to display it nonetheless in order
            // to provide additional features.
            mDialogHelper.showInstallConfirmationDialog(actionRes, this, mAppInfoClickListener);
        } else triggerInstall();
    }

    private void displayInstallerOptions() {
        PackageInfo packageInfo = getCurrentPackage().getNewPackageInfo();
        InstallerOptionsFragment dialog = InstallerOptionsFragment.getInstance(packageInfo.packageName,
                ApplicationInfoCompat.isTestOnly(packageInfo.applicationInfo), mInstallerOptions);
        dialog.show(getSupportFragmentManager(), InstallerOptionsFragment.TAG);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SESSION_ID, mSessionId);
        outState.putString(STATE_PACKAGE_NAME, mPackageName);
        outState.putString(STATE_OPERATION_ID, mOperationId);
        super.onSaveInstanceState(outState);
    }

    @UiThread
    private void install() {
        if (getCurrentPackage().getApkFile().hasObb()
                && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            // Need to request permissions if not given
            mStoragePermission.request(granted -> {
                if (granted) launchInstallerService();
            });
        } else launchInstallerService();
    }

    @UiThread
    private void launchInstallerService() {
        assert mCurrentItem != null;
        PackageInfo packageInfo = getCurrentPackage().getNewPackageInfo();
        InstallerOptions effectiveOptions = InstallerOptions.resolveEffectiveOptions(mInstallerOptions,
                packageInfo.packageName, ApplicationInfoCompat.isTestOnly(packageInfo.applicationInfo));
        int userId = effectiveOptions.getUserId();
        mCurrentItem.setInstallerOptions(effectiveOptions);
        mCurrentItem.setSelectedSplits(mModel.getSelectedSplitsForInstallation());
        int lastUserId = userId == UserHandleHidden.USER_ALL ? UserHandleHidden.myUserId() : userId;
        boolean canDisplayNotification = Utils.canDisplayNotification(this);
        boolean alwaysOnBackground = canDisplayNotification && Prefs.Installer.installInBackground();
        Intent intent = new Intent(this, PackageInstallerService.class);
        IntentCompat.putWrappedParcelableExtra(intent, PackageInstallerService.EXTRA_QUEUE_ITEM, mCurrentItem);
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            // For unprivileged mode, use accessibility service if enabled
            mMultiplexer.enableInstall(true);
        }
        mModel.markCurrentItemSubmitted(lastUserId);
        ContextCompat.startForegroundService(this, intent);
        if (!alwaysOnBackground && mService != null) {
            setInstallFinishedListener();
            showInstallProgress();
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
        Log.d(TAG, "New intent called: %s", intent);
        // Check for action first
        if (ACTION_PACKAGE_INSTALLED.equals(intent.getAction())) {
            mSessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            mPackageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
            mOperationId = intent.getStringExtra(PackageInstallerCompat.EXTRA_OPERATION_ID);
            Intent confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
            try {
                if (mPackageName == null || mOperationId == null || confirmIntent == null)
                    throw new Exception("Empty confirmation intent.");
                Log.d(TAG, "Requesting user confirmation for package %s", mPackageName);
                mConfirmIntentLauncher.launch(confirmIntent);
            } catch (Exception e) {
                e.printStackTrace();
                if (mPackageName != null && mOperationId != null) {
                    PackageInstallerCompat.sendCompletedBroadcast(this, mOperationId, mPackageName,
                            PackageInstallerCompat.STATUS_FAILURE_INCOMPATIBLE_ROM, mSessionId);
                }
                if (!hasNext() && !mIsDealingWithApk) {
                    // No APKs left, this maybe a solo call
                    finish();
                } // else let the original activity decide what to do
            }
            return;
        }
        // New APK files added
        mModel.enqueue(getQueueItems(intent));
        UIUtils.displayShortToast(R.string.added_to_queue);
    }

    @UiThread
    @Override
    public void triggerInstall() {
        // Calls install(), reinstall() (which in terms called install()) and triggerCancel()
        PackageParseResult currentPackage = getCurrentPackage();
        PackageInfo installedPackageInfo = currentPackage.getInstalledPackageInfo();
        if (installedPackageInfo == null) {
            // App not installed
            install();
            return;
        }
        InstallerDialogHelper.OnClickButtonsListener reinstallListener = new InstallerDialogHelper.OnClickButtonsListener() {
            @Override
            public void triggerInstall() {
                // Uninstall and then install again
                reinstall();
            }

            @Override
            public void triggerCancel() {
                PackageInstallerActivity.this.triggerCancel();
            }
        };
        long installedVersionCode = PackageInfoCompat.getLongVersionCode(installedPackageInfo);
        long thisVersionCode = PackageInfoCompat.getLongVersionCode(currentPackage.getNewPackageInfo());
        if (installedVersionCode > thisVersionCode && !SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.INSTALL_PACKAGES)) {
            // Need to uninstall and install again
            SpannableStringBuilder builder = new SpannableStringBuilder()
                    .append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                    .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)))
                    .append("\n\n");
            mDialogHelper.showDowngradeReinstallWarning(builder, reinstallListener, mAppInfoClickListener);
            return;
        }
        if (!currentPackage.isSignatureDifferent()) {
            // Signature is either matched or the app isn't installed
            install();
            return;
        }
        // Signature is different
        ApplicationInfo info = installedPackageInfo.applicationInfo;
        boolean isSystem = ApplicationInfoCompat.isSystemApp(info);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (isSystem) {
            // Cannot reinstall a system app with a different signature
            builder.append(getString(R.string.app_signing_signature_mismatch_for_system_apps));
        } else {
            // Offer user to uninstall and then install the app again
            builder.append(getString(R.string.do_you_want_to_uninstall_and_install)).append(" ")
                    .append(UIUtils.getItalicString(getString(R.string.app_data_will_be_lost)));
        }
        builder.append("\n\n");
        int start = builder.length();
        builder.append(getText(R.string.app_signing_install_without_data_loss));
        builder.setSpan(new RelativeSizeSpan(0.8f), start, builder.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        mDialogHelper.showSignatureMismatchReinstallWarning(builder, reinstallListener, v -> install(), isSystem);
    }

    @Override
    public void triggerCancel() {
        // Run cleanup
        if (mCurrentItem != null && mCurrentItem.getApkSource() instanceof CachedApkSource) {
            ((CachedApkSource) mCurrentItem.getApkSource()).cleanup();
        }
        goToNext();
    }

    private void reinstall() {
        if (!SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.DELETE_PACKAGES)) {
            mMultiplexer.enableUninstall(true);
        }
        mModel.uninstallPackage();
    }

    /**
     * Closes the current APK and start the next
     */
    private void goToNext() {
        mModel.finishCurrentQueueItem();
        mCurrentItem = null;
        mMultiplexer.enableInstall(false);
        mMultiplexer.enableUninstall(false);
        startNextQueueItem();
    }

    private void startNextQueueItem() {
        if (hasNext()) {
            mIsDealingWithApk = true;
            mDialogHelper.initProgress(v -> goToNext());
            mCurrentItem = Objects.requireNonNull(mModel.startNextQueueItem());
            mModel.getPackageInfo(mCurrentItem);
        } else {
            mIsDealingWithApk = false;
            mDialogHelper.dismiss();
            finish();
        }
    }

    private boolean hasNext() {
        return mModel.hasPendingItems();
    }

    @NonNull
    private List<ApkQueueItem> getQueueItems(@NonNull Intent intent) {
        List<ApkQueueItem> items = new ArrayList<>(
                ApkQueueItem.fromIntent(intent, Utils.getRealReferrer(this)));
        ApkSource apkSource = IntentCompat.getUnwrappedParcelableExtra(intent, EXTRA_APK_FILE_LINK, ApkSource.class);
        if (apkSource != null) {
            items.add(ApkQueueItem.fromApkSource(apkSource));
        }
        return items;
    }

    private void showInstallProgress() {
        boolean canDisplayNotification = Utils.canDisplayNotification(this);
        mDialogHelper.showInstallProgressDialog(canDisplayNotification ? v -> {
            unsetInstallFinishedListener();
            goToNext();
        } : null);
    }

    @NonNull
    private PackageParseResult getCurrentPackage() {
        return Objects.requireNonNull(mModel.getCurrentPackage());
    }

    @NonNull
    private String getVersionInfoWithTrackers(@NonNull PackageParseResult packageResult) {
        Resources res = getApplication().getResources();
        PackageInfo newPackageInfo = packageResult.getNewPackageInfo();
        long newVersionCode = PackageInfoCompat.getLongVersionCode(newPackageInfo);
        String newVersionName = newPackageInfo.versionName;
        int trackers = packageResult.getTrackerCount();
        StringBuilder sb = new StringBuilder(res.getString(R.string.version_name_with_code, newVersionName, newVersionCode));
        if (trackers > 0) {
            sb.append(", ").append(res.getQuantityString(R.plurals.no_of_trackers, trackers, trackers));
        }
        return sb.toString();
    }

    public void showInstallationFinishedDialog(String packageName, int result, @Nullable String blockingPackage,
                                               @Nullable String statusMessage) {
        showInstallationFinishedDialog(packageName, getStringFromStatus(result, blockingPackage), statusMessage,
                result == STATUS_SUCCESS);
    }

    public void showInstallationFinishedDialog(String packageName, CharSequence message,
                                               @Nullable String statusMessage, boolean displayOpenAndAppInfo) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(message);
        if (statusMessage != null) {
            ssb.append("\n\n").append(UIUtils.getItalicString(statusMessage));
        }
        Intent intent = PackageManagerCompat.getLaunchIntentForPackage(packageName, UserHandleHidden.myUserId());
        mDialogHelper.showInstallFinishedDialog(ssb, hasNext() ? R.string.next : R.string.close, v -> goToNext(),
                displayOpenAndAppInfo && intent != null ? v -> {
                    try {
                        startActivity(intent);
                    } catch (Throwable th) {
                        UIUtils.displayLongToast(th.getMessage());
                    } finally {
                        goToNext();
                    }
                } : null, displayOpenAndAppInfo ? v -> {
                    try {
                        Intent appDetailsIntent = AppDetailsActivity.getIntent(this, packageName,
                                mModel.getLastUserId(), true);
                        appDetailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(appDetailsIntent);
                    } finally {
                        goToNext();
                    }
                } : null);
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
        if (mService != null && mCurrentItem != null) {
            mService.setOnInstallFinished(mCurrentItem.getOperationId(), mInstallFinishedListener);
        }
    }

    public void unsetInstallFinishedListener() {
        if (mService != null && mCurrentItem != null) {
            mService.removeOnInstallFinished(mCurrentItem.getOperationId(), mInstallFinishedListener);
        }
    }
}
