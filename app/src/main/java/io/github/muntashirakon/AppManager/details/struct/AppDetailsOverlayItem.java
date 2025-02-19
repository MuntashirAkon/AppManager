package io.github.muntashirakon.AppManager.details.struct;

import android.annotation.UserIdInt;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.om.OverlayInfoHidden;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.Refine;

@RequiresApi(Build.VERSION_CODES.O)
public class AppDetailsOverlayItem extends AppDetailsItem<OverlayInfoHidden> {

    @SuppressWarnings("NewApi") // Required due to sdk lying about the real api version requirement for overlay info
    public AppDetailsOverlayItem(@NonNull OverlayInfo overlayInfo) {
        super(Refine.unsafeCast(overlayInfo));
        if (overlayInfo.getOverlayName() != null) {
            name = overlayInfo.getOverlayName();
        } else {
            name = overlayInfo.getOverlayIdentifier().toString();
        }
    }


    public String getPackageName() {
        return item.packageName;
    }

    @Nullable
    public String getCategory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return item.category;
        }
        return null;
    }

    public boolean isEnabled() {
        return item.isEnabled();
    }


    @SuppressWarnings("deprecation")
    public boolean isMutable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return item.isMutable;
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            return getState() != OverlayInfoHidden.STATE_ENABLED_IMMUTABLE;
        }
        return true;
    }

    public String getReadableState() {
        return stateToString(item.state);
    }

    public int getState() {
        return item.state;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    public int getPriority() {
        return item.priority;
    }


    public boolean setEnabled(@NonNull IOverlayManager mgr, boolean enabled) throws RemoteException {
        return mgr.setEnabled(getPackageName(), enabled, item.userId);
    }

    public boolean setPriority(@NonNull IOverlayManager mgr, String newParentPackageName) throws RemoteException {
        return mgr.setPriority(getPackageName(), newParentPackageName, item.userId);
    }

    public boolean setHighestPriority(@NonNull IOverlayManager mgr) throws RemoteException {
        return mgr.setHighestPriority(item.packageName, item.userId);
    }

    public boolean setLowestPriority(@NonNull IOverlayManager mgr) throws RemoteException {
        return mgr.setLowestPriority(item.packageName, item.userId);
    }

    public static String stateToString(@OverlayInfoHidden.State int state) {
        return OverlayInfoHidden.stateToString(state);
    }

    @Nullable
    public String getOverlayName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return item.overlayName;
        }
        return null;
    }

    public String getTargetPackageName() {
        return item.targetPackageName;
    }

    @Nullable
    public String getTargetOverlayableName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return item.targetOverlayableName;
        }
        return null;
    }

    public String getBaseCodePath() {
        return item.baseCodePath;
    }

    @UserIdInt
    public int getUserId() {
        return item.userId;
    }

    public boolean isFabricated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return item.isFabricated;
        }
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "AppDetailsOverlayItem: { " + item + " }";
    }
}
