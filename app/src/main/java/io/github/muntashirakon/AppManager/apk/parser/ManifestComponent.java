// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import android.content.ComponentName;

import java.util.ArrayList;
import java.util.List;

public class ManifestComponent {
    public final ComponentName cn;
    public final List<ManifestIntentFilter> intentFilters;

    public ManifestComponent(ComponentName cn) {
        this.cn = cn;
        intentFilters = new ArrayList<>();
    }
}
