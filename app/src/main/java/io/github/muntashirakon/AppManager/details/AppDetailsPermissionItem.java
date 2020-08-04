package io.github.muntashirakon.AppManager.details;

import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;

/**
 * Stores individual app details item
 */
public class AppDetailsPermissionItem extends AppDetailsItem {
    public boolean isDangerous = false;
    public boolean isGranted = false;
    public int flags = 0;

    public AppDetailsPermissionItem(@NonNull PermissionInfo object) {
        super(object);
    }
}
