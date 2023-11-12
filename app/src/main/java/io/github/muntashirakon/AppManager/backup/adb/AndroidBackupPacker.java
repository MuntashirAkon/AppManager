// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.adb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.muntashirakon.AppManager.utils.ExUtils;
import io.github.muntashirakon.io.IoUtils;
import io.github.muntashirakon.io.Path;

public class AndroidBackupPacker {
    public static void fromTar(@NonNull Path tarSource, @NonNull Path abDest, @Nullable char[] password, int api,
                               boolean compress)
            throws IOException {
        int backupFileVersion = Constants.getBackupFileVersionFromApi(api);
        AndroidBackupHeader header = new AndroidBackupHeader(backupFileVersion, compress, password);
        OutputStream os = abDest.openOutputStream();
        try (InputStream is = tarSource.openInputStream();
             OutputStream realOs = header.write(os)) {
            IoUtils.copy(is, realOs);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            ExUtils.rethrowAsIOException(e);
        }
    }
}
