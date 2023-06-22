// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;

public class NotificationListenerRule extends RuleEntry {
    private boolean mIsGranted;

    public NotificationListenerRule(@NonNull String packageName, String name, boolean isGranted) {
        super(packageName, name, RuleType.NOTIFICATION);
        this.mIsGranted = isGranted;
    }

    public NotificationListenerRule(@NonNull String packageName, String name, @NonNull StringTokenizer tokenizer) {
        super(packageName, name, RuleType.NOTIFICATION);
        if (tokenizer.hasMoreElements()) {
            mIsGranted = Boolean.parseBoolean(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: isGranted not found");
    }

    public boolean isGranted() {
        return mIsGranted;
    }

    public void setGranted(boolean granted) {
        mIsGranted = granted;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationListenerRule{" +
                "packageName='" + packageName + '\'' +
                ", name='" + name + '\'' +
                ", isGranted=" + mIsGranted +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mIsGranted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationListenerRule)) return false;
        if (!super.equals(o)) return false;
        NotificationListenerRule that = (NotificationListenerRule) o;
        return isGranted() == that.isGranted();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isGranted());
    }
}
