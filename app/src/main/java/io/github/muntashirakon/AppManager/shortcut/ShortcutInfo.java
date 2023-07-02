// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public abstract class ShortcutInfo implements Parcelable {
    private String mId;
    private CharSequence mName;
    private Bitmap mIcon;

    public ShortcutInfo() {
    }

    protected ShortcutInfo(Parcel in) {
        mName = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mIcon = ParcelCompat.readParcelable(in, Bitmap.class.getClassLoader(), Bitmap.class);
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public CharSequence getName() {
        return mName;
    }

    public void setName(CharSequence name) {
        mName = name;
    }

    public Bitmap getIcon() {
        return mIcon;
    }

    public void setIcon(Bitmap icon) {
        mIcon = icon;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        TextUtils.writeToParcel(mName, dest, flags);
        dest.writeParcelable(mIcon, flags);
    }

    public abstract Intent toShortcutIntent(@NonNull Context context);
}
