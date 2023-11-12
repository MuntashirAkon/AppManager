// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PathAttributes {
    @NonNull
    public final String name;
    @Nullable
    public final String mimeType;
    public final long lastModified;
    public final long lastAccess;
    public final long creationTime;
    public final boolean isRegularFile;
    public final boolean isDirectory;
    public final boolean isSymbolicLink;
    public final boolean isOtherFile;
    public final long size;

    protected PathAttributes(@NonNull String displayName, @Nullable String mimeType, long lastModified, long lastAccess,
                             long creationTime, boolean isRegularFile, boolean isDirectory, boolean isSymbolicLink,
                             long size) {
        this.name = displayName;
        this.mimeType = mimeType;
        this.lastModified = lastModified;
        this.lastAccess = lastAccess;
        this.creationTime = creationTime;
        this.isRegularFile = isRegularFile;
        this.isDirectory = isDirectory;
        this.isSymbolicLink = isSymbolicLink;
        this.isOtherFile = !isRegularFile && !isDirectory && !isSymbolicLink;
        this.size = size;
    }
}
