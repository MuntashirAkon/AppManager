// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import java.util.Objects;
import java.util.UUID;

import io.github.muntashirakon.AppManager.shortcut.ShortcutInfo;
import io.github.muntashirakon.io.Path;

public class FmShortcutInfo extends ShortcutInfo {
    private final boolean mIsDirectory;
    @NonNull
    private final Uri mUri;
    @Nullable
    private final String mCustomMimeType;

    public FmShortcutInfo(@NonNull Path path, @Nullable String customMimeType) {
        mIsDirectory = path.isDirectory();
        mCustomMimeType = customMimeType;
        mUri = path.getUri();
        setName(path.getName());
        setId(UUID.randomUUID().toString());
    }

    protected FmShortcutInfo(Parcel in) {
        super(in);
        mIsDirectory = ParcelCompat.readBoolean(in);
        mUri = Objects.requireNonNull(ParcelCompat.readParcelable(in, Uri.class.getClassLoader(), Uri.class));
        mCustomMimeType = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelCompat.writeBoolean(dest, mIsDirectory);
        dest.writeParcelable(mUri, flags);
        dest.writeString(mCustomMimeType);
    }

    @Override
    public Intent toShortcutIntent(@NonNull Context context) {
        Intent intent;
        if (mIsDirectory) {
            intent = new Intent(context, FmActivity.class);
            intent.setDataAndType(mUri, mCustomMimeType != null ? mCustomMimeType : DocumentsContract.Document.MIME_TYPE_DIR);
        } else {
            intent = new Intent(context, OpenWithActivity.class);
            if (mCustomMimeType != null) {
                intent.setDataAndType(mUri, mCustomMimeType);
            } else intent.setData(mUri);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        return intent;
    }

    public static final Creator<FmShortcutInfo> CREATOR = new Creator<FmShortcutInfo>() {
        @Override
        public FmShortcutInfo createFromParcel(Parcel source) {
            return new FmShortcutInfo(source);
        }

        @Override
        public FmShortcutInfo[] newArray(int size) {
            return new FmShortcutInfo[size];
        }
    };
}
