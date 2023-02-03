// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.IContentProvider;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.O)
public class ContentProviderHolder implements Parcelable {
    public IContentProvider provider;

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }

    @Override
    public int describeContents() {
        return HiddenUtil.throwUOE();
    }

    public static final Creator<ContentProviderHolder> CREATOR = HiddenUtil.creator();
}
