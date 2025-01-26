// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles.struct;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import io.github.muntashirakon.AppManager.history.IJsonSerializer;
import io.github.muntashirakon.util.LocalizedString;

public abstract class AbsProfile implements LocalizedString, IJsonSerializer {
    @NonNull
    public final String profileId;

    protected AbsProfile(@NonNull String profileId) {
        this.profileId = profileId;
    }

    public abstract void write(@NonNull OutputStream out) throws IOException;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbsProfile)) return false;
        AbsProfile that = (AbsProfile) o;
        return Objects.equals(profileId, that.profileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileId);
    }
}
