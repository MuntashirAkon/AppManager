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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;

/**
 * Rules importer is used to import internal rules to App Manager. Rules should only be imported
 * from settings and app data restore sections (although can be exported from various places).
 * <br>
 * Format: <code>package_name component_name type [mode|is_applied|is_granted]</code>
 * @see RulesExporter
 */
public class RulesImporter implements Closeable {
    private  @NonNull Context mContext;
    private @NonNull HashMap<String, ComponentsBlocker> mComponentsBlockers;
    private @NonNull List<RulesStorageManager.Type> mTypesToImport;
    private @Nullable List<String> mPackagesToImport;

    public RulesImporter(@NonNull List<RulesStorageManager.Type> typesToImport) {
        mContext = AppManager.getContext();
        mComponentsBlockers = new HashMap<>();
        mTypesToImport = typesToImport;
    }

    public void addRulesFromUri(Uri uri, int userHandle) throws IOException {
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(uri)) {
            try (BufferedReader TSVFile = new BufferedReader(new InputStreamReader(inputStream))) {
                StringTokenizer tokenizer;
                String dataRow;
                String packageName;
                while ((dataRow = TSVFile.readLine()) != null){
                    tokenizer = new StringTokenizer(dataRow,"\t");
                    RulesStorageManager.Entry entry = new RulesStorageManager.Entry();
                    if (tokenizer.hasMoreElements()) packageName = tokenizer.nextElement().toString();
                    else throw new IOException("Malformed file.");
                    if (tokenizer.hasMoreElements()) entry.name = tokenizer.nextElement().toString();
                    else throw new IOException("Malformed file.");
                    if (tokenizer.hasMoreElements()) {
                        try {
                            entry.type = RulesStorageManager.Type.valueOf(tokenizer.nextElement().toString());
                        } catch (Exception e) {
                            entry.type = RulesStorageManager.Type.UNKNOWN;
                        }
                    } else throw new IOException("Malformed file.");
                    if (tokenizer.hasMoreElements()) entry.extra = RulesStorageManager.getExtra(entry.type, tokenizer.nextElement().toString());
                    else throw new IOException("Malformed file.");
                    if (mComponentsBlockers.get(packageName) == null) {
                        // Get a read-only instance, commit will be called manually
                        mComponentsBlockers.put(packageName, ComponentsBlocker.getInstance(packageName, userHandle));
                    }
                    if (mTypesToImport.contains(entry.type))
                        Objects.requireNonNull(mComponentsBlockers.get(packageName)).addEntry(entry);
                }
            }
        }
    }

    public List<String> getPackages() {
        return new ArrayList<>(mComponentsBlockers.keySet());
    }

    public void setPackagesToImport(List<String> packageNames) {
        mPackagesToImport = packageNames;
    }

    public void applyRules() {
        if (mPackagesToImport == null) mPackagesToImport = getPackages();
        @NonNull ComponentsBlocker cb;
        for (String packageName: mPackagesToImport) {
            cb = Objects.requireNonNull(mComponentsBlockers.get(packageName));
            cb.readOnly = false;
            // Apply component blocking rules
            cb.applyRules(true);
            // Apply app op and permissions
            cb.applyAppOpsAndPerms(true);
            // Commit changes
            cb.commit();
        }
    }

    @Override
    public void close() {
        for (String packageName: mComponentsBlockers.keySet()) {
            Objects.requireNonNull(mComponentsBlockers.get(packageName)).close();
        }
    }
}
