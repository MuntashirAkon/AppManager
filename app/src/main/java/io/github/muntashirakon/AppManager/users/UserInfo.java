// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.muntashirakon.util.LocalizedString;

public class UserInfo implements LocalizedString {
    @NonNull
    public final UserHandle userHandle;
    public final int id;
    @Nullable
    public final String name;

    UserInfo(@NonNull android.content.pm.UserInfo userInfo) {
        userHandle = userInfo.getUserHandle();
        id = userInfo.id;
        String username = userInfo.name;
        if (username == null) {
            this.name = id == UserHandleHidden.myUserId() ? "This" : "Other";
        } else this.name = username;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return name == null ? String.valueOf(id) : (name + " (" + id + ")");
    }
}
