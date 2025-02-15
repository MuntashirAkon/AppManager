// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.adb.AdbPairingService;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logcat.helper.ServiceHelper;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.session.SessionMonitoringService;
import io.github.muntashirakon.AppManager.users.Owners;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
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

    private static volatile int sWorkingUid = Process.myUid();
    private static volatile boolean sDirectRoot = false; // AM has root AND that root is being used
    private static boolean sIsAdb = false; // UID = 2000
    private static boolean sIsSystem = false; // UID = 1000
    private static boolean sIsRoot = false; // UID = 0

    // Security
    private static final Object sSecurityLock = new Object();
    @GuardedBy("sSecurityLock")
    private static boolean sIsAuthenticated = false;

    private Ops() {
    }

    @AnyThread
    public static int getWorkingUid() {
        return sWorkingUid;
    }

    @AnyThread
    public static void setWorkingUid(int newUid) {
        sWorkingUid = newUid;
    }

    @AnyThread
    public static int getWorkingUidOrRoot() {
        int uid = getWorkingUid();
        if (uid != ROOT_UID && sDirectRoot) {
            return ROOT_UID;
        }
        return uid;
    }

    @AnyThread
    public static boolean isWorkingUidRoot() {
        return getWorkingUid() == ROOT_UID;
    }

    /**
     * Whether App Manager is currently using direct root (e.g. root granted to the app) to perform operations. The
     * result returned by this method may not reflect the actual state due to other factors.
     */
    @AnyThread
    public static boolean isDirectRoot() {
        return sDirectRoot;
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
        if (uid == SHELL_UID) {
            return "ADB";
        }
        if (uid != Process.myUid()) {
            String uidStr = Owners.getUidOwnerMap(false).get(uid);
            if (!TextUtils.isEmpty(uidStr)) {
                return uidStr.substring(0, 1).toUpperCase(Locale.ROOT)
                        + (uidStr.length() > 1 ? uidStr.substring(1) : "");
            }
        }
        return context.getString(R.string.no_root);
    }

    @NoOps
    public static String getMode() {
        String mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        // Backward compatibility for v2.6.0
        if (mode.equals("adb")) {
            mode = MODE_ADB_OVER_TCP;
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
        sDirectRoot = hasRoot();
        if (MODE_AUTO.equals(mode)) {
            autoDetectRootSystemOrAdbAndPersist(context);
            return sIsAdb ? STATUS_SUCCESS : initPermissionsWithSuccess();
        }
        if (MODE_NO_ROOT.equals(mode)) {
            sDirectRoot = false;
            sIsAdb = sIsSystem = sIsRoot = false;
            // Also, stop existing services if any
            if (LocalServices.alive()) {
                LocalServices.stopServices();
            }
            if (LocalServer.alive(context)) {
                // We don't care about its results
                ThreadUtils.postOnBackgroundThread(() -> ExUtils.exceptionAsIgnored(() ->
                        LocalServer.getInstance().closeBgServer()));
            }
            return STATUS_SUCCESS;
        }
        if (!force && isAMServiceUpAndRunning(context, mode)) {
            // An instance of AMService is already running
            return sIsAdb ? STATUS_SUCCESS : initPermissionsWithSuccess();
        }
        try {
            switch (mode) {
                case MODE_ROOT:
                    if (!sDirectRoot) {
                        throw new Exception("Root is unavailable.");
                    }
                    // Disable server first
                    ExUtils.exceptionAsIgnored(() -> {
                        if (LocalServer.alive(context)) {
                            LocalServer.getInstance().closeBgServer();
                        }
                    });
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
                            // Wireless debugging enabled, try auto-connect
                            return STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING;
                        } else {
                            // Wireless debugging is turned off or there's no permission
                            return STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED;
                        }
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
        sIsRoot = sDirectRoot;
        if (sDirectRoot) {
            // Root permission was granted
            setMode(MODE_ROOT);
            // Disable remote server
            ExUtils.exceptionAsIgnored(() -> {
                if (LocalServer.alive(context)) {
                    LocalServer.getInstance().closeBgServer();
                }
            });
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
        setMode(getWorkingUid() != Process.myUid() ? MODE_ADB_OVER_TCP : MODE_NO_ROOT);
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
                .setPositiveButton(R.string.adb_connect, (dialog1, which1) -> callback.onStatusReceived(STATUS_ADB_CONNECT_REQUIRED))
                .setNeutralButton(R.string.adb_pair, (dialog1, which1) -> callback.onStatusReceived(STATUS_ADB_PAIRING_REQUIRED))
                .setNegativeButton(R.string.cancel, (dialog, which) -> callback.connectAdb(-1))
                .show();
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    public static int autoConnectWirelessDebugging(@NonNull Context context) {
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
        } catch (RemoteException | IOException | AdbPairingRequiredException e) {
            Log.e(TAG, "Could not auto-connect to adbd", e);
            // Go back to the last mode
            sIsAdb = lastAdb;
            sIsSystem = lastSystem;
            sIsRoot = lastRoot;
            if (e instanceof AdbPairingRequiredException) {
                // Only pairing is required
                return STATUS_ADB_PAIRING_REQUIRED;
            } else return STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED;
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
        } catch (RemoteException | IOException | AdbPairingRequiredException e) {
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
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.wireless_debugging)
                .setMessage(R.string.adb_pairing_instruction)
                .setCancelable(false)
                .setNeutralButton(R.string.action_manual, (dialog, which) -> {
                    Intent adbPairingServiceIntent = new Intent(activity, AdbPairingService.class)
                            .setAction(AdbPairingService.ACTION_START_PAIRING);
                    ContextCompat.startForegroundService(activity, adbPairingServiceIntent);
                    callback.pairAdb();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> callback.onStatusReceived(STATUS_FAILURE))
                .setPositiveButton(R.string.go, (dialog, which) -> {
                    Intent developerOptionsIntent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Intent adbPairingServiceIntent = new Intent(activity, AdbPairingService.class)
                            .setAction(AdbPairingService.ACTION_START_PAIRING);
                    activity.startActivity(developerOptionsIntent);
                    ContextCompat.startForegroundService(activity, adbPairingServiceIntent);
                    callback.pairAdb();
                })
                .show();
    }

    @WorkerThread
    @NoOps
    @RequiresApi(Build.VERSION_CODES.R)
    @Status
    public static int pairAdb(@NonNull Context context) {
        try {
            AdbConnectionManager conn = AdbConnectionManager.getInstance();
            int status = pairAdbInternal(context, conn);
            if (status == STATUS_ADB_CONNECT_REQUIRED) {
                return connectAdb(context, findAdbPort(context, 7, ServerConfig.getAdbPort()),
                        STATUS_ADB_CONNECT_REQUIRED);
            }
        } catch (Exception e) {
            ThreadUtils.postOnMainThread(() -> UIUtils.displayShortToast(R.string.failed));
            Log.e(TAG, e);
            // Failed, fall-through
        }
        return STATUS_FAILURE;
    }


    @WorkerThread
    @NoOps
    @RequiresApi(Build.VERSION_CODES.R)
    @Status
    private static int pairAdbInternal(@NonNull Context context, @NonNull AdbConnectionManager conn) {
        AtomicReference<CountDownLatch> observerObserver = new AtomicReference<>(new CountDownLatch(1));
        AtomicReference<Exception> pairingError = new AtomicReference<>();
        Observer<Exception> observer = e -> {
            pairingError.set(e);
            observerObserver.get().countDown();
        };
        ThreadUtils.postOnMainThread(() -> conn.getPairingObserver().observeForever(observer));
        while (true) {
            boolean success;
            try {
                success = observerObserver.get().await(1, TimeUnit.HOURS);
            } catch (InterruptedException ignore) {
                success = false;
            }
            if (success) {
                if (pairingError.get() != null) {
                    if (ServiceHelper.checkIfServiceIsRunning(context, AdbPairingService.class)) {
                        observerObserver.set(new CountDownLatch(1));
                        continue;
                    }
                    success = false;
                }
            }
            ThreadUtils.postOnMainThread(() -> conn.getPairingObserver().removeObserver(observer));
            if (success) {
                return STATUS_ADB_CONNECT_REQUIRED;
            } else {
                context.stopService(new Intent(context, AdbPairingService.class));
                return STATUS_FAILURE;
            }
        }
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
        // At this point, we have already checked MODE_AUTO, and MODE_NO_ROOT has lower priority.
        sIsRoot = MODE_ROOT.equals(mode);
        sIsAdb = !sIsRoot; // Because the rests are ADB
        sIsSystem = false;
        if (LocalServer.alive(context)) {
            // Remote server is running, but local server may not be running
            try {
                LocalServer.getInstance();
                LocalServices.bindServicesIfNotAlready();
            } catch (RemoteException | IOException | AdbPairingRequiredException e) {
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
        // TODO: 8/4/24 Remove the first two methods since the third method can be used instead of them
        void connectAdb(int port);

        @RequiresApi(Build.VERSION_CODES.R)
        void pairAdb();

        void onStatusReceived(@Status int status);
    }
}
