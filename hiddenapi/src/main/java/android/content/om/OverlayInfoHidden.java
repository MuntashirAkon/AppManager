package android.content.om;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

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
    public static final int STATE_UNKNOWN = -1;
    public static final int STATE_MISSING_TARGET = 0;
    public static final int STATE_NO_IDMAP = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_ENABLED = 3;
    @Deprecated
    public static final int STATE_TARGET_IS_BEING_REPLACED = 4;
    public static final int STATE_OVERLAY_IS_BEING_REPLACED = 5;
    @Deprecated
    public static final int STATE_ENABLED_IMMUTABLE = 6;
    public static final int STATE_SYSTEM_UPDATE_UNINSTALL = 7;

    public final boolean isMutable = false;
    public final @State int state = STATE_UNKNOWN;
    public final int priority = -1;

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

    @NonNull
    @Contract(pure = true)
    public static String stateToString(@State int state) {
        return "";
    }
}
