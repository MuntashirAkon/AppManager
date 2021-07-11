// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;

public class PermissionRule extends RuleEntry {
    private boolean isGranted;
    @PermissionCompat.PermissionFlags
    private int flags;

    public PermissionRule(@NonNull String packageName, @NonNull String permName, boolean isGranted,
                          @PermissionCompat.PermissionFlags int flags) {
        super(packageName, permName, RuleType.PERMISSION);
        this.isGranted = isGranted;
        this.flags = flags;
    }

    public PermissionRule(@NonNull String packageName, @NonNull String permName, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, permName, RuleType.PERMISSION);
        if (tokenizer.hasMoreElements()) {
            isGranted = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isGranted not found");
        if (tokenizer.hasMoreElements()) {
            flags = Integer.parseInt(tokenizer.nextElement().toString());
        } else {
            // Don't throw exception in order to provide backward compatibility
            flags = 0;
        }
    }

    public boolean isGranted() {
        return isGranted;
    }

    public void setGranted(boolean granted) {
        isGranted = granted;
    }

    @PermissionCompat.PermissionFlags
    public int getFlags() {
        return flags;
    }

    public void setFlags(@PermissionCompat.PermissionFlags int flags) {
        this.flags = flags;
    }

    @NonNull
    @Override
    public String toString() {
        return "PermissionRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", isGranted=" + isGranted +
                ", flags=" + flags +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + isGranted + "\t" + flags;
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
