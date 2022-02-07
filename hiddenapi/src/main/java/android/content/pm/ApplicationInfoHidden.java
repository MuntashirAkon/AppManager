// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(ApplicationInfo.class)
public class ApplicationInfoHidden {
    /**
     * Private/hidden flags. See {@code PRIVATE_FLAG_...} constants.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public int privateFlags;

    /**
     * String retrieved from the seinfo tag found in selinux policy. This value can be set through
     * the mac_permissions.xml policy construct. This value is used for setting an SELinux security
     * context on the process as well as its data directory.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public String seInfo;

    /**
     * String retrieved from the seinfo tag found in selinux policy. This value
     * can be overridden with a value set through the mac_permissions.xml policy
     * construct. This value is useful in setting an SELinux security context on
     * the process as well as its data directory. The String default is being used
     * here to represent a catchall label when no policy matches.
     *
     * @deprecated Replaced with {@link #seInfo} in Android 8 (Oreo)
     */
    @Deprecated
    public String seinfo = "default";

    /**
     * The seinfo tag generated per-user. This value may change based upon the
     * user's configuration. For example, when an instant app is installed for
     * a user. It is an error if this field is ever {@code null} when trying to
     * start a new process.
     * <p>NOTE: We need to separate this out because we modify per-user values
     * multiple times. This needs to be refactored since we're performing more
     * work than necessary and these values should only be set once. When that
     * happens, we can merge the per-user value with the seInfo state above.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    public String seInfoUser;

    /**
     * The primary ABI that this application requires, This is inferred from the ABIs
     * of the native JNI libraries the application bundles. Will be {@code null}
     * if this application does not require any particular ABI.
     * <p>
     * If non-null, the application will always be launched with this ABI.
     */
    @Nullable
    public String primaryCpuAbi;

    @RequiresApi(Build.VERSION_CODES.Q)
    @Nullable
    public String zygotePreloadName;

    @RequiresApi(Build.VERSION_CODES.P)
    public int getHiddenApiEnforcementPolicy() {
        return HiddenUtil.throwUOE();
    }
}
