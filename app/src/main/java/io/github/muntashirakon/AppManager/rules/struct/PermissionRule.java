// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.permission.Permission;
import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;

public class PermissionRule extends RuleEntry {
    private final int mAppOp;

    private boolean mIsGranted;
    @PermissionCompat.PermissionFlags
    private int mFlags;

    public PermissionRule(@NonNull String packageName, @NonNull String permName, boolean isGranted,
                          @PermissionCompat.PermissionFlags int flags) {
        super(packageName, permName, RuleType.PERMISSION);
        mIsGranted = isGranted;
        mFlags = flags;
        mAppOp = AppOpsManager.permissionToOpCode(name);
    }

    public PermissionRule(@NonNull String packageName, @NonNull String permName, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, permName, RuleType.PERMISSION);
        if (tokenizer.hasMoreElements()) {
            mIsGranted = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isGranted not found");
        if (tokenizer.hasMoreElements()) {
            mFlags = Integer.parseInt(tokenizer.nextElement().toString());
        } else {
            // Don't throw exception in order to provide backward compatibility
            mFlags = 0;
        }
        mAppOp = AppOpsManager.permissionToOpCode(name);
    }

    public boolean isGranted() {
        return mIsGranted;
    }

    public void setGranted(boolean granted) {
        mIsGranted = granted;
    }

    @PermissionCompat.PermissionFlags
    public int getFlags() {
        return mFlags;
    }

    public void setFlags(@PermissionCompat.PermissionFlags int flags) {
        mFlags = flags;
    }

    public int getAppOp() {
        return mAppOp;
    }

    public Permission getPermission(boolean appOpAllowed) {
        return new Permission(name, mIsGranted, mAppOp, appOpAllowed, mFlags);
    }

    @NonNull
    @Override
    public String toString() {
        return "PermissionRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", isGranted=" + mIsGranted +
                ", flags=" + mFlags +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mIsGranted + "\t" + mFlags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionRule)) return false;
        if (!super.equals(o)) return false;
        PermissionRule that = (PermissionRule) o;
        return isGranted() == that.isGranted() && getFlags() == that.getFlags();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isGranted(), getFlags());
    }
}
