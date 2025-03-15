package io.github.muntashirakon.AppManager.compat;

import static com.android.internal.R.*;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.FabricatedOverlayHidden;
import android.content.om.IOverlayManager;
import android.content.om.OverlayIdentifier;
import android.content.om.OverlayManagerTransaction;
import android.content.om.OverlayManagerTransactionHidden;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;

import androidx.annotation.AnyRes;
import androidx.annotation.Discouraged;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.ReturnThis;
import androidx.annotation.VisibleForTesting;

import com.topjohnwu.superuser.ShellUtils;

import org.jetbrains.annotations.Contract;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import dev.rikka.tools.refine.Refine;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.ipc.ProxyBinder;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.server.common.Shell;
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
    //Webview Config Info toString: TypedValue{t=0x3/d=0x37 "res/xml/config_webview_packages.xml" a=5 r=0x1170007} assetCookie: 0x5 dataType: 0x3

    public static void createFabOverlayTest(String name) {
        Log.d(TAG, "createWebviewOverlay() called with: name = [" + name + "]");
        FabricatedOverlayBuilder.getPersistentOverlayBuilder(name, "net.tharow.overlaytarget")
                .setResourceValue("net.tharow.overlaytarget:string/lorem_ipsum", TypedValue.TYPE_STRING, "MagicOverride", null)
                .commit();

    }

    public static class FabricatedOverlayBuilder {
        private final FabricatedOverlayHidden internal;
        private final Resources packageResources;
        private final Resources amResources;

        @NonNull
        @Contract("_, _ -> new")
        public static FabricatedOverlayBuilder getNonPersistentOverlayBuilder(@NonNull String overlayName, @NonNull String packageName) {
            ResourceUtil u = new ResourceUtil();
            Context amc = ContextUtils.getContext();
            if (u.loadAndroidResources() || u.resources == null) {
                throw new IllegalStateException("android Resources failed to load");
            }
            return new FabricatedOverlayBuilder(amc, overlayName, packageName, u.resources.getString(string.config_systemShell));
        }

        @NonNull
        @Contract("_, _ -> new")
        public static FabricatedOverlayBuilder getPersistentOverlayBuilder(@NonNull String overlayName, @NonNull String packageName) {
            return new FabricatedOverlayBuilder(ContextUtils.getContext(), overlayName, packageName, packageName);
        }

        public FabricatedOverlayBuilder(@NonNull Context parent, @NonNull String overlayName, @NonNull String packageName, @NonNull String owningPackage) {
            this.internal = Refine.unsafeCast(new FabricatedOverlay(overlayName, packageName));
            ResourceUtil resourceUtil = new ResourceUtil();
            PackageManager pm = ContextUtils.getContext().getPackageManager();
            this.amResources = parent.getResources();
            resourceUtil.loadResources(pm, packageName);
            if (resourceUtil.resources == null) {
                throw new IllegalStateException("Failed to load resources");
            }
            this.packageResources = resourceUtil.resources;
            this.internal.setOwningPackage(owningPackage);
        }

        @ReturnThis
        public FabricatedOverlayBuilder setResValue(@AnyRes int pkgRes, @AnyRes int amRes) {
            setResValue(pkgRes, amRes, null);
            return this;
        }

        @ReturnThis
        public FabricatedOverlayBuilder setResValue(@AnyRes int ogId, @AnyRes int newId, @Nullable String configuration) {
            final String name = packageResources.getResourceName(ogId);
            final TypedValue value = new TypedValue();
            final TypedValue newValue = new TypedValue();
            packageResources.getValue(ogId, value, true);
            amResources.getValue(newId, newValue, true);
            if (value.type != newValue.type) {
                throw new IllegalArgumentException("Given Res Id's are not the same type");
            }
            switch (value.type) {
                case TypedValue.TYPE_STRING:
                    setResourceValue(name, value.type, newValue.string.toString(), configuration);
                    break;
                case TypedValue.TYPE_INT_BOOLEAN:
                case TypedValue.TYPE_INT_DEC:
                case TypedValue.TYPE_INT_HEX:
                case TypedValue.TYPE_INT_COLOR_ARGB4:
                case TypedValue.TYPE_INT_COLOR_ARGB8:
                case TypedValue.TYPE_INT_COLOR_RGB4:
                case TypedValue.TYPE_INT_COLOR_RGB8:
                    setResourceValue(name, value.type, newValue.data, configuration);
                default:
                    throw new IllegalStateException("Unexpected value: " + value.type);
            }
            return this;
        }

        @NonNull
        public OverlayIdentifier getIdentifier() {
            return internal.getIdentifier();
        }
        @ReturnThis
        public FabricatedOverlayBuilder setOwningPackage(@NonNull String owningPackage) {
            internal.setOwningPackage(owningPackage);
            return this;
        }
        @ReturnThis
        public FabricatedOverlayBuilder setTargetOverlayable(@Nullable String targetOverlayable) {
            internal.setTargetOverlayable(targetOverlayable);
            return this;
        }
        @ReturnThis
        public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, int dataType, int value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, dataType, value, configuration);
            return this;
        }
        @ReturnThis
        public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, @NonNull ParcelFileDescriptor value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, value, configuration);
            return this;
        }
        @ReturnThis
        public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, int dataType, @NonNull String value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, dataType, value, configuration);
            return this;
        }
        @ReturnThis
        public FabricatedOverlayBuilder setNinePatchResourceValue(@NonNull String resourceName, @NonNull ParcelFileDescriptor value, @Nullable String configuration) {
            internal.setNinePatchResourceValue(resourceName, value, configuration);
            return this;
        }
        @ReturnThis
        public FabricatedOverlayBuilder setResourceValue(@NonNull String resourceName, @NonNull AssetFileDescriptor value, @Nullable String configuration) {
            internal.setResourceValue(resourceName, value, configuration);
            return this;
        }
        public FabricatedOverlay build() {
            return Refine.unsafeCast(internal);
        }
        public void commit() {
            getOverlayManager().commit(new OverlayManagerTransactionHidden.Builder().registerFabricatedOverlay(this.build()).build());
        }
    }
}
