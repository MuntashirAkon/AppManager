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
import android.app.PendingIntent;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageInstallerSession;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.IOUtils;

@SuppressLint("ShiftFlags")
public final class PackageInstallerCompat extends AMPackageInstaller {
    public static final String TAG = "PINR";

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
    public @interface InstallFlags {}

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

    @SuppressLint("StaticFieldLeak")
    private static PackageInstallerCompat INSTANCE;

    public static PackageInstallerCompat getInstance(int userHandle) {
        if (INSTANCE == null) INSTANCE = new PackageInstallerCompat(userHandle);
        return INSTANCE;
    }

    private IPackageInstaller packageInstaller;
    private PackageInstaller.Session session;
    private String packageName;
    private int sessionId = -1;
    private final int userHandle;
    private final boolean allUsers;

    private PackageInstallerCompat(int userHandle) {
        this.allUsers = userHandle == RunnerUtils.USER_ALL;
        this.userHandle = allUsers ? Users.getCurrentUserHandle() : userHandle;
        Log.d(TAG, "Installing for " + (allUsers ? "all users" : "user " + userHandle));
    }

    @Override
    public boolean install(@NonNull ApkFile apkFile) {
        packageName = apkFile.getPackageName();
        Log.d(TAG, "Install: opening session...");
        if (!openSession()) return false;
        List<ApkFile.Entry> selectedEntries = apkFile.getSelectedEntries();
        Log.d(TAG, "Install: selected entries: " + selectedEntries.size());
        // Write apk files
        for (ApkFile.Entry entry : selectedEntries) {
            try (InputStream apkInputStream = entry.getSignedInputStream(context);
                 OutputStream apkOutputStream = session.openWrite(entry.getFileName(), 0, entry.getFileSize())) {
                IOUtils.copy(apkInputStream, apkOutputStream);
                session.fsync(apkOutputStream);
                Log.d(TAG, "Install: copied entry " + entry.name);
            } catch (IOException e) {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_WRITE, sessionId);
                Log.e(TAG, "Install: Cannot copy files to session.", e);
                return abandon();
            } catch (SecurityException e) {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SECURITY, sessionId);
                Log.e(TAG, "Install: Cannot access apk files.", e);
                return abandon();
            }
        }
        Log.d(TAG, "Install: Running installation...");
        return commit();
    }

    @Override
    public boolean install(@NonNull File[] apkFiles, String packageName) {
        this.packageName = packageName;
        if (!openSession()) return false;
        // Write apk files
        for (File apkFile : apkFiles) {
            try (InputStream apkInputStream = new FileInputStream(apkFile);
                 OutputStream apkOutputStream = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
                IOUtils.copy(apkInputStream, apkOutputStream);
                session.fsync(apkOutputStream);
            } catch (IOException e) {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_WRITE, sessionId);
                Log.e(TAG, "Install: Cannot copy files to session.", e);
                return abandon();
            } catch (SecurityException e) {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SECURITY, sessionId);
                Log.e(TAG, "Install: Cannot access apk files.", e);
                return abandon();
            }
        }
        // Commit
        return commit();
    }

    @Override
    boolean commit() {
        Log.d(TAG, "Commit: calling activity to request permission...");
        IntentSender sender;
        boolean isPrivileged = LocalServer.isAMServiceAlive();
        final Intent[] result = new Intent[1];
        CountDownLatch countDownLatch = new CountDownLatch(isPrivileged ? 1 : 0);
        if (isPrivileged) {
            try {
                //noinspection JavaReflectionMemberAccess
                sender = IntentSender.class.getConstructor(IIntentSender.class)
                        .newInstance(new IIntentSenderAdaptor() {
                            @Override
                            public void send(Intent intent) {
                                result[0] = intent;
                                countDownLatch.countDown();
                            }
                        });
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
                Log.e(TAG, "Commit: Could not commit session.", e);
                return false;
            }
        } else {
            Intent callbackIntent = new Intent(AMPackageInstallerBroadcastReceiver.ACTION_PI_RECEIVER);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, callbackIntent, 0);
            sender = pendingIntent.getIntentSender();
        }
        try {
            session.commit(sender);
        } catch (Exception e) {  // RemoteException
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
            Log.e(TAG, "Commit: Could not commit session.", e);
            return false;
        } catch (NoSuchMethodError e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    session.commit(sender);
                } catch (Exception remoteException) {
                    sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
                    Log.e(TAG, "Commit: Could not commit session.", remoteException);
                    return false;
                }
            } else {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
                Log.e(TAG, "Commit: Could not commit session.", e);
                return false;
            }
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
            Log.e(TAG, "Commit: Could not commit session.", e);
            return false;
        }
        if (isPrivileged && result[0] == null) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
            Log.e(TAG, "Commit: Could not commit session. Returned result is null.");
            return false;
        }
        if (result[0] != null) {
            Intent resultIntent = result[0];
            int status = resultIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                sendCompletedBroadcast(packageName, STATUS_SUCCESS, sessionId);
                return true;
            } else {
                sendCompletedBroadcast(packageName, status, sessionId);
                return false;
            }
        }
        return true;  // Fallback for no-root
    }

    @Override
    boolean openSession() {
        try {
            packageInstaller = IPackageInstaller.Stub.asInterface(new ProxyBinder(AppManager.getIPackageManager().getPackageInstaller().asBinder()));
        } catch (RemoteException e) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_CREATE, sessionId);
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
            sessionId = packageInstaller.createSession(sessionParams, context.getPackageName(), userHandle);
            Log.d(TAG, "OpenSession: session id " + sessionId);
        } catch (RemoteException e) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_CREATE, sessionId);
            Log.e(TAG, "OpenSession: Failed to create install session.", e);
            return false;
        }
        try {
            session = PackageInstallerUtils.createSession(IPackageInstallerSession.Stub.asInterface(new ProxyBinder(packageInstaller.openSession(sessionId).asBinder())));
            Log.d(TAG, "OpenSession: session opened.");
        } catch (RemoteException | InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_CREATE, sessionId);
            Log.e(TAG, "OpenSession: Failed to open install session.", e);
            return false;
        }
        sendStartedBroadcast(packageName, sessionId);
        return true;
    }

    private void cleanOldSessions() {
        List<PackageInstaller.SessionInfo> sessionInfoList;
        try {
            sessionInfoList = packageInstaller.getMySessions(context.getPackageName(), userHandle).getList();
        } catch (RemoteException e) {
            Log.w(TAG, "CleanOldSessions: Could not get previous sessions.");
            return;
        }
        for (PackageInstaller.SessionInfo sessionInfo : sessionInfoList) {
            try {
                packageInstaller.abandonSession(sessionInfo.getSessionId());
            } catch (RemoteException e) {
                Log.w(TAG, "CleanOldSessions: Unable to abandon session", e);
            }
        }
    }

    @Override
    boolean abandon() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {  // RemoteException
                Log.e(TAG, "Abandon: Failed to abandon session.");
            }
        }
        return false;
    }
}
