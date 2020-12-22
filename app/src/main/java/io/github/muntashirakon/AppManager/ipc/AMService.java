/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.ipc;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.IAMService;
import io.github.muntashirakon.AppManager.IRemoteProcess;
import io.github.muntashirakon.AppManager.server.common.IRootIPC;

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
            return new RemoteProcessHolder(process);
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == ProxyBinder.PROXY_BINDER_TRANSACT_CODE) {
                data.enforceInterface(IRootIPC.class.getName());
                transactRemote(data, reply, flags);
                return true;
            }
            Log.d(TAG, String.format("transact: uid=%d, code=%d", Binder.getCallingUid(), code));
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

            Log.d(TAG, String.format("transact: uid=%d, descriptor=%s, code=%d", Binder.getCallingUid(), targetBinder.getInterfaceDescriptor(), targetCode));
            Parcel newData = Parcel.obtain();
            try {
                newData.appendFrom(data, data.dataPosition(), data.dataAvail());
            } catch (Throwable tr) {
                Log.e(TAG, tr.getMessage(), tr);
                return;
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