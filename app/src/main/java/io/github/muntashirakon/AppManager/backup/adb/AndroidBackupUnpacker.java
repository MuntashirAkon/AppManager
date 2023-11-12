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

public class AndroidBackupUnpacker {
    public static void toTar(@NonNull Path abSource, @NonNull Path tarDest, @Nullable char[] password)
            throws IOException {
        AndroidBackupHeader header = new AndroidBackupHeader(password);
        InputStream is = abSource.openInputStream();
        try (OutputStream os = tarDest.openOutputStream();
             InputStream realIs = header.read(is)) {
            IoUtils.copy(realIs, os);
        } catch (Exception e) {
            ExUtils.rethrowAsIOException(e);
        }
    }
}
