package io.github.muntashirakon.AppManager.details.struct;

import android.annotation.SuppressLint;
import android.content.om.OverlayInfo;
import android.content.om.OverlayInfoHidden;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerHidden;
import android.content.om.OverlayManagerTransaction;
import android.content.om.OverlayManagerTransactionHidden;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.Refine;

public class AppDetailsOverlayItem extends AppDetailsItem<OverlayInfo> {

    private final OverlayInfoHidden overlayInternal;


    @SuppressLint("NewApi")
    public AppDetailsOverlayItem(@NonNull OverlayInfo overlayInfo) {
        super(overlayInfo);
        if (overlayInfo.getOverlayName()!=null) {
            name = overlayInfo.getOverlayName();
        } else {
            name = overlayInfo.getOverlayIdentifier().toString();
        }

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

    public boolean isMutable() {
        return overlayInternal.isMutable;
    }

    public String getReadableState() {
        return OverlayInfoHidden.stateToString(overlayInternal.state);
    }

    public int getState() {
        return overlayInternal.state;
    }
    public int getPriority() {
        return overlayInternal.priority;
    }
    
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public boolean setEnabled(OverlayManager mgr, boolean enabled) {
        OverlayManagerTransactionHidden.Builder builder = new OverlayManagerTransactionHidden.Builder();
        builder.setEnabled(this.item.getOverlayIdentifier(), enabled);
        mgr.commit(Refine.unsafeCast(builder.build()));
        return isEnabled();
    }

}
