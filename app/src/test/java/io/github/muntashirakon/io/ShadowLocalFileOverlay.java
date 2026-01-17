// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(LocalFileOverlay.class)
public class ShadowLocalFileOverlay {
    @Implementation
    @Nullable
    public static ExtendedFile getOverlayFileOrNull(@NonNull ExtendedFile file) {
        return null;
    }
}
