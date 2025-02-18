package io.github.muntashirakon.AppManager.compat;

import android.content.om.IOverlayManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import io.github.muntashirakon.AppManager.ipc.ProxyBinder;

@RequiresApi(Build.VERSION_CODES.O)
public class OverlayManagerCompact {
    public static IOverlayManager getOverlayManager() {
        return IOverlayManager.Stub.asInterface(ProxyBinder.getService("overlay"));
    }

    public static IOverlayManager getUnprivilegedOverlayManager() {
        return IOverlayManager.Stub.asInterface(ProxyBinder.getUnprivilegedService("overlay"));
    }

}
