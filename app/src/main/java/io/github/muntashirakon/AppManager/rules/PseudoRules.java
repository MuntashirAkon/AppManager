// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

public class PseudoRules extends RulesStorageManager {
    public PseudoRules(@NonNull String packageName, int userHandle) {
        super(packageName, userHandle);
        setReadOnly();
    }

    @Override
    public void setMutable() {
        // Do nothing
    }

    public void loadExternalEntries(File file) throws IOException, RemoteException {
        super.loadEntries(file, true);
    }

    /**
     * No rules will be loaded
     * @return /dev/null
     */
    @NonNull
    @Override
    protected File getDesiredFile() {
        return new File("/dev/null");
    }
}
