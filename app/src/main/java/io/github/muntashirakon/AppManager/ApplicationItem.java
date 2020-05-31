package io.github.muntashirakon.AppManager;

import android.content.pm.ApplicationInfo;

import io.github.muntashirakon.AppManager.utils.Tuple;

public class ApplicationItem {
    public ApplicationInfo applicationInfo;
    public String label;
    public boolean star;
    public Long date;
    public Long size = -1L;
    public Tuple sha;
}
