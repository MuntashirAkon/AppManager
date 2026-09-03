// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_SYSTEM_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_FIXED;
import static io.github.muntashirakon.AppManager.compat.PermissionCompat.FLAG_PERMISSION_USER_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.AppOpsManager;
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
public class AppOpPermissionControllerTest {
    private static final String PACKAGE_NAME = "sample.package";
    private static final String PERMISSION_NAME = "android.permission.TEST";
    private static final int APP_ID = 12345;
    private static final int APP_OP = 42;

    private FakePackageManagerPlatform mPackageManager;
    private FakeAppOpPlatform mAppOps;
    private AppOpPermissionController mController;
    private AppOpsManagerCompat mAppOpsManager;

    @Before
    public void setUp() {
        List<String> events = new ArrayList<>();
        mPackageManager = new FakePackageManagerPlatform(events);
        mAppOps = new FakeAppOpPlatform(events);
        mController = new AppOpPermissionController(mPackageManager, mAppOps);
        mAppOpsManager = new AppOpsManagerCompat();
    }

    @Test
    public void supportsOnlyAppOpBackedPermissions() {
        assertTrue(mController.supports(runtimePermission(false, false, 0)));
        assertFalse(mController.supports(new RuntimePermission(PERMISSION_NAME, false,
                AppOpsManagerCompat.OP_NONE, false, 0)));
    }

    @Test
    public void modernGrantPersistsPackageManagerBeforeAppOp() throws Exception {
        RuntimePermission permission = runtimePermission(false, false,
                FLAG_PERMISSION_USER_FIXED);
        mAppOps.mode = AppOpsManager.MODE_IGNORED;

        mController.grant(packageInfo(0, Build.VERSION_CODES.M), permission, mAppOpsManager,
                true, false);

        assertTrue(permission.isGranted());
        assertTrue(permission.isAppOpAllowed());
        assertTrue(permission.isUserSet());
        assertEquals(Arrays.asList("grant:0", "flags:0", "appOpCheck", "appOpSet:0"),
                mPackageManager.events);
    }

    @Test
    public void modernRevokePersistsPackageManagerBeforeAppOp() throws Exception {
        RuntimePermission permission = runtimePermission(true, true, FLAG_PERMISSION_USER_SET);
        mPackageManager.permissionState = PERMISSION_GRANTED;
        mAppOps.mode = AppOpsManager.MODE_ALLOWED;

        mController.revoke(packageInfo(0, Build.VERSION_CODES.M), permission, mAppOpsManager, true);

        assertFalse(permission.isGranted());
        assertFalse(permission.isAppOpAllowed());
        assertEquals(Arrays.asList("check:0", "revoke:0", "flags:0", "appOpCheck",
                "appOpSet:1"), mPackageManager.events);
    }

    @Test
    public void readOnlyPermissionChangesOnlyAppOpAndKillsApp() throws Exception {
        ReadOnlyPermission permission = new ReadOnlyPermission(PERMISSION_NAME, true, APP_OP,
                false, 0);
        mAppOps.mode = AppOpsManager.MODE_IGNORED;

        mController.grant(packageInfo(0, Build.VERSION_CODES.UPSIDE_DOWN_CAKE), permission,
                mAppOpsManager, true, true);

        assertEquals(Arrays.asList("appOpCheck", "appOpSet:0", "kill:12345"),
                mPackageManager.events);
    }

    @Test
    public void legacyRuntimeGrantPreservesCompatibilityKill() throws Exception {
        RuntimePermission permission = runtimePermission(true, false, 0);
        mAppOps.mode = AppOpsManager.MODE_IGNORED;

        mController.grant(packageInfo(0, Build.VERSION_CODES.LOLLIPOP_MR1), permission,
                mAppOpsManager, true, false);

        assertEquals(Arrays.asList("grant:0", "flags:0", "appOpCheck", "appOpSet:0",
                "kill:12345"), mPackageManager.events);
    }

    @Test
    public void unchangedLegacyAppOpDoesNotKill() throws Exception {
        RuntimePermission permission = runtimePermission(true, true, 0);
        mAppOps.mode = AppOpsManager.MODE_ALLOWED;

        mController.grant(packageInfo(0, Build.VERSION_CODES.LOLLIPOP_MR1), permission,
                mAppOpsManager, true, false);

        assertFalse(mPackageManager.events.contains("kill:12345"));
        assertFalse(mPackageManager.events.contains("appOpSet:0"));
    }

    @Test
    public void systemFixedPermissionSyncsFlagsButSkipsPlatformAndAppOpWrites() throws Exception {
        RuntimePermission permission = runtimePermission(true, false, FLAG_PERMISSION_SYSTEM_FIXED);

        mController.grant(packageInfo(0, Build.VERSION_CODES.M), permission, mAppOpsManager,
                true, false);

        // PermissionMutation follows the legacy/read-only branch and retains its historical kill
        // decision even though the system-fixed guard suppresses the actual AppOp write.
        assertEquals(Arrays.asList("flags:0", "kill:12345"), mPackageManager.events);
    }

    @Test
    public void secondaryUserBroadcastsAfterAllWrites() throws Exception {
        RuntimePermission permission = runtimePermission(false, false, 0);

        mController.grant(packageInfo(10, Build.VERSION_CODES.M), permission, mAppOpsManager,
                true, false);

        assertEquals(Arrays.asList("grant:10", "flags:10", "appOpCheck", "appOpSet:0",
                "altered"), mPackageManager.events);
    }

    @Test
    public void missingCapabilityRejectsBeforeMutation() throws Exception {
        RuntimePermission permission = runtimePermission(false, false, 0);
        mPackageManager.canModify = false;

        try {
            mController.grant(packageInfo(0, Build.VERSION_CODES.M), permission, mAppOpsManager,
                    true, false);
            fail("Missing capability must reject the mutation.");
        } catch (PermissionException expected) {
            assertFalse(permission.isGranted());
            assertTrue(mPackageManager.events.isEmpty());
        }
    }

    @Test
    public void appOpFailureStopsBeforeCrossUserBroadcastAndKill() throws Exception {
        RuntimePermission permission = runtimePermission(true, false, 0);
        mAppOps.failure = new RemoteException("app op failed");

        try {
            mController.grant(packageInfo(10, Build.VERSION_CODES.LOLLIPOP_MR1), permission,
                    mAppOpsManager, true, false);
            fail("AppOp failure must be reported.");
        } catch (PermissionException expected) {
            assertEquals(Arrays.asList("grant:10", "flags:10", "appOpCheck"),
                    mPackageManager.events);
        }
    }

    @Test
    public void directModeMutationSkipsUnchangedMode() throws Exception {
        mAppOps.mode = AppOpsManager.MODE_ALLOWED;

        assertFalse(mController.setAppOpMode(mAppOpsManager, APP_OP, PACKAGE_NAME, APP_ID,
                AppOpsManager.MODE_ALLOWED));

        assertEquals(Arrays.asList("appOpCheck"), mPackageManager.events);
    }

    @Test
    public void foregroundModeIsNormalizedToAllowedOnGrant() throws Exception {
        RuntimePermission permission = runtimePermission(true, true, 0);
        mAppOps.mode = AppOpsManager.MODE_FOREGROUND;

        mController.grant(packageInfo(0, Build.VERSION_CODES.UPSIDE_DOWN_CAKE), permission,
                mAppOpsManager, true, false);

        assertEquals(AppOpsManager.MODE_ALLOWED, mAppOps.mode);
        assertTrue(mPackageManager.events.contains("appOpSet:0"));
    }

    @Test
    public void batchGrantComposesPackageManagerAndAppOpWithoutChangingFlags() {
        mAppOps.mode = AppOpsManager.MODE_IGNORED;

        PermissionChangeResult result = mController.trySetGrantedForBatch(
                packageInfo(0, Build.VERSION_CODES.M), Manifest.permission.CAMERA, 0,
                mAppOpsManager, true);

        assertTrue(result.isSuccessful());
        assertEquals(Arrays.asList("appOpCheck", "check:0", "grant:0", "appOpCheck",
                "appOpSet:0"), mPackageManager.events);
        assertFalse(mPackageManager.events.contains("flags:0"));
    }

    @Test
    public void batchLegacyRevokeKeepsPlatformGrantAndDeniesAppOp() {
        mPackageManager.permissionState = PERMISSION_GRANTED;
        mAppOps.mode = AppOpsManager.MODE_ALLOWED;

        PermissionChangeResult result = mController.trySetGrantedForBatch(
                packageInfo(0, Build.VERSION_CODES.LOLLIPOP_MR1), Manifest.permission.CAMERA, 0,
                mAppOpsManager, false);

        assertTrue(result.isSuccessful());
        assertEquals(Arrays.asList("appOpCheck", "check:0", "grant:0", "appOpCheck",
                "appOpSet:1", "kill:12345"), mPackageManager.events);
    }

    private static RuntimePermission runtimePermission(boolean granted, boolean appOpAllowed,
                                                       int flags) {
        return new RuntimePermission(PERMISSION_NAME, granted, APP_OP, appOpAllowed, flags);
    }

    private static PackageInfo packageInfo(int userId, int targetSdk) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = PACKAGE_NAME;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = PACKAGE_NAME;
        packageInfo.applicationInfo.uid = UserHandleHidden.getUid(userId, APP_ID);
        packageInfo.applicationInfo.targetSdkVersion = targetSdk;
        return packageInfo;
    }

    private static final class FakePackageManagerPlatform
            implements PackageManagerPermissionPlatform {
        final List<String> events;
        boolean canModify = true;
        int permissionState = PERMISSION_DENIED;

        FakePackageManagerPlatform(@NonNull List<String> events) {
            this.events = events;
        }

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
            PermissionInfo permissionInfo = new PermissionInfo();
            permissionInfo.name = permissionName;
            permissionInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
            return permissionInfo;
        }

        @Override
        public int getPermissionFlags(@NonNull String permissionName, @NonNull String packageName,
                                      int userId) {
            return 0;
        }

        @Override
        public void grantPermission(@NonNull String packageName, @NonNull String permissionName,
                                    int userId) {
            events.add("grant:" + userId);
            permissionState = PERMISSION_GRANTED;
        }

        @Override
        public void revokePermission(@NonNull String packageName, @NonNull String permissionName,
                                     int userId, @Nullable String reason) {
            events.add("revoke:" + userId);
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

    private static final class FakeAppOpPlatform implements AppOpPermissionPlatform {
        private final List<String> events;
        int mode = AppOpsManager.MODE_IGNORED;
        @Nullable
        Exception failure;

        FakeAppOpPlatform(@NonNull List<String> events) {
            this.events = events;
        }

        @Override
        public int checkOperation(@NonNull AppOpsManagerCompat appOpsManager, int appOp, int uid,
                                  @NonNull String packageName) throws Exception {
            events.add("appOpCheck");
            if (failure != null) throw failure;
            return mode;
        }

        @Override
        public void setMode(@NonNull AppOpsManagerCompat appOpsManager, int appOp, int uid,
                            @NonNull String packageName, int mode) {
            events.add("appOpSet:" + mode);
            this.mode = mode;
        }

        @Override
        public boolean canKillUid() {
            return true;
        }

        @Override
        public void killUid(int uid, @NonNull String reason) {
            events.add("kill:" + UserHandleHidden.getAppId(uid));
        }
    }
}
