/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.om;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

import misc.utils.HiddenUtil;

/**
 * Immutable info on an overlayable defined inside a target package.
 *
 */
public final class OverlayableInfo {

    /**
     * The "name" attribute of the overlayable tag. Used to identify the set of resources overlaid.
     */
    @NonNull
    public final String name;

    @Nullable
    public final String actor;


    /**
     * Creates a new OverlayableInfo.
     *
     * @param name
     *   The "name" attribute of the overlayable tag. Used to identify the set of resources overlaid.
     * @param actor
     *   The "actor" attribute of the overlayable tag. Used to signal which apps are allowed to
     *   modify overlay state for this overlayable.
     */
    public OverlayableInfo(
            @NonNull String name,
            @Nullable String actor) {
        this.name = name;
        this.actor = actor;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return HiddenUtil.throwUOE(o);
    }

    @Override
    public int hashCode() {
        return HiddenUtil.throwUOE();
    }
}
