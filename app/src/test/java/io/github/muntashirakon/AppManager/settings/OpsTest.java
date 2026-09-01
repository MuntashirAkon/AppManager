// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Process;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowAdb;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowPermissions;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowRoot;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowServices;
import io.github.muntashirakon.test.shadows.ShadowOpsDependencies.ShadowUsers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {
        ShadowOpsDependencies.ShadowRoot.class,
        ShadowOpsDependencies.ShadowAdb.class,
        ShadowOpsDependencies.ShadowPermissions.class,
        ShadowOpsDependencies.ShadowUsers.class,
        ShadowOpsDependencies.ShadowServices.class,
        ShadowOpsDependencies.ShadowServer.class,
})
@LooperMode(LooperMode.Mode.PAUSED)
public class OpsTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        ShadowOpsDependencies.reset();
        ReflectionHelpers.setStaticField(Ops.class, "sDirectRoot", false);
        ReflectionHelpers.setStaticField(Ops.class, "sIsAdb", false);
        ReflectionHelpers.setStaticField(Ops.class, "sIsSystem", false);
        ReflectionHelpers.setStaticField(Ops.class, "sIsRoot", false);
        Ops.setWorkingUid(Process.myUid());
        Ops.setMode(Ops.MODE_AUTO);
    }

    @Test
    public void invalidPersistedModeFallsBackToAuto() {
        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, "invalid-mode");

        assertEquals(Ops.MODE_AUTO, Ops.getMode());
        assertEquals(Ops.MODE_AUTO, AppPref.getString(AppPref.PrefKey.PREF_MODE_OF_OPS_STR));
        assertThrows(IllegalArgumentException.class, () -> Ops.setMode("invalid-mode"));
    }

    @Test
    public void autoCommitsNoRootAfterDetection() {
        ShadowAdb.adbdRunning = false;

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false));

        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        assertFalse(Ops.isDirectRoot());
        assertFalse(Ops.isAdb());
    }

    @Test
    public void autoCommitsRootAfterValidatedRootBinding() {
        ShadowRoot.rootGiven = true;
        ShadowServices.bindUid = Ops.ROOT_UID;

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false));

        assertEquals(Ops.MODE_ROOT, Ops.getMode());
        assertTrue(Ops.isDirectRoot());
        assertEquals(Ops.ROOT_UID, Ops.getWorkingUid());
    }

    @Test
    public void autoCommitsAdbAfterValidatedAdbBinding() {
        ShadowServices.bindUid = Ops.SHELL_UID;

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false));

        assertEquals(Ops.MODE_ADB_OVER_TCP, Ops.getMode());
        assertTrue(Ops.isAdb());
        assertEquals(Ops.SHELL_UID, Ops.getWorkingUid());
    }

    @Test
    public void failedRootBindingDisablesDirectRoot() {
        ShadowRoot.rootGiven = true;
        ShadowServices.bindFailure = true;
        Ops.setMode(Ops.MODE_ROOT);

        assertEquals(Ops.STATUS_FAILURE, Ops.init(mContext, true));

        assertFalse(Ops.isDirectRoot());
        assertFalse(ShadowServices.alive);
        assertEquals(Process.myUid(), Ops.getWorkingUid());
    }

    @Test
    public void incompleteAdbStopsPrivilegedServices() {
        ShadowPermissions.adbPermissionGranted = false;
        Ops.setMode(Ops.MODE_ADB_OVER_TCP);

        assertEquals(Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS, Ops.init(mContext, true));

        assertFalse(Ops.isAdb());
        assertFalse(Ops.isDirectRoot());
        assertFalse(ShadowServices.alive);
        assertEquals(1, ShadowServices.stopCalls);
        assertEquals(Process.myUid(), Ops.getWorkingUid());
    }

    @Test
    public void forcedRootTransitionReplacesShellBinder() {
        ShadowRoot.rootGiven = true;
        ShadowServices.alive = true;
        ShadowUsers.remoteUid = Ops.SHELL_UID;
        Ops.setWorkingUid(Ops.SHELL_UID);
        ShadowServices.bindUid = Ops.ROOT_UID;
        Ops.setMode(Ops.MODE_ROOT);

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, true));

        assertEquals(1, ShadowServices.stopCalls);
        assertEquals(1, ShadowServices.bindCalls);
        assertEquals(Ops.ROOT_UID, Ops.getWorkingUid());
        assertTrue(Ops.isDirectRoot());
    }

    @Test
    public void adbModeDoesNotRetainDirectRoot() {
        ShadowRoot.rootGiven = true;
        ShadowServices.bindUid = Ops.SHELL_UID;
        Ops.setMode(Ops.MODE_ADB_OVER_TCP);

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, true));

        assertTrue(Ops.isAdb());
        assertFalse(Ops.isDirectRoot());
        assertEquals(Ops.SHELL_UID, Ops.getWorkingUid());
    }

    @Test
    public void reusedAdbServiceDoesNotRetainDirectRoot() {
        ShadowRoot.rootGiven = true;
        ShadowServices.alive = true;
        ShadowUsers.remoteUid = Ops.SHELL_UID;
        Ops.setWorkingUid(Ops.SHELL_UID);
        Ops.setMode(Ops.MODE_ADB_OVER_TCP);

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false));

        assertTrue(Ops.isAdb());
        assertFalse(Ops.isDirectRoot());
        assertEquals(0, ShadowServices.bindCalls);
    }

    @Test
    public void autoRejectsIncompleteAdbAndCommitsNoRoot() {
        ShadowPermissions.adbPermissionGranted = false;
        Ops.setMode(Ops.MODE_AUTO);

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false));

        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        assertFalse(ShadowServices.alive);
        assertFalse(Ops.isAdb());
        assertEquals(Process.myUid(), Ops.getWorkingUid());
    }

    @Test
    public void autoRejectsExistingIncompleteAdbAndCommitsNoRoot() {
        ShadowPermissions.adbPermissionGranted = false;
        ShadowServices.alive = true;
        ShadowUsers.remoteUid = Ops.SHELL_UID;
        Ops.setWorkingUid(Ops.SHELL_UID);

        assertEquals(Ops.STATUS_SUCCESS, Ops.init(mContext, false));

        assertEquals(Ops.MODE_NO_ROOT, Ops.getMode());
        assertFalse(ShadowServices.alive);
        assertFalse(Ops.isAdb());
        assertEquals(Process.myUid(), Ops.getWorkingUid());
    }

    @Test
    public void connectRejectsPortsOutsideTcpRange() {
        ServerConfig.setAdbPort(5555);

        assertEquals(Ops.STATUS_FAILURE, Ops.connectAdb(mContext, 0, Ops.STATUS_FAILURE));
        assertEquals(Ops.STATUS_FAILURE, Ops.connectAdb(mContext, 65536, Ops.STATUS_FAILURE));

        assertEquals(5555, ServerConfig.getAdbPort());
        assertEquals(0, ShadowServices.bindCalls);
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.setAdbPort(0));
        assertThrows(IllegalArgumentException.class, () -> ServerConfig.setAdbPort(65536));
    }
}
