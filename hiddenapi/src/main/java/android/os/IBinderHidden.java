// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package android.os;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.FileDescriptor;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(IBinder.class)
public interface IBinderHidden {
    /**
     * @deprecated Replaced in Android 8 (Oreo) by {@link #shellCommand(FileDescriptor, FileDescriptor, FileDescriptor, String[], ShellCallback, ResultReceiver)}
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.N)
    void shellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
                      String[] args, ResultReceiver resultReceiver) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O)
    void shellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
                      String[] args, @Nullable ShellCallback shellCallback,
                      ResultReceiver resultReceiver) throws RemoteException;
}
