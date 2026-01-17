// SPDX-License-Identifier: Apache-2.0

package android.content.om;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

/**
 * An immutable information about an overlay.
 *
 * <p>Applications calling {@link OverlayManager#getOverlayInfosForTarget(String)} get the
 * information list of the registered overlays. Each element in the list presents the information of
 * the particular overlay.
 *
 * <p>Immutable overlay information about a package. All PackageInfos that
 * represent an overlay package will have a corresponding OverlayInfo.
 *
 * @see OverlayManager#getOverlayInfosForTarget(String)
 */
@RequiresApi(api = Build.VERSION_CODES.O)
@RefineAs(OverlayInfo.class)
public final class OverlayInfoHidden {
    @SuppressLint("InlinedApi")
    @IntDef(value = {
            STATE_UNKNOWN,
            STATE_MISSING_TARGET,
            STATE_NO_IDMAP,
            STATE_DISABLED,
            STATE_ENABLED,
            STATE_ENABLED_IMMUTABLE,
            STATE_OVERLAY_IS_BEING_REPLACED,
            STATE_SYSTEM_UPDATE_UNINSTALL,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    /**
     * An internal state used as the initial state of an overlay. OverlayInfo
     * objects exposed outside the {@code
     * com.android.server.om.OverlayManagerService} should never have this
     * state.
     */
    public static final int STATE_UNKNOWN = -1;

    /**
     * The target package of the overlay is not installed. The overlay cannot be enabled.
     */
    public static final int STATE_MISSING_TARGET = 0;

    /**
     * Creation of idmap file failed (e.g. no matching resources). The overlay
     * cannot be enabled.
     */
    public static final int STATE_NO_IDMAP = 1;

    /**
     * The overlay is currently disabled. It can be enabled.
     *
     * @see IOverlayManager#setEnabled
     */
    public static final int STATE_DISABLED = 2;

    /**
     * The overlay is currently enabled. It can be disabled.
     *
     * @see IOverlayManager#setEnabled
     */
    public static final int STATE_ENABLED = 3;

    /**
     * The target package is currently being upgraded or downgraded; the state
     * will change once the package installation has finished.
     *
     * @deprecated No longer used. Caused invalid transitions from enabled -> upgrading -> enabled,
     * where an update is propagated when nothing has changed. Can occur during --dont-kill
     * installs when code and resources are hot swapped and the Activity should not be relaunched.
     * In all other cases, the process and therefore Activity is killed, so the state loop is
     * irrelevant.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @Deprecated//SinceApi(api = Build.VERSION_CODES.Q)
    public static final int STATE_TARGET_IS_BEING_REPLACED = 4;
    /**
     * The overlay package is currently being upgraded or downgraded; the state
     * will change once the package installation has finished.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static final int STATE_OVERLAY_IS_BEING_REPLACED = 5;

    /**
     * The overlay package is currently enabled because it is marked as
     * 'immutable'. It cannot be disabled but will change state if for instance
     * its target is uninstalled.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @Deprecated//SinceApi(api = Build.VERSION_CODES.R)
    public static final int STATE_ENABLED_IMMUTABLE = 6;

    /**
     * The target package needs to be refreshed as a result of a system update uninstall, which
     * must recalculate the state of overlays against the newly enabled system package, which may
     * differ in resources/policy from the /data variant that was uninstalled.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final int STATE_SYSTEM_UPDATE_UNINSTALL = 7;

    /**
     * Overlay category: theme.
     * <p>
     * Change how Android (including the status bar, dialogs, ...) looks.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public static final String CATEGORY_THEME = "android.theme";

    /**
     * Package name of the overlay package
     */
    @NonNull
    public final String packageName = HiddenUtil.throwUOE();

    /**
     * The unique name within the package of the overlay.
     */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.S)
    public final String overlayName = HiddenUtil.throwUOE();

    /**
     * Package name of the target package
     */
    @NonNull
    public final String targetPackageName = HiddenUtil.throwUOE();

    /**
     * Name of the target overlayable declaration.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Nullable
    public final String targetOverlayableName = HiddenUtil.throwUOE();

    /**
     * Category of the overlay package
     */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.P)
    public final String category = HiddenUtil.throwUOE();

    /**
     * Full path to the base APK for this overlay package
     */
    @NonNull
    public final String baseCodePath = HiddenUtil.throwUOE();

    /**
     * The state of this OverlayInfo as defined by the STATE_* constants in this class.
     */
    public final @State int state = HiddenUtil.throwUOE();

    /**
     * User handle for which this overlay applies
     */
    public final int userId = HiddenUtil.throwUOE();

    /**
     * Priority as configured by {@code com.android.internal.content.om.OverlayConfig}.
     * Not intended to be exposed to 3rd party.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public final int priority = HiddenUtil.throwUOE();

    /**
     * isMutable as configured by {@code com.android.internal.content.om.OverlayConfig}.
     * If false, the overlay is unconditionally loaded and cannot be unloaded. Not intended to be
     * exposed to 3rd party.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public final boolean isMutable = HiddenUtil.throwUOE();
    /**
     * isFabricated if this Overlay was made by the shell/ some SystemUI "theme" loaders, also
     * this value is not updated nor tracked outside of the real manager so this value can't be trusted.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    public final boolean isFabricated = HiddenUtil.throwUOE();


    /**
     * Return true if this overlay is enabled, i.e. should be used to overlay
     * the resources in the target package.
     * <p>
     * Disabled overlay packages are installed but are currently not in use.
     *
     * @return true if the overlay is enabled, else false.
     */

    public boolean isEnabled() {
        return HiddenUtil.throwUOE();
    }

    /**
     * Translate a state to a human readable string. Only intended for
     * debugging purposes.
     *
     * @return a human readable String representing the state.
     */
    public static String stateToString(@State int state) {
        return HiddenUtil.throwUOE(state);
    }
}
