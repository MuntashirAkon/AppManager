// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.appops;

import android.app.AppOpsManagerHidden;
import android.os.Build;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.Refine;

import static io.github.muntashirakon.AppManager.appops.AppOpsManager.MAX_PRIORITY_UID_STATE;
import static io.github.muntashirakon.AppManager.appops.AppOpsManager.MIN_PRIORITY_UID_STATE;
import static io.github.muntashirakon.AppManager.appops.AppOpsManager.OP_FLAGS_ALL;

class OpEntryCompat {
    AppOpsManagerHidden.OpEntry opEntry;

    /* package */ OpEntryCompat(Parcelable opEntry) {
        this.opEntry = Refine.unsafeCast(opEntry);
    }

    public int getOp() {
        return opEntry.getOp();
    }

    @NonNull
    public String getName() {
        return AppOpsManager.opToName(getOp());
    }

    @NonNull
    public String getOpStr() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getOpStr();
        }
        return AppOpsManager.opToPublicName(getOp());
    }

    @Nullable
    public String getPermission() {
        return AppOpsManager.opToPermission(getOp());
    }

    @AppOpsManager.Mode
    public int getMode() {
        return opEntry.getMode();
    }

    @AppOpsManager.Mode
    public int getDefaultMode() {
        return AppOpsManager.opToDefaultMode(getOp());
    }

    public long getTime() {
        return getLastAccessTime(OP_FLAGS_ALL);
    }

    public long getLastAccessTime(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastAccessTime(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastAccessTime();
        }
        return opEntry.getTime();
    }

    public long getLastAccessForegroundTime(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastAccessForegroundTime(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastAccessForegroundTime();
        } else return opEntry.getTime();
    }

    public long getLastAccessBackgroundTime(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastAccessBackgroundTime(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastAccessBackgroundTime();
        } else return opEntry.getTime();
    }

    public long getLastAccessTime(@AppOpsManager.UidState int fromUidState,
                                  @AppOpsManager.UidState int toUidState,
                                  @AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastAccessTime(fromUidState, toUidState, flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastTimeFor(fromUidState);
        } else return opEntry.getTime();
    }

    public long getRejectTime() {
        return getLastRejectTime(OP_FLAGS_ALL);
    }

    public long getLastRejectTime(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastRejectTime(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastRejectTime();
        } else return opEntry.getRejectTime();
    }

    public long getLastRejectForegroundTime(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastRejectForegroundTime(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastRejectForegroundTime();
        } else return opEntry.getRejectTime();
    }

    public long getLastRejectBackgroundTime(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastRejectBackgroundTime(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastRejectBackgroundTime();
        } else return opEntry.getRejectTime();
    }

    public long getLastRejectTime(@AppOpsManager.UidState int fromUidState,
                                  @AppOpsManager.UidState int toUidState,
                                  @AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastRejectTime(fromUidState, toUidState, flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return opEntry.getLastRejectTimeFor(fromUidState);
        } else return opEntry.getRejectTime();
    }

    public boolean isRunning() {
        return opEntry.isRunning();
    }

    public long getDuration() {
        return getLastDuration(MAX_PRIORITY_UID_STATE, MIN_PRIORITY_UID_STATE, OP_FLAGS_ALL);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    public long getLastDuration(@AppOpsManager.OpFlags int flags) {
        return opEntry.getLastDuration(flags);
    }

    public long getLastForegroundDuration(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastForegroundDuration(flags);
        } else return opEntry.getDuration();
    }

    public long getLastBackgroundDuration(@AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastBackgroundDuration(flags);
        } else return opEntry.getDuration();
    }

    public long getLastDuration(@AppOpsManager.UidState int fromUidState,
                                @AppOpsManager.UidState int toUidState,
                                @AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getLastDuration(fromUidState, toUidState, flags);
        } else return opEntry.getDuration();
    }

    // Deprecated in R
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    public int getProxyUid() {
        return opEntry.getProxyUid();
    }

    // Deprecated in R
    @Deprecated
    public int getProxyUid(@AppOpsManager.UidState int uidState, @AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getProxyUid(uidState, flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return opEntry.getProxyUid();
        }
        return 0;
    }

    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    @Nullable
    public String getProxyPackageName() {
        return opEntry.getProxyPackageName();
    }

    // Deprecated in R
    @Deprecated
    @Nullable
    public String getProxyPackageName(@AppOpsManager.UidState int uidState, @AppOpsManager.OpFlags int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return opEntry.getProxyPackageName(uidState, flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return opEntry.getProxyPackageName();
        }
        return null;
    }

    // TODO(24/12/20): Get proxy info (From API 30)
}