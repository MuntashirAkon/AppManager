// SPDX-License-Identifier: GPL-3.0-or-later

package android.app.backup;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

/**
 * Direct interface to the Backup Manager Service that applications invoke on.  The only
 * operation currently needed is a simple notification that the app has made changes to
 * data it wishes to back up, so the system should run a backup pass.
 * <p>
 * Apps will use the {@link android.app.backup.BackupManager} class rather than going through
 * this Binder interface directly.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
public interface IBackupManager extends IInterface {
    abstract class Stub extends Binder implements IBackupManager {
        public static IBackupManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Enable/disable the backup service entirely.  When disabled, no backup
     * or restore operations will take place.  Data-changed notifications will
     * still be observed and collected, however, so that changes made while the
     * mechanism was disabled will still be backed up properly if it is enabled
     * at some point in the future.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     * If {@code userId} is different from the calling user id, then the caller must hold the
     * android.permission.INTERACT_ACROSS_USERS_FULL permission.
     *
     * @param userId User id for which backup service should be enabled/disabled.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    void setBackupEnabledForUser(int userId, boolean isEnabled) throws RemoteException;

    /**
     * Enable/disable the backup service entirely.  When disabled, no backup
     * or restore operations will take place.  Data-changed notifications will
     * still be observed and collected, however, so that changes made while the
     * mechanism was disabled will still be backed up properly if it is enabled
     * at some point in the future.
     * <p>
     * Callers must hold the android.permission.BACKUP permission to use this method.
     */
    void setBackupEnabled(boolean isEnabled) throws RemoteException;

    /**
     * Report whether the backup mechanism is currently enabled.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     * If {@code userId} is different from the calling user id, then the caller must hold the
     * android.permission.INTERACT_ACROSS_USERS_FULL permission.
     *
     * @param userId User id for which the backup service status should be reported.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean isBackupEnabledForUser(int userId) throws RemoteException;

    /**
     * Report whether the backup mechanism is currently enabled.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    boolean isBackupEnabled() throws RemoteException;

    /**
     * Set the device's backup password.  Returns {@code true} if the password was set
     * successfully, {@code false} otherwise.  Typically a failure means that an incorrect
     * current password was supplied.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    boolean setBackupPassword(String currentPw, String newPw) throws RemoteException;

    /**
     * Reports whether a backup password is currently set.  If not, then a null or empty
     * "current password" argument should be passed to setBackupPassword().
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     */
    boolean hasBackupPassword() throws RemoteException;

    /**
     * Write a full backup of the given package to the supplied file descriptor.
     * The fd may be a socket or other non-seekable destination.  If no package names
     * are supplied, then every application on the device will be backed up to the output.
     *
     * <p>This method is <i>synchronous</i> -- it does not return until the backup has
     * completed.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @param fd                The file descriptor to which a 'tar' file stream is to be written
     * @param includeApks       If <code>true</code>, the resulting tar stream will include the
     *                          application .apk files themselves as well as their data.
     * @param includeObbs       If <code>true</code>, the resulting tar stream will include any
     *                          application expansion (OBB) files themselves belonging to each application.
     * @param includeShared     If <code>true</code>, the resulting tar stream will include
     *                          the contents of the device's shared storage (SD card or equivalent).
     * @param allApps           If <code>true</code>, the resulting tar stream will include all
     *                          installed applications' data, not just those named in the <code>packageNames</code>
     *                          parameter.
     * @param allIncludesSystem If {@code true}, then {@code allApps} will be interpreted
     *                          as including packages pre-installed as part of the system. If {@code false},
     *                          then setting {@code allApps} to {@code true} will mean only that all 3rd-party
     *                          applications will be included in the dataset.
     * @param packageNames      The package names of the apps whose data (and optionally .apk files)
     *                          are to be backed up.  The <code>allApps</code> parameter supersedes this.
     * @deprecated Replaced by {@link #adbBackup(ParcelFileDescriptor, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, String[])} in API 26 (Android O)
     */
    @Deprecated
    void fullBackup(ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs,
                    boolean includeShared, boolean doWidgets, boolean allApps, boolean allIncludesSystem,
                    boolean doCompress, String[] packageNames) throws RemoteException;

    /**
     * Write a backup of the given package to the supplied file descriptor.
     * The fd may be a socket or other non-seekable destination.  If no package names
     * are supplied, then every application on the device will be backed up to the output.
     * Currently only used by the 'adb backup' command.
     *
     * <p>This method is <i>synchronous</i> -- it does not return until the backup has
     * completed.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @param fd                The file descriptor to which a 'tar' file stream is to be written
     * @param includeApks       If <code>true</code>, the resulting tar stream will include the
     *                          application .apk files themselves as well as their data.
     * @param includeObbs       If <code>true</code>, the resulting tar stream will include any
     *                          application expansion (OBB) files themselves belonging to each application.
     * @param includeShared     If <code>true</code>, the resulting tar stream will include
     *                          the contents of the device's shared storage (SD card or equivalent).
     * @param allApps           If <code>true</code>, the resulting tar stream will include all
     *                          installed applications' data, not just those named in the <code>packageNames</code>
     *                          parameter.
     * @param allIncludesSystem If {@code true}, then {@code allApps} will be interpreted
     *                          as including packages pre-installed as part of the system. If {@code false},
     *                          then setting {@code allApps} to {@code true} will mean only that all 3rd-party
     *                          applications will be included in the dataset.
     * @param doKeyValue        If {@code true}, also packages supporting key-value backup will be backed
     *                          up. If {@code false}, key-value packages will be skipped.
     * @param packageNames      The package names of the apps whose data (and optionally .apk files)
     *                          are to be backed up.  The <code>allApps</code> parameter supersedes this.
     * @deprecated Replaced by {@link #adbBackup(int, ParcelFileDescriptor, boolean, boolean, boolean, boolean, boolean, boolean, boolean, boolean, String[])} in API 29 (Android 10)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    void adbBackup(ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs,
                   boolean includeShared, boolean doWidgets, boolean allApps, boolean allIncludesSystem,
                   boolean doCompress, boolean doKeyValue, String[] packageNames) throws RemoteException;

    /**
     * Write a backup of the given package to the supplied file descriptor.
     * The fd may be a socket or other non-seekable destination.  If no package names
     * are supplied, then every application on the device will be backed up to the output.
     * Currently only used by the 'adb backup' command.
     *
     * <p>This method is <i>synchronous</i> -- it does not return until the backup has
     * completed.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     * If the {@code userId} is different from the calling user id, then the caller must hold the
     * android.permission.INTERACT_ACROSS_USERS_FULL permission.
     *
     * @param userId            User id for which backup should be performed.
     * @param fd                The file descriptor to which a 'tar' file stream is to be written.
     * @param includeApks       If <code>true</code>, the resulting tar stream will include the
     *                          application .apk files themselves as well as their data.
     * @param includeObbs       If <code>true</code>, the resulting tar stream will include any
     *                          application expansion (OBB) files themselves belonging to each application.
     * @param includeShared     If <code>true</code>, the resulting tar stream will include
     *                          the contents of the device's shared storage (SD card or equivalent).
     * @param allApps           If <code>true</code>, the resulting tar stream will include all
     *                          installed applications' data, not just those named in the <code>packageNames</code>
     *                          parameter.
     * @param allIncludesSystem If {@code true}, then {@code allApps} will be interpreted
     *                          as including packages pre-installed as part of the system. If {@code false},
     *                          then setting {@code allApps} to {@code true} will mean only that all 3rd-party
     *                          applications will be included in the dataset.
     * @param doKeyValue        If {@code true}, also packages supporting key-value backup will be backed
     *                          up. If {@code false}, key-value packages will be skipped.
     * @param packageNames      The package names of the apps whose data (and optionally .apk files)
     *                          are to be backed up.  The <code>allApps</code> parameter supersedes this.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    void adbBackup(int userId, ParcelFileDescriptor fd, boolean includeApks, boolean includeObbs,
                   boolean includeShared, boolean doWidgets, boolean allApps, boolean allIncludesSystem,
                   boolean doCompress, boolean doKeyValue, String[] packageNames) throws RemoteException;

    /**
     * Restore device content from the data stream passed through the given socket.  The
     * data stream must be in the format emitted by fullBackup().
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @deprecated Replaced by {@link #adbRestore(ParcelFileDescriptor)} in API 26 (Android O)
     */
    void fullRestore(ParcelFileDescriptor fd) throws RemoteException;

    /**
     * Restore device content from the data stream passed through the given socket.  The
     * data stream must be in the format emitted by adbBackup().
     * Currently only used by the 'adb restore' command.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @deprecated Replaced by {@link #adbRestore(int, ParcelFileDescriptor)} in API 29 (Android 10)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.O)
    void adbRestore(ParcelFileDescriptor fd) throws RemoteException;

    /**
     * Restore device content from the data stream passed through the given socket.  The
     * data stream must be in the format emitted by adbBackup().
     * Currently only used by the 'adb restore' command.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     * If the {@code userId} is different from the calling user id, then the caller must hold the
     * android.permission.INTERACT_ACROSS_USERS_FULL.
     *
     * @param userId User id for which restore should be performed.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    void adbRestore(int userId, ParcelFileDescriptor fd) throws RemoteException;

    /**
     * Make the device's backup and restore machinery (in)active.  When it is inactive,
     * the device will not perform any backup operations, nor will it deliver data for
     * restore, although clients can still safely call BackupManager methods.
     *
     * @param whichUser  User handle of the defined user whose backup active state
     *                   is to be adjusted.
     * @param makeActive {@code true} when backup services are to be made active;
     *                   {@code false} otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    void setBackupServiceActive(int whichUser, boolean makeActive) throws RemoteException;

    /**
     * Queries the activity status of backup service as set by {@link #setBackupServiceActive}.
     *
     * @param whichUser User handle of the defined user whose backup active state
     *                  is being queried.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    boolean isBackupServiceActive(int whichUser) throws RemoteException;

    /**
     * Checks if the user is ready for backup or not.
     *
     * @param userId User id for which this operation should be performed.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    boolean isUserReadyForBackup(int userId) throws RemoteException;

    /**
     * Ask the framework whether this app is eligible for backup.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @param packageName The name of the package.
     * @return Whether this app is eligible for backup.
     * @deprecated Replaced by {@link #isAppEligibleForBackupForUser(int, String)} in API 29 (Android 10)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    boolean isAppEligibleForBackup(String packageName) throws RemoteException;

    /**
     * Ask the framework whether this app is eligible for backup.
     *
     * <p>If you are calling this method multiple times, you should instead use
     * {@link #filterAppsEligibleForBackup(String[])} to save resources.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     * If {@code userId} is different from the calling user id, then the caller must hold the
     * android.permission.INTERACT_ACROSS_USERS_FULL permission.
     *
     * @param userId      User id for which this operation should be performed.
     * @param packageName The name of the package.
     * @return Whether this app is eligible for backup.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    boolean isAppEligibleForBackupForUser(int userId, String packageName) throws RemoteException;

    /**
     * Filter the packages that are eligible for backup and return the result.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     *
     * @param packages The list of packages to filter.
     * @return The packages eligible for backup.
     * @deprecated Replaced by {@link #filterAppsEligibleForBackupForUser(int, String[])} in API 29 (Android 10)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    String[] filterAppsEligibleForBackup(String[] packages) throws RemoteException;

    /**
     * Filter the packages that are eligible for backup and return the result.
     *
     * <p>Callers must hold the android.permission.BACKUP permission to use this method.
     * If {@code userId} is different from the calling user id, then the caller must hold the
     * android.permission.INTERACT_ACROSS_USERS_FULL permission.
     *
     * @param userId   User id for which the filter should be performed.
     * @param packages The list of packages to filter.
     * @return The packages eligible for backup.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    String[] filterAppsEligibleForBackupForUser(int userId, String[] packages) throws RemoteException;
}