// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.content.ComponentName;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
public class ArchivedActivityParcel implements Parcelable {
    public String title;
    public ComponentName originalComponentName;
    // PNG compressed bitmaps.
    public byte[] iconBitmap;
    public byte[] monochromeIconBitmap;

    public static final Creator<ArchivedActivityParcel> CREATOR = HiddenUtil.creator();

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }
}
