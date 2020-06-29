package io.github.muntashirakon.AppManager.types;

import androidx.annotation.NonNull;

/**
 * Stores individual app details item
 */
public class AppDetailsItem {
    public @NonNull Object vanillaItem;
    public @NonNull String name = "";
    public boolean isBlocked = false;

    public AppDetailsItem(@NonNull Object object) {
        vanillaItem = object;
    }
}
