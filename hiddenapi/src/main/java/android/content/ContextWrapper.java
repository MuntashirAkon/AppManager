// SPDX-License-Identifier: Apache-2.0

package android.content;

import misc.utils.HiddenUtil;

public class ContextWrapper extends Context {
    public ContextWrapper(Context base) {
        HiddenUtil.throwUOE(base);
    }
}