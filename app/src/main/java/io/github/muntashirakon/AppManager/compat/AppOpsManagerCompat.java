// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.AppOpsManagerHidden;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandleHidden;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.collection.SparseArrayCompat;

import com.android.internal.app.IAppOpsService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import aosp.libcore.util.EmptyArray;
import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.MiuiUtils;
import io.github.muntashirakon.AppManager.utils.PermissionUtils;

@SuppressLint("SoonBlockedPrivateApi")
public class AppOpsManagerCompat {
    @IntRange(from = -1, to = 5)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final int OP_FLAG_SELF;
    public static final int OP_FLAG_TRUSTED_PROXY;
    public static final int OP_FLAG_UNTRUSTED_PROXY;
    public static final int OP_FLAG_TRUSTED_PROXIED;
    public static final int OP_FLAG_UNTRUSTED_PROXIED;
    public static final int OP_FLAGS_ALL;
    public static final int OP_FLAGS_ALL_TRUSTED;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            OP_FLAG_SELF = AppOpsManagerHidden.OP_FLAG_SELF;
            OP_FLAG_TRUSTED_PROXY = AppOpsManagerHidden.OP_FLAG_TRUSTED_PROXY;
            OP_FLAG_UNTRUSTED_PROXY = AppOpsManagerHidden.OP_FLAG_UNTRUSTED_PROXY;
            OP_FLAG_TRUSTED_PROXIED = AppOpsManagerHidden.OP_FLAG_TRUSTED_PROXIED;
            OP_FLAG_UNTRUSTED_PROXIED = AppOpsManagerHidden.OP_FLAG_UNTRUSTED_PROXIED;
            OP_FLAGS_ALL = AppOpsManagerHidden.OP_FLAGS_ALL;
            OP_FLAGS_ALL_TRUSTED = AppOpsManagerHidden.OP_FLAGS_ALL_TRUSTED;
        } else {
            OP_FLAG_SELF = 0;
            OP_FLAG_TRUSTED_PROXY = 0;
            OP_FLAG_UNTRUSTED_PROXY = 0;
            OP_FLAG_TRUSTED_PROXIED = 0;
            OP_FLAG_UNTRUSTED_PROXIED = 0;
            OP_FLAGS_ALL = 0;
            OP_FLAGS_ALL_TRUSTED = 0;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface OpFlags {
    }

    public static final int UID_STATE_PERSISTENT;
    public static final int UID_STATE_TOP;
    public static final int UID_STATE_FOREGROUND_SERVICE_LOCATION;
    public static final int UID_STATE_FOREGROUND_SERVICE;
    public static final int UID_STATE_FOREGROUND;
    public static final int UID_STATE_BACKGROUND;
    public static final int UID_STATE_CACHED;

    public static final int MAX_PRIORITY_UID_STATE;
    public static final int MIN_PRIORITY_UID_STATE;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            UID_STATE_PERSISTENT = AppOpsManagerHidden.UID_STATE_PERSISTENT;
            UID_STATE_TOP = AppOpsManagerHidden.UID_STATE_TOP;
            UID_STATE_FOREGROUND_SERVICE = AppOpsManagerHidden.UID_STATE_FOREGROUND_SERVICE;
            UID_STATE_FOREGROUND = AppOpsManagerHidden.UID_STATE_FOREGROUND;
            UID_STATE_BACKGROUND = AppOpsManagerHidden.UID_STATE_BACKGROUND;
            UID_STATE_CACHED = AppOpsManagerHidden.UID_STATE_CACHED;
        } else {
            UID_STATE_PERSISTENT = 0;
            UID_STATE_TOP = 0;
            UID_STATE_FOREGROUND_SERVICE = 0;
            UID_STATE_FOREGROUND = 0;
            UID_STATE_BACKGROUND = 0;
            UID_STATE_CACHED = 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UID_STATE_FOREGROUND_SERVICE_LOCATION = AppOpsManagerHidden.UID_STATE_FOREGROUND_SERVICE_LOCATION;
            MAX_PRIORITY_UID_STATE = AppOpsManagerHidden.MAX_PRIORITY_UID_STATE;
            MIN_PRIORITY_UID_STATE = AppOpsManagerHidden.MIN_PRIORITY_UID_STATE;
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            UID_STATE_FOREGROUND_SERVICE_LOCATION = 0;
            MAX_PRIORITY_UID_STATE = UID_STATE_PERSISTENT;
            MIN_PRIORITY_UID_STATE = UID_STATE_CACHED;
        } else {
            UID_STATE_FOREGROUND_SERVICE_LOCATION = 0;
            MAX_PRIORITY_UID_STATE = 0;
            MIN_PRIORITY_UID_STATE = 0;
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface UidState {
    }

    private static final SparseArrayCompat<String> sModes = new SparseArrayCompat<>();
    private static final String[] sOpToString;

    public static final int OP_NONE = AppOpsManagerHidden.OP_NONE;
    /**
     * Control whether an application is allowed to run in the background.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    public static final int OP_RUN_IN_BACKGROUND = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? AppOpsManagerHidden.OP_RUN_IN_BACKGROUND : 0;
    /**
     * Run jobs when in background
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static final int OP_RUN_ANY_IN_BACKGROUND = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? AppOpsManagerHidden.OP_RUN_ANY_IN_BACKGROUND : 0;
    public static final int _NUM_OP = AppOpsManagerHidden._NUM_OP;
    /**
     * Mapping from a permission to the corresponding app op.
     */
    private static final HashMap<String, Integer> sPermToOp = new HashMap<>();
    /**
     * Some ops don't have any permissions associated with them and are enabled by default.
     * We are interested in the parents of these ops.
     */
    public static List<Integer> sOpWithoutPerms;

    static {
        String[] opToString = EmptyArray.STRING;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Only needed for API 22 and earlier
            try {
                Field sOpToStringField = AppOpsManagerHidden.class.getDeclaredField("sOpToString");
                sOpToStringField.setAccessible(true);
                opToString = (String[]) sOpToStringField.get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        sOpToString = opToString;

        for (Field field : AppOpsManager.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType() == int.class && field.getName().startsWith("MODE_")) {
                try {
                    sModes.put(field.getInt(null), field.getName());
                } catch (IllegalAccessException ignore) {
                }
            }
        }

        HashSet<Integer> opWithoutPerms = new HashSet<>();
        for (int i = 0; i < _NUM_OP; i++) {
            String permission = AppOpsManagerHidden.opToPermission(i);
            if (permission != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    // Only needed for API 22 and earlier
                    sPermToOp.put(permission, i);
                }
            } else {
                // No permission
                opWithoutPerms.add(AppOpsManagerHidden.opToSwitch(i));
            }
        }
        sOpWithoutPerms = new ArrayList<>(opWithoutPerms);
    }

    public static boolean isMiuiOp(int op) {
        try {
            return MiuiUtils.isMiui() && op > AppOpsManagerHidden.MIUI_OP_START;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    public static List<Integer> getAllOps() {
        List<Integer> appOps = new ArrayList<>();
        for (int i = 0; i < _NUM_OP; ++i) {
            appOps.add(i);
        }
        if (MiuiUtils.isMiui()) {
            try {
                for (int op = AppOpsManagerHidden.MIUI_OP_START + 1; op < AppOpsManagerHidden.MIUI_OP_END; ++op) {
                    appOps.add(op);
                }
            } catch (Exception ignore) {
            }
        }
        return appOps;
    }

    @NonNull
    public static List<Integer> getOpsWithoutPermissions() {
        return sOpWithoutPerms;
    }

    @NonNull
    public static List<Integer> getModeConstants() {
        return new ArrayList<Integer>(sModes.size()) {{
            for (int i = 0; i < sModes.size(); ++i) {
                add(sModes.keyAt(i));
            }
        }};
    }

    @NonNull
    public static String modeToName(@IntRange(from = -1) int mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return AppOpsManagerHidden.modeToName(mode);
        }
        // Fallback for pre28
        String fieldName = sModes.get(mode);
        if (fieldName == null) {
            return "mode=" + mode;
        }
        switch (mode) {
            case AppOpsManager.MODE_ALLOWED:
                return "allow";
            case AppOpsManager.MODE_IGNORED:
                return "ignore";
            case AppOpsManager.MODE_ERRORED:
                return "deny";
            // Rests have the same name as the constant name in lower case minus the MODE_ prefix
        }
        return fieldName.substring(5).toLowerCase(Locale.ROOT);
    }

    /**
     * Retrieve the op switch that controls the given operation.
     */
    public static int opToSwitch(int op) {
        return AppOpsManagerHidden.opToSwitch(op);
    }

    @NonNull
    public static String opToName(int op) {
        return AppOpsManagerHidden.opToName(op);
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     */
    @Nullable
    public static String opToPermission(int op) {
        return AppOpsManagerHidden.opToPermission(op);
    }

    /**
     * Retrieve the default mode for the operation.
     */
    public static int opToDefaultMode(int op) {
        return AppOpsManagerHidden.opToDefaultMode(op);
    }

    /**
     * Retrieve the app op code for a permission, or {@link #OP_NONE} if there is not one.
     * This API is intended to be used for mapping runtime or appop permissions
     * to the corresponding app op.
     */
    public static int permissionToOpCode(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return AppOpsManagerHidden.permissionToOpCode(permission);
        }
        // Fallback for Lollipop
        Integer boxedOpCode = sPermToOp.get(permission);
        if (boxedOpCode == null || boxedOpCode >= _NUM_OP) {
            return OP_NONE;
        }
        return boxedOpCode;
    }

    /**
     * Gets the app op name associated with a given permission.
     * The app op name is one of the public constants defined
     * in this class such as {@code #OPSTR_COARSE_LOCATION}.
     * This API is intended to be used for mapping runtime
     * permissions to the corresponding app op.
     *
     * @param permission The permission.
     * @return The app op associated with the permission or null.
     */
    @Nullable
    public static String permissionToOp(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return AppOpsManagerHidden.permissionToOp(permission);
        }
        // Fallback for Lollipop
        final int opCode = permissionToOpCode(permission);
        if (opCode == OP_NONE) {
            return null;
        }
        return sOpToString[opCode];
    }

    public static class PackageOps implements Parcelable {
        private final String mPackageName;
        private final int mUid;
        private final List<OpEntry> mEntries;

        public PackageOps(String packageName, int uid, List<OpEntry> entries) {
            mPackageName = packageName;
            mUid = uid;
            mEntries = entries;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public int getUid() {
            return mUid;
        }

        public List<OpEntry> getOps() {
            return mEntries;
        }

        @NonNull
        @Override
        public String toString() {
            return "PackageOps{" +
                    "mPackageName='" + mPackageName + '\'' +
                    ", mUid=" + mUid +
                    ", mEntries=" + mEntries +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(this.mPackageName);
            dest.writeInt(this.mUid);
            dest.writeTypedList(this.mEntries);
        }

        protected PackageOps(@NonNull Parcel in) {
            this.mPackageName = in.readString();
            this.mUid = in.readInt();
            this.mEntries = new ArrayList<>();
            in.readTypedList(this.mEntries, OpEntry.CREATOR);
        }

        public static final Parcelable.Creator<PackageOps> CREATOR = new Parcelable.Creator<PackageOps>() {
            @NonNull
            @Override
            public PackageOps createFromParcel(Parcel source) {
                return new PackageOps(source);
            }

            @NonNull
            @Override
            public PackageOps[] newArray(int size) {
                return new PackageOps[size];
            }
        };
    }

    public static class OpEntry implements Parcelable {
        private final AppOpsManagerHidden.OpEntry opEntry;

        public OpEntry(Parcelable opEntry) {
            this.opEntry = Refine.unsafeCast(opEntry);
        }

        protected OpEntry(Parcel in) {
            opEntry = AppOpsManagerHidden.OpEntry.CREATOR.createFromParcel(in);
        }

        public static final Creator<OpEntry> CREATOR = new Creator<OpEntry>() {
            @NonNull
            @Override
            public OpEntry createFromParcel(Parcel in) {
                return new OpEntry(in);
            }

            @NonNull
            @Override
            public OpEntry[] newArray(int size) {
                return new OpEntry[size];
            }
        };

        public int getOp() {
            return opEntry.getOp();
        }

        @NonNull
        public String getName() {
            return opToName(getOp());
        }

        @Nullable
        public String getPermission() {
            return opToPermission(getOp());
        }

        @Mode
        public int getMode() {
            return opEntry.getMode();
        }

        @Mode
        public int getDefaultMode() {
            return opToDefaultMode(getOp());
        }

        public long getTime() {
            return getLastAccessTime(OP_FLAGS_ALL);
        }

        public long getLastAccessTime(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastAccessTime(flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastAccessTime();
            }
            return opEntry.getTime();
        }

        public long getLastAccessForegroundTime(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastAccessForegroundTime(flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastAccessForegroundTime();
            } else return opEntry.getTime();
        }

        public long getLastAccessBackgroundTime(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastAccessBackgroundTime(flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastAccessBackgroundTime();
            } else return opEntry.getTime();
        }

        public long getLastAccessTime(@UidState int fromUidState,
                                      @UidState int toUidState,
                                      @OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastAccessTime(fromUidState, toUidState, flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastTimeFor(fromUidState);
            } else return opEntry.getTime();
        }

        public long getRejectTime() {
            return getLastRejectTime(OP_FLAGS_ALL);
        }

        public long getLastRejectTime(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastRejectTime(flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastRejectTime();
            } else return opEntry.getRejectTime();
        }

        public long getLastRejectForegroundTime(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastRejectForegroundTime(flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastRejectForegroundTime();
            } else return opEntry.getRejectTime();
        }

        public long getLastRejectBackgroundTime(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastRejectBackgroundTime(flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
                return opEntry.getLastRejectBackgroundTime();
            } else return opEntry.getRejectTime();
        }

        public long getLastRejectTime(@UidState int fromUidState,
                                      @UidState int toUidState,
                                      @OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastRejectTime(fromUidState, toUidState, flags);
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
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
        public long getLastDuration(@OpFlags int flags) {
            return opEntry.getLastDuration(flags);
        }

        public long getLastForegroundDuration(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastForegroundDuration(flags);
            } else return opEntry.getDuration();
        }

        public long getLastBackgroundDuration(@OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getLastBackgroundDuration(flags);
            } else return opEntry.getDuration();
        }

        public long getLastDuration(@UidState int fromUidState,
                                    @UidState int toUidState,
                                    @OpFlags int flags) {
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
        public int getProxyUid(@UidState int uidState, @OpFlags int flags) {
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
        public String getProxyPackageName(@UidState int uidState, @OpFlags int flags) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return opEntry.getProxyPackageName(uidState, flags);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return opEntry.getProxyPackageName();
            }
            return null;
        }

        // TODO(24/12/20): Get proxy info (From API 30)


        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(opEntry, flags);
        }
    }

    public static int getModeFromOpEntriesOrDefault(int op, @Nullable List<OpEntry> opEntries) {
        if (op <= OP_NONE || op >= _NUM_OP || opEntries == null) {
            return AppOpsManager.MODE_IGNORED;
        }
        for (OpEntry opEntry : opEntries) {
            if (opEntry.getOp() == op) {
                return opEntry.getMode();
            }
        }
        return opToDefaultMode(op);
    }

    @NonNull
    public static List<OpEntry> getConfiguredOpsForPackage(@NonNull AppOpsManagerCompat appOpsManager,
                                                                               @NonNull String packageName, int uid)
            throws RemoteException {
        List<PackageOps> packageOpsList = appOpsManager.getOpsForPackage(uid, packageName, null);
        if (packageOpsList.size() == 1) {
            return packageOpsList.get(0).getOps();
        }
        return Collections.emptyList();
    }

    private final IAppOpsService appOpsService;

    public AppOpsManagerCompat(Context context) {
        if (!PermissionUtils.hasAppOpsPermission() && Ops.isReallyPrivileged()) {
            try {
                PermissionCompat.grantPermission(
                        context.getPackageName(),
                        ManifestCompat.permission.GET_APP_OPS_STATS,
                        UserHandleHidden.myUserId());
            } catch (RemoteException e) {
                throw new RuntimeException("Couldn't grant " + ManifestCompat.permission.GET_APP_OPS_STATS, e);
            }
        }
        // Local/remote services are handled automatically
        this.appOpsService = IAppOpsService.Stub.asInterface(ProxyBinder.getService(Context.APP_OPS_SERVICE));
    }

    /**
     * Get the mode of operation of the given package or uid. This denotes the actual working state which is not
     * necessarily the same mode set using {@link #setMode(int, int, String, int)}.
     *
     * @param op          One of the OP_*
     * @param uid         User ID for the package(s)
     * @param packageName Name of the package
     * @return One of the MODE_*
     */
    @AppOpsManagerCompat.Mode
    public int checkOperation(int op, int uid, String packageName) throws RemoteException {
        return appOpsService.checkOperation(op, uid, packageName);
    }

    public List<AppOpsManagerCompat.PackageOps> getOpsForPackage(int uid, String packageName, @Nullable int[] ops)
            throws RemoteException {
        // Check using uid mode and package mode, override ops in package mode from uid mode
        List<AppOpsManagerCompat.OpEntry> opEntries = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                addAllRelevantOpEntriesWithNoOverride(opEntries, appOpsService.getUidOps(uid, ops));
            } catch (NullPointerException e) {
                Log.e("AppOpsManagerCompat", "Could not get app ops for UID " + uid, e);
            }
        }
        addAllRelevantOpEntriesWithNoOverride(opEntries, appOpsService.getOpsForPackage(uid, packageName, ops));
        return Collections.singletonList(new AppOpsManagerCompat.PackageOps(packageName, uid, opEntries));
    }

    @NonNull
    public List<AppOpsManagerCompat.PackageOps> getPackagesForOps(int[] ops) throws RemoteException {
        List<Parcelable> opsForPackage = appOpsService.getPackagesForOps(ops);
        List<AppOpsManagerCompat.PackageOps> packageOpsList = new ArrayList<>();
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                AppOpsManagerCompat.PackageOps packageOps = opsConvert(Refine.unsafeCast(o));
                packageOpsList.add(packageOps);
            }
        }
        return packageOpsList;
    }

    public void setMode(int op, int uid, String packageName, @AppOpsManagerCompat.Mode int mode)
            throws RemoteException {
        if (AppOpsManagerCompat.isMiuiOp(op) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Only package mode works in MIUI-only app ops and before Android M
            appOpsService.setMode(op, uid, packageName, mode);
        } else {
            // Set UID mode
            appOpsService.setUidMode(op, uid, mode);
        }
    }

    public void resetAllModes(@UserIdInt int reqUserId, @NonNull String reqPackageName) throws RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            appOpsService.resetAllModes(reqUserId, reqPackageName);
        }
    }

    private static void addAllRelevantOpEntriesWithNoOverride(final List<AppOpsManagerCompat.OpEntry> opEntries,
                                                              @Nullable final List<Parcelable> opsForPackage) {
        if (opsForPackage != null) {
            for (Parcelable o : opsForPackage) {
                AppOpsManagerCompat.PackageOps packageOps = opsConvert(Refine.unsafeCast(o));
                for (AppOpsManagerCompat.OpEntry opEntry : packageOps.getOps()) {
                    if (!opEntries.contains(opEntry)) {
                        opEntries.add(opEntry);
                    }
                }
            }
        }
    }

    @NonNull
    private static AppOpsManagerCompat.PackageOps opsConvert(@NonNull AppOpsManagerHidden.PackageOps packageOps) {
        String packageName = packageOps.getPackageName();
        int uid = packageOps.getUid();
        List<AppOpsManagerCompat.OpEntry> opEntries = new ArrayList<>();
        for (Parcelable opEntry : packageOps.getOps()) {
            opEntries.add(new AppOpsManagerCompat.OpEntry(opEntry));
        }
        return new AppOpsManagerCompat.PackageOps(packageName, uid, opEntries);
    }
}
