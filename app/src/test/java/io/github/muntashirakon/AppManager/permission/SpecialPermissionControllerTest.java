// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.os.Build;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class SpecialPermissionControllerTest {
    @Test
    public void api23IncludesPackageAndGlobalSettingsActions() {
        SpecialPermissionController controller =
                new SpecialPermissionController(Build.VERSION_CODES.M);

        PermissionUserAction overlay =
                controller.getUserAction(Manifest.permission.SYSTEM_ALERT_WINDOW);
        PermissionUserAction notificationPolicy =
                controller.getUserAction(Manifest.permission.ACCESS_NOTIFICATION_POLICY);

        assertNotNull(overlay);
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, overlay.getAction());
        assertTrue(overlay.supportsPackage());
        assertNotNull(notificationPolicy);
        assertFalse(notificationPolicy.supportsPackage());
    }

    @Test
    public void apiGatingDoesNotOfferUnavailableActions() {
        SpecialPermissionController controller =
                new SpecialPermissionController(Build.VERSION_CODES.M);

        assertNull(controller.getUserAction(Manifest.permission.REQUEST_INSTALL_PACKAGES));
        assertNull(controller.getUserAction(Manifest.permission.MANAGE_EXTERNAL_STORAGE));
    }

    @Test
    public void boundServiceActionsRemainGlobalAndUserMediated() {
        SpecialPermissionController controller =
                new SpecialPermissionController(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        PermissionChangeResult result = controller.requestChange(
                Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, true);

        assertEquals(PermissionChangeResult.Status.USER_ACTION_REQUIRED, result.getStatus());
        assertNotNull(result.getUserAction());
        assertFalse(result.getUserAction().supportsPackage());
    }

    @Test
    public void unknownSpecialPermissionIsUnsupported() {
        SpecialPermissionController controller =
                new SpecialPermissionController(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

        PermissionChangeResult result = controller.requestChange("android.permission.UNKNOWN", true);

        assertEquals(PermissionChangeResult.Status.UNSUPPORTED, result.getStatus());
        assertNull(result.getUserAction());
    }

    @Test
    public void componentSpecialStateIsUnknownRatherThanDenied() {
        SpecialPermissionController controller =
                new SpecialPermissionController(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        Permission permission = new Permission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                false, io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat.OP_NONE, false, 0);

        assertEquals(PermissionState.UNKNOWN, controller.getState(permission));
        assertFalse(controller.getState(permission).isGranted());
    }

    @Test
    public void appOpSpecialStateUsesEffectivePermissionState() {
        SpecialPermissionController controller =
                new SpecialPermissionController(Build.VERSION_CODES.M);
        Permission permission = new Permission(Manifest.permission.SYSTEM_ALERT_WINDOW,
                true, 1, true, 0);

        assertEquals(PermissionState.GRANTED, controller.getState(permission));
    }

}
