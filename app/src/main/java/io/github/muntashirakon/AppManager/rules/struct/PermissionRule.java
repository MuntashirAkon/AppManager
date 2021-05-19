// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.servermanager.PermissionCompat;

public class PermissionRule extends RuleEntry {
    private boolean isGranted;
    @PermissionCompat.PermissionFlags
    private int flags;

    public PermissionRule(@NonNull String packageName, @NonNull String permName, boolean isGranted,
                          @PermissionCompat.PermissionFlags int flags) {
        super(packageName, permName, RulesStorageManager.Type.PERMISSION);
        this.isGranted = isGranted;
        this.flags = flags;
    }

    public PermissionRule(@NonNull String packageName, @NonNull String permName, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, permName, RulesStorageManager.Type.PERMISSION);
        if (tokenizer.hasMoreElements()) {
            isGranted = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isGranted not found");
        if (tokenizer.hasMoreElements()) {
            flags = Integer.parseInt(tokenizer.nextElement().toString());
        } else {
            // Don't throw exception in order to provide backward compatibility
            Log.d(PermissionRule.class.getSimpleName(), "Invalid format: flags not found");
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
}
