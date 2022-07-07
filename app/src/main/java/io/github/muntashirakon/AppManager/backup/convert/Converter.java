// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import io.github.muntashirakon.AppManager.backup.BackupException;

public abstract class Converter {
    public abstract void convert() throws BackupException;

    public abstract void cleanup();

    public abstract String getPackageName();
}
