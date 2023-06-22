// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.proc;

public class ProcUidNetStat {
    public final int uid;
    public final long txBytes;
    public final long rxBytes;

    public ProcUidNetStat(int uid, long txBytes, long rxBytes) {
        this.uid = uid;
        this.txBytes = txBytes;
        this.rxBytes = rxBytes;
    }
}
