// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
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
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.session.SessionMonitoringService;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.ScrollableDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

/**
 * Controls mode of operation and other related functions
 */
public class Ops {
    public static final String TAG = Ops.class.getSimpleName();

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

    public static int ROOT_UID = 0;
    public static int SHELL_UID = 2000;
    public static int PHONE_UID = Process.PHONE_UID;
    public static int SYSTEM_UID = Process.SYSTEM_UID;

    private static boolean sIsAdb = false; // UID = 2000
    private static boolean sIsSystem = false; // UID = 1000
    private static boolean sIsRoot = false; // UID = 0

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
        return sIsRoot || sIsAdb || sIsSystem;
    }

    /**
     * Whether App Manager is running in root mode
     */
    @AnyThread
    public static boolean isRoot() {
        return sIsRoot;
    }

    /**
     * Whether App Manager is running in system mode
     */
    @AnyThread
    public static boolean isSystem() {
        return sIsSystem;
    }

    /**
     * Whether App Manager is running in ADB mode
     */
    @AnyThread
    public static boolean isAdb() {
        return sIsAdb;
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
    @MainThread
    public static void setAuthenticated(@NonNull Context context, boolean authenticated) {
        synchronized (sSecurityLock) {
            sIsAuthenticated = authenticated;
            if (Prefs.Privacy.isPersistentSessionAllowed()) {
                Intent service = new Intent(context, SessionMonitoringService.class);
                if (authenticated) {
                    ContextCompat.startForegroundService(context, service);
                } else {
                    context.stopService(service);
                }
            }
        }
    }

    @NonNull
    public static CharSequence getInferredMode(@NonNull Context context) {
        int uid = Users.getSelfOrRemoteUid();
        if (uid == ROOT_UID) {
            return context.getString(R.string.root);
        }
        if (uid == SYSTEM_UID) {
            return context.getString(R.string.system);
        }
        if (uid == SHELL_UID) {
            return "ADB";
        }
        return context.getString(R.string.no_root);
    }

    @NoOps
    public static String getMode() {
        String mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        // Backward compatibility for v2.6.0
        if (mode.equals("adb")) {
            mode = Ops.MODE_ADB_OVER_TCP;
        }
        if ((MODE_ADB_OVER_TCP.equals(mode) || MODE_ADB_WIFI.equals(mode))
                && !SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET)) {
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
            autoDetectRootSystemOrAdbAndPersist(context);
            return sIsAdb ? STATUS_SUCCESS : initPermissionsWithSuccess();
        }
        if (MODE_NO_ROOT.equals(mode)) {
            sIsAdb = sIsSystem = sIsRoot = false;
            // Also, stop existing services if any
            LocalServices.stopServices();
            return STATUS_SUCCESS;
        }
        if (!force && isAMServiceUpAndRunning(context, mode)) {
            // An instance of AMService is already running
            return sIsAdb ? STATUS_SUCCESS : initPermissionsWithSuccess();
        }
        try {
            switch (mode) {
                case MODE_ROOT:
                    if (!hasRoot()) {
                        throw new Exception("Root is unavailable.");
                    }
                    sIsSystem = sIsAdb = false;
                    sIsRoot = true;
                    LocalServices.bindServicesIfNotAlready();
                    return initPermissionsWithSuccess();
                case MODE_ADB_WIFI:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (!Utils.isWifiActive(context.getApplicationContext())) {
                            throw new Exception("Wifi not enabled.");
                        }
                        if (AdbUtils.enableWirelessDebugging(context)) {
                            return STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING;
                        }
                        Log.w(TAG, "Could not ensure wireless debugging, falling back...");
                    } // else fallback to ADB over TCP
                case MODE_ADB_OVER_TCP:
                    sIsRoot = sIsSystem = false;
                    sIsAdb = true;
                    ServerConfig.setAdbPort(findAdbPort(context, 10, AdbUtils.getAdbPortOrDefault()));
                    LocalServer.restart();
                    LocalServices.bindServicesIfNotAlready();
                    return checkRootOrIncompleteUsbDebuggingInAdb();
            }
        } catch (Throwable e) {
            Log.e(TAG, e);
            // Fallback to no-root mode for this session, this does not modify the user preference
            sIsAdb = sIsSystem = sIsRoot = false;
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
    private static void autoDetectRootSystemOrAdbAndPersist(@NonNull Context context) {
        sIsRoot = hasRoot();
        if (sIsRoot) {
            // Root permission was granted
            setMode(MODE_ROOT);
            // Disable ADB and force root
            sIsSystem = sIsAdb = false;
            if (LocalServices.alive()) {
                if (Users.getSelfOrRemoteUid() == ROOT_UID) {
                    // Service is already running in root mode
                    return;
                }
                // Service is running in ADB/other mode, but we need root
                LocalServices.stopServices();
            }
            try {
                // Service is confirmed dead
                LocalServices.bindServices();
                if (LocalServices.alive() && Users.getSelfOrRemoteUid() == ROOT_UID) {
                    // Service is running in root
                    return;
                }
            } catch (RemoteException e) {
                Log.e(TAG, e);
            }
            // Root is granted but Binder communication cannot be initiated
            Log.e(TAG, "Root granted but could not use root to initiate a connection. Trying ADB...");
            if (AdbUtils.startAdb(AdbUtils.getAdbPortOrDefault())) {
                Log.i(TAG, "Started ADB over TCP via root.");
            } else {
                Log.w(TAG, "Could not start ADB over TCP via root.");
            }
            sIsRoot = false;
            // Fall-through, in case we can use other options
        }
        // Root was not working/granted, but check for AM service just in case
        if (LocalServices.alive()) {
            setMode(MODE_ADB_OVER_TCP);
            int uid = Users.getSelfOrRemoteUid();
            if (uid == ROOT_UID) {
                sIsSystem = sIsAdb = false;
                sIsRoot = true;
                return;
            }
            if (uid == SYSTEM_UID) {
                sIsRoot = sIsAdb = false;
                sIsSystem = true;
                return;
            }
            if (uid == SHELL_UID) {
                sIsRoot = sIsSystem = false;
                sIsAdb = true;
                ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
                return;
            }
        }
        // Root not granted
        if (!SelfPermissions.checkSelfPermission(Manifest.permission.INTERNET)) {
            // INTERNET permission is not granted
            setMode(MODE_NO_ROOT);
            // Skip checking for ADB
            sIsAdb = false;
            return;
        }
        // Check for ADB
        if (!AdbUtils.isAdbdRunning()) {
            // ADB not running. In auto mode, we do not attempt to enable it either
            setMode(MODE_NO_ROOT);
            sIsAdb = sIsSystem = sIsRoot = false;
            return;
        }
        sIsAdb = true; // First enable ADB if not already
        try {
            ServerConfig.setAdbPort(findAdbPort(context, 7, ServerConfig.getAdbPort()));
            LocalServer.restart();
            LocalServices.bindServicesIfNotAlready();
        } catch (Throwable e) {
            Log.e(TAG, e);
        }
        sIsAdb = LocalServices.alive();
        if (sIsAdb) {
            // No need to return anything here because we're in auto-mode.
            // Any message produced by the method below is just a helpful message.
            checkRootOrIncompleteUsbDebuggingInAdb();
        }
        setMode(isPrivileged() ? MODE_ADB_OVER_TCP : MODE_NO_ROOT);
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
        boolean lastSystem = sIsSystem;
        boolean lastRoot = sIsRoot;
        sIsAdb = true;
        sIsSystem = sIsRoot = false;
        try {
            ServerConfig.setAdbPort(findAdbPort(context, 5, ServerConfig.getAdbPort()));
            LocalServer.restart();
            LocalServices.bindServicesIfNotAlready();
            return checkRootOrIncompleteUsbDebuggingInAdb();
        } catch (RemoteException | IOException e) {
            Log.e(TAG, "Could not auto-connect to adbd", e);
            // Go back to the last mode
            sIsAdb = lastAdb;
            sIsSystem = lastSystem;
            sIsRoot = lastRoot;
            return returnCodeOnFailure;
        }
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    public static int connectAdb(@NonNull Context context, int port, @Status int returnCodeOnFailure) {
        if (port < 0) return returnCodeOnFailure;
        boolean lastAdb = sIsAdb;
        boolean lastSystem = sIsSystem;
        boolean lastRoot = sIsRoot;
        sIsAdb = true;
        sIsSystem = sIsRoot = false;
        try {
            ServerConfig.setAdbPort(port);
            LocalServer.restart();
            LocalServices.bindServicesIfNotAlready();
            return checkRootOrIncompleteUsbDebuggingInAdb();
        } catch (RemoteException | IOException e) {
            Log.e(TAG, "Could not connect to adbd using port " + port, e);
            // Go back to the last mode
            sIsAdb = lastAdb;
            sIsSystem = lastSystem;
            sIsRoot = lastRoot;
            return returnCodeOnFailure;
        }
    }

    @UiThread
    @NoOps
    public static void connectAdbInput(@NonNull FragmentActivity activity,
                                       @NonNull AdbConnectionInterface callback) {
        new TextInputDialogBuilder(activity, R.string.port_number)
                .setTitle(R.string.wireless_debugging)
                .setInputText(String.valueOf(ServerConfig.getAdbPort()))
                .setHelperText(R.string.adb_connect_port_number_description)
                .setPositiveButton(R.string.ok, (dialog2, which2, inputText, isChecked) -> {
                    if (TextUtils.isEmpty(inputText)) {
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
                .setCancelable(false)
                .show();
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
            return connectAdb(context, findAdbPort(context, 7, ServerConfig.getAdbPort()),
                    STATUS_ADB_CONNECT_REQUIRED);
        } catch (Exception e) {
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
            Log.e(TAG, e);
            // Failed, fall-through
        }
        return STATUS_FAILURE;
    }

    @UiThread
    public static void displayIncompleteUsbDebuggingMessage(@NonNull FragmentActivity activity) {
        new ScrollableDialogBuilder(activity)
                .setTitle(R.string.adb_incomplete_usb_debugging_title)
                .setMessage(R.string.adb_incomplete_usb_debugging_message)
                .enableAnchors()
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

    private static int initPermissionsWithSuccess() {
        SelfPermissions.init();
        return STATUS_SUCCESS;
    }

    /**
     * @return {@code true} iff AMService is up and running
     */
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private static boolean isAMServiceUpAndRunning(@NonNull Context context, @Mode @NonNull String mode) {
        boolean lastAdb = sIsAdb;
        boolean lastSystem = sIsSystem;
        boolean lastRoot = sIsRoot;
        // At this point, we have already checked MODE_AUTO and MODE_NO_ROOT.
        sIsRoot = MODE_ROOT.equals(mode);
        sIsAdb = !sIsRoot; // Because the rests are ADB
        sIsSystem = false;
        if (LocalServer.alive(context)) {
            // Remote server is running, but local server may not be running
            try {
                LocalServer.getInstance();
                LocalServices.bindServicesIfNotAlready();
            } catch (RemoteException | IOException e) {
                Log.e(TAG, e);
                // fall-through, because the remote service may still be alive
            }
        }
        if (LocalServices.alive()) {
            // AM service is running
            int uid = Users.getSelfOrRemoteUid();
            if (sIsRoot && uid == ROOT_UID) {
                // AM service is running as root
                return true;
            }
            if (uid == SYSTEM_UID) {
                // AM service is running as system
                sIsSystem = true;
                sIsRoot = sIsAdb = false;
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
        sIsSystem = lastSystem;
        sIsRoot = lastRoot;
        return false;
    }

    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private static int checkRootOrIncompleteUsbDebuggingInAdb() {
        // ADB already granted and AM service is running
        int uid = Users.getSelfOrRemoteUid();
        if (uid == ROOT_UID) {
            // AM service is being run as root
            sIsRoot = true;
            sIsSystem = sIsAdb = false;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.warning_working_on_root_mode));
        } else if (uid == SYSTEM_UID) {
            // AM service is being run as system
            sIsSystem = true;
            sIsRoot = sIsAdb = false;
            ThreadUtils.postOnMainThread(() -> UIUtils.displayLongToast(R.string.warning_working_on_system_mode));
        } else if (uid == SHELL_UID) { // ADB mode
            if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.GRANT_RUNTIME_PERMISSIONS)) {
                // USB debugging is incomplete, revert back to no-root
                sIsAdb = sIsSystem = sIsRoot = false;
                return STATUS_FAILURE_ADB_NEED_MORE_PERMS;
            }
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
        } else {
            // No-root mode
            sIsAdb = sIsSystem = sIsRoot = false;
            return STATUS_FAILURE;
        }
        return initPermissionsWithSuccess();
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
    private static int findAdbPort(@NonNull Context context, long timeoutInSeconds, int defaultPort)
            throws IOException {
        if (!AdbUtils.isAdbdRunning()) {
            throw new IOException("ADB daemon not running.");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Find ADB port only in Android 11 (R) or later
            try {
                return findAdbPort(context, timeoutInSeconds);
            } catch (IOException | InterruptedException e) {
                Log.w(TAG, "Could not find ADB port", e);
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
