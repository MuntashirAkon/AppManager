// SPDX-License-Identifier: GPL-3.0-or-later

package android.app;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@SuppressWarnings("FieldMayBeFinal")
@RefineAs(AppOpsManager.class)
public class AppOpsManagerHidden {
    /**
     * Uid state: The UID is a foreground persistent app. The lower the UID
     * state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_PERSISTENT = 100;

    /**
     * Uid state: The UID is top foreground app. The lower the UID
     * state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_TOP = 200;

    /**
     * Uid state: The UID is running a foreground service of location type.
     * The lower the UID state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int UID_STATE_FOREGROUND_SERVICE_LOCATION = 300;

    /**
     * Uid state: The UID is running a foreground service. The lower the UID
     * state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_FOREGROUND_SERVICE = 400;

    /**
     * Uid state: The UID is a foreground app. The lower the UID
     * state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_FOREGROUND = 500;

    /**
     * Last UID state in which we don't restrict what an op can do.
     *
     * @deprecated Replaced by {@link #UID_STATE_MAX_LAST_NON_RESTRICTED} in Android 10 (SDK 29)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_LAST_NON_RESTRICTED = HiddenUtil.throwUOE();

    /**
     * The max, which is min priority, UID state for which any app op
     * would be considered as performed in the foreground.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static /*final*/ int UID_STATE_MAX_LAST_NON_RESTRICTED = HiddenUtil.throwUOE();

    /**
     * Uid state: The UID is a background app. The lower the UID
     * state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_BACKGROUND = 600;

    /**
     * Uid state: The UID is a cached app. The lower the UID
     * state the more important the UID is for the user.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int UID_STATE_CACHED = 700;

    /**
     * Uid state: The UID state with the highest priority.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int MAX_PRIORITY_UID_STATE = UID_STATE_PERSISTENT;

    /**
     * Uid state: The UID state with the lowest priority.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int MIN_PRIORITY_UID_STATE = UID_STATE_CACHED;


    /**
     * Flag: non proxy operations. These are operations
     * performed on behalf of the app itself and not on behalf of
     * another one.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAG_SELF = 0x1;

    /**
     * Flag: trusted proxy operations. These are operations
     * performed on behalf of another app by a trusted app.
     * Which is work a trusted app blames on another app.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAG_TRUSTED_PROXY = 0x2;

    /**
     * Flag: untrusted proxy operations. These are operations
     * performed on behalf of another app by an untrusted app.
     * Which is work an untrusted app blames on another app.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAG_UNTRUSTED_PROXY = 0x4;

    /**
     * Flag: trusted proxied operations. These are operations
     * performed by a trusted other app on behalf of an app.
     * Which is work an app was blamed for by a trusted app.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAG_TRUSTED_PROXIED = 0x8;

    /**
     * Flag: untrusted proxied operations. These are operations
     * performed by an untrusted other app on behalf of an app.
     * Which is work an app was blamed for by an untrusted app.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAG_UNTRUSTED_PROXIED = 0x10;

    /**
     * Flags: all operations. These include operations matched
     * by {@link #OP_FLAG_SELF}, {@link #OP_FLAG_TRUSTED_PROXIED},
     * {@link #OP_FLAG_UNTRUSTED_PROXIED}, {@link #OP_FLAG_TRUSTED_PROXIED},
     * {@link #OP_FLAG_UNTRUSTED_PROXIED}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAGS_ALL = OP_FLAG_SELF
            | OP_FLAG_TRUSTED_PROXY
            | OP_FLAG_UNTRUSTED_PROXY
            | OP_FLAG_TRUSTED_PROXIED
            | OP_FLAG_UNTRUSTED_PROXIED;

    /**
     * Flags: all trusted operations which is ones either the app did {@link #OP_FLAG_SELF},
     * or it was blamed for by a trusted app {@link #OP_FLAG_TRUSTED_PROXIED}, or ones the
     * app if untrusted blamed on other apps {@link #OP_FLAG_UNTRUSTED_PROXY}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static final int OP_FLAGS_ALL_TRUSTED = OP_FLAG_SELF
            | OP_FLAG_UNTRUSTED_PROXY
            | OP_FLAG_TRUSTED_PROXIED;


    public static final int OP_NONE = -1;
    /**
     * Retrieve current usage stats via {@link android.app.usage.UsageStatsManager}.
     */
    public static /*final*/ int OP_GET_USAGE_STATS = 43;
    /**
     * Control whether an application is allowed to run in the background.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static /*final*/ int OP_RUN_IN_BACKGROUND = 63;
    /**
     * Run jobs when in background
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static /*final*/ int OP_RUN_ANY_IN_BACKGROUND = 70;
    /**
     * Access all external storage
     */
    public static /*final*/ int OP_MANAGE_EXTERNAL_STORAGE = 92;
    public static /*final*/ int _NUM_OP = 121;

    public static /*final*/ int MIUI_OP_START = 10000;
    public static /*final*/ int MIUI_OP_END = 10040;

    /**
     * This maps each operation to the public string constant for it.
     * If it doesn't have a public string constant, it maps to null.
     */
    private static String[] sOpToString = HiddenUtil.throwUOE();

    /**
     * Retrieve the op switch that controls the given operation.
     */
    public static int opToSwitch(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve a non-localized name for the operation, for debugging output.
     */
    @NonNull
    public static String opToName(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve a non-localized public name for the operation.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    public static String opToPublicName(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     */
    @Nullable
    public static String opToPermission(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     *
     * @param op The operation name.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Nullable
    public static String opToPermission(@NonNull String op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the user restriction associated with an operation, or null if there is not one.
     */
    @Nullable
    public static String opToRestriction(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the app op code for a permission, or {@link #OP_NONE} if there is not one.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static int permissionToOpCode(String permission) {
        return HiddenUtil.throwUOE(permission);
    }

    /**
     * Retrieve whether the op allows the system (and system ui) to
     * bypass the user restriction.
     */
    public static boolean opAllowSystemBypassRestriction(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the default mode for the operation.
     * <p>
     * <b>Note:</b> In some cases, this might throw {@link NoSuchMethodError} in which case, use {@link #opToDefaultMode(int, boolean)}.
     */
    public static int opToDefaultMode(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the default mode for the operation.
     * <p>
     * <b>Note:</b> The only known case is Android 6.0.1 in Samsung devices (API 23)
     */
    public static int opToDefaultMode(int op, boolean isStrict) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve the default mode for the app op.
     *
     * @param appOp The app op name
     * @return the default mode for the app op
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static int opToDefaultMode(@NonNull String appOp) {
        return HiddenUtil.throwUOE(appOp);
    }

    /**
     * Retrieve the human readable mode.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static String modeToName(int mode) {
        return HiddenUtil.throwUOE(mode);
    }

    /**
     * Retrieve whether the op can be read by apps with manage appops permission.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static boolean opRestrictsRead(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Retrieve whether the op allows itself to be reset.
     */
    public static boolean opAllowsReset(int op) {
        return HiddenUtil.throwUOE(op);
    }

    /**
     * Gets the app op name associated with a given permission.
     * The app op name is one of the public constants defined
     * in this class such as {@code OPSTR_COARSE_LOCATION}.
     * This API is intended to be used for mapping runtime
     * permissions to the corresponding app op.
     *
     * @param permission The permission.
     * @return The app op associated with the permission or null.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static String permissionToOp(String permission) {
        return HiddenUtil.throwUOE(permission);
    }

    public static int strOpToOp(String op) {
        return HiddenUtil.throwUOE(op);
    }

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

        public static final Creator<PackageOps> CREATOR = HiddenUtil.creator();

        @Override
        public int describeContents() {
            return HiddenUtil.throwUOE();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            HiddenUtil.throwUOE(dest, flags);
        }
    }

    public static class OpEntry implements Parcelable {
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            HiddenUtil.throwUOE(dest, flags);
        }

        @Override
        public int describeContents() {
            return HiddenUtil.throwUOE();
        }

        public static final Creator<OpEntry> CREATOR = HiddenUtil.creator();

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
