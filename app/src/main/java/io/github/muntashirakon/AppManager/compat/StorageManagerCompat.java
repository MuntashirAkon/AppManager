// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.compat;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Copyright 2018 Fung Gwo (fythonx@gmail.com)
// Modified from https://gist.github.com/fython/924f8d9019bca75d22de116bb69a54a1
public final class StorageManagerCompat {
    private static final String TAG = StorageManagerCompat.class.getSimpleName();

    public static final int DEFAULT_BUFFER_SIZE = 1024 * 50;

    /**
     * Flag indicating that a disk space allocation request should be allowed to
     * clear up to all reserved disk space.
     */
    public static final int FLAG_ALLOCATE_DEFY_ALL_RESERVED = 1 << 1;

    /**
     * Flag indicating that a disk space allocation request should be allowed to
     * clear up to half of all reserved disk space.
     */
    public static final int FLAG_ALLOCATE_DEFY_HALF_RESERVED = 1 << 2;

    @IntDef(flag = true, value = {
            FLAG_ALLOCATE_DEFY_ALL_RESERVED,
            FLAG_ALLOCATE_DEFY_HALF_RESERVED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AllocateFlags {
    }

    private StorageManagerCompat() {
    }
}