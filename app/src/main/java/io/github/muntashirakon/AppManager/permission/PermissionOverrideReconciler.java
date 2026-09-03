// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.muntashirakon.AppManager.types.UserPackagePair;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.db.dao.PermissionOverrideDao;
import io.github.muntashirakon.AppManager.db.entity.PermissionOverride;

public final class PermissionOverrideReconciler {
    public static final int SYNCED = 0;
    public static final int PENDING = 1;
    public static final int FAILED = 2;

    interface Platform {
        int resolveUid(@NonNull String packageName, int userId) throws Exception;

        boolean isEnforced(int uid, @NonNull PermissionOverride override) throws Exception;

        void apply(int uid, @NonNull PermissionOverride override) throws Exception;
    }

    private final PermissionOverrideDao mDao;
    private final Platform mPlatform;
    private final Executor mExecutor;
    private final Object mStateLock = new Object();
    private final Set<UserPackagePair> mQueuedKeys = new java.util.HashSet<>();

    PermissionOverrideReconciler(@NonNull PermissionOverrideDao dao,
                                 @NonNull Platform platform) {
        mDao = dao;
        mPlatform = platform;
        mExecutor = Executors.newSingleThreadExecutor();
    }

    public void reconcile(@NonNull String packageName, int userId) {
        UserPackagePair key = new UserPackagePair(packageName, userId);
        synchronized (mQueuedKeys) {
            if (!mQueuedKeys.add(key)) return;
        }
        mExecutor.execute(() -> {
            try {
                reconcileNow(packageName, userId);
            } finally {
                synchronized (mQueuedKeys) {
                    mQueuedKeys.remove(key);
                }
            }
        });
    }

    public void reconcileAll() {
        mExecutor.execute(() -> {
            Set<UserPackagePair> targets = new LinkedHashSet<>();
            for (PermissionOverride override : mDao.getAll()) {
                targets.add(new UserPackagePair(override.packageName, override.userId));
            }
            Set<UserPackagePair> ownedTargets = new LinkedHashSet<>();
            synchronized (mQueuedKeys) {
                for (UserPackagePair target : targets) {
                    if (mQueuedKeys.add(target)) {
                        ownedTargets.add(target);
                    }
                }
            }
            for (UserPackagePair target : ownedTargets) {
                try {
                    reconcileNow(target.getPackageName(), target.getUserId());
                } finally {
                    synchronized (mQueuedKeys) {
                        mQueuedKeys.remove(target);
                    }
                }
            }
        });
    }

    public void remove(@NonNull String packageName, int userId) {
        mExecutor.execute(() -> removeNow(packageName, userId));
    }

    void removeNow(@NonNull String packageName, int userId) {
        synchronized (mStateLock) {
            mDao.deleteForPackage(packageName, userId);
        }
    }

    void reconcileNow(@NonNull String packageName, int userId) {
        synchronized (mStateLock) {
            List<PermissionOverride> overrides = mDao.getForPackage(packageName, userId);
            if (overrides.isEmpty()) return;
            int uid;
            try {
                // Resolve UID once per package/user so all overrides use the same current identity.
                uid = mPlatform.resolveUid(packageName, userId);
            } catch (Exception e) {
                for (PermissionOverride override : overrides) {
                    override.syncStatus = FAILED;
                    mDao.insert(override);
                }
                return;
            }
            for (PermissionOverride override : overrides) {
                override.syncStatus = PENDING;
                mDao.insert(override);
                try {
                    if (!mPlatform.isEnforced(uid, override)) mPlatform.apply(uid, override);
                    override.syncStatus = SYNCED;
                    override.syncTime = System.currentTimeMillis();
                } catch (Exception e) {
                    override.syncStatus = FAILED;
                }
                mDao.insert(override);
            }
        }
    }
}
