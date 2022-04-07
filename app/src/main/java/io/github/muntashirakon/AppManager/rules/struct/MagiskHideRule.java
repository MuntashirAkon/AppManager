// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.magisk.MagiskProcess;
import io.github.muntashirakon.AppManager.rules.RuleType;

public class MagiskHideRule extends RuleEntry {
    @NonNull
    private final MagiskProcess magiskProcess;

    public MagiskHideRule(@NonNull MagiskProcess magiskProcess) {
        super(magiskProcess.packageName, magiskProcess.name, RuleType.MAGISK_HIDE);
        this.magiskProcess = magiskProcess;
    }

    public MagiskHideRule(@NonNull String packageName, @NonNull String processName, @NonNull StringTokenizer tokenizer)
            throws IllegalArgumentException {
        super(packageName, processName.equals(STUB) ? packageName : processName, RuleType.MAGISK_HIDE);
        magiskProcess = new MagiskProcess(packageName, name); // name cannot be STUB
        magiskProcess.setAppZygote(name.endsWith("_zygote"));
        if (tokenizer.hasMoreElements()) {
            magiskProcess.setEnabled(Boolean.parseBoolean(tokenizer.nextElement().toString()));
        } else throw new IllegalArgumentException("Invalid format: isHidden not found");
        if (tokenizer.hasMoreElements()) {
            magiskProcess.setIsolatedProcess(Boolean.parseBoolean(tokenizer.nextElement().toString()));
        }
    }

    @NonNull
    public MagiskProcess getMagiskProcess() {
        return magiskProcess;
    }

    @NonNull
    @Override
    public String toString() {
        return "MagiskHideRule{" +
                "packageName='" + packageName + '\'' +
                "processName='" + name + '\'' +
                ", isHidden=" + magiskProcess.isEnabled() +
                ", isIsolated=" + magiskProcess.isIsolatedProcess() +
                ", isAppZygote=" + magiskProcess.isAppZygote() +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + magiskProcess.isEnabled()
                + "\t" + magiskProcess.isIsolatedProcess();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MagiskHideRule)) return false;
        if (!super.equals(o)) return false;
        MagiskHideRule that = (MagiskHideRule) o;
        return magiskProcess.isEnabled() == that.magiskProcess.isEnabled()
                && magiskProcess.isIsolatedProcess() == that.magiskProcess.isIsolatedProcess();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), magiskProcess.isEnabled(), magiskProcess.isIsolatedProcess());
    }
}
