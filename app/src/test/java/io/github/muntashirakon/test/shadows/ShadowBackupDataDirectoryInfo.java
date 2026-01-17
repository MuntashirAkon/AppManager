// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.test.shadows;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import java.io.File;

import io.github.muntashirakon.AppManager.backup.BackupDataDirectoryInfo;
import io.github.muntashirakon.AppManager.utils.RoboUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

@Implements(BackupDataDirectoryInfo.class)
public class ShadowBackupDataDirectoryInfo {
    @RealObject
    private BackupDataDirectoryInfo mRealObject;

    @Implementation
    public Path getDirectory() {
        boolean hasSep = mRealObject.rawPath.startsWith(File.separator);
        return Paths.get(RoboUtils.getTestBaseDir().getAbsolutePath()
                + (hasSep ? (File.separator + mRealObject.rawPath) : mRealObject.rawPath));
    }
}
