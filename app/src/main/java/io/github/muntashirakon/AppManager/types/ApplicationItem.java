package io.github.muntashirakon.AppManager.types;

import android.content.pm.ApplicationInfo;

import io.github.muntashirakon.AppManager.utils.Tuple;

/**
 * Stores an application info
 */
public class ApplicationItem {
    /**
     * Application info
     */
    public ApplicationInfo applicationInfo;
    /**
     * Application label (or name)
     */
    public String label;
    /**
     * True if debuggable, false otherwise
     */
    public boolean star;
    /**
     * Last update date
     */
    public Long date;
    /**
     * Target SDK version * -1
     */
    public Long size = -1L;
    /**
     * Issuer and signature
     */
    public Tuple<String, String> sha;
}
