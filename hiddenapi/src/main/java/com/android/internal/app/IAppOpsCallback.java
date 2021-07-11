// SPDX-License-Identifier: Apache-2.0

package com.android.internal.app;

// This interface is also used by native code, so must
// be kept in sync with frameworks/native/libs/binder/include/binder/IAppOpsCallback.h
interface IAppOpsCallback {
    void opChanged(int op, int uid, String packageName);
}