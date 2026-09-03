// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVOKED_COMPAT;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class PermissionMutationTest {
    private static final String PERMISSION_NAME = "android.permission.TEST";
    private static final int APP_OP = 42;

    @Test
    public void grantRuntimePermissionUpdatesGrantAppOpAndUserFlags() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, false, APP_OP, false,
                FLAG_PERMISSION_USER_FIXED);

        boolean killApp = PermissionMutation.prepareGrant(packageInfo(Build.VERSION_CODES.M),
                permission, true, false);

        assertTrue(permission.isGranted());
        assertTrue(permission.isAppOpAllowed());
        assertTrue(permission.isUserSet());
        assertFalse(permission.isUserFixed());
        assertFalse(killApp);
    }

    @Test
    public void grantRuntimePermissionCanMarkDecisionFixed() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, false,
                AppOpsManagerCompat.OP_NONE, false, FLAG_PERMISSION_USER_SET);

        PermissionMutation.prepareGrant(packageInfo(Build.VERSION_CODES.M), permission, true, true);

        assertTrue(permission.isGranted());
        assertTrue(permission.isUserFixed());
        assertFalse(permission.isUserSet());
    }

    @Test
    public void revokeRuntimePermissionUpdatesGrantAppOpAndUserFlags() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, true, APP_OP, true,
                FLAG_PERMISSION_USER_SET);

        boolean killApp = PermissionMutation.prepareRevoke(packageInfo(Build.VERSION_CODES.M),
                permission, true);

        assertFalse(permission.isGranted());
        assertFalse(permission.isAppOpAllowed());
        assertTrue(permission.isUserFixed());
        assertFalse(permission.isUserSet());
        assertFalse(killApp);
    }

    @Test
    public void revokeRuntimePermissionWithoutFixedDecisionSetsUserSet() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, true,
                AppOpsManagerCompat.OP_NONE, false, FLAG_PERMISSION_USER_FIXED);

        PermissionMutation.prepareRevoke(packageInfo(Build.VERSION_CODES.M), permission, false);

        assertFalse(permission.isGranted());
        assertTrue(permission.isUserSet());
        assertFalse(permission.isUserFixed());
    }

    @Test
    public void developmentPermissionUsesModernGrantState() throws Exception {
        DevelopmentPermission permission = new DevelopmentPermission(PERMISSION_NAME, false,
                AppOpsManagerCompat.OP_NONE, false, 0);

        PermissionMutation.prepareGrant(packageInfo(Build.VERSION_CODES.LOLLIPOP), permission,
                true, false);

        assertTrue(permission.isGranted());
        assertTrue(permission.isUserSet());
    }

    @Test
    public void legacyRuntimePermissionUsesAppOpCompatibilityState() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, true, APP_OP, false,
                FLAG_PERMISSION_REVIEW_REQUIRED | FLAG_PERMISSION_REVOKED_COMPAT);

        boolean grantKillsApp = PermissionMutation.prepareGrant(
                packageInfo(Build.VERSION_CODES.LOLLIPOP_MR1), permission, true, false);

        assertTrue(permission.isGranted());
        assertTrue(permission.isAppOpAllowed());
        assertFalse(permission.isReviewRequired());
        assertFalse(permission.isRevokedCompat());
        assertTrue(grantKillsApp);

        boolean revokeKillsApp = PermissionMutation.prepareRevoke(
                packageInfo(Build.VERSION_CODES.LOLLIPOP_MR1), permission, true);

        assertTrue(permission.isGranted());
        assertFalse(permission.isAppOpAllowed());
        assertTrue(permission.isRevokedCompat());
        assertTrue(revokeKillsApp);
    }

    @Test
    public void deniedLegacyRuntimePermissionIsRejected() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, false, APP_OP, false,
                0);

        try {
            PermissionMutation.prepareGrant(packageInfo(Build.VERSION_CODES.LOLLIPOP_MR1),
                    permission, true, false);
            fail("A denied legacy runtime permission must be rejected.");
        } catch (PermissionException expected) {
            assertFalse(permission.isGranted());
        }
    }

    @Test
    public void unchangedLegacyAppOpDoesNotRequireKill() throws Exception {
        RuntimePermission permission = new RuntimePermission(PERMISSION_NAME, true, APP_OP, true, 0);

        assertFalse(PermissionMutation.prepareGrant(
                packageInfo(Build.VERSION_CODES.LOLLIPOP_MR1), permission, true, false));
    }

    private static PackageInfo packageInfo(int targetSdk) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.targetSdkVersion = targetSdk;
        return packageInfo;
    }
}
