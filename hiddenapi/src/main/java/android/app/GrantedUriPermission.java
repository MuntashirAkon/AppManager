// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import misc.utils.HiddenUtil;

public class GrantedUriPermission implements Parcelable {
    public final Uri uri;
    public final String packageName;

    public GrantedUriPermission(@NonNull Uri uri, @Nullable String packageName) {
        HiddenUtil.throwUOE(uri, packageName);
        throw new UnsupportedOperationException();
    }
}
