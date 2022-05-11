// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbConnectionManager;
import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.NoOps;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.UiThreadHandler;

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

    @IntDef({STATUS_SUCCESS, STATUS_FAILED, STATUS_DISPLAY_WIRELESS_DEBUGGING, STATUS_DISPLAY_PAIRING, STATUS_DISPLAY_CONNECT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
    }

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_FAILED = 1;
    public static final int STATUS_DISPLAY_WIRELESS_DEBUGGING = 2;
    public static final int STATUS_DISPLAY_PAIRING = 3;
    public static final int STATUS_DISPLAY_CONNECT = 4;

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

    /**
     * Whether App Manager is running in root mode
     */
    @AnyThread
    public static boolean isRoot() {
        return sIsRoot;
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
    @AnyThread
    public static void setAuthenticated(boolean authenticated) {
        synchronized (sSecurityLock) {
            sIsAuthenticated = authenticated;
        }
    }

    @Deprecated
    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    public static synchronized void init(@NonNull FragmentActivity activity, boolean force) {
        Context context = activity.getApplicationContext();
        String mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
        if (MODE_AUTO.equals(mode)) {
            autoDetectRootOrAdb(context);
            return;
        }
        if (MODE_NO_ROOT.equals(mode)) {
            sIsAdb = sIsRoot = false;
            return;
        }
        if (!force && isAMServiceUpAndRunning(context, mode)) {
            // An instance of AMService is already running
            return;
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
                    return;
                case MODE_ADB_WIFI:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        connectWirelessDebugging(activity);
                        return;
                    } // else fallback to ADB over TCP
                case "adb":
                    if (mode.equals("adb")) {
                        // Backward compatibility for v2.6.0 or earlier
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, MODE_ADB_OVER_TCP);
                    }
                    // fallback to ADB over TCP
                case MODE_ADB_OVER_TCP:
                    sIsAdb = true;
                    sIsRoot = false;
                    ServerConfig.setAdbPort(findAdbPortNoThrow(context, 10, ServerConfig.getAdbPort()));
                    LocalServer.restart();
            }
        } catch (Throwable e) {
            Log.e("ModeOfOps", e);
            // Fallback to no-root mode for this session, this does not modify the user preference
            sIsAdb = sIsRoot = false;
            UiThreadHandler.run(() -> UIUtils.displayLongToast(R.string.failed_to_use_the_current_mode_of_operation));
        }
    }

    @WorkerThread
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    @Status
    public static int init(@NonNull Context context, boolean force) {
        String mode = AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR);
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
                        return STATUS_DISPLAY_WIRELESS_DEBUGGING;
                    } // else fallback to ADB over TCP
                case "adb":
                    if (mode.equals("adb")) {
                        // Backward compatibility for v2.6.0 or earlier
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, MODE_ADB_OVER_TCP);
                    }
                    // fallback to ADB over TCP
                case MODE_ADB_OVER_TCP:
                    sIsAdb = true;
                    sIsRoot = false;
                    ServerConfig.setAdbPort(findAdbPortNoThrow(context, 10, ServerConfig.DEFAULT_ADB_PORT));
                    LocalServer.restart();
                    return STATUS_SUCCESS;
            }
        } catch (Throwable e) {
            Log.e("ModeOfOps", e);
            // Fallback to no-root mode for this session, this does not modify the user preference
            sIsAdb = sIsRoot = false;
            UiThreadHandler.run(() -> UIUtils.displayLongToast(R.string.failed_to_use_the_current_mode_of_operation));
        }
        return STATUS_FAILED;
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
        //noinspection AssignmentUsedAsCondition
        if (sIsRoot = hasRoot()) {
            // Root permission was granted, disable ADB
            sIsAdb = false;
            try {
                LocalServer.launchAmService();
            } catch (RemoteException e) {
                Log.e("ROOT", e);
            }
            sIsRoot = LocalServer.isAMServiceAlive();
            return;
        }
        // Root not granted, check ADB
        sIsAdb = true; // First enable ADB
        try {
            ServerConfig.setAdbPort(findAdbPortNoThrow(context, 7, ServerConfig.getAdbPort()));
            LocalServer.restart();
        } catch (RemoteException | IOException e) {
            Log.e("ADB", e);
        }
        //noinspection AssignmentUsedAsCondition
        if (sIsAdb = LocalServer.isAMServiceAlive()) {
            UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
        }
    }

    @Deprecated
    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    private static void connectWirelessDebugging(@NonNull FragmentActivity activity)
            throws InterruptedException, IOException, RemoteException {
        sIsAdb = true;
        sIsRoot = false;
        try {
            ServerConfig.setAdbPort(findAdbPortNoThrow(activity, 5, ServerConfig.getAdbPort()));
            LocalServer.restart();
            return; // Success
        } catch (RemoteException | IOException e) {
            Log.e("ADB", e);
            // Failed, fall-through
        }
        CountDownLatch waitForPort = new CountDownLatch(1);
        AtomicInteger adbPort = new AtomicInteger(-1);
        AdbUtils.AdbConnectionCallback callback = new AdbUtils.AdbConnectionCallback() {
            @UiThread
            @Override
            public void connect(int port) {
                adbPort.set(port);
                waitForPort.countDown();
            }

            @UiThread
            @Override
            public void pair(@Nullable String pairingCode, int port) {
                if (pairingCode == null || port == -1) {
                    waitForPort.countDown();
                    return;
                }
                new Thread(() -> {
                    try {
                        AdbConnectionManager.getInstance().pair(ServerConfig.getAdbHost(activity), port, pairingCode.trim());
                        try {
                            adbPort.set(findAdbPort(activity, 7));
                            waitForPort.countDown();
                        } catch (Throwable ignore) {
                        }
                        UiThreadHandler.run(() -> {
                            UIUtils.displayShortToast(R.string.paired_successfully);
                            if (adbPort.get() == -1 && !activity.isDestroyed()) {
                                AdbUtils.displayAdbConnect(activity, this);
                            } else waitForPort.countDown();
                        });
                    } catch (Exception e) {
                        UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.failed));
                        waitForPort.countDown();
                        Log.e("ADB", e);
                    }
                }).start();
            }
        };
        UiThreadHandler.run(() -> AdbUtils.configureWirelessDebugging(activity, callback));
        Log.e("ADB", "Before");
        waitForPort.await(2, TimeUnit.MINUTES);
        Log.e("ADB", "After");
        if (adbPort.get() == -1) {
            // One last try
            adbPort.set(findAdbPort(activity, 5));
        }
        if (adbPort.get() != -1) {
            ServerConfig.setAdbPort(adbPort.get());
        }
        LocalServer.restart();
    }

    @UiThread
    @RequiresApi(Build.VERSION_CODES.R)
    @NoOps // Although we've used Ops checks, its overall usage does not affect anything
    public static void connectWirelessDebugging(@NonNull FragmentActivity activity,
                                                @NonNull SecurityAndOpsViewModel viewModel) {
        AdbUtils.AdbConnectionCallback callback = new AdbUtils.AdbConnectionCallback() {
            @UiThread
            @Override
            public void connect(int port) {
                viewModel.connectAdb(port);
            }

            @UiThread
            @Override
            public void pair(@Nullable String pairingCode, int port) {
                viewModel.pairAdb(pairingCode, port);
            }
        };
        AdbUtils.configureWirelessDebugging(activity, callback);
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
            return STATUS_SUCCESS;
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
    public static int connectAdb(int port, @Status int returnCodeOnFailure) {
        if (port < 0) return returnCodeOnFailure;
        boolean lastAdb = sIsAdb;
        boolean lastRoot = sIsRoot;
        sIsAdb = true;
        sIsRoot = false;
        try {
            ServerConfig.setAdbPort(port);
            LocalServer.restart();
            return STATUS_SUCCESS;
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
    public static void connectAdbInput(@NonNull FragmentActivity activity, @NonNull SecurityAndOpsViewModel viewModel) {
        AdbUtils.AdbConnectionCallback callback = new AdbUtils.AdbConnectionCallback() {
            @UiThread
            @Override
            public void connect(int port) {
                viewModel.connectAdb(port);
            }

            @UiThread
            @Override
            public void pair(@Nullable String pairingCode, int port) {
            }
        };
        AdbUtils.displayAdbConnect(activity, callback);
    }

    @WorkerThread
    @NoOps
    @RequiresApi(Build.VERSION_CODES.R)
    @Status
    public static int pairAdb(@NonNull Context context, @Nullable String pairingCode, int port) {
        if (pairingCode == null || port < 0) return STATUS_FAILED;
        try {
            AdbConnectionManager.getInstance().pair(ServerConfig.getAdbHost(context), port, pairingCode.trim());
            UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.paired_successfully));
            return connectAdb(findAdbPortNoThrow(context, 7, ServerConfig.getAdbPort()), STATUS_DISPLAY_CONNECT);
        } catch (Exception e) {
            UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.failed));
            Log.e("ADB", e);
            // Failed, fall-through
        }
        return STATUS_FAILED;
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
            // Remote server is running
            try {
                LocalServer.getInstance();
            } catch (RemoteException | IOException e) {
                Log.e("CHECK", e);
                // fall-through, because the remote service may still be alive
            }
        }
        if (LocalServer.isAMServiceAlive()) {
            // AM service is running
            try {
                if (LocalServices.getAmService().getUid() == 0) {
                    // AM service is being run as root
                    if (sIsAdb) {
                        UiThreadHandler.run(() -> UIUtils.displayLongToast(R.string.warning_working_on_root_mode));
                    }
                    sIsRoot = true;
                    sIsAdb = false;
                } else {
                    if (sIsRoot) {
                        // AM is supposed to be run as root, not ADB. Abort service.
                        LocalServices.stopServices();
                        // Throw error to revert changes
                        throw new RemoteException("App Manager was running as ADB, root was requested.");
                    } else {
                        // sIsRoot = false;
                        sIsAdb = true;
                        UiThreadHandler.run(() -> UIUtils.displayShortToast(R.string.working_on_adb_mode));
                    }
                }
                return true;
            } catch (RemoteException e) {
                Log.e("CHECK", e);
                // Fall-through
            }
        }
        // Checks are failed, revert everything
        sIsAdb = lastAdb;
        sIsRoot = lastRoot;
        return false;
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
}
