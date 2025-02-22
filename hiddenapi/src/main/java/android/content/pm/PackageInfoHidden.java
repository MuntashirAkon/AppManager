// SPDX-License-Identifier: GPL-3.0-or-later

package android.content.pm;

import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(PackageInfo.class)
public class PackageInfoHidden {
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    public boolean isStub;

    public boolean coreApp;

    public boolean requiredForAllUsers;

    public String restrictedAccountType;

    public String requiredAccountType;

    /**
     * What package, if any, this package will overlay.
     * <p>
     * Package name of target package, or null.
     */
    @Nullable
    public String overlayTarget;

    /**
     * The name of the overlayable set of elements package, if any, this package will overlay.
     * <p>
     * Overlayable name defined within the target package, or null.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Nullable
    public String targetOverlayableName;

    /**
     * The overlay category, if any, of this package
     */
    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    public String overlayCategory;

    @RequiresApi(Build.VERSION_CODES.O)
    public int overlayPriority;

    /**
     * @deprecated Replaced by {@link #overlayFlags} in Android 8.0.0_r37 and in Android 8.1.0_r15
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Deprecated
    public boolean isStaticOverlay;

    /**
     * Flag for use with {@link #overlayFlags}. Marks the overlay as static, meaning it cannot
     * be enabled/disabled at runtime.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public static final int FLAG_OVERLAY_STATIC = 1 << 1;

    /**
     * Flag for use with {@link #overlayFlags}. Marks the overlay as trusted (not 3rd party).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public static final int FLAG_OVERLAY_TRUSTED = 1 << 2;

    /**
     * Modifiers that affect the state of this overlay. See {@link #FLAG_OVERLAY_STATIC},
     * {@link #FLAG_OVERLAY_TRUSTED}.
     *
     * @deprecated Replaced in Android 9.0 by {@link #isStaticOverlayPackage()}
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Deprecated
    public int overlayFlags;

    /**
     * Whether the overlay is static, meaning it cannot be enabled/disabled at runtime.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public boolean mOverlayIsStatic;

    /**
     * Returns true if the package is a valid static Runtime Overlay package. Static overlays
     * are not updatable outside of a system update and are safe to load in the system process.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    public boolean isStaticOverlayPackage() {
        return HiddenUtil.throwUOE();
    }
}
