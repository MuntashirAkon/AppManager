// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.app.PendingIntent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;

import java.util.List;

public interface IPackageInstaller extends IInterface {
    /**
     * @deprecated Replaced in Android 12 (API 31) with {@link #createSession(PackageInstaller.SessionParams, String, String, int)}
     */
    @Deprecated
    int createSession(PackageInstaller.SessionParams params, String installerPackageName,
                      int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    int createSession(PackageInstaller.SessionParams params, String installerPackageName,
                      String installerAttributionTag, int userId);

    void updateSessionAppIcon(int sessionId, Bitmap appIcon) throws RemoteException;

    void updateSessionAppLabel(int sessionId, String appLabel) throws RemoteException;

    void abandonSession(int sessionId) throws RemoteException;

    IPackageInstallerSession openSession(int sessionId) throws RemoteException;

    PackageInstaller.SessionInfo getSessionInfo(int sessionId) throws RemoteException;

    ParceledListSlice<PackageInstaller.SessionInfo> getAllSessions(int userId)
            throws RemoteException;

    ParceledListSlice<PackageInstaller.SessionInfo> getMySessions(String installerPackageName,
                                                                  int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    ParceledListSlice<PackageInstaller.SessionInfo> getStagedSessions() throws RemoteException;

    void registerCallback(IPackageInstallerCallback callback, int userId) throws RemoteException;

    void unregisterCallback(IPackageInstallerCallback callback) throws RemoteException;

    /**
     * @deprecated Replaced in Android M (API 23) with {@link #uninstall(String, String, int, IntentSender, int)}
     */
    @Deprecated
    void uninstall(String packageName, int flags, IntentSender statusReceiver, int userId) throws RemoteException;

    /**
     * @deprecated Replaced in Android O (API 26) with {@link #uninstall(VersionedPackage, String, int, IntentSender, int)}
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void uninstall(String packageName, String callerPackageName, int flags,
                   IntentSender statusReceiver, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void uninstall(VersionedPackage versionedPackage, String callerPackageName, int flags,
                   IntentSender statusReceiver, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.Q)
    void uninstallExistingPackage(VersionedPackage versionedPackage, String callerPackageName,
                                  IntentSender statusReceiver, int userId);

    @RequiresApi(Build.VERSION_CODES.Q)
    void installExistingPackage(String packageName, int installFlags, int installReason,
                                IntentSender statusReceiver, int userId,
                                List<String> whiteListedPermissions) throws RemoteException;

    void setPermissionsResult(int sessionId, boolean accepted) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void disableVerificationForUid(int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.S)
    void setAllowUnlimitedSilentUpdates(String installerPackageName) throws RemoteException;
    @RequiresApi(Build.VERSION_CODES.S)
    void setSilentUpdatesThrottleTime(long throttleTimeInSeconds) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
    void requestArchive(String packageName, String callerPackageName, int flags,
                        IntentSender statusReceiver, UserHandle userHandle) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
    void requestUnarchive(String packageName, String callerPackageName,
                          IntentSender statusReceiver,
                          UserHandle userHandle) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
    void installPackageArchived(ArchivedPackageParcel archivedPackageParcel,
                                PackageInstaller.SessionParams params,
                                IntentSender statusReceiver,
                                String installerPackageName, UserHandle userHandle) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
    void reportUnarchivalStatus(int unarchiveId, int status, long requiredStorageBytes,
                                PendingIntent userActionIntent, UserHandle userHandle) throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstaller {
        public static IPackageInstaller asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}