// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.Objects;

import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;

public class FreezeUnfreezeShortcutInfo extends ShortcutInfo {
    @NonNull
    public final String packageName;
    @UserIdInt
    public final int userId;
    @FreezeUnfreeze.FreezeFlags
    public final int flags;

    private int mPrivateFlags;

    public FreezeUnfreezeShortcutInfo(@NonNull String packageName, int userId, int flags) {
        setId("freeze:u=" + userId + ",p=" + packageName);
        this.packageName = packageName;
        this.userId = userId;
        this.flags = flags;
    }

    protected FreezeUnfreezeShortcutInfo(Parcel in) {
        super(in);
        packageName = in.readString();
        userId = in.readInt();
        flags = in.readInt();
        mPrivateFlags = in.readInt();
    }

    public int getPrivateFlags() {
        return mPrivateFlags;
    }

    public void setPrivateFlags(int privateFlags) {
        mPrivateFlags = privateFlags;
    }

    public void addPrivateFlags(int privateFlags) {
        mPrivateFlags |= privateFlags;
    }

    public void removePrivateFlags(int privateFlags) {
        mPrivateFlags &= ~privateFlags;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(packageName);
        dest.writeInt(userId);
        dest.writeInt(flags);
        dest.writeInt(mPrivateFlags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, userId);
    }

    @Override
    public Intent toShortcutIntent(@NonNull Context context) {
        return FreezeUnfreeze.getShortcutIntent(context, this);
    }

    public static final Creator<FreezeUnfreezeShortcutInfo> CREATOR = new Creator<FreezeUnfreezeShortcutInfo>() {
        @Override
        public FreezeUnfreezeShortcutInfo createFromParcel(Parcel source) {
            return new FreezeUnfreezeShortcutInfo(source);
        }

        @Override
        public FreezeUnfreezeShortcutInfo[] newArray(int size) {
            return new FreezeUnfreezeShortcutInfo[size];
        }
    };
}