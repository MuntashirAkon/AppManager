// SPDX-License-Identifier: Apache-2.0

package android.os;

import androidx.annotation.RequiresApi;

import misc.utils.HiddenUtil;

@RequiresApi(Build.VERSION_CODES.N)
public class ServiceSpecificException extends RuntimeException {
    public final int errorCode = HiddenUtil.throwUOE();
}