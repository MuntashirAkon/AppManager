// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.Nullable;

final class AudioMetadata {
    public Uri uri;
    @Nullable
    public Bitmap cover;
    public String title;
    public String artist;
    public String album;
    public String albumArtist;
}
