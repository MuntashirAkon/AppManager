// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import androidx.annotation.NonNull;

/**
 * Stores individual app details item
 */
public class AppDetailsItem<T> {
    public @NonNull
    T vanillaItem;
    public @NonNull
    String name = "";

    public AppDetailsItem(@NonNull T object) {
        vanillaItem = object;
    }
}
