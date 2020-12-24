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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.utils.AppPref;

public final class PackageInstallerShell extends AMPackageInstaller {
    public static final String TAG = "PIS";

    private static final String installCmd = RunnerUtils.CMD_PM;

    @SuppressLint("StaticFieldLeak")
    private static PackageInstallerShell INSTANCE;

    public static PackageInstallerShell getInstance(int userHandle) {
        if (INSTANCE == null) INSTANCE = new PackageInstallerShell(userHandle);
        return INSTANCE;
    }

    private int sessionId = -1;
    private final int userHandle;
    private String packageName;

    public PackageInstallerShell(int userHandle) {
        this.userHandle = userHandle;
    }

    @Override
    public boolean install(@NonNull ApkFile apkFile) {
        if (apkFile.needSigning()) {
            // TODO(23/12/20): Find a way to pipe the streams directly instead of staging first
            File[] apkFiles;
            try {
                apkFiles = getStagingApkFiles(apkFile.getSelectedEntries());
            } catch (IOException e) {
                Log.e(TAG, "Install: Could not cache apk files.", e);
                return false;
            }
            return install(apkFiles, apkFile.getPackageName());
        } else {
            this.packageName = apkFile.getPackageName();
            if (!openSession()) return false;
            String cmd;
            Runner.Result result;
            String buf;
            // Write apk files
            for (ApkFile.Entry entry : apkFile.getSelectedEntries()) {
                try (InputStream apkInputStream = entry.getSignedInputStream(context)) {
                    cmd = installCmd + " install-write -S " + entry.getFileSize() + " " + sessionId +
                            " " + entry.getFileName() + " -";
                    result = Runner.runCommand(cmd, apkInputStream);
                    buf = result.getOutput();
                    if (!buf.contains("Success")) {
                        sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_WRITE, sessionId);
                        Log.e(TAG, String.format("Install: Failed to write %s.", entry.getFileName()));
                        return abandon();
                    }
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
    }

    // https://cs.android.com/android/_/android/platform/system/core/+/5b940dc7f9c0364d84469cad7b47a5ffaa33600b:adb/client/adb_install.cpp;drc=71afeb9a5e849e8752c470aa31c568be2e48d0b6;l=538
    @Override
    public boolean install(@NonNull File[] apkFiles, String packageName) {
        this.packageName = packageName;
        if (!openSession()) return false;
        String cmd;
        Runner.Result result;
        String buf;
        // Write apk files
        for (File apkFile : apkFiles) {
            try (InputStream apkInputStream = new FileInputStream(apkFile)) {
                cmd = installCmd + " install-write -S " + apkFile.length() + " " + sessionId + " " +
                        apkFile.getName() + " -";
                result = Runner.runCommand(cmd, apkInputStream);
                buf = result.getOutput();
                if (!buf.contains("Success")) {
                    sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_WRITE, sessionId);
                    Log.e(TAG, String.format("Install: Failed to write %s.", apkFile.getName()));
                    return abandon();
                }
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
    boolean openSession() {
        String cmd = installCmd + " install-create -r -d -t --user " +
                RunnerUtils.userHandleToUser(userHandle) + " -i " +
                AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALLER_APP_STR) +
                " --install-location " + AppPref.get(AppPref.PrefKey.PREF_INSTALLER_INSTALL_LOCATION_INT);
        Runner.Result result = Runner.runCommand(cmd);
        String buf = result.getOutput();
        if (!buf.contains("Success")) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_CREATE, sessionId);
            Log.e(TAG, "Install: Failed to create install session. " + buf);
            return false;
        }
        int start = buf.indexOf('[');
        int end = buf.indexOf(']');
        try {
            sessionId = Integer.parseInt(buf.substring(start + 1, end));
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_CREATE, sessionId);
            Log.e(TAG, "Install: Failed to parse session id.", e);
            return false;
        }
        if (sessionId < 0) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_CREATE, sessionId);
            Log.e(TAG, "Install: Session id cannot be less than 0.");
            return false;
        }
        sendStartedBroadcast(packageName, sessionId);
        return true;
    }

    @Override
    boolean abandon() {
        Runner.Result result = Runner.runCommand(installCmd + " install-abandon " + sessionId);
        String buf = result.getOutput();
        if (!buf.contains("Success")) {
            sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_ABANDON, sessionId);
            Log.e(TAG, "Abandon: Failed to abandon session.");
        }
        return false;
    }

    @Override
    boolean commit() {
        // Finalize session
        Runner.Result result = Runner.runCommand(installCmd + " install-commit " + sessionId);
        String buf = result.getOutput();
        if (!buf.contains("Success")) {
            int start = buf.indexOf('[');
            int end = buf.indexOf(']');
            try {
                String statusStr = buf.substring(start + 1, end);
                sendCompletedBroadcast(packageName, statusStrToStatus(statusStr), sessionId);
                Log.e(TAG, "Install: " + statusStr);
            } catch (IndexOutOfBoundsException e) {
                sendCompletedBroadcast(packageName, STATUS_FAILURE_SESSION_COMMIT, sessionId);
                Log.e(TAG, "Install: Failed to commit the install.");
            }
            return false;
        }
        sendCompletedBroadcast(packageName, STATUS_SUCCESS, sessionId);
        return true;
    }

    @NonNull
    public static File[] getStagingApkFiles(@NonNull List<ApkFile.Entry> apkEntries) throws IOException {
        File[] apkFiles = new File[apkEntries.size()];
        for (int i = 0; i < apkFiles.length; ++i) apkFiles[i] = apkEntries.get(i).getSignedFile(context);
        return apkFiles;
    }

    private int statusStrToStatus(@NonNull String statusStr) {
        switch (statusStr) {
//            case "INSTALL_FAILED_ALREADY_EXISTS":
//            case "INSTALL_FAILED_INVALID_APK":
//            case "INSTALL_FAILED_INVALID_URI":
//            case "INSTALL_FAILED_NO_SHARED_USER":
//            case "INSTALL_FAILED_DEXOPT":
//            case "INSTALL_FAILED_CONTAINER_ERROR":
//            case "INSTALL_FAILED_INVALID_INSTALL_LOCATION":
//            case "INSTALL_FAILED_VERIFICATION_TIMEOUT":
//            case "INSTALL_FAILED_MISSING_SPLIT":
//            case "INSTALL_PARSE_FAILED_NOT_APK":
//            case "INSTALL_PARSE_FAILED_BAD_MANIFEST":
//            case "INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION":
//            case "INSTALL_PARSE_FAILED_NO_CERTIFICATES":
//            case "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES":
//            case "INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING":
//            case "INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME":
//            case "INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID":
//            case "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED":
//            case "INSTALL_PARSE_FAILED_MANIFEST_EMPTY":
//            case "INSTALL_FAILED_INTERNAL_ERROR":
//            case "INSTALL_FAILED_DUPLICATE_PERMISSION":
//            case "INSTALL_FAILED_INSTANT_APP_INVALID":
//            case "INSTALL_FAILED_BAD_DEX_METADATA":
//            case "INSTALL_FAILED_BAD_SIGNATURE":
//            case "INSTALL_FAILED_MULTIPACKAGE_INCONSISTENCY":
//                return STATUS_FAILURE_INVALID;
            case "INSTALL_FAILED_INSUFFICIENT_STORAGE":
            case "INSTALL_FAILED_MEDIA_UNAVAILABLE":
                return STATUS_FAILURE_STORAGE;
            case "INSTALL_FAILED_DUPLICATE_PACKAGE":
            case "INSTALL_FAILED_CONFLICTING_PROVIDER":
                return STATUS_FAILURE_CONFLICT;
            case "INSTALL_FAILED_UPDATE_INCOMPATIBLE":
            case "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE":
            case "INSTALL_FAILED_MISSING_SHARED_LIBRARY":
            case "INSTALL_FAILED_REPLACE_COULDNT_DELETE":
            case "INSTALL_FAILED_OLDER_SDK":
            case "INSTALL_FAILED_NEWER_SDK":
            case "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE":
            case "INSTALL_FAILED_MISSING_FEATURE":
            case "INSTALL_FAILED_VERSION_DOWNGRADE":
            case "INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE":
            case "INSTALL_FAILED_SANDBOX_VERSION_DOWNGRADE":
            case "INSTALL_FAILED_NO_MATCHING_ABIS":
                return STATUS_FAILURE_INCOMPATIBLE;
            case "INSTALL_FAILED_TEST_ONLY":
            case "INSTALL_FAILED_VERIFICATION_FAILURE":
            case "INSTALL_FAILED_PACKAGE_CHANGED":
            case "INSTALL_FAILED_UID_CHANGED":
            case "INSTALL_FAILED_USER_RESTRICTED":
            case "INSTALL_FAILED_OTHER_STAGED_SESSION_IN_PROGRESS":
            case "INSTALL_FAILED_WRONG_INSTALLED_VERSION":
                return STATUS_FAILURE_BLOCKED;
            case "INSTALL_FAILED_ABORTED":
                return STATUS_FAILURE_ABORTED;
        }
        return STATUS_FAILURE_INVALID;
    }
}
