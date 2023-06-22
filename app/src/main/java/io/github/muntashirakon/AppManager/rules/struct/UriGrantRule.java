// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RuleType;
import io.github.muntashirakon.AppManager.uri.UriManager;

public class UriGrantRule extends RuleEntry {
    @NonNull
    private final UriManager.UriGrant mUriGrant;

    public UriGrantRule(@NonNull String packageName, @NonNull UriManager.UriGrant uriGrant) {
        super(packageName, STUB, RuleType.URI_GRANT);
        mUriGrant = uriGrant;
    }

    public UriGrantRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RuleType.URI_GRANT);
        if (tokenizer.hasMoreElements()) {
            mUriGrant = UriManager.UriGrant.unflattenFromString(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: uriGrant not found");
    }

    @NonNull
    public UriManager.UriGrant getUriGrant() {
        return mUriGrant;
    }

    @NonNull
    @Override
    public String toString() {
        return "UriGrantRule{" +
                "packageName='" + packageName + '\'' +
                ", uriGrant=" + mUriGrant +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + mUriGrant.flattenToString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UriGrantRule)) return false;
        if (!super.equals(o)) return false;
        UriGrantRule that = (UriGrantRule) o;
        return getUriGrant().equals(that.getUriGrant());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getUriGrant());
    }
}
