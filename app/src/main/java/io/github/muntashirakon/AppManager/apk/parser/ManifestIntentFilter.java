// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.parser;

import androidx.collection.ArraySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ManifestIntentFilter {
    public final Set<String> actions = new ArraySet<>();
    public final Set<String> categories = new ArraySet<>();
    public final List<ManifestData> data = new ArrayList<>();
    public int priority;

    public static class ManifestData {
        public String scheme;
        public String host;
        public String port;
        public String path;
        public String pathPattern;
        public String pathPrefix;
        public String pathSuffix;
        public String pathAdvancedPattern;
        public String mimeType;
    }
}
