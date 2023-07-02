// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;

public class InterceptorShortcutInfo extends ShortcutInfo {
    private final Intent intent;

    public InterceptorShortcutInfo(@NonNull Intent intent) {
        this.intent = intent;
    }

    protected InterceptorShortcutInfo(Parcel in) {
        super(in);
        intent = ParcelCompat.readParcelable(in, Intent.class.getClassLoader(), Intent.class);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(intent, flags);
    }

    @Override
    public Intent toShortcutIntent(@NonNull Context context) {
        return intent;
    }

    public static final Creator<InterceptorShortcutInfo> CREATOR = new Creator<InterceptorShortcutInfo>() {
        @Override
        public InterceptorShortcutInfo createFromParcel(Parcel source) {
            return new InterceptorShortcutInfo(source);
        }

        @Override
        public InterceptorShortcutInfo[] newArray(int size) {
            return new InterceptorShortcutInfo[size];
        }
    };
}
