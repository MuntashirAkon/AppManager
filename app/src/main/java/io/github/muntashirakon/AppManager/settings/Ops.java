// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

/**
 * Controls mode of operation and other related functions
 */
public class Ops {
    @StringDef({MODE_AUTO, MODE_ROOT, MODE_ADB_OVER_TCP, MODE_ADB_WIFI, MODE_NO_ROOT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final String MODE_AUTO = "auto";
    public static final String MODE_ROOT = "root";
    public static final String MODE_ADB_OVER_TCP = "adb_tcp";
    public static final String MODE_ADB_WIFI = "adb_wifi";
    public static final String MODE_NO_ROOT = "no-root";

    @IntDef({
            STATUS_SUCCESS,
            STATUS_FAILURE,
            STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING,
            STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED,
            STATUS_ADB_PAIRING_REQUIRED,
            STATUS_ADB_CONNECT_REQUIRED,
            STATUS_FAILURE_ADB_NEED_MORE_PERMS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILURE = 1;
    public static final int STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING = 2;
    public static final int STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED = 3;
    public static final int STATUS_ADB_PAIRING_REQUIRED = 4;
    public static final int STATUS_ADB_CONNECT_REQUIRED = 5;
    public static final int STATUS_FAILURE_ADB_NEED_MORE_PERMS = 6;

    private static final int ROOT_UID = 0;
    private static final int SHELL_UID = 2000;

    private static boolean sIsAdb = false;
    private static boolean sIsRoot = false;

    // Security
    private static final Object sSecurityLock = new Object();
    @GuardedBy("sSecurityLock")
    private static boolean sIsAuthenticated = false;

    private Ops() {
    }

    /**
     * Whether App Manager is running in the privileged mode.
     *
     * @return {@code true} iff user chose to run App Manager in the privileged mode.
     */
    @AnyThread
    public static boolean isPrivileged() {
        // Currently, root and ADB are the only privileged mode
        return sIsRoot || sIsAdb;
    }

    @AnyThread
    public static boolean isReallyPrivileged() {
        return isPrivileged() && LocalServer.isAMServiceAlive();
    }

    /**
     * Whether App Manager is running in root mode
     */
    @AnyThread
    public static boolean isRoot() {
        return sIsRoot;
    }

    @AnyThread
    public static boolean isReallyRoot() {
        return isRoot() && getUid() == ROOT_UID;
    }

    /**
     * Whether App Manager is running in ADB mode
     */
    @AnyThread
    public static boolean isAdb() {
        return sIsAdb;
    }

    @AnyThread
    public static boolean isReallyAdb() {
        return isAdb() && getUid() == SHELL_UID;
    }

    /**
     * Whether the current App Manager session is authenticated by the user. It does two things:
     * <ol>
     *     <li>If security is enabled, it marks that the user has got passed the security challenge.
     *     <li>It checks if a mode of operation is set before proceeding further.
     * </ol>
     */
    @GuardedBy("sSecurityLock")
    @AnyThread
    public static boolean isAuthenticated() {
        synchronized (sSecurityLock) {
            return sIsAuthenticated;
        }
    }

    @GuardedBy("sSecurityLock")
    @AnyThread
    public static void setAuthenticated(boolean authenticated) {
        synchronized (sSecurityLock) {
            sIsAuthenticated = authenticated;
        }
    }

    @NoOps
    public static String getMode() {
        String mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        // Backward compatibility for v2.6.0
        if (mode.equals("adb")) {
            mode = Ops.MODE_ADB_OVER_TCP;
        }
        if ((MODE_ADB_OVER_TCP.equals(mode) || MODE_ADB_WIFI.equals(mode)) && !PermissionUtils.hasInternet()) {
            // ADB enabled but the INTERNET permission is not granted, replace current with auto.
            return MODE_AUTO;
        }
        return mode;
    }

    @NoOps
    public static void setMode(@NonNull String newMode) {
        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, newMode);
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    public static int init(@NonNull Context context, boolean force) {
        String mode = getMode();
        if (MODE_AUTO.equals(mode)) {
            autoDetectRootOrAdb(context);
            return STATUS_SUCCESS;
        }
        if (MODE_NO_ROOT.equals(mode)) {
            sIsAdb = sIsRoot = false;
            return STATUS_SUCCESS;
        }
        if (!force && isAMServiceUpAndRunning(context, mode)) {
            // An instance of AMService is already running
            return STATUS_SUCCESS;
        }
        try {
            switch (mode) {
                case MODE_ROOT:
                    if (!hasRoot()) {
                        throw new Exception("Root is unavailable.");
                    }
                    sIsAdb = false;
                    sIsRoot = true;
                    LocalServer.launchAmService();
                    return STATUS_SUCCESS;
                case MODE_ADB_WIFI:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        return STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING;
                    } // else fallback to ADB over TCP
                case MODE_ADB_OVER_TCP:
                    sIsAdb = true;
                    sIsRoot = false;
                    ServerConfig.setAdbPort(findAdbPortNoThrow(context, 10, ServerConfig.DEFAULT_ADB_PORT));
                    LocalServer.restart();
                    return checkRootOrIncompleteUsbDebuggingInAdb();
            }
        } catch (Throwable e) {
            Log.e("ModeOfOps", e);
            // Fallback to no-root mode for this session, this does not modify the user preference
            sIsAdb = sIsRoot = false;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.failed_to_use_the_current_mode_of_operation));
        }
        return STATUS_FAILURE;
    }

    /**
     * Whether App Manager has been granted root permission.
     *
     * @return {@code true} iff root is granted.
     */
    @AnyThread
    @NoOps
    public static boolean hasRoot() {
        return RunnerUtils.isRootGiven();
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private static void autoDetectRootOrAdb(@NonNull Context context) {
        sIsRoot = hasRoot();
        if (sIsRoot) {
            // Root permission was granted, disable ADB and force root
            sIsAdb = false;
            if (LocalServer.isAMServiceAlive()) {
                if (getUid() == ROOT_UID) {
                    // Service is already running in root mode
                    return;
                }
                // Service is running in ADB mode, but we need root
                LocalServices.stopServices();
            }
            try {
                // AM service is confirmed dead
                LocalServer.launchAmService();
                if (LocalServer.isAMServiceAlive() && getUid() == ROOT_UID) {
                    // Service is running in root
                    return;
                }
            } catch (RemoteException e) {
                Log.e("ROOT", e);
            }
            // Root is granted but Binder communication cannot be initiated
            Log.e("ROOT", "Root granted but could not use root to initiate a connection. Trying ADB...");
            if (AdbUtils.startAdb(ServerConfig.DEFAULT_ADB_PORT)) {
                Log.d("ROOT", "Started ADB over TCP via root.");
            } else {
                Log.d("ROOT", "Could not start ADB over TCP via root.");
            }
            sIsRoot = false;
            // Fall-through, in case we can use other options
        }
        // Root was not working/granted, but check for AM service just in case
        if (LocalServer.isAMServiceAlive()) {
            int uid = getUid();
            if (uid == ROOT_UID) {
                sIsAdb = false;
                sIsRoot = true;
                return;
            }
            if (uid == SHELL_UID) {
                sIsAdb = true;
                sIsRoot = false;
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
                return;
            }
        }
        // Root not granted
        if (!PermissionUtils.hasInternet()) {
            // INTERNET permission is not granted, skip checking for ADB
            sIsAdb = false;
            return;
        }
        // Check for ADB
        sIsAdb = true; // First enable ADB if not already
        try {
            ServerConfig.setAdbPort(findAdbPortNoThrow(context, 7, ServerConfig.getAdbPort()));
            LocalServer.restart();
        } catch (Throwable e) {
            Log.e("ADB", e);
        }
        sIsAdb = LocalServer.isAMServiceAlive();
        if (sIsAdb) {
            // No need to return anything here because we're in auto-mode.
            // Any message produced by the method below is just a helpful message.
            checkRootOrIncompleteUsbDebuggingInAdb();
        }
    }

    @UiThread
    @RequiresApi(Build.VERSION_CODES.R)
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    public static void connectWirelessDebugging(@NonNull FragmentActivity activity,
                                                @NonNull AdbConnectionInterface callback) {
        DialogTitleBuilder builder = new DialogTitleBuilder(activity)
                .setTitle(R.string.wireless_debugging)
                .setEndIcon(R.drawable.ic_open_in_new, v -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                })
                .setEndIconContentDescription(R.string.open_developer_options_page);

        new MaterialAlertDialogBuilder(activity)
                .setCustomTitle(builder.build())
                .setMessage(R.string.choose_what_to_do)
                .setCancelable(false)
                .setPositiveButton(R.string.adb_connect, (dialog1, which1) -> callback.onStatusReceived(Ops.STATUS_ADB_CONNECT_REQUIRED))
                .setNeutralButton(R.string.adb_pair, (dialog1, which1) -> callback.onStatusReceived(Ops.STATUS_ADB_PAIRING_REQUIRED))
                .setNegativeButton(R.string.cancel, (dialog, which) -> callback.connectAdb(-1))
                .show();
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    public static int autoConnectAdb(@NonNull Context context, @Status int returnCodeOnFailure) {
        boolean lastAdb = sIsAdb;
        boolean lastRoot = sIsRoot;
        sIsAdb = true;
        sIsRoot = false;
        try {
            ServerConfig.setAdbPort(findAdbPortNoThrow(context, 5, ServerConfig.getAdbPort()));
            LocalServer.restart();
            return checkRootOrIncompleteUsbDebuggingInAdb();
        } catch (RemoteException | IOException e) {
            Log.e("ADB", e);
            // Failed, fall-through
        }
        sIsAdb = lastAdb;
        sIsRoot = lastRoot;
        return returnCodeOnFailure;
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    public static int connectAdb(@NonNull Context context, int port, @Status int returnCodeOnFailure) {
        if (port < 0) return returnCodeOnFailure;
        boolean lastAdb = sIsAdb;
        boolean lastRoot = sIsRoot;
        sIsAdb = true;
        sIsRoot = false;
        try {
            ServerConfig.setAdbPort(port);
            LocalServer.restart();
            return checkRootOrIncompleteUsbDebuggingInAdb();
        } catch (RemoteException | IOException e) {
            Log.e("ADB", e);
            // Failed, fall-through
        }
        sIsAdb = lastAdb;
        sIsRoot = lastRoot;
        return returnCodeOnFailure;
    }

    @UiThread
    @NoOps
    public static void connectAdbInput(@NonNull FragmentActivity activity,
                                       @NonNull AdbConnectionInterface callback) {
        AlertDialog alertDialog = new TextInputDialogBuilder(activity, R.string.port_number)
                .setTitle(R.string.wireless_debugging)
                .setInputText(String.valueOf(ServerConfig.getAdbPort()))
                .setHelperText(R.string.adb_connect_port_number_description)
                .setPositiveButton(R.string.ok, (dialog2, which2, inputText, isChecked) -> {
                    if (TextUtilsCompat.isEmpty(inputText)) {
                        UIUtils.displayShortToast(R.string.port_number_empty);
                        callback.connectAdb(-1);
                        return;
                    }
                    try {
                        callback.connectAdb(Integer.decode(inputText.toString().trim()));
                    } catch (NumberFormatException e) {
                        UIUtils.displayShortToast(R.string.port_number_invalid);
                        callback.connectAdb(-1);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which, inputText, isChecked) -> callback.connectAdb(-1))
                .create();
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @UiThread
    @NoOps
    public static void pairAdbInput(@NonNull FragmentActivity activity,
                                    @NonNull AdbConnectionInterface callback) {
        View view = View.inflate(activity, R.layout.dialog_adb_pairing, null);
        EditText adbPairingCodeInput = view.findViewById(R.id.adb_pairing_code);
        EditText portNumberInput = view.findViewById(R.id.port_number);
        LiveData<Integer> portNumberLiveData = callback.startObservingAdbPairingPort();
        portNumberLiveData.observe(activity, portNumber -> portNumberInput.setText(String.valueOf(portNumber)));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.wireless_debugging)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    Editable rawPairingCode = adbPairingCodeInput.getText();
                    Editable portString = portNumberInput.getText();
                    if (TextUtils.isEmpty(rawPairingCode)) {
                        UIUtils.displayShortToast(R.string.port_number_pairing_code_empty);
                        callback.pairAdb(null, -1);
                        return;
                    }
                    String pairingCode = Objects.requireNonNull(rawPairingCode).toString().trim();
                    if (TextUtils.isEmpty(portString)) {
                        UIUtils.displayShortToast(R.string.port_number_pairing_code_empty);
                        callback.pairAdb(pairingCode, -1);
                        return;
                    }
                    int port;
                    try {
                        port = Integer.decode(portString.toString().trim());
                        callback.pairAdb(pairingCode, port);
                    } catch (NumberFormatException e) {
                        UIUtils.displayShortToast(R.string.port_number_invalid);
                        callback.pairAdb(pairingCode, -1);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> callback.pairAdb(null, -1))
                .setOnDismissListener(dialog -> {
                    callback.stopObservingAdbPairingPort();
                    portNumberLiveData.removeObservers(activity);
                })
                .show();
    }

    @WorkerThread
    @NoOps
    @RequiresApi(Build.VERSION_CODES.R)
    @Status
    public static int pairAdb(@NonNull Context context, @Nullable String pairingCode, int port) {
        if (pairingCode == null || port < 0) return STATUS_FAILURE;
        try {
            AdbConnectionManager.getInstance().pair(ServerConfig.getAdbHost(context), port, pairingCode.trim());
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.paired_successfully));
            return connectAdb(context, findAdbPortNoThrow(context, 7, ServerConfig.getAdbPort()),
                    STATUS_ADB_CONNECT_REQUIRED);
        } catch (Exception e) {
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
            Log.e("ADB", e);
            // Failed, fall-through
        }
        return STATUS_FAILURE;
    }

    @UiThread
    public static void displayIncompleteUsbDebuggingMessage(@NonNull FragmentActivity activity) {
        new ScrollableDialogBuilder(activity)
                .setTitle(R.string.adb_incomplete_usb_debugging_title)
                .setMessage(R.string.adb_incomplete_usb_debugging_message)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.open, (dialog, which, isChecked) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        activity.startActivity(intent);
                    } catch (Throwable ignore) {
                    }
                })
                .show();
    }

    /**
     * @return {@code true} iff AMService is up and running
     */
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private static boolean isAMServiceUpAndRunning(@NonNull Context context, @Mode @NonNull String mode) {
        boolean lastAdb = sIsAdb;
        boolean lastRoot = sIsRoot;
        // At this point, we have already checked MODE_AUTO and MODE_NO_ROOT.
        sIsRoot = MODE_ROOT.equals(mode);
        sIsAdb = !sIsRoot; // Because the rests are ADB
        if (LocalServer.isLocalServerAlive(context)) {
            // Remote server is running, but local server may not be running
            try {
                LocalServer.getInstance();
            } catch (RemoteException | IOException e) {
                Log.e("CHECK", e);
                // fall-through, because the remote service may still be alive
            }
        }
        if (LocalServer.isAMServiceAlive()) {
            // AM service is running
            if (sIsRoot && getUid() == ROOT_UID) {
                // AM service is running as root
                return true;
            }
            if (sIsAdb) {
                // AM service is running as ADB
                return checkRootOrIncompleteUsbDebuggingInAdb() == STATUS_SUCCESS;
            }
            // All checks are failed, stop services
            LocalServices.stopServices();
        }
        // Checks are failed, revert everything
        sIsAdb = lastAdb;
        sIsRoot = lastRoot;
        return false;
    }

    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private static int checkRootOrIncompleteUsbDebuggingInAdb() {
        // ADB already granted and AM service is running
        int uid = getUid();
        if (uid == ROOT_UID) {
            // AM service is being run as root
            sIsRoot = true;
            sIsAdb = false;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.warning_working_on_root_mode));
        } else {
            if (!PermissionUtils.hasSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS)) {
                // USB debugging is incomplete, revert back to no-root
                sIsAdb = sIsRoot = false;
                return STATUS_FAILURE_ADB_NEED_MORE_PERMS;
            }
        }
        ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
        return STATUS_SUCCESS;
    }

    @NoOps
    @IntRange(from = -1)
    private static int getUid() {
        return ExUtils.requireNonNullElse(() -> LocalServices.getAmService().getUid(), -1);
    }

    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    @NoOps
    private static int findAdbPort(@NonNull Context context, long timeoutInSeconds)
            throws IOException, InterruptedException {
        return AdbUtils.getLatestAdbDaemon(context, timeoutInSeconds, TimeUnit.SECONDS).second;
    }

    @WorkerThread
    @NoOps
    private static int findAdbPortNoThrow(@NonNull Context context, long timeoutInSeconds, int defaultPort) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Find ADB port only in Android 11 (R) or later
            try {
                return findAdbPort(context, timeoutInSeconds);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return defaultPort;
    }

    @AnyThread
    public interface AdbConnectionInterface {
        void connectAdb(int port);

        @RequiresApi(Build.VERSION_CODES.R)
        void pairAdb(@Nullable String pairingCode, int port);

        void onStatusReceived(@Status int status);

        @NonNull
        LiveData<Integer> startObservingAdbPairingPort();

        void stopObservingAdbPairingPort();
    }
}
