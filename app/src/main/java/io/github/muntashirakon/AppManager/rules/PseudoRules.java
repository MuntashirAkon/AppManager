package io.github.muntashirakon.AppManager.rules;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import android.os.RemoteException;
import androidx.annotation.NonNull;

public class PseudoRules extends RulesStorageManager {
    public PseudoRules(@NonNull Context context, @NonNull String packageName, int userHandle) {
        super(context, packageName, userHandle);
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
