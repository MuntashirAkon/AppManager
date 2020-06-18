package io.github.muntashirakon.AppManager;

import androidx.annotation.NonNull;

/**
 * Stores individual app details item
 */
public class AppDetailsItem {
    public @NonNull Object vanillaItem;
    public @NonNull String name = "";

    public AppDetailsItem(@NonNull Object object) {
        vanillaItem = object;
    }
}
