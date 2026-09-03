// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.permission;

import androidx.annotation.NonNull;

import io.github.muntashirakon.AppManager.compat.ActivityManagerCompat;
import io.github.muntashirakon.AppManager.compat.AppOpsManagerCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;

/**
 * Platform-facing AppOps and process operations used by {@link AppOpPermissionController}.
 */
interface AppOpPermissionPlatform {
    int checkOperation(@NonNull AppOpsManagerCompat appOpsManager, int appOp, int uid,
                       @NonNull String packageName) throws Exception;

    void setMode(@NonNull AppOpsManagerCompat appOpsManager, int appOp, int uid,
                 @NonNull String packageName, @AppOpsManagerCompat.Mode int mode) throws Exception;

    boolean canKillUid();

    void killUid(int uid, @NonNull String reason) throws Exception;
}

final class FrameworkAppOpPermissionPlatform implements AppOpPermissionPlatform {
    @Override
    public int checkOperation(@NonNull AppOpsManagerCompat appOpsManager, int appOp, int uid,
                              @NonNull String packageName) throws Exception {
        return appOpsManager.checkOperation(appOp, uid, packageName);
    }

    @Override
    public void setMode(@NonNull AppOpsManagerCompat appOpsManager, int appOp, int uid,
                        @NonNull String packageName, int mode) throws Exception {
        appOpsManager.setMode(appOp, uid, packageName, mode);
    }

    @Override
    public boolean canKillUid() {
        return SelfPermissions.canKillUid();
    }

    @Override
    public void killUid(int uid, @NonNull String reason) throws Exception {
        ActivityManagerCompat.killUid(uid, reason);
    }
}
