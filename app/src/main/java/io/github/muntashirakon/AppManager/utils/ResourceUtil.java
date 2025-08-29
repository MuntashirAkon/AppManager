// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.utils;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.XmlRes;
import androidx.core.content.res.ResourcesCompat;

import java.io.IOException;

public final class ResourceUtil {

    private static final String TAG = ResourceUtil.class.getSimpleName();

    public static class ParsedResource {
        @NonNull
        private final String mPackageName;
        @NonNull
        private final Resources mRes;
        @DrawableRes
        private final int mResId;

        private ParsedResource(@NonNull String packageName, @NonNull Resources res, @DrawableRes int resId) {
            mPackageName = packageName;
            mRes = res;
            mResId = resId;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        /**
         * @see ResourcesCompat#getDrawable(Resources, int, Resources.Theme)
         */
        @Nullable
        public Drawable getDrawable() {
            return getDrawable(null);
        }

        /**
         * @see ResourcesCompat#getDrawable(Resources, int, Resources.Theme)
         */
        @Nullable
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
        int indexOfColon = resName.indexOf(':');
        int indexOfSlash = resName.indexOf('/');
        if (indexOfColon == -1 || indexOfSlash == -1) {
            throw new Resources.NotFoundException("Resource " + resName + " is not found.");
        }
        String packageName = resName.substring(0, indexOfColon);
        String type = resName.substring(indexOfColon + 1, indexOfSlash);
        String name = resName.substring(indexOfSlash + 1);
        Resources res = pm.getResourcesForApplication(packageName);
        @SuppressLint("DiscouragedApi")
        int resId = res.getIdentifier(name, type, packageName);
        if (resId == 0) {
            throw new Resources.NotFoundException("Resource " + name + " of type " + type + " is not found in package " + packageName);
        }
        return new ParsedResource(packageName, res, resId);
    }

    @SuppressLint("DiscouragedApi")
    public static int getRawDataId(@NonNull Context context, @NonNull String name) {
        return context.getResources().getIdentifier(name, "raw", context.getPackageName());
    }

    @Nullable
    public String packageName;
    @Nullable
    public String className;
    @Nullable
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
    @NonNull
    @SuppressLint("DiscouragedApi")
    public String getString(@NonNull String stringRes) throws Resources.NotFoundException {
        if (resources == null) {
            throw new Resources.NotFoundException("No resource could be loaded.");
        }
        int intStringRes = resources.getIdentifier(stringRes, "string", packageName);
        if (intStringRes == 0) throw new Resources.NotFoundException("String resource ID " + stringRes);
        return this.resources.getString(intStringRes);
    }

    @SuppressWarnings("NewApi")
    @NonNull
    public static AssetFileDescriptor getXmlAssetFd(Resources resources, @XmlRes int id) throws Resources.NotFoundException {
        Log.d(TAG, "getXmlAssetFd() called with: resources = [" + resources + "], id = [" + id + "]");
        final TypedValue value = new TypedValue();
        if (resources == null) {
            throw new Resources.NotFoundException("Resources Not Init");
        }
        try {
            resources.getValue(id, value, true);
            if (value.type == TypedValue.TYPE_STRING) {
                return resources.getAssets().openNonAssetFd(value.assetCookie, value.string.toString());
            }
            throw new Resources.NotFoundException("Resource ID #0x" + Integer.toHexString(id) + " type #0x "+Integer.toHexString(value.type) + " is not valid");
        } catch (IOException e) {
            throw new Resources.NotFoundException("Resource ID #0x "+ Integer.toHexString(id), e);
        }
    }
}
