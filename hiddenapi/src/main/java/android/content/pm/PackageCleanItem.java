// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Parcelable;

import misc.utils.HiddenUtil;

public class PackageCleanItem implements Parcelable {
    public final int userId;
    public final String packageName;
    public final boolean andCode;

    public PackageCleanItem(int userId, String packageName, boolean andCode) {
        HiddenUtil.throwUOE(userId, packageName, andCode);
        throw new UnsupportedOperationException();
    }
}
