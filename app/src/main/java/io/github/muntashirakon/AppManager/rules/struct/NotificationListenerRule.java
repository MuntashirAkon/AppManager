// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RulesStorageManager;

public class NotificationListenerRule extends RuleEntry {
    private boolean isGranted;

    public NotificationListenerRule(@NonNull String packageName, String name, boolean isGranted) {
        super(packageName, name, RulesStorageManager.Type.NOTIFICATION);
        this.isGranted = isGranted;
    }

    public NotificationListenerRule(@NonNull String packageName, String name, @NonNull StringTokenizer tokenizer) {
        super(packageName, name, RulesStorageManager.Type.NOTIFICATION);
        if (tokenizer.hasMoreElements()) {
            isGranted = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isGranted not found");
    }

    public boolean isGranted() {
        return isGranted;
    }

    public void setGranted(boolean granted) {
        isGranted = granted;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationListenerRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", isGranted=" + isGranted +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + isGranted;
    }
}
