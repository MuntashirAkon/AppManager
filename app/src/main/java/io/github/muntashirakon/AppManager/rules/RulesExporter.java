// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.rules;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;

/**
 * Export rules to external directory either for a single package or multiple packages.
 *
 * @see RulesImporter
 */
public class RulesExporter {
    @NonNull
    private final Context mContext;
    @Nullable
    private List<String> mPackagesToExport;
    @NonNull
    private final List<RuleType> mTypesToExport;
    @NonNull
    private final int[] userHandles;

    public RulesExporter(@NonNull List<RuleType> typesToExport, @Nullable List<String> packagesToExport, @NonNull int[] userHandles) {
        mContext = AppManager.getContext();
        mPackagesToExport = packagesToExport;
        mTypesToExport = typesToExport;
        this.userHandles = userHandles;
    }

    public void saveRules(Uri uri) throws IOException {
        if (mPackagesToExport == null) mPackagesToExport = ComponentUtils.getAllPackagesWithRules();
        try (OutputStream outputStream = mContext.getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) throw new IOException("Content provider has crashed.");
            for (String packageName: mPackagesToExport) {
                for (int userHandle : userHandles) {
                    // Get a read-only instance
                    try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userHandle)) {
                        ComponentUtils.storeRules(outputStream, cb.getAll(mTypesToExport), true);
                    }
                }
            }
        }
    }
}
