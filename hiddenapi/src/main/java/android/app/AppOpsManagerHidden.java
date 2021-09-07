// SPDX-License-Identifier: GPL-3.0-or-later

package android.app;

import android.os.Build;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(AppOpsManager.class)
public class AppOpsManagerHidden {
    public static int _NUM_OP;

    public static class PackageOps implements Parcelable {
        @NonNull
        public String getPackageName() {
            return HiddenUtil.throwUOE();
        }

        public int getUid() {
            return HiddenUtil.throwUOE();
        }

        @NonNull
        public List<Parcelable> getOps() {
            return HiddenUtil.throwUOE();
        }
    }

    public static class OpEntry implements Parcelable {
        public int getOp() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @NonNull
        public String getOpStr() {
            return HiddenUtil.throwUOE();
        }

        public int getMode() {
            return HiddenUtil.throwUOE();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @Deprecated
        public long getTime() {
            return HiddenUtil.throwUOE();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        @Deprecated
        public long getLastAccessTime() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessTime(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastAccessForegroundTime() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessForegroundTime(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastAccessBackgroundTime() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessBackgroundTime(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastTimeFor(int uidState) {
            return HiddenUtil.throwUOE(uidState);
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessTime(int fromUidState, int toUidState, int flags) {
            return HiddenUtil.throwUOE(fromUidState, toUidState, flags);
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @Deprecated
        public long getRejectTime() {
            return HiddenUtil.throwUOE();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectTime() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectTime(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectForegroundTime() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectForegroundTime(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectBackgroundTime() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectBackgroundTime(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectTimeFor(int uidState) {
            return HiddenUtil.throwUOE(uidState);
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectTime(int fromUidState, int toUidState, int flags) {
            return HiddenUtil.throwUOE(fromUidState, toUidState, flags);
        }

        public boolean isRunning() {
            return HiddenUtil.throwUOE();
        }

        /**
         * @return {@code int} value up to Android 9 (P), {@code long} after Android 10 (Q)
         * @deprecated since Android 11 (R), but mustn't be used since Android 10 (Q)
         */
        @Deprecated
        public int getDuration() {
            return HiddenUtil.throwUOE();
        }

        @RequiresApi(Build.VERSION_CODES.R)
        public long getLastDuration(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastForegroundDuration(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastBackgroundDuration(int flags) {
            return HiddenUtil.throwUOE(flags);
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastDuration(int fromUidState, int toUidState, int flags) {
            return HiddenUtil.throwUOE(fromUidState, toUidState, flags);
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.M)
        public int getProxyUid() {
            return HiddenUtil.throwUOE();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        public int getProxyUid(int uidState, int flags) {
            return HiddenUtil.throwUOE(uidState, flags);
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.M)
        @Nullable
        public String getProxyPackageName() {
            return HiddenUtil.throwUOE();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        @Nullable
        public String getProxyPackageName(int uidState, int flags) {
            return HiddenUtil.throwUOE(uidState, flags);
        }

        // TODO(24/12/20): Get proxy info (From API 30)
    }
}
