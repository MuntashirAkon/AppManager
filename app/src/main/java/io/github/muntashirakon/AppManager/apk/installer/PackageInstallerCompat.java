// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

import static io.github.muntashirakon.AppManager.compat.PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageInstallerSession;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstallerHidden;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import aosp.libcore.util.EmptyArray;
import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkUtils;
import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.PackageManagerCompat;
import io.github.muntashirakon.AppManager.compat.PendingIntentCompat;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.progress.ProgressHandler;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.settings.Prefs;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.BroadcastUtils;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.MiuiUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

// FIXME: 21/1/23 This class has too many design issues that has to be addressed at some later time.
//  One example is the handling of userId, which should be independent of the class itself.k
@SuppressLint("ShiftFlags")
public final class PackageInstallerCompat {
    public static final String TAG = PackageInstallerCompat.class.getSimpleName();

    public static final String ACTION_INSTALL_STARTED = BuildConfig.APPLICATION_ID + ".action.INSTALL_STARTED";
    public static final String ACTION_INSTALL_COMPLETED = BuildConfig.APPLICATION_ID + ".action.INSTALL_COMPLETED";
    // For rootless installer to prevent PackageInstallerService from hanging
    public static final String ACTION_INSTALL_INTERACTION_BEGIN = BuildConfig.APPLICATION_ID + ".action.INSTALL_INTERACTION_BEGIN";
    public static final String ACTION_INSTALL_INTERACTION_END = BuildConfig.APPLICATION_ID + ".action.INSTALL_INTERACTION_END";

    @IntDef({
            STATUS_SUCCESS,
            STATUS_FAILURE_ABORTED,
            STATUS_FAILURE_BLOCKED,
            STATUS_FAILURE_CONFLICT,
            STATUS_FAILURE_INCOMPATIBLE,
            STATUS_FAILURE_INVALID,
            STATUS_FAILURE_STORAGE,
            // Custom
            STATUS_FAILURE_SECURITY,
            STATUS_FAILURE_SESSION_CREATE,
            STATUS_FAILURE_SESSION_WRITE,
            STATUS_FAILURE_SESSION_COMMIT,
            STATUS_FAILURE_SESSION_ABANDON,
            STATUS_FAILURE_INCOMPATIBLE_ROM,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    /**
     * See {@link PackageInstaller#STATUS_SUCCESS}
     */
    public static final int STATUS_SUCCESS = PackageInstaller.STATUS_SUCCESS;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_ABORTED}
     */
    public static final int STATUS_FAILURE_ABORTED = PackageInstaller.STATUS_FAILURE_ABORTED;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_BLOCKED}
     */
    public static final int STATUS_FAILURE_BLOCKED = PackageInstaller.STATUS_FAILURE_BLOCKED;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_CONFLICT}
     */
    public static final int STATUS_FAILURE_CONFLICT = PackageInstaller.STATUS_FAILURE_CONFLICT;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_INCOMPATIBLE}
     */
    public static final int STATUS_FAILURE_INCOMPATIBLE = PackageInstaller.STATUS_FAILURE_INCOMPATIBLE;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_INVALID}
     */
    public static final int STATUS_FAILURE_INVALID = PackageInstaller.STATUS_FAILURE_INVALID;
    /**
     * See {@link PackageInstaller#STATUS_FAILURE_STORAGE}
     */
    public static final int STATUS_FAILURE_STORAGE = PackageInstaller.STATUS_FAILURE_STORAGE;
    // Custom status
    /**
     * The operation failed because the apk file(s) are not accessible.
     */
    public static final int STATUS_FAILURE_SECURITY = -2;
    /**
     * The operation failed because it failed to create an installer session.
     */
    public static final int STATUS_FAILURE_SESSION_CREATE = -3;
    /**
     * The operation failed because it failed to write apk files to session.
     */
    public static final int STATUS_FAILURE_SESSION_WRITE = -4;
    /**
     * The operation failed because it could not commit the installer session.
     */
    public static final int STATUS_FAILURE_SESSION_COMMIT = -5;
    /**
     * The operation failed because it could not abandon the installer session. This is a redundant
     * failure.
     */
    public static final int STATUS_FAILURE_SESSION_ABANDON = -6;
    /**
     * The operation failed because the current ROM is incompatible with PackageInstaller
     */
    public static final int STATUS_FAILURE_INCOMPATIBLE_ROM = -7;

    @SuppressLint({"NewApi", "UniqueConstants", "InlinedApi"})
    @IntDef(flag = true, value = {
            INSTALL_REPLACE_EXISTING,
            INSTALL_ALLOW_TEST,
            INSTALL_EXTERNAL,
            INSTALL_INTERNAL,
            INSTALL_FROM_ADB,
            INSTALL_ALL_USERS,
            INSTALL_REQUEST_DOWNGRADE,
            INSTALL_GRANT_RUNTIME_PERMISSIONS,
            INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS,
            INSTALL_FORCE_VOLUME_UUID,
            INSTALL_FORCE_PERMISSION_PROMPT,
            INSTALL_INSTANT_APP,
            INSTALL_DONT_KILL_APP,
            INSTALL_FULL_APP,
            INSTALL_ALLOCATE_AGGRESSIVE,
            INSTALL_VIRTUAL_PRELOAD,
            INSTALL_APEX,
            INSTALL_ENABLE_ROLLBACK,
            INSTALL_DISABLE_VERIFICATION,
            INSTALL_ALLOW_DOWNGRADE,
            INSTALL_ALLOW_DOWNGRADE_API29,
            INSTALL_STAGED,
            INSTALL_DRY_RUN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InstallFlags {
    }

    /**
     * Flag parameter for {@code #installPackage} to indicate that you want to replace an already
     * installed package, if one exists.
     */
    public static final int INSTALL_REPLACE_EXISTING = 0x00000002;

    /**
     * Flag parameter for {@code #installPackage} to indicate that you want to
     * allow test packages (those that have set android:testOnly in their
     * manifest) to be installed.
     */
    public static final int INSTALL_ALLOW_TEST = 0x00000004;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this
     * package has to be installed on the sdcard.
     *
     * @deprecated Removed in API 29 (Android 10)
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static final int INSTALL_EXTERNAL = 0x00000008;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package
     * has to be installed on the sdcard.
     */
    public static final int INSTALL_INTERNAL = 0x00000010;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this install
     * was initiated via ADB.
     */
    public static final int INSTALL_FROM_ADB = 0x00000020;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this install
     * should immediately be visible to all users.
     */
    public static final int INSTALL_ALL_USERS = 0x00000040;

    /**
     * Flag parameter for {@code #installPackage} to indicate that an upgrade to a lower version
     * of a package than currently installed has been requested.
     *
     * <p>Note that this flag doesn't guarantee that downgrade will be performed. That decision
     * depends
     * on whenever:
     * <ul>
     * <li>An app is debuggable.
     * <li>Or a build is debuggable.
     * <li>Or {@link #INSTALL_ALLOW_DOWNGRADE} is set.
     * </ul>
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_REQUEST_DOWNGRADE = 0x00000080;

    /**
     * Flag parameter for {@code #installPackage} to indicate that all runtime
     * permissions should be granted to the package. If {@link #INSTALL_ALL_USERS}
     * is set the runtime permissions will be granted to all users, otherwise
     * only to the owner.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static final int INSTALL_GRANT_RUNTIME_PERMISSIONS = 0x00000100;

    /**
     * Flag parameter for {@code #installPackage} to indicate that all restricted
     * permissions should be whitelisted. If {@link #INSTALL_ALL_USERS}
     * is set the restricted permissions will be whitelisted for all users, otherwise
     * only to the owner.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS = 0x00400000;

    @RequiresApi(Build.VERSION_CODES.M)
    public static final int INSTALL_FORCE_VOLUME_UUID = 0x00000200;

    /**
     * Flag parameter for {@code #installPackage} to indicate that we always want to force
     * the prompt for permission approval. This overrides any special behaviour for internal
     * components.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int INSTALL_FORCE_PERMISSION_PROMPT = 0x00000400;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package is
     * to be installed as a lightweight "ephemeral" app.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int INSTALL_INSTANT_APP = 0x00000800;  // AKA INSTALL_EPHEMERAL

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package contains
     * a feature split to an existing application and the existing application should not
     * be killed during the installation process.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int INSTALL_DONT_KILL_APP = 0x00001000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package is an
     * upgrade to a package that refers to the SDK via release letter.
     *
     * @deprecated Removed in API 20 (Android 10)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int INSTALL_FORCE_SDK = 0x00002000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package is
     * to be installed as a heavy weight app. This is fundamentally the opposite of
     * {@link #INSTALL_INSTANT_APP}.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public static final int INSTALL_FULL_APP = 0x00004000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package
     * is critical to system health or security, meaning the system should use
     * {@code StorageManager#FLAG_ALLOCATE_AGGRESSIVE} internally.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public static final int INSTALL_ALLOCATE_AGGRESSIVE = 0x00008000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package
     * is a virtual preload.
     */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    public static final int INSTALL_VIRTUAL_PRELOAD = 0x00010000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package
     * is an APEX package
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_APEX = 0x00020000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that rollback
     * should be enabled for this install.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_ENABLE_ROLLBACK = 0x00040000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that package verification should be
     * disabled for this package.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_DISABLE_VERIFICATION = 0x00080000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that
     * {@link #INSTALL_REQUEST_DOWNGRADE} should be allowed.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_ALLOW_DOWNGRADE_API29 = 0x00100000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that this package
     * is being installed as part of a staged install.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_STAGED = 0x00200000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that package should only be verified
     * but not installed.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int INSTALL_DRY_RUN = 0x00800000;

    /**
     * Flag parameter for {@code #installPackage} to indicate that it is okay
     * to install an update to an app where the newly installed app has a lower
     * version code than the currently installed app.
     *
     * @deprecated Replaced by {@link #INSTALL_ALLOW_DOWNGRADE_API29} in Android 10
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static final int INSTALL_ALLOW_DOWNGRADE = 0x00000080;

    @SuppressLint({"NewApi", "InlinedApi"})
    @IntDef(flag = true, value = {
            DELETE_KEEP_DATA,
            DELETE_ALL_USERS,
            DELETE_SYSTEM_APP,
            DELETE_DONT_KILL_APP,
            DELETE_CHATTY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeleteFlags {
    }

    /**
     * Flag parameter for {@code #deletePackage} to indicate that you don't want to delete the
     * package's data directory.
     */
    public static final int DELETE_KEEP_DATA = 0x00000001;

    /**
     * Flag parameter for {@code #deletePackage} to indicate that you want the
     * package deleted for all users.
     */
    public static final int DELETE_ALL_USERS = 0x00000002;

    /**
     * Flag parameter for {@code #deletePackage} to indicate that, if you are calling
     * uninstall on a system that has been updated, then don't do the normal process
     * of uninstalling the update and rolling back to the older system version (which
     * needs to happen for all users); instead, just mark the app as uninstalled for
     * the current user.
     */
    public static final int DELETE_SYSTEM_APP = 0x00000004;

    /**
     * Flag parameter for {@code #deletePackage} to indicate that, if you are calling
     * uninstall on a package that is replaced to provide new feature splits, the
     * existing application should not be killed during the removal process.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int DELETE_DONT_KILL_APP = 0x00000008;

    /**
     * Flag parameter for {@code #deletePackage} to indicate that package deletion
     * should be chatty.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static final int DELETE_CHATTY = 0x80000000;

    public interface OnInstallListener {
        @WorkerThread
        void onStartInstall(int sessionId, String packageName);

        // MIUI-begin: MIUI 12.5+ workaround

        /**
         * MIUI 12.5+ may require more than one tries in order to have successful installations. This is only needed
         * during APK installations, not APK uninstallations or install-existing attempts.
         *
         * @param apkFile Underlying APK file if available.
         */
        @WorkerThread
        default void onAnotherAttemptInMiui(@Nullable ApkFile apkFile) {
        }
        // MIUI-end

        @WorkerThread
        void onFinishedInstall(int sessionId, String packageName, int result, @Nullable String blockingPackage,
                               @Nullable String statusMessage);
    }

    @NonNull
    public static PackageInstallerCompat getNewInstance() {
        return new PackageInstallerCompat();
    }

    @NonNull
    public static PackageInstallerCompat getNewInstance(@NonNull String installerPackageName) {
        return new PackageInstallerCompat(installerPackageName);
    }

    @SuppressLint("StaticFieldLeak")
    private static final Context context = AppManager.getContext();

    private CountDownLatch installWatcher;
    private CountDownLatch interactionWatcher;

    private boolean closeApkFile = true;
    private boolean installCompleted = false;
    @Nullable
    private ApkFile apkFile;
    private String packageName;
    @Nullable
    private CharSequence appLabel;
    private int sessionId = -1;
    @Status
    private int finalStatus = STATUS_FAILURE_INVALID;
    @Nullable
    private String statusMessage;
    private PackageInstallerBroadcastReceiver piReceiver;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction() == null) return;
            int sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            Log.d(TAG, "Action: " + intent.getAction());
            Log.d(TAG, "Session ID: " + sessionId);
            switch (intent.getAction()) {
                case ACTION_INSTALL_STARTED:
                    // Session successfully created
                    if (onInstallListener != null) {
                        onInstallListener.onStartInstall(sessionId, packageName);
                    }
                    break;
                case ACTION_INSTALL_INTERACTION_BEGIN:
                    // An installation prompt is being shown to the user
                    // Run indefinitely until user finally decides to do something about it
                    break;
                case ACTION_INSTALL_INTERACTION_END:
                    // The installation prompt is hidden by the user, either by clicking cancel or install,
                    // or just clicking on some place else (latter is our main focus)
                    if (PackageInstallerCompat.this.sessionId == sessionId) {
                        // The user interaction is done, it doesn't take more than 1 minute now
                        interactionWatcher.countDown();
                    }
                    break;
                case ACTION_INSTALL_COMPLETED:
                    // Either it failed to create a session or the installation was completed,
                    // regardless of the status: success or failure
                    finalStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, STATUS_FAILURE_INVALID);
                    String blockingPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);
                    statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    // Run install completed
                    installCompleted = true;
                    ThreadUtils.postOnBackgroundThread(() ->
                            installCompleted(sessionId, finalStatus, blockingPackage, statusMessage));
                    break;
            }
        }
    };

    @Nullable
    private OnInstallListener onInstallListener;
    private IPackageInstaller packageInstaller;
    private PackageInstaller.Session session;
    // MIUI-added: Multiple attempts may be required
    int attempts = 1;
    private final String installerPackageName;
    private final boolean isPrivileged;

    private PackageInstallerCompat() {
        this(Ops.isReallyPrivileged() ? Prefs.Installer.getInstallerPackageName() : context.getPackageName());
    }

    private PackageInstallerCompat(@NonNull String installerPackageName) {
        this.isPrivileged = Ops.isReallyPrivileged();
        this.installerPackageName = installerPackageName;
        Log.d(TAG, "Installer app: " + installerPackageName);
    }

    public void setOnInstallListener(@Nullable OnInstallListener onInstallListener) {
        this.onInstallListener = onInstallListener;
    }

    public void setAppLabel(@Nullable CharSequence appLabel) {
        this.appLabel = appLabel;
    }

    @NonNull
    private static int[] getAllRequestedUsers(int userId) {
        switch (userId) {
            case UserHandleHidden.USER_ALL:
                return Users.getUsersIds();
            case UserHandleHidden.USER_NULL:
                return EmptyArray.INT;
            default:
                return new int[]{userId};
        }
    }

    public boolean install(@NonNull ApkFile apkFile, @UserIdInt int userId) {
        return install(apkFile, userId, null);
    }

    public boolean install(@NonNull ApkFile apkFile, @UserIdInt int userId, @Nullable ProgressHandler progressHandler) {
        ThreadUtils.ensureWorkerThread();
        try {
            this.apkFile = apkFile;
            this.packageName = apkFile.getPackageName();
            initBroadcastReceiver();
            int installFlags = getInstallFlags(userId);
            int[] allRequestedUsers = getAllRequestedUsers(userId);
            if (allRequestedUsers.length == 0) {
                Log.d(TAG, "Install: no users.");
                callFinish(STATUS_FAILURE_INVALID);
                return false;
            }
            ThreadUtils.postOnBackgroundThread(() -> {
                // TODO: 6/6/23 Wait for this task to finish before returning
                for (int u : allRequestedUsers) {
                    copyObb(apkFile, u);
                }
            });
            userId = allRequestedUsers[0];
            Log.d(TAG, "Install: opening session...");
            if (!openSession(userId, installFlags)) return false;
            List<ApkFile.Entry> selectedEntries = apkFile.getSelectedEntries();
            Log.d(TAG, "Install: selected entries: " + selectedEntries.size());
            // Write apk files
            long totalSize = 0;
            for (ApkFile.Entry entry : selectedEntries) {
                try {
                    totalSize += entry.getSignedFile().length();
                } catch (IOException e) {
                    callFinish(STATUS_FAILURE_INVALID);
                    Log.e(TAG, "Install: Cannot retrieve the selected APK files.", e);
                    return abandon();
                }
            }
            for (ApkFile.Entry entry : selectedEntries) {
                try (InputStream apkInputStream = entry.getSignedInputStream();
                     OutputStream apkOutputStream = session.openWrite(entry.getFileName(), 0, entry.getFileSize())) {
                    IoUtils.copy(apkInputStream, apkOutputStream, totalSize, progressHandler);
                    session.fsync(apkOutputStream);
                    Log.d(TAG, "Install: copied entry " + entry.name);
                } catch (IOException e) {
                    callFinish(STATUS_FAILURE_SESSION_WRITE);
                    Log.e(TAG, "Install: Cannot copy files to session.", e);
                    return abandon();
                } catch (SecurityException e) {
                    callFinish(STATUS_FAILURE_SECURITY);
                    Log.e(TAG, "Install: Cannot access apk files.", e);
                    return abandon();
                }
            }
            Log.d(TAG, "Install: Running installation...");
            // Commit
            return commit(userId);
        } finally {
            unregisterReceiver();
        }
    }

    public boolean install(@NonNull Path[] apkFiles, @NonNull String packageName, @UserIdInt int userId) {
        return install(apkFiles, packageName, userId, null);
    }

    public boolean install(@NonNull Path[] apkFiles, @NonNull String packageName, @UserIdInt int userId,
                           @Nullable ProgressHandler progressHandler) {
        ThreadUtils.ensureWorkerThread();
        try {
            this.apkFile = null;
            this.packageName = packageName;
            initBroadcastReceiver();
            int installFlags = getInstallFlags(userId);
            int[] allRequestedUsers = getAllRequestedUsers(userId);
            if (allRequestedUsers.length == 0) {
                Log.d(TAG, "Install: no users.");
                callFinish(STATUS_FAILURE_INVALID);
                return false;
            }
            userId = allRequestedUsers[0];
            if (!openSession(userId, installFlags)) return false;
            long totalSize = 0;
            for (Path apkFile : apkFiles) {
                totalSize += apkFile.length();
            }
            // Write apk files
            for (Path apkFile : apkFiles) {
                try (InputStream apkInputStream = apkFile.openInputStream();
                     OutputStream apkOutputStream = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
                    IoUtils.copy(apkInputStream, apkOutputStream, totalSize, progressHandler);
                    session.fsync(apkOutputStream);
                } catch (IOException e) {
                    callFinish(STATUS_FAILURE_SESSION_WRITE);
                    Log.e(TAG, "Install: Cannot copy files to session.", e);
                    return abandon();
                } catch (SecurityException e) {
                    callFinish(STATUS_FAILURE_SECURITY);
                    Log.e(TAG, "Install: Cannot access apk files.", e);
                    return abandon();
                }
            }
            // Commit
            return commit(userId);
        } finally {
            unregisterReceiver();
        }
    }

    private boolean commit(int userId) {
        IntentSender sender;
        LocalIntentReceiver intentReceiver;
        if (isPrivileged) {
            Log.d(TAG, "Commit: Commit via LocalIntentReceiver...");
            try {
                intentReceiver = new LocalIntentReceiver();
                sender = intentReceiver.getIntentSender();
            } catch (Exception e) {
                callFinish(STATUS_FAILURE_SESSION_COMMIT);
                Log.e(TAG, "Commit: Could not commit session.", e);
                return false;
            }
        } else {
            Log.d(TAG, "Commit: Calling activity to request permission...");
            intentReceiver = null;
            Intent callbackIntent = new Intent(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER);
            @SuppressLint("WrongConstant")
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, callbackIntent,
                    PendingIntentCompat.FLAG_MUTABLE);
            sender = pendingIntent.getIntentSender();
        }
        Log.d(TAG, "Commit: Committing...");
        try {
            session.commit(sender);
        } catch (Throwable e) {  // primarily RemoteException
            callFinish(STATUS_FAILURE_SESSION_COMMIT);
            Log.e(TAG, "Commit: Could not commit session.", e);
            return false;
        }
        if (!isPrivileged) {
            Log.d(TAG, "Commit: Waiting for user interaction...");
            // Wait for user interaction (if needed)
            try {
                // Wait for user interaction
                interactionWatcher.await();
                // Wait for the installation to complete
                installWatcher.await(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Log.e(TAG, "Installation interrupted.", e);
            }
        } else {
            Intent resultIntent = intentReceiver.getResult();
            finalStatus = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
            statusMessage = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        }
        Log.d(TAG, "Commit: Finishing...");
        // We might want to use {@code callFinish(finalStatus);} here, but it doesn't always work
        // since the object is garbage collected almost immediately.
        if (!installCompleted) {
            installCompleted(sessionId, finalStatus, null, statusMessage);
        }
        if (finalStatus == PackageInstaller.STATUS_SUCCESS && userId != UserHandleHidden.myUserId()) {
            BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), new String[]{packageName});
        }
        return finalStatus == PackageInstaller.STATUS_SUCCESS;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean openSession(@UserIdInt int userId, @InstallFlags int installFlags) {
        try {
            packageInstaller = PackageManagerCompat.getPackageInstaller();
        } catch (RemoteException e) {
            callFinish(STATUS_FAILURE_SESSION_CREATE);
            Log.e(TAG, "OpenSession: Could not get PackageInstaller.", e);
            return false;
        }
        // Clean old sessions
        cleanOldSessions();
        // Create install session
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        Refine.<PackageInstallerHidden.SessionParams>unsafeCast(sessionParams).installFlags |= installFlags;
        // Set installation location
        sessionParams.setInstallLocation(Prefs.Installer.getInstallLocation());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER);
        }
        if (!isPrivileged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sessionId = packageInstaller.createSession(sessionParams, installerPackageName,
                        context.getAttributionTag(), userId);
            } else {
                //noinspection deprecation
                sessionId = packageInstaller.createSession(sessionParams, installerPackageName, userId);
            }
            Log.d(TAG, "OpenSession: session id " + sessionId);
        } catch (RemoteException e) {
            callFinish(STATUS_FAILURE_SESSION_CREATE);
            Log.e(TAG, "OpenSession: Failed to create install session.", e);
            return false;
        }
        try {
            session = Refine.unsafeCast(new PackageInstallerHidden.Session(IPackageInstallerSession.Stub.asInterface(
                    new ProxyBinder(packageInstaller.openSession(sessionId).asBinder()))));
            Log.d(TAG, "OpenSession: session opened.");
        } catch (RemoteException e) {
            callFinish(STATUS_FAILURE_SESSION_CREATE);
            Log.e(TAG, "OpenSession: Failed to open install session.", e);
            return false;
        }
        sendStartedBroadcast(packageName, sessionId);
        return true;
    }

    @InstallFlags
    private static int getInstallFlags(@UserIdInt int userId) {
        int flags = INSTALL_ALLOW_TEST | INSTALL_REPLACE_EXISTING | INSTALL_ALLOW_DOWNGRADE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            flags |= INSTALL_ALLOW_DOWNGRADE_API29;
        }
        if (Ops.isReallyPrivileged() && userId == UserHandleHidden.USER_ALL) {
            flags |= INSTALL_ALL_USERS;
        }
        return flags;
    }

    public boolean installExisting(@NonNull String packageName, @UserIdInt int userId) {
        ThreadUtils.ensureWorkerThread();
        if (onInstallListener != null) {
            onInstallListener.onStartInstall(sessionId, packageName);
        }
        installWatcher = new CountDownLatch(0);
        interactionWatcher = new CountDownLatch(0);
        if (!Ops.isReallyPrivileged()) {
            installCompleted(sessionId, STATUS_FAILURE_BLOCKED, "android", "STATUS_FAILURE_BLOCKED: Insufficient permission.");
            Log.d(TAG, "InstallExisting: Only works in privileged mode.");
            return false;
        }
        // User ID must be a real user
        List<Integer> userIdWithoutInstalledPkg = new ArrayList<>();
        switch (userId) {
            case UserHandleHidden.USER_ALL: {
                int[] userIds = Users.getUsersIds();
                for (int u : userIds) {
                    try {
                        PackageManagerCompat.getPackageInfo(packageName,
                                PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, u);
                    } catch (Throwable th) {
                        userIdWithoutInstalledPkg.add(u);
                    }
                }
                break;
            }
            case UserHandleHidden.USER_NULL:
                installCompleted(sessionId, STATUS_FAILURE_INVALID, null, "STATUS_FAILURE_INVALID: No user is selected.");
                Log.d(TAG, "InstallExisting: No user is selected.");
                return false;
            default:
                try {
                    PackageManagerCompat.getPackageInfo(packageName,
                            PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
                    installCompleted(sessionId, STATUS_FAILURE_ABORTED, null, "STATUS_FAILURE_ABORTED: Already installed.");
                    Log.d(TAG, "InstallExisting: Already installed.");
                    return false;
                } catch (Throwable th) {
                    userIdWithoutInstalledPkg.add(userId);
                }
        }
        if (userIdWithoutInstalledPkg.isEmpty()) {
            installCompleted(sessionId, STATUS_FAILURE_INVALID, null, "STATUS_FAILURE_INVALID: Could not find a valid user to perform install-existing.");
            Log.d(TAG, "InstallExisting: Could not find any valid user.");
            return false;
        }
        int installFlags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            installFlags |= INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS;
        }
        int installReason;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            installReason = PackageManager.INSTALL_REASON_USER;
        } else installReason = 0;
        for (int u : userIdWithoutInstalledPkg) {
            try {
                int res = PackageManagerCompat.installExistingPackageAsUser(packageName, u, installFlags, installReason, null);
                if (res != 1 /* INSTALL_SUCCEEDED */) {
                    installCompleted(sessionId, res, null, null);
                    Log.e(TAG, "InstallExisting: Install failed with code " + res);
                    return false;
                }
                if (u != UserHandleHidden.myUserId()) {
                    BroadcastUtils.sendPackageAdded(ContextUtils.getContext(), new String[]{packageName});
                }
            } catch (Throwable th) {
                installCompleted(sessionId, STATUS_FAILURE_ABORTED, null, "STATUS_FAILURE_ABORTED: " + th.getMessage());
                Log.e(TAG, "InstallExisting: Could not install package for user " + u, th);
                return false;
            }
        }
        installCompleted(sessionId, STATUS_SUCCESS, null, null);
        return true;
    }

    @WorkerThread
    private void copyObb(@NonNull ApkFile apkFile, @UserIdInt int userId) {
        if (!apkFile.hasObb()) return;
        boolean tmpCloseApkFile = closeApkFile;
        // Disable closing apk file in case the installation is finished already.
        closeApkFile = false;
        try {
            // Get writable OBB directory
            Path writableObbDir = ApkUtils.getOrCreateObbDir(packageName, userId);
            // Delete old files
            for (Path oldFile : writableObbDir.listFiles()) {
                oldFile.delete();
            }
            apkFile.extractObb(writableObbDir);
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.obb_files_extracted_successfully));
        } catch (Exception e) {
            Log.e(TAG, e);
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.failed_to_extract_obb_files));
        } finally {
            if (installWatcher.getCount() != 0) {
                // Reset close apk file if the installation isn't completed
                closeApkFile = tmpCloseApkFile;
            } else {
                // Install completed, close apk file if requested
                if (tmpCloseApkFile) apkFile.close();
            }
        }
    }

    private void cleanOldSessions() {
        if (isPrivileged) return;
        List<PackageInstaller.SessionInfo> sessionInfoList;
        try {
            sessionInfoList = packageInstaller.getMySessions(context.getPackageName(), UserHandleHidden.myUserId()).getList();
        } catch (Throwable e) {
            Log.w(TAG, "CleanOldSessions: Could not get previous sessions.", e);
            return;
        }
        for (PackageInstaller.SessionInfo sessionInfo : sessionInfoList) {
            try {
                packageInstaller.abandonSession(sessionInfo.getSessionId());
            } catch (Throwable e) {
                Log.w(TAG, "CleanOldSessions: Unable to abandon session", e);
            }
        }
    }

    private boolean abandon() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {  // RemoteException
                Log.e(TAG, "Abandon: Failed to abandon session.");
            }
        }
        return false;
    }

    private void callFinish(int result) {
        sendCompletedBroadcast(packageName, result, sessionId);
    }

    private void installCompleted(int sessionId,
                                  int finalStatus,
                                  @Nullable String blockingPackage,
                                  @Nullable String statusMessage) {
        ThreadUtils.ensureWorkerThread();
        // MIUI-begin: In MIUI 12.5 and 20.2.0, it might be required to try installing the APK files more than once.
        if (finalStatus == STATUS_FAILURE_ABORTED
                && this.sessionId == sessionId
                && onInstallListener != null
                && !Ops.isPrivileged()
                && MiuiUtils.isActualMiuiVersionAtLeast("12.5", "20.2.0")
                && Objects.equals(statusMessage, "INSTALL_FAILED_ABORTED: Permission denied")
                && attempts <= 3) {
            // Try once more
            ++attempts;
            Log.i(TAG, "MIUI: Installation attempt no " + attempts + " for package " + packageName);
            interactionWatcher.countDown();
            installWatcher.countDown();
            // Remove old broadcast receivers
            unregisterReceiver();
            onInstallListener.onAnotherAttemptInMiui(apkFile);
            return;
        }
        // MIUI-end
        // No need to check package name since it's been checked before
        if (finalStatus == STATUS_FAILURE_SESSION_CREATE || (this.sessionId == sessionId)) {
            if (onInstallListener != null) {
                onInstallListener.onFinishedInstall(sessionId, packageName, finalStatus,
                        blockingPackage, statusMessage);
            }
            if (closeApkFile && apkFile != null) {
                apkFile.close();
            }
            interactionWatcher.countDown();
            installWatcher.countDown();
        }
    }

    @SuppressWarnings("deprecation")
    public boolean uninstall(String packageName, @UserIdInt int userId, boolean keepData) {
        ThreadUtils.ensureWorkerThread();
        this.packageName = packageName;
        String callerPackageName = isPrivileged ? ActivityManagerCompat.SHELL_PACKAGE_NAME : context.getPackageName();
        initBroadcastReceiver();
        try {
            int flags;
            try {
                flags = getDeleteFlags(packageName, userId, keepData);
            } catch (Exception e) {
                callFinish(STATUS_FAILURE_SESSION_CREATE);
                Log.e(TAG, "Uninstall: Could not get PackageInstaller.", e);
                return false;
            }
            userId = getCorrectUserIdForUninstallation(packageName, userId);
            try {
                packageInstaller = PackageManagerCompat.getPackageInstaller();
            } catch (RemoteException e) {
                callFinish(STATUS_FAILURE_SESSION_CREATE);
                Log.e(TAG, "Uninstall: Could not get PackageInstaller.", e);
                return false;
            }

            // Perform uninstallation
            IntentSender sender;
            LocalIntentReceiver intentReceiver;
            if (isPrivileged) {
                Log.d(TAG, "Uninstall: Uninstall via LocalIntentReceiver...");
                try {
                    intentReceiver = new LocalIntentReceiver();
                    sender = intentReceiver.getIntentSender();
                } catch (Exception e) {
                    callFinish(STATUS_FAILURE_SESSION_COMMIT);
                    Log.e(TAG, "Uninstall: Could not uninstall " + packageName, e);
                    return false;
                }
            } else {
                Log.d(TAG, "Uninstall: Calling activity to request permission...");
                intentReceiver = null;
                Intent callbackIntent = new Intent(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER);
                @SuppressLint("WrongConstant")
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, callbackIntent,
                        PendingIntentCompat.FLAG_MUTABLE);
                sender = pendingIntent.getIntentSender();
            }
            Log.d(TAG, "Uninstall: Uninstalling...");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    packageInstaller.uninstall(new VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST),
                            callerPackageName, flags, sender, userId);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    packageInstaller.uninstall(packageName, callerPackageName, flags, sender, userId);
                } else packageInstaller.uninstall(packageName, flags, sender, userId);
            } catch (Throwable th) { // primarily RemoteException
                callFinish(STATUS_FAILURE_SESSION_COMMIT);
                Log.e(TAG, "Uninstall: Could not uninstall " + packageName, th);
                return false;
            }
            if (!isPrivileged) {
                Log.d(TAG, "Uninstall: Waiting for user interaction...");
                // Wait for user interaction (if needed)
                try {
                    // Wait for user interaction
                    interactionWatcher.await();
                    // Wait for the installation to complete
                    installWatcher.await(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Installation interrupted.", e);
                }
            } else {
                Intent resultIntent = intentReceiver.getResult();
                finalStatus = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                statusMessage = resultIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            }
            Log.d(TAG, "Uninstall: Finished with status " + finalStatus);
            if (!installCompleted) {
                installCompleted(sessionId, finalStatus, null, statusMessage);
            }
            if (finalStatus == PackageInstaller.STATUS_SUCCESS && userId != UserHandleHidden.myUserId()) {
                BroadcastUtils.sendPackageAltered(ContextUtils.getContext(), new String[]{packageName});
            }
            return finalStatus == PackageInstaller.STATUS_SUCCESS;
        } finally {
            unregisterReceiver();
        }
    }

    @DeleteFlags
    private static int getDeleteFlags(@NonNull String packageName, @UserIdInt int userId, boolean keepData)
            throws PackageManager.NameNotFoundException, RemoteException {
        boolean isPrivileged = Ops.isReallyPrivileged();
        int flags = 0;
        if (!isPrivileged || userId != UserHandleHidden.USER_ALL) {
            if (!isPrivileged && userId == UserHandleHidden.USER_ALL) {
                // PackageInfo expects a valid user ID
                userId = UserHandleHidden.myUserId();
            }
            PackageInfo info = PackageManagerCompat.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES
                    | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, userId);
            final boolean isSystem = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // If we are being asked to delete a system app for just one
            // user set flag so it disables rather than reverting to system
            // version of the app.
            if (isSystem) {
                flags |= DELETE_SYSTEM_APP;
            }
        } else {
            flags |= DELETE_ALL_USERS;
        }
        if (isPrivileged && keepData) {
            flags |= DELETE_KEEP_DATA;
        }
        return flags;
    }

    private static int getCorrectUserIdForUninstallation(@NonNull String packageName, @UserIdInt int userId) {
        if (userId == UserHandleHidden.USER_ALL) {
            int[] users = Users.getUsersIds();
            for (int user : users) {
                try {
                    PackageManagerCompat.getPackageInfo(packageName, MATCH_UNINSTALLED_PACKAGES
                            | PackageManagerCompat.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES, user);
                    return user;
                } catch (Throwable ignore) {
                }
            }
        }
        return userId;
    }

    // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/pm/PackageManagerShellCommand.java;l=3855;drc=d31ee388115d17c2fd337f2806b37390c7d29834
    private static class LocalIntentReceiver {
        private final LinkedBlockingQueue<Intent> mResult = new LinkedBlockingQueue<>();

        private final IIntentSender.Stub mLocalSender = new IIntentSender.Stub() {
            @Override
            public int send(int code, Intent intent, String resolvedType, IIntentReceiver finishedReceiver, String requiredPermission) {
                send(intent);
                return 0;
            }

            @Override
            public int send(int code, Intent intent, String resolvedType, IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
                send(intent);
                return 0;
            }

            @Override
            public void send(int code, Intent intent, String resolvedType, IBinder whitelistToken, IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
                send(intent);
            }

            public void send(Intent intent) {
                try {
                    mResult.offer(intent, 5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        @SuppressWarnings("JavaReflectionMemberAccess")
        public IntentSender getIntentSender() throws Exception {
            return IntentSender.class.getConstructor(IBinder.class)
                    .newInstance(mLocalSender.asBinder());
        }

        public Intent getResult() {
            try {
                return mResult.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void unregisterReceiver() {
        if (piReceiver != null) {
            ContextUtils.unregisterReceiver(context, piReceiver);
        }
        ContextUtils.unregisterReceiver(context, broadcastReceiver);
    }

    private void initBroadcastReceiver() {
        installWatcher = new CountDownLatch(1);
        interactionWatcher = new CountDownLatch(1);
        piReceiver = new PackageInstallerBroadcastReceiver();
        piReceiver.setAppLabel(appLabel);
        piReceiver.setPackageName(packageName);
        context.registerReceiver(piReceiver, new IntentFilter(PackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER));
        // Add receivers
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_INSTALL_COMPLETED);
        intentFilter.addAction(ACTION_INSTALL_STARTED);
        intentFilter.addAction(ACTION_INSTALL_INTERACTION_BEGIN);
        intentFilter.addAction(ACTION_INSTALL_INTERACTION_END);
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    static void sendStartedBroadcast(String packageName, int sessionId) {
        Intent broadcastIntent = new Intent(ACTION_INSTALL_STARTED);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        context.sendBroadcast(broadcastIntent);
    }

    static void sendCompletedBroadcast(String packageName, @Status int status, int sessionId) {
        Intent broadcastIntent = new Intent(ACTION_INSTALL_COMPLETED);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, packageName);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_STATUS, status);
        broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId);
        context.sendBroadcast(broadcastIntent);
    }
}
