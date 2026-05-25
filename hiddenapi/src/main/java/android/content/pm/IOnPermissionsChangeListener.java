// SPDX-License-Identifier: Apache-2.0

package android.content.pm;

import android.os.IInterface;

/**
 * @deprecated Replaced in API 30 (Android R) with {@link android.permission.IOnPermissionsChangeListener}
 */
@Deprecated
public interface IOnPermissionsChangeListener extends IInterface {
    void onPermissionsChanged(int uid);
}