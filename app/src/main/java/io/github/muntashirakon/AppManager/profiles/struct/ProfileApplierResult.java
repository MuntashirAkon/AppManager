// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

public class ProfileApplierResult {
    public static final ProfileApplierResult EMPTY_RESULT = new ProfileApplierResult();

    private boolean mRequiresRestart;

    public void setRequiresRestart(boolean requiresRestart) {
        mRequiresRestart = requiresRestart;
    }

    public boolean requiresRestart() {
        return mRequiresRestart;
    }
}
