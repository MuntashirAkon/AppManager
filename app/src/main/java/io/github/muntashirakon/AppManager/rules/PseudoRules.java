// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.io.Path;

public class PseudoRules extends RulesStorageManager {
    public PseudoRules(@NonNull String packageName, int userHandle) {
        super(packageName, userHandle);
        setReadOnly();
    }

    @Override
    public void setMutable() {
        // Do nothing
    }

    public void loadExternalEntries(Path file) throws IOException, RemoteException {
        super.loadEntries(file, true);
    }

    /**
     * No rules will be loaded
     *
     * @return /dev/null
     */
    @NonNull
    @Override
    protected Path getDesiredFile() {
        return new Path(AppManager.getContext(), new File("/dev/null"));
    }
}
