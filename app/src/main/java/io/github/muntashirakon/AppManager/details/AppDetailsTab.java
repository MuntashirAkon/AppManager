// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import androidx.annotation.StringRes;

public final class AppDetailsTab {
    private final int mId;
    @StringRes
    private final int mTitleRes;

    public AppDetailsTab(@AppDetailsFragment.Property int id, @StringRes int titleRes) {
        mId = id;
        mTitleRes = titleRes;
    }

    @AppDetailsFragment.Property
    public int getId() {
        return mId;
    }

    @StringRes
    public int getTitleRes() {
        return mTitleRes;
    }
}
