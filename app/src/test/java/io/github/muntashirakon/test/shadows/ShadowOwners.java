// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import androidx.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import io.github.muntashirakon.AppManager.users.Owners;

@Implements(value = Owners.class, minSdk = 21)
public class ShadowOwners {

    @Implementation
    @NonNull
    public static String getOwnerName(int uid) {
        return String.valueOf(uid);
    }
}
