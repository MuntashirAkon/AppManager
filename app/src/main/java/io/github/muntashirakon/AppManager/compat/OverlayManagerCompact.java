package io.github.muntashirakon.AppManager.compat;

import android.content.om.IOverlayManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

@RequiresApi(Build.VERSION_CODES.O)
@SuppressWarnings("NewApi")
public class OverlayManagerCompact {
    public static final String TAG = OverlayManagerCompact.class.getSimpleName();

    @RequiresPermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)
    public static IOverlayManager getOverlayManager() {
        return IOverlayManager.Stub.asInterface(ProxyBinder.getService("overlay"));
    }
    //Webview Config Info toString: TypedValue{t=0x3/d=0x37 "res/xml/config_webview_packages.xml" a=5 r=0x1170007} assetCookie: 0x5 dataType: 0x3

}
