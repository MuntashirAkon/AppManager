// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;

import io.github.muntashirakon.io.FileSystemManager;

public class FileSystemService extends RootService {
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return FileSystemManager.getService();
    }

    @Override
    public void onRebind(@NonNull Intent intent) {
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(@NonNull Intent intent) {
        return true;
    }
}
