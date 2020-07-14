package io.github.muntashirakon.AppManager.types;

import android.content.pm.ComponentInfo;

import androidx.annotation.NonNull;

/**
 * Stores individual app details component item
 */
public class AppDetailsComponentItem extends AppDetailsItem {
    public boolean isTracker = false;
    public AppDetailsComponentItem(@NonNull ComponentInfo componentInfo) {
        super(componentInfo);
    }
}
