// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import android.content.pm.FeatureInfo;

import androidx.annotation.NonNull;

public class AppDetailsFeatureItem extends AppDetailsItem<FeatureInfo> {
    public static final String OPEN_GL_ES = "OpenGL ES";

    public final boolean required;
    public final boolean available;

    public AppDetailsFeatureItem(@NonNull FeatureInfo featureInfo, boolean available) {
        super(featureInfo);
        // Currently, feature only has a single flag, which specifies whether the feature is required.
        this.required = (featureInfo.flags & FeatureInfo.FLAG_REQUIRED) != 0;
        this.available = available;
    }
}
