package io.github.muntashirakon.AppManager.details.struct;

import static dev.rikka.tools.refine.Refine.unsafeCast;

import android.annotation.SuppressLint;
import android.content.om.OverlayInfo;
import android.content.om.OverlayInfoHidden;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerHidden;

import androidx.annotation.NonNull;

import dev.rikka.tools.refine.Refine;

public class AppDetailsOverlayItem extends AppDetailsItem<OverlayInfo> {

    private final OverlayInfoHidden overlayInternal;


    @SuppressLint("NewApi")
    public AppDetailsOverlayItem(@NonNull OverlayInfo overlayInfo) {
        super(overlayInfo);
        if (overlayInfo.getOverlayName()!=null) name = overlayInfo.getOverlayName();
        overlayInternal = Refine.<OverlayInfoHidden>unsafeCast(overlayInfo);

    }

    public String getPackageName() {
        return overlayInternal.getPackageName();
    }

    public String getCategory() {
        return overlayInternal.getCategory();
    }

    public boolean isEnabled() {
        return overlayInternal.isEnabled();
    }



}
