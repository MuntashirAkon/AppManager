package io.github.muntashirakon.AppManager.details.struct;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.om.OverlayInfoHidden;
import android.os.Build;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.Refine;


@SuppressWarnings("NewApi") // Required due to sdk lying about the real api version requirement for overlay info
@RequiresApi(Build.VERSION_CODES.O)
public class AppDetailsOverlayItem extends AppDetailsItem<OverlayInfo> {
    private final OverlayInfoHidden internal;


    public AppDetailsOverlayItem(@NonNull OverlayInfo overlayInfo) {
        super(overlayInfo);
        this.internal = Refine.unsafeCast(overlayInfo);
        if (overlayInfo.getOverlayName()!=null) {
            name = overlayInfo.getOverlayName();
        } else {
            name = overlayInfo.getOverlayIdentifier().toString();
        }
        Parcel source = Parcel.obtain();
        overlayInfo.writeToParcel(source, 0);
    }


    public String getPackageName() {
        return internal.getPackageName();
    }

    public String getCategory() {
            return internal.getCategory();
    }

    public boolean isEnabled() {
        return internal.isEnabled();
    }

    public boolean isMutable() {
        return internal.isMutable;
    }

    public String getReadableState() {
        return stateToString(internal.state);
    }

    public int getState() {
        return internal.state;
    }
    public int getPriority() {
        return internal.priority;
    }
    

    public boolean setEnabled(@NonNull IOverlayManager mgr, boolean enabled, int userId) {
        return mgr.setEnabled(getPackageName(), enabled, userId);
    }

    public static String stateToString(@OverlayInfoHidden.State int state) {
        return OverlayInfoHidden.stateToString(state);
    }
    public String getOverlayName() {
        return internal.getOverlayName();
    }

    public String getTargetPackageName() {
        return internal.getTargetPackageName();
    }

    public String getTargetOverlayableName() {
        return internal.getTargetOverlayableName();
    }

    public String getBaseCodePath() {
        return internal.getBaseCodePath();
    }

    public int getUserId() {
        return internal.getUserId();
    }

    public boolean isFabricated() {
        return internal.isFabricated();
    }
}
