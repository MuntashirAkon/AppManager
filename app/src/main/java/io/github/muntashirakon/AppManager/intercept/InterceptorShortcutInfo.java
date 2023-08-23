// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;

public class InterceptorShortcutInfo extends ShortcutInfo {
    public static final String TAG = InterceptorShortcutInfo.class.getSimpleName();

    private final Intent intent;

    public InterceptorShortcutInfo(@NonNull Intent intent) {
        this.intent = new Intent(intent);
        fixIntent(this.intent);
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

    @SuppressWarnings("deprecation")
    private static void fixIntent(@NonNull Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            // Nothing to do
            return;
        }
        // Shortcuts use PersistableBundle for extras which only support 12 types
        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (!isValidType(value)) {
                // Not a valid type, remove it from intent
                Log.w(TAG, "Removing unsupported key %s (class: %s, value: %s)", key, value.getClass().getName(), value);
                intent.removeExtra(key);
            }
        }
    }

    /**
     * @see PersistableBundle#isValidType(Object)
     */
    private static boolean isValidType(@Nullable Object value) {
        return (value instanceof Integer) || (value instanceof Long) ||
                (value instanceof Double) || (value instanceof String) ||
                (value instanceof int[]) || (value instanceof long[]) ||
                (value instanceof double[]) || (value instanceof String[]) ||
                (value instanceof PersistableBundle) || (value == null) ||
                (value instanceof Boolean) || (value instanceof boolean[]);
    }
}
