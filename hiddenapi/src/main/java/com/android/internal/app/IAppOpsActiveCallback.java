// SPDX-License-Identifier: Apache-2.0

package com.android.internal.app;

// Interface to observe op active changes
interface IAppOpsActiveCallback {
    void opActiveChanged(int op, int uid, String packageName, boolean active);
}