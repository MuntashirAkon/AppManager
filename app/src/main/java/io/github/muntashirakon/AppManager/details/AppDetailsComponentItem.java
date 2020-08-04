package io.github.muntashirakon.AppManager.details;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

/**
 * Stores individual app details component item
 */
public class AppDetailsComponentItem extends AppDetailsItem {
    public boolean isTracker = false;
    public boolean isBlocked = false;

    public AppDetailsComponentItem(@NonNull ComponentInfo componentInfo) {
        super(componentInfo);
    }
}
