// SPDX-License-Identifier: Apache-2.0

package com.android.internal.app;

// Interface to observe op note/checks of ops
interface IAppOpsNotedCallback {
    void opNoted(int op, int uid, String packageName, int mode);
}