// SPDX-License-Identifier: Apache-2.0

package android.os;

import misc.utils.HiddenUtil;

public class ServiceManager {
    public static IBinder getService(String name) {
        return HiddenUtil.throwUOE(name);
    }

    public static void addService(String name, IBinder service) {
        HiddenUtil.throwUOE(name, service);
    }
}
