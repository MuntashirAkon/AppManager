package android.app;

import android.os.Binder;
import android.os.IBinder;

/**
 * @deprecated Since Android O
 */
public abstract class ActivityManagerNative extends Binder implements IActivityManager {
    /**
     * Cast a Binder object into an activity manager interface, generating
     * a proxy if needed.
     *
     * @deprecated Since Android O. Use {@link IActivityManager.Stub#asInterface(IBinder)} instead.
     */
    @Deprecated
    static public IActivityManager asInterface(IBinder obj) {
        throw new UnsupportedOperationException();
    }
}
