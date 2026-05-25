// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import misc.utils.HiddenUtil;

public interface IPackageStatsObserver extends IInterface {
    void onGetStatsCompleted(PackageStats pStats, boolean succeeded);

    abstract class Stub extends Binder implements IPackageStatsObserver {
        public static IPackageDataObserver asInterface(IBinder binder) {
            return HiddenUtil.throwUOE(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
