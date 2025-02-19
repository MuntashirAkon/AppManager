package io.github.muntashirakon.AppManager.compat;

import android.content.om.IOverlayManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

@RequiresApi(Build.VERSION_CODES.O)
public class OverlayManagerCompact {
    @RequiresPermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)
    public static IOverlayManager getOverlayManager() {
        return IOverlayManager.Stub.asInterface(ProxyBinder.getService("overlay"));
    }
}
