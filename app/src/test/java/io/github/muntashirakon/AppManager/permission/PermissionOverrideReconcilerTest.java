// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao;
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;

public class PermissionOverrideReconcilerTest {
    private static final String PACKAGE_NAME = "sample.package";

    @Test
    public void alreadyEnforcedOverrideIsOnlyMarkedSynced() {
        FakeDao dao = new FakeDao();
        PermissionOverride override = override(true);
        dao.insert(override);
        FakePlatform platform = new FakePlatform();
        platform.enforced = true;

        new PermissionOverrideReconciler(dao, platform).reconcileNow(PACKAGE_NAME, 0);

        assertEquals(PermissionOverrideReconciler.SYNCED, dao.getValue().syncStatus);
        assertTrue(dao.getValue().syncTime > 0);
        assertEquals(0, platform.applyCount);
        assertEquals(1, platform.resolveCount);
    }

    @Test
    public void missingOverrideIsAppliedAndMarkedSynced() {
        FakeDao dao = new FakeDao();
        PermissionOverride override = override(10, false);
        dao.insert(override);
        FakePlatform platform = new FakePlatform();

        new PermissionOverrideReconciler(dao, platform).reconcileNow(PACKAGE_NAME, 10);

        assertEquals(PermissionOverrideReconciler.SYNCED, dao.getValue().syncStatus);
        assertEquals(1, platform.applyCount);
        assertEquals(10010, platform.lastUid);
        assertEquals(10, platform.lastOverride.userId);
    }

    @Test
    public void applyFailureLeavesOverrideFailed() {
        FakeDao dao = new FakeDao();
        dao.insert(override(true));
        FakePlatform platform = new FakePlatform();
        platform.failApply = true;

        new PermissionOverrideReconciler(dao, platform).reconcileNow(PACKAGE_NAME, 0);

        assertEquals(PermissionOverrideReconciler.FAILED, dao.getValue().syncStatus);
        assertEquals(1, platform.applyCount);
    }

    @Test
    public void uidResolutionFailureMarksEveryOverrideFailed() {
        FakeDao dao = new FakeDao();
        dao.insert(override(true));
        dao.insert(new PermissionOverride(PACKAGE_NAME, 0, "android.permission.SECOND", false, "8"));
        FakePlatform platform = new FakePlatform();
        platform.failResolve = true;

        new PermissionOverrideReconciler(dao, platform).reconcileNow(PACKAGE_NAME, 0);

        for (PermissionOverride value : dao.values()) {
            assertEquals(PermissionOverrideReconciler.FAILED, value.syncStatus);
        }
        assertEquals(1, platform.resolveCount);
        assertEquals(0, platform.applyCount);
    }

    @Test
    public void sharedUidPackagesAreBothReconciledUsingCurrentUid() {
        FakeDao dao = new FakeDao();
        PermissionOverride first = override(true);
        PermissionOverride second = new PermissionOverride("shared.second", 0,
                "android.permission.INTERNET", false, "7");
        dao.insert(first);
        dao.insert(second);
        FakePlatform platform = new FakePlatform();
        platform.sharedUid = true;

        PermissionOverrideReconciler reconciler = new PermissionOverrideReconciler(dao, platform);
        reconciler.reconcileNow(PACKAGE_NAME, 0);
        reconciler.reconcileNow("shared.second", 0);

        assertEquals(2, platform.applyCount);
        assertEquals(4242, platform.lastUid);
        for (PermissionOverride value : dao.values()) {
            assertEquals(PermissionOverrideReconciler.SYNCED, value.syncStatus);
        }
    }

    private static PermissionOverride override(boolean granted) {
        return override(0, granted);
    }

    private static PermissionOverride override(int userId, boolean granted) {
        return new PermissionOverride(PACKAGE_NAME, userId, "android.permission.INTERNET", granted, "7");
    }

    private static final class FakeDao implements PermissionOverrideDao {
        private final Map<String, PermissionOverride> values = new LinkedHashMap<>();

        @Override
        public PermissionOverride get(String packageName, int userId, String permissionName) {
            return values.get(key(packageName, userId, permissionName));
        }

        @Override
        public List<PermissionOverride> getForPackage(String packageName, int userId) {
            List<PermissionOverride> result = new ArrayList<>();
            for (PermissionOverride value : values.values()) {
                if (value.packageName.equals(packageName) && value.userId == userId)
                    result.add(value);
            }
            return result;
        }

        @Override
        public List<PermissionOverride> getAll() {
            return new ArrayList<>(values.values());
        }

        @Override
        public void insert(PermissionOverride override) {
            values.put(key(override.packageName, override.userId, override.permissionName), override);
        }

        @Override
        public void delete(String packageName, int userId, String permissionName) {
            values.remove(key(packageName, userId, permissionName));
        }

        @Override
        public void deleteForPackage(String packageName, int userId) {
            for (PermissionOverride value : new ArrayList<>(values.values())) {
                if (value.packageName.equals(packageName) && value.userId == userId) {
                    values.remove(key(packageName, userId, value.permissionName));
                }
            }
        }

        PermissionOverride getValue() {
            return values.values().iterator().next();
        }

        List<PermissionOverride> values() {
            return new ArrayList<>(values.values());
        }

        private static String key(String packageName, int userId, String permissionName) {
            return packageName + ':' + userId + ':' + permissionName;
        }
    }

    private static final class FakePlatform implements PermissionOverrideReconciler.Platform {
        boolean enforced;
        boolean failResolve;
        boolean failApply;
        boolean sharedUid;
        int resolveCount;
        int applyCount;
        int lastUid;
        PermissionOverride lastOverride;

        @Override
        public int resolveUid(@NonNull String packageName, int userId) throws Exception {
            resolveCount++;
            if (failResolve) throw new Exception("missing package");
            return sharedUid ? 4242 : 10000 + userId;
        }

        @Override
        public boolean isEnforced(int uid, @NonNull PermissionOverride override) {
            return enforced;
        }

        @Override
        public void apply(int uid, @NonNull PermissionOverride override) throws Exception {
            applyCount++;
            lastUid = uid;
            lastOverride = override;
            if (failApply) throw new Exception("backend unavailable");
        }
    }
}
