// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details.struct;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Stores individual app details item
 */
public class AppDetailsItem<T> {
    @NonNull
    public T mainItem;
    @NonNull
    public String name = "";

    public AppDetailsItem(@NonNull T object) {
        mainItem = object;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppDetailsItem)) return false;
        AppDetailsItem<?> that = (AppDetailsItem<?>) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
