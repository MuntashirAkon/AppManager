// SPDX-License-Identifier: GPL-3.0-or-later

package android.app;

import android.os.Build;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(AppOpsManager.class)
public class AppOpsManagerHidden {
    public static int _NUM_OP;

    @RefineAs(AppOpsManager.PackageOps.class)
    public static class PackageOps implements Parcelable {
        @NonNull
        public String getPackageName() {
            throw new UnsupportedOperationException();
        }

        public int getUid() {
            throw new UnsupportedOperationException();
        }

        @NonNull
        public List<Parcelable> getOps() {
            throw new UnsupportedOperationException();
        }
    }

    @RefineAs(AppOpsManager.OpEntry.class)
    public static class OpEntry implements Parcelable {
        public int getOp() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        @NonNull
        public String getOpStr() {
            throw new UnsupportedOperationException();
        }

        public int getMode() {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @Deprecated
        public long getTime() {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        @Deprecated
        public long getLastAccessTime() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessTime(int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastAccessForegroundTime() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessForegroundTime(int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastAccessBackgroundTime() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessBackgroundTime(int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastTimeFor(int uidState) {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastAccessTime(int fromUidState, int toUidState, int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @Deprecated
        public long getRejectTime() {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectTime() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectTime(int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectForegroundTime() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectForegroundTime(int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectBackgroundTime() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectBackgroundTime(int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated Removed in Android 10 (Q)
         */
        @RequiresApi(Build.VERSION_CODES.P)
        public long getLastRejectTimeFor(int uidState) {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastRejectTime(int fromUidState, int toUidState, int flags) {
            throw new UnsupportedOperationException();
        }

        public boolean isRunning() {
            throw new UnsupportedOperationException();
        }

        /**
         * @return {@code int} value up to Android 9 (P), {@code long} after Android 10 (Q)
         * @deprecated since Android 11 (R), but mustn't be used since Android 10 (Q)
         */
        @Deprecated
        public int getDuration() {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.R)
        public long getLastDuration(int flags) {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastForegroundDuration(int flags) {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastBackgroundDuration(int flags) {
            throw new UnsupportedOperationException();
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        public long getLastDuration(int fromUidState, int toUidState, int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.M)
        public int getProxyUid() {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        public int getProxyUid(int uidState, int flags) {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.M)
        @Nullable
        public String getProxyPackageName() {
            throw new UnsupportedOperationException();
        }

        /**
         * @deprecated since Android 11 (R)
         */
        @RequiresApi(Build.VERSION_CODES.Q)
        @Nullable
        public String getProxyPackageName(int uidState, int flags) {
            throw new UnsupportedOperationException();
        }

        // TODO(24/12/20): Get proxy info (From API 30)
    }
}
