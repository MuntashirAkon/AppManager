// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.pm.PermissionInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.permission.ReadOnlyPermission;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class AppDetailsPermissionItemTest {
    @Test
    public void overlayCanRecordFirstToggleWithoutInitialOverride() {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.name = Manifest.permission.INTERNET;
        ReadOnlyPermission permission = new ReadOnlyPermission(permissionInfo.name, true,
                AppOpsManagerCompat.OP_NONE, false, 0);
        AppDetailsPermissionItem item = new AppDetailsPermissionItem(permissionInfo, permission,
                0, true, true, null);

        assertTrue(item.isOverlayModifiable());
        assertFalse(item.hasOverlayState());
        assertTrue(item.isGranted());

        item.setEffectiveGranted(false);

        assertTrue(item.hasOverlayState());
        assertFalse(item.isGranted());
    }

    @Test
    public void regularPermissionRecordsEffectiveStateAfterSuccessfulToggle() {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.name = "android.permission.TEST";
        ReadOnlyPermission permission = new ReadOnlyPermission(permissionInfo.name, true,
                AppOpsManagerCompat.OP_NONE, false, 0);
        AppDetailsPermissionItem item = new AppDetailsPermissionItem(permissionInfo, permission,
                0, true, false, null);

        item.setEffectiveGranted(false);

        assertFalse(item.isOverlayModifiable());
        assertFalse(item.hasOverlayState());
        assertFalse(item.isGranted());
    }
}
