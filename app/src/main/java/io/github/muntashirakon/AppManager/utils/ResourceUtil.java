// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import androidx.annotation.NonNull;

public final class ResourceUtil {
    public String packageName;
    public String className;
    public Resources resources;

    public boolean loadResources(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            this.packageName = packageName;
            this.className = null;
            this.resources = pm.getResourcesForApplication(packageName);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean loadResources(@NonNull PackageManager pm, @NonNull String packageName, @NonNull String className) {
        try {
            this.packageName = packageName;
            this.className = className;
            this.resources = pm.getResourcesForActivity(new ComponentName(packageName, className));
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public boolean loadAndroidResources() {
        this.packageName = "android";
        this.className = null;
        this.resources = Resources.getSystem();
        return true;
    }

    /**
     * Return the string value associated with a particular resource ID. It will be stripped of any styled text information.
     *
     * @param stringRes The desired resource identifier.
     * @return String The string data associated with the resource, stripped of styled text information.
     * @throws Resources.NotFoundException Throws NotFoundException if the given ID does not exist.
     */
    public String getString(@NonNull String stringRes) throws Resources.NotFoundException {
        int intStringRes = this.resources.getIdentifier(stringRes, "string", packageName);
        if (intStringRes == 0) throw new Resources.NotFoundException("String resource ID " + stringRes);
        return this.resources.getString(intStringRes);
    }
}
