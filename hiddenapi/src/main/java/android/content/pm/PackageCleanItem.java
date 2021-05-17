// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Parcelable;

public class PackageCleanItem implements Parcelable {
    public final int userId;
    public final String packageName;
    public final boolean andCode;

    public PackageCleanItem(int userId, String packageName, boolean andCode) {
        throw new UnsupportedOperationException();
    }
}
