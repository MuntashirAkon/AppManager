// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.io.IOException;

import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

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
    protected Path getDesiredFile(boolean create) {
        return Paths.get("/dev/null");
    }
}
