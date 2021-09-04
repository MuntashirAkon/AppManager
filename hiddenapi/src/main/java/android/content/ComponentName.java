// SPDX-License-Identifier: Apache-2.0

package android.content;

import android.os.Parcelable;

import misc.utils.HiddenUtil;

public final class ComponentName implements Parcelable, Cloneable, Comparable<ComponentName> {
    @Override
    public int compareTo(ComponentName o) {
        return HiddenUtil.throwUOE(o);
    }
}
