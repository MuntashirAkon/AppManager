// SPDX-License-Identifier: Apache-2.0

package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;

import androidx.annotation.RequiresApi;

import java.util.List;

public interface INotificationManager extends IInterface {
    abstract class Stub extends Binder implements INotificationManager {
        public static INotificationManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    void setNotificationsEnabledForPackage(String pkg, int uid, boolean enabled) throws RemoteException;

    // Added because there's no public API to get these notifications
    StatusBarNotification[] getActiveNotifications(String callingPkg) throws RemoteException;

    /**
     * Updates the notification's enabled state. Additionally locks importance for all of the
     * notifications belonging to the app, such that future notifications aren't reconsidered for
     * blocking helper.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    void setNotificationsEnabledWithImportanceLockForPackage(String pkg, int uid, boolean enabled) throws RemoteException;

    boolean areNotificationsEnabledForPackage(String pkg, int uid) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    boolean areNotificationsEnabled(String pkg) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.N)
    int getPackageImportance(String pkg) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    boolean isNotificationListenerAccessGranted(ComponentName listener) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    boolean isNotificationListenerAccessGrantedForUser(ComponentName listener, int userId) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    boolean isNotificationAssistantAccessGranted(ComponentName assistant) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void setNotificationListenerAccessGranted(ComponentName listener, boolean enabled) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void setNotificationAssistantAccessGranted(ComponentName assistant, boolean enabled) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void setNotificationListenerAccessGrantedForUser(ComponentName listener, int userId, boolean enabled) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    void setNotificationAssistantAccessGrantedForUser(ComponentName assistant, int userId, boolean enabled) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    List<String> getEnabledNotificationListenerPackages() throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    List<ComponentName> getEnabledNotificationListeners(int userId) throws RemoteException;
}