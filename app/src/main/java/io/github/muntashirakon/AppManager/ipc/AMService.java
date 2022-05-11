// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.ipc;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

import aosp.android.content.pm.ParceledListSlice;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.AppManager.IRemoteShell;
import io.github.muntashirakon.AppManager.ipc.ps.ProcessEntry;
import io.github.muntashirakon.AppManager.ipc.ps.Ps;
import io.github.muntashirakon.AppManager.server.common.IRootServiceManager;

public class AMService extends RootService {
    static class IAMServiceImpl extends IAMService.Stub {
        /**
         * To get {@link Process}, wrap it using {@link RemoteProcess}. Since the streams are piped,
         * I/O operations may have to be done in different threads.
         */
        @Override
        public IRemoteProcess newProcess(String[] cmd, String[] env, String dir) throws RemoteException {
            Process process;
            try {
                process = Runtime.getRuntime().exec(cmd, env, dir != null ? new File(dir) : null);
            } catch (Exception e) {
                throw new RemoteException(e.getMessage());
            }
            return new RemoteProcessImpl(process);
        }

        @Override
        public IRemoteShell getShell(String[] cmd) {
            return new RemoteShellImpl(cmd);
        }

        @Override
        public ParceledListSlice<ProcessEntry> getRunningProcesses() {
            Ps ps = new Ps();
            ps.loadProcesses();
            return new ParceledListSlice<>(ps.getProcesses());
        }

        @Override
        public int getUid() {
            return android.os.Process.myUid();
        }

        @Override
        public void symlink(String file, String link) throws RemoteException {
            try {
                Os.symlink(file, link);
            } catch (ErrnoException e) {
                throw new RemoteException(e.getMessage());
            }
        }

        @Override
        public boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == ProxyBinder.PROXY_BINDER_TRANSACT_CODE) {
                data.enforceInterface(IRootServiceManager.class.getName());
                transactRemote(data, reply, flags);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        /**
         * Call target Binder received through {@link ProxyBinder}.
         *
         * @author Rikka
         */
        private void transactRemote(Parcel data, Parcel reply, int flags) throws RemoteException {
            IBinder targetBinder = data.readStrongBinder();
            int targetCode = data.readInt();

            Parcel newData = Parcel.obtain();
            try {
                newData.appendFrom(data, data.dataPosition(), data.dataAvail());
            } catch (Throwable th) {
                throw (RemoteException) new RemoteException(th.getMessage()).initCause(th);
            }
            try {
                long id = Binder.clearCallingIdentity();
                targetBinder.transact(targetCode, newData, reply, flags);
                Binder.restoreCallingIdentity(id);
            } finally {
                newData.recycle();
            }
        }
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        Log.d(TAG, "AMService: onBind");
        return new IAMServiceImpl();
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