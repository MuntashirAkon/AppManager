// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.types;

import java.io.File;

import androidx.annotation.NonNull;
import io.github.muntashirakon.io.ProxyFile;

/**
 * Start with a new file, delete old one if existed
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class FreshFile extends ProxyFile {
    public FreshFile(@NonNull String pathname) {
        super(pathname);
        delete();
    }

    public FreshFile(@NonNull String parent, @NonNull String child) {
        super(parent, child);
        delete();
    }

    public FreshFile(@NonNull File parent, @NonNull String child) {
        super(parent, child);
        delete();
    }
}
