// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.users;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserHandleHidden;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dev.rikka.tools.refine.Refine;
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
        name = userInfo.name;
    }

    UserInfo(@NonNull UserHandle userHandle, @Nullable String name) {
        this.userHandle = userHandle;
        this.id = Refine.<UserHandleHidden>unsafeCast(userHandle).getIdentifier();
        if (name == null) {
            this.name = id == UserHandleHidden.myUserId() ? "Main" : "Work";
        } else this.name = name;
    }

    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        return name == null ? String.valueOf(id) : (name + " (" + id + ")");
    }
}
