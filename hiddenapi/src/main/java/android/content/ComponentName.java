// SPDX-License-Identifier: Apache-2.0

package android.content;

import android.os.Parcelable;

public final class ComponentName implements Parcelable, Cloneable, Comparable<ComponentName> {
    @Override
    public int compareTo(ComponentName o) {
        throw new UnsupportedOperationException();
    }
}
