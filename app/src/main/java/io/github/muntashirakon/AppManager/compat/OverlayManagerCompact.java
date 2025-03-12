package io.github.muntashirakon.AppManager.compat;

import static com.android.internal.R.*;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.FabricatedOverlayHidden;
import android.content.om.IOverlayManager;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManagerTransaction;
import android.content.om.OverlayManagerTransactionHidden;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.os.Build;
import android.os.IIdmap2;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;

import androidx.annotation.AnyRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.ContextUtils;
import io.github.muntashirakon.AppManager.utils.ResourceUtil;

@RequiresApi(Build.VERSION_CODES.O)
@SuppressWarnings("NewApi")
public class OverlayManagerCompact {
    public static final String TAG = OverlayManagerCompact.class.getSimpleName();

    @RequiresPermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)
    public static IOverlayManager getOverlayManager() {
        return IOverlayManager.Stub.asInterface(ProxyBinder.getService("overlay"));
    }
    public static void testCreateSelfTag(String name) {
        Log.d(TAG, "testCreateSelfTag() called with: name = [" + name + "]");
        OverlayManagerTransaction trans = OverlayManagerTransaction.newInstance();
        FabricatedOverlayBuilder fabBuilder = new FabricatedOverlayBuilder(name, BuildConfig.APPLICATION_ID);
        ResourceUtil resourceUtil = new ResourceUtil();
        Context context = ContextUtils.getContext();
        if (!resourceUtil.loadResources(context.getPackageManager(), BuildConfig.APPLICATION_ID)) {
            return;
        }
        Resources resources = resourceUtil.resources;
        if (resources == null) {
            return;
        }
        String resName = resources.getResourceName(R.string.sort_by_overlay_names);
        fabBuilder.setResValue(R.string.sort_by_overlay_names, R.string.copy_profile_id);
        Class<?> clazz = OverlayManagerTransactionHidden.Builder.class;
        for (Method method : clazz.getMethods()) {
            Log.d(TAG, "%s: %s", clazz.getSimpleName(), method.toGenericString());
        }
        for (Field field : clazz.getFields()) {
            Log.d(TAG, "%s: %s", clazz.getSimpleName(), field.toGenericString());
        }
        //transBuilder.setSelfTargeting(true);
        trans.registerFabricatedOverlay(fabBuilder.build());
        getOverlayManager().commit(trans);


    }
    public static void createWebviewOverlay(String name) {
        Log.d(TAG, "createWebviewOverlay() called with: name = [" + name + "]");
        OverlayManagerTransactionHidden.Builder transBuilder = new OverlayManagerTransactionHidden.Builder();
        FabricatedOverlayBuilder fabBuilder = new FabricatedOverlayBuilder(name, "android");
        ResourceUtil resourceUtil = new ResourceUtil();
        if (!resourceUtil.loadAndroidResources()) {
            return;
        }
        Resources resources = resourceUtil.resources;
        if (resources == null) {
            return;
        }
        String resName = resources.getResourceName(string.config_chooserActivity);
        /* AssetFileDescriptor fd = ResourceUtil.getXmlAssetFd(ContextUtils.getContext().getResources(), R.xml.config_webview_packages);
        try (AssetFileDescriptor fd2 = ContextUtils.getContext().getAssets().openFd("config_webview_packages.xml")) {
            ContextUtils.getContext().getResources().getValue(R.xml.config_webview_packages, ret, true);

            overlay.setResourceValue("android:xml/config_webview_packages", fd2.getParcelFileDescriptor(), null);
        } catch (IOException e){
            Log.e(TAG, e);
        }
        */
        TypedValue ret = new TypedValue();
        resources.getValue(string.config_chooserActivity, ret, true);
        Log.d(TAG, "id = "+string.chooseActivity);
        Log.d(TAG, "com.android.internal.R.xml.config_webview_packages: %s %s", resName, ret.toString());
        //resources.getString(R.xml.config_webview_packages)
        fabBuilder.setResourceValue(resources.getResourceName(string.config_chooserActivity), TypedValue.TYPE_STRING,  "io.github.muntashirakon.AppManager/.intercept.ActivityInterceptor", null);
        transBuilder.registerFabricatedOverlay(fabBuilder.build());
        transBuilder.setEnabled(fabBuilder.getIdentifier(), true);
        getOverlayManager().commit(transBuilder.build());
    }

    public static class FabricatedOverlayBuilder {
        private final FabricatedOverlay internal;
        private final ResourceUtil packageResources;
        private final ResourceUtil amResources;

        public FabricatedOverlayBuilder(@NonNull String overlayName, @NonNull String packageName) {
            this.internal = new FabricatedOverlay(overlayName, packageName);
            this.packageResources = new ResourceUtil();
            this.amResources = new ResourceUtil();
            PackageManager pm = ContextUtils.getContext().getPackageManager();
            this.packageResources.loadResources(pm, packageName);
            this.amResources.loadResources(pm, BuildConfig.APPLICATION_ID);
            if (this.packageResources.resources == null || this.amResources.resources == null) {
                throw new IllegalStateException("Failed to load resources");
            }
        }

        public void setResValue(@AnyRes int ogId, @AnyRes int newId ) {//,boolean preserveConfig, boolean useAmConfig) {
            final String name = packageResources.resources.getResourceName(ogId);
            final TypedValue value = new TypedValue();
            final TypedValue newValue = new TypedValue();
            packageResources.resources.getValue(ogId, value, true);
            amResources.resources.getValue(newId, newValue, true);
            if (value.type != newValue.type) {
                throw new IllegalArgumentException("Given Res Id's are not the same type");
            }
            //if (preserveConfig && (packageResources.resources.getConfiguration().equals(amResources.resources.getConfiguration()))) {
            //    throw new IllegalArgumentException("Given Resources Don't have same config with perservceConfig enabled");
            //}
            //Configuration configuration = (preserveConfig || useAmConfig) ? (useAmConfig) ? amResources.resources.getConfiguration() : packageResources.resources.getConfiguration() : null;
            switch (value.type) {
                case TypedValue.TYPE_STRING:
                    setResourceValue(name, value.type, newValue.string.toString(), null);
                    break;
                case TypedValue.TYPE_INT_BOOLEAN:
                case TypedValue.TYPE_INT_DEC:
                case TypedValue.TYPE_INT_HEX:
                case TypedValue.TYPE_INT_COLOR_ARGB4:
                case TypedValue.TYPE_INT_COLOR_ARGB8:
                case TypedValue.TYPE_INT_COLOR_RGB4:
                case TypedValue.TYPE_INT_COLOR_RGB8:
                    setResourceValue(name, value.type, newValue.data, null);
                default:
                    throw new IllegalStateException("Unexpected value: " + value.type);
            }

        }

        @NonNull
        public OverlayIdentifier getIdentifier() {
            return internal.getIdentifier();
        }
        public void setTargetOverlayable(@Nullable String targetOverlayable) {
            internal.setTargetOverlayable(targetOverlayable);
        }
        public void setResourceValue(@NonNull String resourceName, int dataType, int value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, dataType, value, configuration);
        }
        public void setResourceValue(@NonNull String resourceName, @NonNull ParcelFileDescriptor value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, value, configuration);
        }
        public void setResourceValue(@NonNull String resourceName, int dataType, @NonNull String value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, dataType, value, configuration);
        }
        public void setNinePatchResourceValue(@NonNull String resourceName, @NonNull ParcelFileDescriptor value, @Nullable String configuration) {
            internal.setNinePatchResourceValue(resourceName, value, configuration);
        }
        public void setResourceValue(@NonNull String resourceName, @NonNull AssetFileDescriptor value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, value, configuration);
        }

        public FabricatedOverlay build() {
            return internal;
        }
    }
}
