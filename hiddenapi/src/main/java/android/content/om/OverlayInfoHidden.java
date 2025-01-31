package android.content.om;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(OverlayInfo.class)
public class OverlayInfoHidden {
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
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {}

    /**
     * An internal state used as the initial state of an overlay. OverlayInfo
     * objects exposed outside the {@link
     *
     *
     *
     */
    public static final int STATE_UNKNOWN = -1;

    /**
     * The target package of the overlay is not installed. The overlay cannot be enabled.
     *
     */
    public static final int STATE_MISSING_TARGET = 0;

    /**
     * Creation of idmap file failed (e.g. no matching resources). The overlay
     * cannot be enabled.
     *
     */
    public static final int STATE_NO_IDMAP = 1;

    /**
     * The overlay is currently disabled. It can be enabled.
     *
     *
     */
    public static final int STATE_DISABLED = 2;

    /**
     * The overlay is currently enabled. It can be disabled.
     *
     *
     */
    public static final int STATE_ENABLED = 3;

    /**
     * The target package is currently being upgraded or downgraded; the state
     * will change once the package installation has finished.
     * @hide
     *
     * @deprecated No longer used. Caused invalid transitions from enabled -> upgrading -> enabled,
     * where an update is propagated when nothing has changed. Can occur during --dont-kill
     * installs when code and resources are hot swapped and the Activity should not be relaunched.
     * In all other cases, the process and therefore Activity is killed, so the state loop is
     * irrelevant.
     */
    @Deprecated
    public static final int STATE_TARGET_IS_BEING_REPLACED = 4;

    /**
     * The overlay package is currently being upgraded or downgraded; the state
     * will change once the package installation has finished.
     * @hide
     */
    public static final int STATE_OVERLAY_IS_BEING_REPLACED = 5;

    /**
     * The overlay package is currently enabled because it is marked as
     * 'immutable'. It cannot be disabled but will change state if for instance
     * its target is uninstalled.
     * @hide
     */
    @Deprecated
    public static final int STATE_ENABLED_IMMUTABLE = 6;

    /**
     * The target package needs to be refreshed as a result of a system update uninstall, which
     * must recalculate the state of overlays against the newly enabled system package, which may
     * differ in resources/policy from the /data variant that was uninstalled.
     * @hide
     */
    public static final int STATE_SYSTEM_UPDATE_UNINSTALL = 7;

    @NonNull
    public String getPackageName() {
        return "";
    }

    @Nullable
    public String getCategory() {
        return "";
    }

    public boolean isEnabled() {
        return false;
    }

    public static String stateToString(@State int state) {
        return "";
    }
}
