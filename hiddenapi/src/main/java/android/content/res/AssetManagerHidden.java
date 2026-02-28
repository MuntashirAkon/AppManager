/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.res;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

/**
 * Provides access to an application's raw asset files; see {@link Resources}
 * for the way most applications will want to retrieve their resource data.
 * This class presents a lower-level API that allows you to open and read raw
 * files that have been bundled with the application as a simple stream of
 * bytes.
 * @noinspection JavadocBlankLines
 */
@SuppressWarnings("unused")
@RefineAs(AssetManager.class)
public final class AssetManagerHidden implements AutoCloseable {
    /**
     * Cookie value to use when the actual cookie is unknown. This value tells the system to search
     * all the ApkAssets for the asset.
     */
    public static final int COOKIE_UNKNOWN = -1;

    /**
     * Mode for {@link #open(String, int)}: no specific information about how
     * data will be accessed.
     */
    public static final int ACCESS_UNKNOWN = 0;
    /**
     * Mode for {@link #open(String, int)}: Read chunks, and seek forward and
     * backward.
     */
    public static final int ACCESS_RANDOM = 1;
    /**
     * Mode for {@link #open(String, int)}: Read sequentially, with an
     * occasional forward seek.
     */
    public static final int ACCESS_STREAMING = 2;
    /**
     * Mode for {@link #open(String, int)}: Attempt to load contents into
     * memory, for fast small reads.
     */
    public static final int ACCESS_BUFFER = 3;

    /**
     * Close this asset manager.
     */
    @Override
    public void close() {
        HiddenUtil.throwUOE();
    }


    /**
     * Open an asset using ACCESS_STREAMING mode.  This provides access to
     * files that have been bundled with an application as assets -- that is,
     * files placed in to the "assets" directory.
     *
     * @param fileName The name of the asset to open.  This name can be hierarchical.
     *
     * @see #open(String, int)
     * @see #list
     */
    public @NonNull InputStream open(@NonNull String fileName) throws IOException {
        return HiddenUtil.throwUOE(fileName);
    }

    /**
     * Open an asset using an explicit access mode, returning an InputStream to
     * read its contents.  This provides access to files that have been bundled
     * with an application as assets -- that is, files placed in to the
     * "assets" directory.
     *
     * @param fileName The name of the asset to open.  This name can be hierarchical.
     * @param accessMode Desired access mode for retrieving the data.
     *
     * @see #ACCESS_UNKNOWN
     * @see #ACCESS_STREAMING
     * @see #ACCESS_RANDOM
     * @see #ACCESS_BUFFER
     * @see #open(String)
     * @see #list
     */
    public @NonNull InputStream open(@NonNull String fileName, int accessMode) throws IOException {
        return HiddenUtil.throwUOE(fileName, accessMode);
    }

    /**
     * Open an uncompressed asset by mmapping it and returning an {@link AssetFileDescriptor}.
     * This provides access to files that have been bundled with an application as assets -- that
     * is, files placed in to the "assets" directory.
     *
     * The asset must be uncompressed, or an exception will be thrown.
     *
     * @param fileName The name of the asset to open.  This name can be hierarchical.
     * @return An open AssetFileDescriptor.
     */
    public @NonNull AssetFileDescriptor openFd(@NonNull String fileName) throws IOException {
        return HiddenUtil.throwUOE(fileName);
    }

    /**
     * Return a String array of all the assets at the given path.
     *
     * @param path A relative path within the assets, i.e., "docs/home.html".
     *
     * @return String[] Array of strings, one for each asset.  These file
     *         names are relative to 'path'.  You can open the file by
     *         concatenating 'path' and a name in the returned string (via
     *         File) and passing that to open().
     *
     * @see #open
     */
    public @Nullable String[] list(@NonNull String path) throws IOException {
        return HiddenUtil.throwUOE(path);
    }

    /**
     * Open a non-asset file as an asset using ACCESS_STREAMING mode.  This
     * provides direct access to all of the files included in an application
     * package (not only its assets).  Applications should not normally use
     * this.
     *
     * @param fileName Name of the asset to retrieve.
     *
     * @see #open(String)
     * @hide
     */
    public @NonNull InputStream openNonAsset(@NonNull String fileName) throws IOException {
        return HiddenUtil.throwUOE(fileName);
    }

    /**
     * Open a non-asset file as an asset using a specific access mode.  This
     * provides direct access to all of the files included in an application
     * package (not only its assets).  Applications should not normally use
     * this.
     *
     * @param fileName Name of the asset to retrieve.
     * @param accessMode Desired access mode for retrieving the data.
     *
     * @see #ACCESS_UNKNOWN
     * @see #ACCESS_STREAMING
     * @see #ACCESS_RANDOM
     * @see #ACCESS_BUFFER
     * @see #open(String, int)
     * @hide
     */
    public @NonNull InputStream openNonAsset(@NonNull String fileName, int accessMode)
            throws IOException {
       return HiddenUtil.throwUOE(fileName, accessMode);
    }

    /**
     * Open a non-asset in a specified package.  Not for use by applications.
     *
     * @param cookie Identifier of the package to be opened.
     * @param fileName Name of the asset to retrieve.
     * @hide
     */
    public @NonNull InputStream openNonAsset(int cookie, @NonNull String fileName)
            throws IOException {
        return HiddenUtil.throwUOE(cookie, fileName);
    }

    /**
     * Open a non-asset in a specified package.  Not for use by applications.
     *
     * @param cookie Identifier of the package to be opened.
     * @param fileName Name of the asset to retrieve.
     * @param accessMode Desired access mode for retrieving the data.
     * @hide
     */
    public @NonNull InputStream openNonAsset(int cookie, @NonNull String fileName, int accessMode)
            throws IOException {
       return HiddenUtil.throwUOE(cookie, fileName, accessMode);
    }

    /**
     * Open a non-asset as an asset by mmapping it and returning an {@link AssetFileDescriptor}.
     * This provides direct access to all of the files included in an application
     * package (not only its assets).  Applications should not normally use this.
     *
     * The asset must not be compressed, or an exception will be thrown.
     *
     * @param fileName Name of the asset to retrieve.
     */
    public @NonNull AssetFileDescriptor openNonAssetFd(@NonNull String fileName)
            throws IOException {
        return HiddenUtil.throwUOE(fileName);
    }

    /**
     * Open a non-asset as an asset by mmapping it and returning an {@link AssetFileDescriptor}.
     * This provides direct access to all of the files included in an application
     * package (not only its assets).  Applications should not normally use this.
     *
     * The asset must not be compressed, or an exception will be thrown.
     *
     * @param cookie Identifier of the package to be opened.
     * @param fileName Name of the asset to retrieve.
     */
    public @NonNull AssetFileDescriptor openNonAssetFd(int cookie, @NonNull String fileName)
            throws IOException {
        return HiddenUtil.throwUOE(cookie, fileName);
    }

    public @Nullable Map<String, String> getOverlayableMap(String packageName) {
        return HiddenUtil.throwUOE(packageName);
    }

    public @Nullable String getOverlayablesToString(String packageName) {
        return HiddenUtil.throwUOE(packageName);
    }
}
