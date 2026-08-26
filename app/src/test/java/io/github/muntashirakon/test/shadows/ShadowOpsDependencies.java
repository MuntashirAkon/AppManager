// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import android.Manifest;
import android.content.Context;
import android.os.Process;
import android.os.RemoteException;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.IOException;

import io.github.muntashirakon.AppManager.adb.AdbUtils;
import io.github.muntashirakon.AppManager.ipc.LocalServices;
import io.github.muntashirakon.AppManager.runner.RunnerUtils;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.servermanager.LocalServer;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.adb.AdbPairingRequiredException;

/** Controllable Robolectric replacements for the static backends used by {@link Ops}. */
public final class ShadowOpsDependencies {
    private ShadowOpsDependencies() {
    }

    public static void reset() {
        ShadowRoot.rootGiven = false;
        ShadowAdb.adbdRunning = true;
        ShadowPermissions.internetGranted = true;
        ShadowPermissions.adbPermissionGranted = true;
        ShadowServices.alive = false;
        ShadowServices.bindUid = Ops.SHELL_UID;
        ShadowServices.bindFailure = false;
        ShadowServices.bindCalls = 0;
        ShadowServices.stopCalls = 0;
        ShadowUsers.remoteUid = Process.myUid();
        ShadowServer.alive = false;
        ShadowServer.restartFailure = false;
        ShadowServer.restartCalls = 0;
    }

    @Implements(RunnerUtils.class)
    public static class ShadowRoot {
        public static boolean rootGiven;

        @Implementation
        public static boolean isRootGiven() {
            return rootGiven;
        }
    }

    @Implements(AdbUtils.class)
    public static class ShadowAdb {
        public static boolean adbdRunning;

        @Implementation
        public static boolean isAdbdRunning() {
            return adbdRunning;
        }

        @Implementation
        public static int getAdbPortOrDefault() {
            return 5555;
        }

        @Implementation
        public static boolean startAdb(int port) {
            return false;
        }
    }

    @Implements(SelfPermissions.class)
    public static class ShadowPermissions {
        public static boolean internetGranted;
        public static boolean adbPermissionGranted;

        @Implementation
        public static boolean checkSelfPermission(String permissionName) {
            return !Manifest.permission.INTERNET.equals(permissionName) || internetGranted;
        }

        @Implementation
        public static boolean checkSelfOrRemotePermission(String permissionName) {
            return adbPermissionGranted;
        }

        @Implementation
        public static void init() {
        }
    }

    @Implements(Users.class)
    public static class ShadowUsers {
        public static int remoteUid;

        @Implementation
        public static int getSelfOrRemoteUid() {
            return remoteUid;
        }
    }

    @Implements(LocalServices.class)
    public static class ShadowServices {
        public static boolean alive;
        public static int bindUid;
        public static boolean bindFailure;
        public static int bindCalls;
        public static int stopCalls;

        @Implementation
        public static boolean alive() {
            return alive;
        }

        @Implementation
        public static void bindServicesIfNotAlready() throws RemoteException {
            if (!alive) {
                bindServices();
            }
        }

        @Implementation
        public static void bindServices() throws RemoteException {
            ++bindCalls;
            if (bindFailure) {
                throw new RemoteException("Simulated bind failure");
            }
            alive = true;
            ShadowUsers.remoteUid = bindUid;
            Ops.setWorkingUid(bindUid);
        }

        @Implementation
        public static void stopServices() {
            ++stopCalls;
            alive = false;
            ShadowUsers.remoteUid = Process.myUid();
            Ops.setWorkingUid(Process.myUid());
        }
    }

    @Implements(LocalServer.class)
    public static class ShadowServer {
        public static boolean alive;
        public static boolean restartFailure;
        public static int restartCalls;

        @Implementation
        public static boolean alive(Context context) {
            return alive;
        }

        @Implementation
        public static void restart() throws IOException, AdbPairingRequiredException {
            ++restartCalls;
            if (restartFailure) {
                throw new IOException("Simulated restart failure");
            }
        }
    }
}
