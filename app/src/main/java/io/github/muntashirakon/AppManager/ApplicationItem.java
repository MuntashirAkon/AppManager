package io.github.muntashirakon.AppManager;

import android.content.pm.ApplicationInfo;

import io.github.muntashirakon.AppManager.utils.Tuple;

class ApplicationItem {
    ApplicationInfo applicationInfo;
    String label;
    boolean star;
    Long date;
    Long size = -1L;
    Tuple sha;
}
