// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Parcelable;

import java.util.List;

import misc.utils.HiddenUtil;

public class ParceledListSlice<T extends Parcelable> {
    public List<T> getList() {
        return HiddenUtil.throwUOE();
    }
}
