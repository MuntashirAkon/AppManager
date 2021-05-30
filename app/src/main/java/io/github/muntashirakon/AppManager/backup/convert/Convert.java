// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.convert;

import io.github.muntashirakon.AppManager.backup.BackupException;

public interface Convert {
    void convert() throws BackupException;
}
