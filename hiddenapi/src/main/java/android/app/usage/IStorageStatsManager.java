// SPDX-License-Identifier: Apache-2.0

package android.app.usage;

import android.content.pm.ParceledListSlice;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.O)
public interface IStorageStatsManager extends IInterface {
    abstract class Stub extends Binder implements IStorageStatsManager {
        public Stub() {
        }

        public static IStorageStatsManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    boolean isQuotaSupported(String volumeUuid, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.P)
    boolean isReservedSupported(String volumeUuid, String callingPackage) throws RemoteException;

    long getTotalBytes(String volumeUuid, String callingPackage) throws RemoteException;

    long getFreeBytes(String volumeUuid, String callingPackage) throws RemoteException;

    long getCacheBytes(String volumeUuid, String callingPackage) throws RemoteException;

    long getCacheQuotaBytes(String volumeUuid, int uid, String callingPackage) throws RemoteException;

    StorageStats queryStatsForPackage(String volumeUuid, String packageName, int userId, String callingPackage) throws RemoteException;

    StorageStats queryStatsForUid(String volumeUuid, int uid, String callingPackage) throws RemoteException;

    StorageStats queryStatsForUser(String volumeUuid, int userId, String callingPackage) throws RemoteException;

    ExternalStorageStats queryExternalStatsForUser(String volumeUuid, int userId, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    ParceledListSlice queryCratesForPackage(String volumeUuid, String packageName, int userId, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    ParceledListSlice queryCratesForUid(String volumeUuid, int uid, String callingPackage) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    ParceledListSlice queryCratesForUser(String volumeUuid, int userId, String callingPackage) throws RemoteException;
}