// SPDX-License-Identifier: GPL-3.0-or-later

package android.content;

import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(Intent.class)
public class IntentHidden {
    @IntDef(flag = true, value = {
            EXTENDED_FLAG_FILTER_MISMATCH,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExtendedFlags {}

    /**
     * This flag is not normally set by application code, but set for you by the system if
     * an external intent does not match the receiving component's intent filter.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 r50
    public static final int EXTENDED_FLAG_FILTER_MISMATCH = 1 << 0;


    /**
     * Retrieve any extended flags associated with this intent.  You will
     * normally just set them with {@code #setExtendedFlags} and let the system
     * take the appropriate action with them.
     *
     * @return The currently set extended flags.
     * @see #addExtendedFlags
     * @see #removeExtendedFlags
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 r50
    public @ExtendedFlags int getExtendedFlags() {
        return HiddenUtil.throwUOE();
    }


    /**
     * Add additional extended flags to the intent (or with existing flags value).
     *
     * @param flags The new flags to set.
     * @return Returns the same Intent object, for chaining multiple calls into
     *         a single statement.
     * @see #getExtendedFlags
     * @see #removeExtendedFlags
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 r50
    public @NonNull Intent addExtendedFlags(@ExtendedFlags int flags) {
        return HiddenUtil.throwUOE(flags);
    }


    /**
     * Remove these extended flags from the intent.
     *
     * @param flags The flags to remove.
     * @see #getExtendedFlags
     * @see #addExtendedFlags
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Android 14 r50
    public void removeExtendedFlags(@ExtendedFlags int flags) {
        HiddenUtil.throwUOE(flags);
    }
}
