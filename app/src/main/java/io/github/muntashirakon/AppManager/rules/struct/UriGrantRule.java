// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules.struct;

import androidx.annotation.NonNull;

import java.util.StringTokenizer;

import io.github.muntashirakon.AppManager.rules.RulesStorageManager;
import io.github.muntashirakon.AppManager.uri.UriManager;

public class UriGrantRule extends RuleEntry {
    private final UriManager.UriGrant uriGrant;

    public UriGrantRule(@NonNull String packageName, @NonNull UriManager.UriGrant uriGrant) {
        super(packageName, STUB, RulesStorageManager.Type.URI_GRANT);
        this.uriGrant = uriGrant;
    }

    public UriGrantRule(@NonNull String packageName, @NonNull StringTokenizer tokenizer) {
        super(packageName, STUB, RulesStorageManager.Type.URI_GRANT);
        if (tokenizer.hasMoreElements()) {
            uriGrant = UriManager.UriGrant.unflattenFromString(tokenizer.nextElement().toString());
        } else throw new IllegalArgumentException("Invalid format: uriGrant not found");
    }

    public UriManager.UriGrant getUriGrant() {
        return uriGrant;
    }

    @NonNull
    @Override
    public String toString() {
        return "UriGrantRule{" +
                "packageName='" + packageName + '\'' +
                ", uriGrant=" + uriGrant +
                '}';
    }

    @NonNull
    @Override
    public String flattenToString(boolean isExternal) {
        return addPackageWithTab(isExternal) + name + "\t" + type.name() + "\t" + uriGrant.flattenToString();
    }
}
