// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_AUTO_REVOKED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_ONE_TIME;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_POLICY_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_REVIEW_REQUIRED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_SYSTEM_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class PackageManagerPermissionControllerTest {
    private static final String PACKAGE_NAME = "sample.package";
    private static final String PERMISSION_NAME = "android.permission.TEST";
    private static final int APP_ID = 12345;

    private FakePlatform mPlatform;
    private PackageManagerPermissionController mController;

    @Before
    public void setUp() {
        mPlatform = new FakePlatform();
        mController = new PackageManagerPermissionController(mPlatform);
    }

    @Test
    public void supportsOnlyPackageManagerPermissions() {
        RuntimePermission packageManagerPermission = runtimePermission(false,
                AppOpsManagerCompat.OP_NONE, false, 0);
        RuntimePermission appOpPermission = runtimePermission(false, 42, false, 0);
        ReadOnlyPermission readOnlyPermission = new ReadOnlyPermission(PERMISSION_NAME, true,
                AppOpsManagerCompat.OP_NONE, false, 0);

        assertTrue(mController.supports(packageManagerPermission));
        assertFalse(mController.supports(appOpPermission));
        assertFalse(mController.supports(readOnlyPermission));
    }

    @Test
    public void grantPersistsPermissionBeforeFlags() throws Exception {
        RuntimePermission permission = runtimePermission(false, AppOpsManagerCompat.OP_NONE, false,
                FLAG_PERMISSION_USER_FIXED);

        mController.grant(packageInfo(0), permission, true, false);

        assertTrue(permission.isGranted());
        assertTrue(permission.isUserSet());
        assertFalse(permission.isUserFixed());
        assertEquals(Arrays.asList("grant:0", "flags:0"), mPlatform.events);
        assertEquals(FLAG_PERMISSION_USER_SET, mPlatform.lastFlagValues);
        assertTrue((mPlatform.lastFlagMask & FLAG_PERMISSION_USER_FIXED) != 0);
        assertTrue((mPlatform.lastFlagMask & FLAG_PERMISSION_ONE_TIME) != 0);
        assertTrue((mPlatform.lastFlagMask & FLAG_PERMISSION_AUTO_REVOKED) != 0);
    }

    @Test
    public void revokeChecksGrantThenPersistsPermissionAndFlags() throws Exception {
        RuntimePermission permission = runtimePermission(true, AppOpsManagerCompat.OP_NONE, false,
                FLAG_PERMISSION_REVIEW_REQUIRED);
        mPlatform.permissionState = PERMISSION_GRANTED;

        mController.revoke(packageInfo(0), permission, true, "test reason");

        assertFalse(permission.isGranted());
        assertEquals("test reason", mPlatform.lastRevokeReason);
        assertEquals(Arrays.asList("check:0", "revoke:0", "flags:0"), mPlatform.events);
    }

    @Test
    public void revokeSkipsPlatformCallWhenAlreadyRevoked() throws Exception {
        RuntimePermission permission = runtimePermission(true, AppOpsManagerCompat.OP_NONE, false, 0);
        mPlatform.permissionState = PERMISSION_DENIED;

        mController.revoke(packageInfo(0), permission, false);

        assertEquals(Arrays.asList("check:0", "flags:0"), mPlatform.events);
    }

    @Test
    public void secondaryUserUsesUidUserAndBroadcastsAfterFlags() throws Exception {
        int userId = 10;
        RuntimePermission permission = runtimePermission(false, AppOpsManagerCompat.OP_NONE, false, 0);

        mController.grant(packageInfo(userId), permission, true, false);

        assertEquals(Arrays.asList("grant:10", "flags:10", "altered"), mPlatform.events);
    }

    @Test
    public void systemFixedPermissionIsRejectedBeforeMutation() throws Exception {
        RuntimePermission permission = runtimePermission(false, AppOpsManagerCompat.OP_NONE, false,
                FLAG_PERMISSION_SYSTEM_FIXED);

        assertFalse(mController.isModifiable(permission));
        try {
            mController.grant(packageInfo(0), permission, true, false);
            fail("A system-fixed permission must not be modified.");
        } catch (PermissionException expected) {
            assertFalse(permission.isGranted());
            assertTrue(mPlatform.events.isEmpty());
        }
    }

    @Test
    public void missingBackendCapabilityIsRejectedBeforeMutation() throws Exception {
        RuntimePermission permission = runtimePermission(false, AppOpsManagerCompat.OP_NONE, false, 0);
        mPlatform.canModify = false;

        try {
            mController.grant(packageInfo(0), permission, true, false);
            fail("A permission must not be modified without backend capability.");
        } catch (PermissionException expected) {
            assertFalse(permission.isGranted());
            assertTrue(mPlatform.events.isEmpty());
        }
    }

    @Test
    public void policyFixedPermissionIsRejectedBeforeMutation() throws Exception {
        RuntimePermission permission = runtimePermission(false, AppOpsManagerCompat.OP_NONE, false,
                FLAG_PERMISSION_POLICY_FIXED);

        assertFalse(mController.isModifiable(permission));
        try {
            mController.grant(packageInfo(0), permission, true, false);
            fail("A policy-fixed permission must not be modified.");
        } catch (PermissionException expected) {
            assertFalse(permission.isGranted());
            assertTrue(mPlatform.events.isEmpty());
        }
    }

    @Test
    public void platformFailureKeepsExistingInMemoryMutationSemantics() throws Exception {
        RuntimePermission permission = runtimePermission(false, AppOpsManagerCompat.OP_NONE, false, 0);
        mPlatform.grantFailure = new RemoteException("grant failed");

        try {
            mController.grant(packageInfo(0), permission, true, false);
            fail("The platform failure must be reported.");
        } catch (PermissionException expected) {
            // PermUtils historically mutates its model before attempting the platform write.
            assertTrue(permission.isGranted());
            assertEquals(Arrays.asList("grant:0"), mPlatform.events);
        }
    }

    @Test
    public void batchGrantChangesGrantBitWithoutChangingFlags() throws Exception {
        mPlatform.permissionFlags = FLAG_PERMISSION_USER_FIXED;

        mController.setPlatformGranted(packageInfo(0), PERMISSION_NAME, 0, true);

        assertEquals(Arrays.asList("info", "check:0", "permissionFlags:0", "grant:0"),
                mPlatform.events);
        assertFalse(mPlatform.events.contains("flags:0"));
    }

    @Test
    public void batchRevokeSupportsAppOpBackedRuntimePermissionWithoutChangingAppOp() throws Exception {
        mPlatform.permissionState = PERMISSION_GRANTED;

        mController.setPlatformGranted(packageInfo(0), Manifest.permission.CAMERA, 0, false);

        assertEquals(Arrays.asList("info", "check:0", "permissionFlags:0", "check:0",
                "revoke:0"), mPlatform.events);
    }

    @Test
    public void batchMutationRejectsMismatchedPackageUserBeforeReadingState() throws Exception {
        try {
            mController.setPlatformGranted(packageInfo(10), PERMISSION_NAME, 0, true);
            fail("A context resolved for another user must be rejected.");
        } catch (PermissionException expected) {
            assertTrue(mPlatform.events.isEmpty());
        }
    }

    @Test
    public void structuredBatchResultDistinguishesUnsupportedCapability() {
        mPlatform.canModify = false;

        PermissionChangeResult result = mController.trySetPlatformGranted(packageInfo(0),
                PERMISSION_NAME, 0, true);

        assertEquals(PermissionChangeResult.Status.UNSUPPORTED, result.getStatus());
        assertFalse(result.isSuccessful());
        assertEquals(Arrays.asList("info", "check:0", "permissionFlags:0"), mPlatform.events);
    }

    @Test
    public void structuredBatchResultReportsPlatformFailure() {
        mPlatform.grantFailure = new RemoteException("grant failed");

        PermissionChangeResult result = mController.trySetPlatformGranted(packageInfo(0),
                PERMISSION_NAME, 0, true);

        assertEquals(PermissionChangeResult.Status.FAILURE, result.getStatus());
        assertFalse(result.isSuccessful());
        assertTrue(result.getCause() instanceof PermissionException);
    }

    private static RuntimePermission runtimePermission(boolean granted, int appOp,
                                                       boolean appOpAllowed, int flags) {
        return new RuntimePermission(PERMISSION_NAME, granted, appOp, appOpAllowed, flags);
    }

    private static PackageInfo packageInfo(int userId) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE_NAME;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = PACKAGE_NAME;
        packageInfo.applicationInfo.uid = UserHandleHidden.getUid(userId, APP_ID);
        packageInfo.applicationInfo.targetSdkVersion = Build.VERSION_CODES.M;
        return packageInfo;
    }

    private static final class FakePlatform implements PackageManagerPermissionPlatform {
        final List<String> events = new ArrayList<>();
        boolean canModify = true;
        int permissionState = PERMISSION_DENIED;
        int permissionFlags;
        int lastFlagMask;
        int lastFlagValues;
        @Nullable
        String lastRevokeReason;
        @Nullable
        RemoteException grantFailure;

        @Override
        public boolean canModifyPermissions() {
            return canModify;
        }

        @Override
        public int checkPermission(@NonNull String permissionName, @NonNull String packageName,
                                   int userId) {
            events.add("check:" + userId);
            return permissionState;
        }

        @Nullable
        @Override
        public PermissionInfo getPermissionInfo(@NonNull String permissionName,
                                                @NonNull String packageName) {
            events.add("info");
            PermissionInfo permissionInfo = new PermissionInfo();
            permissionInfo.name = permissionName;
            permissionInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
            return permissionInfo;
        }

        @Override
        public int getPermissionFlags(@NonNull String permissionName, @NonNull String packageName,
                                      int userId) {
            events.add("permissionFlags:" + userId);
            return permissionFlags;
        }

        @Override
        public void grantPermission(@NonNull String packageName, @NonNull String permissionName,
                                    int userId) throws RemoteException {
            events.add("grant:" + userId);
            if (grantFailure != null) {
                throw grantFailure;
            }
            permissionState = PERMISSION_GRANTED;
        }

        @Override
        public void revokePermission(@NonNull String packageName, @NonNull String permissionName,
                                     int userId, @Nullable String reason) {
            events.add("revoke:" + userId);
            lastRevokeReason = reason;
            permissionState = PERMISSION_DENIED;
        }

        @Override
        public boolean getCheckAdjustPolicyFlagPermission(
                @NonNull ApplicationInfo applicationInfo) {
            return false;
        }

        @Override
        public void updatePermissionFlags(@NonNull String permissionName,
                                          @NonNull String packageName, int flagMask,
                                          int flagValues, boolean checkAdjustPolicy, int userId) {
            events.add("flags:" + userId);
            lastFlagMask = flagMask;
            lastFlagValues = flagValues;
        }

        @Override
        public int getCurrentUserId() {
            return 0;
        }

        @Override
        public void onPackageAltered(@NonNull String packageName) {
            events.add("altered");
        }
    }
}
