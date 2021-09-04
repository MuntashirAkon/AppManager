// SPDX-License-Identifier: GPL-3.0-or-later

package android.os.storage;

import android.os.Parcelable;

import java.io.File;

import dev.rikka.tools.refine.RefineAs;
import misc.utils.HiddenUtil;

@RefineAs(StorageVolume.class)
public final class StorageVolumeHidden implements Parcelable {
    public File getPathFile() {
        return HiddenUtil.throwUOE();
    }

    public String getUserLabel() {
        return HiddenUtil.throwUOE();
    }
}
