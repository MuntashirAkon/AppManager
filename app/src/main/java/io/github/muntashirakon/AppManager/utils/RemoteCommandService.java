// RemoteCommandService.java
package io.github.muntashirakon.AppManager.utils;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import rikka.shizuku.ShizukuService;

public class RemoteCommandService extends ShizukuService {

    @Override
    public IBinder onBind(Intent intent) {
        return new IRemoteCommandService.Stub() {
            @Override
            public int runCommand(String command) throws RemoteException {
                try {
                    Process process = new ProcessBuilder("sh", "-c", command).start();
                    return process.waitFor();
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
            }
        };
    }
}