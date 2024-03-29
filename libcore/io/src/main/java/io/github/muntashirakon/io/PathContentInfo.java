// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class PathContentInfo {
    public static final String TAG = PathContentInfo.class.getSimpleName();

    @NonNull
    private final String mName;
    @Nullable
    private final String mMessage;
    @Nullable
    private final String mMimeType;
    @Nullable
    private final String[] mFileExtensions;
    private final boolean mPartial;

    protected PathContentInfo(@NonNull String name, @Nullable String message, @Nullable String mimeType,
                              @Nullable String[] fileExtensions, boolean partial) {
        mName = name;
        mMessage = message;
        mMimeType = mimeType;
        mFileExtensions = fileExtensions;
        mPartial = partial;
    }

    /**
     * Returns the short name of the content either from the content-type or extracted from the message. If the
     * content-type is known then this is a specific name string. Otherwise, this is usually the first word of the
     * message generated by the magic file.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the mime-type or null if none.
     */
    @Nullable
    public String getMimeType() {
        return mMimeType;
    }

    /**
     * Returns the full message as generated by the magic matching code or null if none. This should be similar to the
     * output from the Unix file(1) command.
     */
    @Nullable
    public String getMessage() {
        return mMessage;
    }

    /**
     * Returns an array of associated file-extensions or null if none.
     */
    @Nullable
    public String[] getFileExtensions() {
        return mFileExtensions;
    }

    /**
     * Whether this was a partial match. For some types, there is a main matching pattern and then more
     * specific patterns which detect additional features of the type. A partial match means that none of the more
     * specific patterns fully matched the content. It's probably still of the type but just not a variant that the
     * entries from the magic file(s) know about.
     */
    public boolean isPartial() {
        return mPartial;
    }
}
