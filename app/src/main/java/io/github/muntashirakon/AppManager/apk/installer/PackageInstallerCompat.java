// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.installer;

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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.OsEnvironment;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.PackageManagerCompat;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.ProxyFile;

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

    @SuppressLint({"NewApi", "UniqueConstants"})
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
    @Deprecated
    public static final int INSTALL_ALLOW_DOWNGRADE = 0x00000080;

    @SuppressLint("NewApi")
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
        void onStartInstall(int sessionId, String packageName);

        void onFinishedInstall(int sessionId, String packageName, int result, @Nullable String blockingPackage);
    }

    @NonNull
    public static PackageInstallerCompat getNewInstance(int userHandle) {
        return new PackageInstallerCompat(userHandle);
    }

    @NonNull
    public static PackageInstallerCompat getNewInstance(int userHandle, @NonNull String installerPackageName) {
        PackageInstallerCompat packageInstaller = new PackageInstallerCompat(userHandle);
        packageInstaller.installerPackageName = installerPackageName;
        return packageInstaller;
    }

    @SuppressLint("StaticFieldLeak")
    private static final Context context = AppManager.getContext();

    private CountDownLatch installWatcher;
    private CountDownLatch interactionWatcher;

    private boolean closeApkFile = false;
    private boolean installCompleted = false;
    @Nullable
    private ApkFile apkFile;
    private String packageName;
    private CharSequence appLabel;
    private int sessionId = -1;
    private final int userHandle;
    @Status
    private int finalStatus = STATUS_FAILURE_INVALID;
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
                    // Run install completed
                    installCompleted = true;
                    installCompleted(sessionId, finalStatus, blockingPackage);
                    break;
            }
        }
    };

    @Nullable
    private OnInstallListener onInstallListener;
    private IPackageInstaller packageInstaller;
    private PackageInstaller.Session session;
    private final boolean allUsers;
    private String installerPackageName;
    private final boolean isPrivileged;

    private PackageInstallerCompat(@UserIdInt int userHandle) {
        this.isPrivileged = LocalServer.isAMServiceAlive();
        this.allUsers = isPrivileged && userHandle == UserHandleHidden.USER_ALL;
        this.userHandle = allUsers ? UserHandleHidden.myUserId() : userHandle;
        Log.d(TAG, "Installing for " + (allUsers ? "all users" : "user " + userHandle));
        if (isPrivileged) {
            this.installerPackageName = (String) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR);
        } else {
            this.installerPackageName = context.getPackageName();
        }
        Log.d(TAG, "Installer app: " + installerPackageName);
    }

    public void setOnInstallListener(@Nullable OnInstallListener onInstallListener) {
        this.onInstallListener = onInstallListener;
    }

    public void setAppLabel(CharSequence appLabel) {
        this.appLabel = appLabel;
    }

    public void setCloseApkFile(boolean closeApkFile) {
        this.closeApkFile = closeApkFile;
    }

    public boolean install(@NonNull ApkFile apkFile) {
        try {
            this.apkFile = apkFile;
            this.packageName = apkFile.getPackageName();
            initBroadcastReceiver();
            new Thread(() -> copyObb(apkFile)).start();
            Log.d(TAG, "Install: opening session...");
            if (!openSession()) return false;
            List<ApkFile.Entry> selectedEntries = apkFile.getSelectedEntries();
            Log.d(TAG, "Install: selected entries: " + selectedEntries.size());
            // Write apk files
            for (ApkFile.Entry entry : selectedEntries) {
                try (InputStream apkInputStream = entry.getSignedInputStream(context);
                     OutputStream apkOutputStream = session.openWrite(entry.getFileName(), 0, entry.getFileSize())) {
                    IoUtils.copy(apkInputStream, apkOutputStream);
                    session.fsync(apkOutputStream);
                    Log.d(TAG, "Install: copied entry " + entry.name);
                } catch (IOException | RemoteException e) {
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
            return commit();
        } finally {
            unregisterReceiver();
        }
    }

    public boolean install(@NonNull Path[] apkFiles, String packageName) {
        try {
            this.apkFile = null;
            this.packageName = packageName;
            initBroadcastReceiver();
            if (!openSession()) return false;
            // Write apk files
            for (Path apkFile : apkFiles) {
                try (InputStream apkInputStream = apkFile.openInputStream();
                     OutputStream apkOutputStream = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
                    IoUtils.copy(apkInputStream, apkOutputStream);
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
            return commit();
        } finally {
            unregisterReceiver();
        }
    }

    private boolean commit() {
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
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, callbackIntent, 0);
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
                Log.e("PIS", "Installation interrupted.", e);
            }
        } else {
            Intent resultIntent = intentReceiver.getResult();
            finalStatus = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
        }
        Log.d(TAG, "Commit: Finishing...");
        // We might want to use {@code callFinish(finalStatus);} here, but it doesn't always work
        // since the object is garbage collected almost immediately.
        if (!installCompleted) {
            installCompleted(sessionId, finalStatus, null);
        }
        return finalStatus == PackageInstaller.STATUS_SUCCESS;
    }

    private boolean openSession() {
        try {
            packageInstaller = PackageManagerCompat.getPackageInstaller(AppManager.getIPackageManager());
        } catch (RemoteException e) {
            callFinish(STATUS_FAILURE_SESSION_CREATE);
            Log.e(TAG, "OpenSession: Could not get PackageInstaller.", e);
            return false;
        }
        // Clean old sessions
        cleanOldSessions();
        // Create install session
        PackageInstaller.SessionParams sessionParams = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        try {
            int flags = PackageInstallerUtils.getInstallFlags(sessionParams);
            flags |= (INSTALL_ALLOW_TEST | INSTALL_REPLACE_EXISTING | INSTALL_ALLOW_DOWNGRADE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                flags |= INSTALL_ALLOW_DOWNGRADE_API29;
            }
            if (allUsers) {
                flags |= INSTALL_ALL_USERS;
            }
            PackageInstallerUtils.setInstallFlags(sessionParams, flags);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        sessionParams.setInstallLocation((Integer) AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER);
        }
        try {
            sessionId = packageInstaller.createSession(sessionParams, installerPackageName, userHandle);
            Log.d(TAG, "OpenSession: session id " + sessionId);
        } catch (RemoteException e) {
            callFinish(STATUS_FAILURE_SESSION_CREATE);
            Log.e(TAG, "OpenSession: Failed to create install session.", e);
            return false;
        }
        try {
            session = PackageInstallerUtils.createSession(IPackageInstallerSession.Stub.asInterface(new ProxyBinder(packageInstaller.openSession(sessionId).asBinder())));
            Log.d(TAG, "OpenSession: session opened.");
        } catch (RemoteException | InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            callFinish(STATUS_FAILURE_SESSION_CREATE);
            Log.e(TAG, "OpenSession: Failed to open install session.", e);
            return false;
        }
        sendStartedBroadcast(packageName, sessionId);
        return true;
    }

    @WorkerThread
    private void copyObb(@NonNull ApkFile apkFile) {
        if (!apkFile.hasObb()) return;
        boolean tmpCloseApkFile = closeApkFile;
        // Disable closing apk file in case the install is finished already.
        closeApkFile = false;
        try {
            // Get the first writable external storage directory
            OsEnvironment.UserEnvironment ue = OsEnvironment.getUserEnvironment(userHandle);
            ProxyFile[] extDirs = ue.getExternalDirs();
            ProxyFile writableExtDir = null;
            for (ProxyFile extDir : extDirs) {
                if (extDir.canWrite() || extDir.getAbsolutePath().startsWith("/storage/emulated")) {
                    writableExtDir = extDir;
                    break;
                }
            }
            if (writableExtDir == null) throw new IOException("Couldn't find any writable Obb dir");
            // Get writable OBB directory
            final ProxyFile writableObbDir = new ProxyFile(writableExtDir.getAbsolutePath() + "/" +
                    ApkFile.OBB_DIR + "/" + packageName);
            if (writableObbDir.exists()) {
                File[] oldObbFiles = ArrayUtils.defeatNullable(writableObbDir.listFiles());
                // Delete old files
                for (File oldFile : oldObbFiles) {
                    //noinspection ResultOfMethodCallIgnored
                    oldFile.delete();
                }
            } else {
                if (!writableObbDir.mkdirs()) throw new IOException("Couldn't create Obb dir");
            }
            apkFile.extractObb(writableObbDir);
            UiThreadHandler.run(() -> UIUtils.displayLongToast(R.string.obb_files_extracted_successfully));
        } catch (Exception e) {
            Log.e(TAG, e);
            UiThreadHandler.run(() -> UIUtils.displayLongToast(R.string.failed_to_extract_obb_files));
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
            sessionInfoList = packageInstaller.getMySessions(context.getPackageName(), userHandle).getList();
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

    private void installCompleted(int sessionId, int finalStatus,
                                  @Nullable String blockingPackage) {
        // No need to check package name since it's been checked before
        if (finalStatus == STATUS_FAILURE_SESSION_CREATE || (sessionId != -1 && this.sessionId == sessionId)) {
            if (onInstallListener != null) {
                onInstallListener.onFinishedInstall(sessionId, packageName, finalStatus, blockingPackage);
            }
            if (closeApkFile && apkFile != null) {
                apkFile.close();
            }
            interactionWatcher.countDown();
            installWatcher.countDown();
        }
    }

    @SuppressWarnings("deprecation")
    public static void uninstall(String packageName, @UserIdInt int userHandle, boolean keepData) throws Exception {
        IPackageInstaller pi = PackageManagerCompat.getPackageInstaller(AppManager.getIPackageManager());
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        IntentSender sender = receiver.getIntentSender();
        boolean isPrivileged = LocalServer.isAMServiceAlive();
        int flags = 0;
        if (!isPrivileged || userHandle != UserHandleHidden.USER_ALL) {
            PackageInfo info = PackageManagerCompat.getPackageInfo(packageName, 0, userHandle);
            final boolean isSystem = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // If we are being asked to delete a system app for just one
            // user set flag so it disables rather than reverting to system
            // version of the app.
            if (isSystem) {
                flags |= DELETE_SYSTEM_APP;
            }
        }
        if (isPrivileged) {
            if (keepData) {
                flags |= DELETE_KEEP_DATA;
            }
            if (userHandle == UserHandleHidden.USER_ALL) {
                flags |= DELETE_ALL_USERS;
                // Get correct user handle
                int[] users = Users.getUsersIds();
                for (int user : users) {
                    try {
                        PackageManagerCompat.getPackageInfo(packageName, 0, user);
                        userHandle = user;
                        break;
                    } catch (Throwable ignore) {
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pi.uninstall(new VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST),
                    null, flags, sender, userHandle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pi.uninstall(packageName, null, flags, sender, userHandle);
        } else pi.uninstall(packageName, flags, sender, userHandle);
        final Intent result = receiver.getResult();
        final int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status != PackageInstaller.STATUS_SUCCESS) {
            throw new Exception(result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
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
        if (piReceiver != null) context.unregisterReceiver(piReceiver);
        context.unregisterReceiver(broadcastReceiver);
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
