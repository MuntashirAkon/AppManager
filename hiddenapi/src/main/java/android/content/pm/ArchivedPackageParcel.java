// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // 14 r50
public class ArchivedPackageParcel implements Parcelable {
    public String packageName;
    public SigningDetails signingDetails;
    public int versionCode;
    public int versionCodeMajor;
    public int targetSdkVersion;
    public String defaultToDeviceProtectedStorage;
    public String requestLegacyExternalStorage;
    public String userDataFragile;
    public ArchivedActivityParcel[] archivedActivities;

    public static final Creator<ArchivedPackageParcel> CREATOR = HiddenUtil.creator();

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        HiddenUtil.throwUOE(dest, flags);
    }
}
