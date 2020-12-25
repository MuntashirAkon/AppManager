package android.content.pm;

import android.os.IInterface;

interface IPackageDeleteObserver extends IInterface {
    void packageDeleted(String packageName, int returnCode);
}