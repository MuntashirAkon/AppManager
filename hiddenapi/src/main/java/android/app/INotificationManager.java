package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

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

    void cancelAllNotifications(String pkg, int userId) throws RemoteException;

    void clearData(String pkg, int uid, boolean fromApp) throws RemoteException;

    void setNotificationsEnabledForPackage(String pkg, int uid, boolean enabled) throws RemoteException;

    /**
     * Updates the notification's enabled state. Additionally locks importance for all of the
     * notifications belonging to the app, such that future notifications aren't reconsidered for
     * blocking helper.
     */
    void setNotificationsEnabledWithImportanceLockForPackage(String pkg, int uid, boolean enabled) throws RemoteException;

    boolean areNotificationsEnabledForPackage(String pkg, int uid) throws RemoteException;

    boolean areNotificationsEnabled(String pkg) throws RemoteException;

    int getPackageImportance(String pkg) throws RemoteException;

    boolean isPackagePaused(String pkg) throws RemoteException;

    boolean isNotificationListenerAccessGranted(ComponentName listener) throws RemoteException;

    boolean isNotificationListenerAccessGrantedForUser(ComponentName listener, int userId) throws RemoteException;

    boolean isNotificationAssistantAccessGranted(ComponentName assistant) throws RemoteException;

    void setNotificationListenerAccessGranted(ComponentName listener, boolean enabled) throws RemoteException;

    void setNotificationAssistantAccessGranted(ComponentName assistant, boolean enabled) throws RemoteException;

    void setNotificationListenerAccessGrantedForUser(ComponentName listener, int userId, boolean enabled) throws RemoteException;

    void setNotificationAssistantAccessGrantedForUser(ComponentName assistant, int userId, boolean enabled) throws RemoteException;

    List<String> getEnabledNotificationListenerPackages() throws RemoteException;

    List<ComponentName> getEnabledNotificationListeners(int userId) throws RemoteException;

    ComponentName getAllowedNotificationAssistantForUser(int userId) throws RemoteException;

    ComponentName getAllowedNotificationAssistant() throws RemoteException;

    void setPrivateNotificationsAllowed(boolean allow) throws RemoteException;

    boolean getPrivateNotificationsAllowed() throws RemoteException;
}