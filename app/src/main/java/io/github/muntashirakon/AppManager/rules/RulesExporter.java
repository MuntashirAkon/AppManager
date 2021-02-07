/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.rules;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

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
    private final List<RulesStorageManager.Type> mTypesToExport;
    @NonNull
    private final int[] userHandles;

    public RulesExporter(@NonNull List<RulesStorageManager.Type> typesToExport, @Nullable List<String> packagesToExport, @NonNull int[] userHandles) {
        mContext = AppManager.getContext();
        mPackagesToExport = packagesToExport;
        mTypesToExport = typesToExport;
        this.userHandles = userHandles;
    }

    public void saveRules(Uri uri) throws IOException {
        if (mPackagesToExport == null) mPackagesToExport = ComponentUtils.getAllPackagesWithRules();
        try (OutputStream outputStream = mContext.getContentResolver().openOutputStream(uri)) {
            for (String packageName: mPackagesToExport) {
                for (int userHandle : userHandles) {
                    // Get a read-only instance
                    try (ComponentsBlocker cb = ComponentsBlocker.getInstance(packageName, userHandle)) {
                        for (RulesStorageManager.Entry entry : cb.getAll()) {
                            if (mTypesToExport.contains(entry.type)) {
                                outputStream.write(String.format("%s\t%s\t%s\t%s\n", packageName,
                                        entry.name, entry.type.name(), entry.extra).getBytes());
                            }
                        }
                    }
                }
            }
        }
    }
}
