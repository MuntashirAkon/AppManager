// SPDX-License-Identifier: Apache-2.0

package android.permission;

import android.os.IInterface;

interface IOnPermissionsChangeListener extends IInterface {
    void onPermissionsChanged(int uid);
}