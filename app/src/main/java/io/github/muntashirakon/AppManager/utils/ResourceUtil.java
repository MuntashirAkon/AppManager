// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

public final class ResourceUtil {
    public static class ParsedResource {
        private final String mPackageName;
        private final Resources mRes;
        private final int mResId;

        private ParsedResource(@NonNull String packageName, @NonNull Resources res, int resId) {
            mPackageName = packageName;
            mRes = res;
            mResId = resId;
        }

        public String getPackageName() {
            return mPackageName;
        }

        /**
         * @see ResourcesCompat#getDrawable(Resources, int, Resources.Theme)
         */
        public Drawable getDrawable() {
            return getDrawable(null);
        }

        /**
         * @see ResourcesCompat#getDrawable(Resources, int, Resources.Theme)
         */
        public Drawable getDrawable(@Nullable Resources.Theme theme) {
            return ResourcesCompat.getDrawable(mRes, mResId, theme);
        }
    }

    /**
     * Parse a resource name having the following format:
     * <p>
     * <code>
     * package-name:type/res-name
     * </code>
     */
    @NonNull
    public static ParsedResource getResourceFromName(@NonNull PackageManager pm, @NonNull String resName)
            throws PackageManager.NameNotFoundException, Resources.NotFoundException {
        String packageName = resName.substring(0, resName.indexOf(':'));
        String type = resName.substring(resName.indexOf(':') + 1, resName.indexOf('/'));
        String name = resName.substring(resName.indexOf('/') + 1);
        Resources res = pm.getResourcesForApplication(packageName);
        int resId = res.getIdentifier(name, type, packageName);
        if (resId == 0) {
            throw new Resources.NotFoundException("Resource " + name + " of type " + type + " is not found in package " + packageName);
        }
        return new ParsedResource(packageName, res, resId);
    }

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
