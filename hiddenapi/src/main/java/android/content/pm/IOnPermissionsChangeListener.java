package android.content.pm;

import android.os.IInterface;

interface IOnPermissionsChangeListener extends IInterface {
    void onPermissionsChanged(int uid);
}