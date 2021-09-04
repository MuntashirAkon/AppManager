// SPDX-License-Identifier: GPL-3.0-or-later

package android.os.storage;

import android.os.Parcelable;

import java.io.File;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(StorageVolume.class)
public final class StorageVolumeHidden implements Parcelable {
    public File getPathFile() {
        throw new UnsupportedOperationException();
    }

    public String getUserLabel() {
        throw new UnsupportedOperationException();
    }
}