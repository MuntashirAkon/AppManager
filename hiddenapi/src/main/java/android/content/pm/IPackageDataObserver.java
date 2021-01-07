package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IPackageDataObserver extends IInterface {
    void onRemoveCompleted(String packageName, boolean succeeded);

    abstract class Stub extends Binder implements IPackageDataObserver {
        public static IPackageDataObserver asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}