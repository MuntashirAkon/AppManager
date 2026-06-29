// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;
import misc.utils.VersionCodes;

/**
 * A list of {@link PackageInfo}s that can be parcelled.
 *
 * <p>This class is used to parcel a list of {@link PackageInfo}s efficiently, deduping common
 * strings that are reused across packages and paginating a list that is too large.
 */
@RequiresApi(VersionCodes.CINNAMON_BUN)
public final class PackageInfoList extends ParceledListSlice<PackageInfo> {

    /**
     * Returns an empty {@link PackageInfoList}.
     */
    public static PackageInfoList emptyList() {
        return HiddenUtil.throwUOE();
    }
}