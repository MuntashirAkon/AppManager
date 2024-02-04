// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import android.content.pm.ApplicationInfo;
import android.content.pm.ApplicationInfoHidden;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.io.Paths;

public final class ApplicationInfoCompat {
    /**
     * Value for {@code #privateFlags}: true if the application is hidden via restrictions and for
     * most purposes is considered as not installed.
     */
    public static final int PRIVATE_FLAG_HIDDEN = 1;

    /**
     * Value for {@code #privateFlags}: set to <code>true</code> if the application
     * has reported that it is heavy-weight, and thus can not participate in
     * the normal application lifecycle.
     *
     * <p>Comes from the
     * android.R.styleable#AndroidManifestApplication_cantSaveState
     * attribute of the &lt;application&gt; tag.
     */
    public static final int PRIVATE_FLAG_CANT_SAVE_STATE = 1 << 1;

    /**
     * Value for {@code #privateFlags}: set to {@code true} if the application
     * is permitted to hold privileged permissions.
     */
    public static final int PRIVATE_FLAG_PRIVILEGED = 1 << 3;

    /**
     * Value for {@code #privateFlags}: {@code true} if the application has any IntentFiler
     * with some data URI using HTTP or HTTPS with an associated VIEW action.
     */
    public static final int PRIVATE_FLAG_HAS_DOMAIN_URLS = 1 << 4;

    /**
     * When set, the default data storage directory for this app is pointed at
     * the device-protected location.
     */
    public static final int PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE = 1 << 5;

    /**
     * When set, assume that all components under the given app are direct boot
     * aware, unless otherwise specified.
     */
    public static final int PRIVATE_FLAG_DIRECT_BOOT_AWARE = 1 << 6;

    /**
     * Value for {@code #privateFlags}: {@code true} if the application is installed
     * as instant app.
     */
    public static final int PRIVATE_FLAG_INSTANT = 1 << 7;

    /**
     * When set, at least one component inside this application is direct boot
     * aware.
     */
    public static final int PRIVATE_FLAG_PARTIALLY_DIRECT_BOOT_AWARE = 1 << 8;


    /**
     * When set, signals that the application is required for the system user and should not be
     * uninstalled.
     */
    public static final int PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER = 1 << 9;

    /**
     * When set, the application explicitly requested that its activities be resizeable by default.
     * {@code android.R.styleable#AndroidManifestActivity_resizeableActivity}
     */
    public static final int PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE = 1 << 10;

    /**
     * When set, the application explicitly requested that its activities *not* be resizeable by
     * default.
     * {@code android.R.styleable#AndroidManifestActivity_resizeableActivity}
     */
    public static final int PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE = 1 << 11;

    /**
     * The application isn't requesting explicitly requesting for its activities to be resizeable or
     * non-resizeable by default. So, we are making it activities resizeable by default based on the
     * target SDK version of the app.
     * {@code android.R.styleable#AndroidManifestActivity_resizeableActivity}
     * <p>
     * NOTE: This only affects apps with target SDK >= N where the resizeableActivity attribute was
     * introduced. It shouldn't be confused with {@code ActivityInfo#RESIZE_MODE_FORCE_RESIZEABLE}
     * where certain pre-N apps are forced to the resizeable.
     */
    public static final int PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION = 1 << 12;

    /**
     * Value for {@code #privateFlags}: {@code true} means the OS should go ahead and
     * run full-data backup operations for the app even when it is in a
     * foreground-equivalent run state.  Defaults to {@code false} if unspecified.
     */
    public static final int PRIVATE_FLAG_BACKUP_IN_FOREGROUND = 1 << 13;

    /**
     * Value for {@code #privateFlags}: {@code true} means this application
     * contains a static shared library. Defaults to {@code false} if unspecified.
     */
    public static final int PRIVATE_FLAG_STATIC_SHARED_LIBRARY = 1 << 14;

    /**
     * Value for {@code #privateFlags}: When set, the application will only have its splits loaded
     * if they are required to load a component. Splits can be loaded on demand using the
     * {@code Context#createContextForSplit(String)} API.
     */
    public static final int PRIVATE_FLAG_ISOLATED_SPLIT_LOADING = 1 << 15;

    /**
     * Value for {@code #privateFlags}: When set, the application was installed as
     * a virtual preload.
     */
    public static final int PRIVATE_FLAG_VIRTUAL_PRELOAD = 1 << 16;

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * OEM partition of the system image.
     */
    public static final int PRIVATE_FLAG_OEM = 1 << 17;

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * vendor partition of the system image.
     */
    public static final int PRIVATE_FLAG_VENDOR = 1 << 18;

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * product partition of the system image.
     */
    public static final int PRIVATE_FLAG_PRODUCT = 1 << 19;

    /**
     * Value for {@code #privateFlags}: whether this app is signed with the
     * platform key.
     */
    public static final int PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY = 1 << 20;

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * system_ext partition of the system image.
     */
    public static final int PRIVATE_FLAG_SYSTEM_EXT = 1 << 21;

    /**
     * Indicates whether this package requires access to non-SDK APIs.
     * Only system apps and tests are allowed to use this property.
     */
    public static final int PRIVATE_FLAG_USES_NON_SDK_API = 1 << 22;

    /**
     * Indicates whether this application can be profiled by the shell user,
     * even when running on a device that is running in user mode.
     */
    public static final int PRIVATE_FLAG_PROFILEABLE_BY_SHELL = 1 << 23;

    /**
     * Indicates whether this package requires access to non-SDK APIs.
     * Only system apps and tests are allowed to use this property.
     */
    public static final int PRIVATE_FLAG_HAS_FRAGILE_USER_DATA = 1 << 24;

    /**
     * Indicates whether this application wants to use the embedded dex in the APK, rather than
     * extracted or locally compiled variants. This keeps the dex code protected by the APK
     * signature. Such apps will always run in JIT mode (same when they are first installed), and
     * the system will never generate ahead-of-time compiled code for them. Depending on the app's
     * workload, there may be some run time performance change, noteably the cold start time.
     */
    public static final int PRIVATE_FLAG_USE_EMBEDDED_DEX = 1 << 25;

    /**
     * Value for {@code #privateFlags}: indicates whether this application's data will be cleared
     * on a failed restore.
     *
     * <p>Comes from the
     * android.R.styleable#AndroidManifestApplication_allowClearUserDataOnFailedRestore attribute
     * of the &lt;application&gt; tag.
     */
    public static final int PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE = 1 << 26;

    /**
     * Value for {@code #privateFlags}: true if the application allows its audio playback
     * to be captured by other apps.
     */
    public static final int PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE = 1 << 27;

    /**
     * Indicates whether this package is in fact a runtime resource overlay.
     */
    public static final int PRIVATE_FLAG_IS_RESOURCE_OVERLAY = 1 << 28;

    /**
     * Value for {@code #privateFlags}: If {@code true} this app requests
     * full external storage access. The request may not be honored due to
     * policy or other reasons.
     */
    public static final int PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE = 1 << 29;

    /**
     * Value for {@code #privateFlags}: whether this app is pre-installed on the
     * ODM partition of the system image.
     */
    public static final int PRIVATE_FLAG_ODM = 1 << 30;

    /**
     * Value for {@code #privateFlags}: If {@code true} this app allows heap tagging.
     * {@code com.android.server.am.ProcessList#NATIVE_HEAP_POINTER_TAGGING}
     */
    public static final int PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING = 1 << 31;

    @IntDef(flag = true, value = {
            PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE,
            PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE_VIA_SDK_VERSION,
            PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE,
            PRIVATE_FLAG_BACKUP_IN_FOREGROUND,
            PRIVATE_FLAG_CANT_SAVE_STATE,
            PRIVATE_FLAG_DEFAULT_TO_DEVICE_PROTECTED_STORAGE,
            PRIVATE_FLAG_DIRECT_BOOT_AWARE,
            PRIVATE_FLAG_HAS_DOMAIN_URLS,
            PRIVATE_FLAG_HIDDEN,
            PRIVATE_FLAG_INSTANT,
            PRIVATE_FLAG_IS_RESOURCE_OVERLAY,
            PRIVATE_FLAG_ISOLATED_SPLIT_LOADING,
            PRIVATE_FLAG_OEM,
            PRIVATE_FLAG_PARTIALLY_DIRECT_BOOT_AWARE,
            PRIVATE_FLAG_USE_EMBEDDED_DEX,
            PRIVATE_FLAG_PRIVILEGED,
            PRIVATE_FLAG_PRODUCT,
            PRIVATE_FLAG_SYSTEM_EXT,
            PRIVATE_FLAG_PROFILEABLE_BY_SHELL,
            PRIVATE_FLAG_REQUIRED_FOR_SYSTEM_USER,
            PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY,
            PRIVATE_FLAG_STATIC_SHARED_LIBRARY,
            PRIVATE_FLAG_VENDOR,
            PRIVATE_FLAG_VIRTUAL_PRELOAD,
            PRIVATE_FLAG_HAS_FRAGILE_USER_DATA,
            PRIVATE_FLAG_ALLOW_CLEAR_USER_DATA_ON_FAILED_RESTORE,
            PRIVATE_FLAG_ALLOW_AUDIO_PLAYBACK_CAPTURE,
            PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE,
            PRIVATE_FLAG_ODM,
            PRIVATE_FLAG_ALLOW_NATIVE_HEAP_POINTER_TAGGING,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ApplicationInfoPrivateFlags {
    }

    /**
     * Represents the default policy. The actual policy used will depend on other properties of
     * the application, e.g. the target SDK version.
     */
    public static final int HIDDEN_API_ENFORCEMENT_DEFAULT = -1;
    /**
     * No API enforcement; the app can access the entire internal private API. Only for use by
     * system apps.
     */
    public static final int HIDDEN_API_ENFORCEMENT_DISABLED = 0;
    /**
     * No API enforcement, but enable the detection logic and warnings. Observed behaviour is the
     * same as {@link #HIDDEN_API_ENFORCEMENT_DISABLED} but you may see warnings in the log when
     * APIs are accessed.
     */
    public static final int HIDDEN_API_ENFORCEMENT_JUST_WARN = 1;
    /**
     * Dark grey list enforcement. Enforces the dark grey and black lists
     */
    public static final int HIDDEN_API_ENFORCEMENT_ENABLED = 2;
    /**
     * Blacklist enforcement only.
     */
    public static final int HIDDEN_API_ENFORCEMENT_BLACK = 3;

    @ApplicationInfoPrivateFlags
    public static int getPrivateFlags(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Refine.<ApplicationInfoHidden>unsafeCast(info).privateFlags;
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    public static String getSeInfo(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Refine.<ApplicationInfoHidden>unsafeCast(info).seInfo + Refine.<ApplicationInfoHidden>unsafeCast(info).seInfoUser;
        } else return Refine.<ApplicationInfoHidden>unsafeCast(info).seinfo;
    }

    @Nullable
    public static String getPrimaryCpuAbi(@NonNull ApplicationInfo info) {
        return Refine.<ApplicationInfoHidden>unsafeCast(info).primaryCpuAbi;
    }

    @Nullable
    public static String getZygotePreloadName(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Refine.<ApplicationInfoHidden>unsafeCast(info).zygotePreloadName;
        }
        return null;
    }

    public static int getHiddenApiEnforcementPolicy(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Refine.<ApplicationInfoHidden>unsafeCast(info).getHiddenApiEnforcementPolicy();
        }
        return HIDDEN_API_ENFORCEMENT_DISABLED;
    }

    public static boolean isSystemApp(@NonNull ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    public static boolean isStopped(@NonNull ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_STOPPED) != 0;
    }

    public static boolean isInstalled(@NonNull ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0
                && info.processName != null
                && Paths.exists(info.publicSourceDir);
    }

    public static boolean isTestOnly(@NonNull ApplicationInfo info) {
        return (info.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
    }

    public static boolean isSuspended(@NonNull ApplicationInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return (info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0;
        }
        // Not supported
        return false;
    }

    public static boolean isHidden(@NonNull ApplicationInfo info) {
        return (getPrivateFlags(info) & PRIVATE_FLAG_HIDDEN) != 0;
    }

    public static boolean isStaticSharedLibrary(@NonNull ApplicationInfo info) {
        // Android 8+
        return (getPrivateFlags(info) & PRIVATE_FLAG_STATIC_SHARED_LIBRARY) != 0;
    }

    public static boolean isPrivileged(@NonNull ApplicationInfo info) {
        return (getPrivateFlags(info) & PRIVATE_FLAG_PRIVILEGED) != 0;
    }

    public static boolean hasDomainUrls(@NonNull ApplicationInfo info) {
        return (getPrivateFlags(info) & PRIVATE_FLAG_HAS_DOMAIN_URLS) != 0;
    }

    /**
     * {@link ApplicationInfo#loadLabel(PackageManager)} can throw NPE for uninstalled apps in unprivileged mode.
     *
     * @return App label or package name if an error is occurred.
     */
    @NonNull
    public static CharSequence loadLabelSafe(@NonNull ApplicationInfo info, @NonNull PackageManager pm) {
        if (Paths.exists(info.publicSourceDir)) {
            return info.loadLabel(pm);
        }
        return info.packageName;
    }
}
