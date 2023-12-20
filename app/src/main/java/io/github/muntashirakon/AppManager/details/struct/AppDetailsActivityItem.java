// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.ActivityInfo;

import androidx.annotation.NonNull;

public class AppDetailsActivityItem extends AppDetailsComponentItem {
    public boolean canLaunchAssist;

    public AppDetailsActivityItem(@NonNull ActivityInfo componentInfo) {
        super(componentInfo);
    }
}
