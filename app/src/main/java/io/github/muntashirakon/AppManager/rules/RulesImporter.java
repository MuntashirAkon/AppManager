// SPDX-License-Identifier: GPL-3.0-or-later

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
import java.util.StringTokenizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.utils.IOUtils;

/**
 * Rules importer is used to import internal rules to App Manager. Rules should only be imported
 * from settings and app data restore sections (although can be exported from various places).
 * <br>
 * Format: <code>package_name component_name type [mode|is_applied|is_granted]</code>
 *
 * @see RulesExporter
 */
public class RulesImporter implements Closeable {
    @NonNull
    private final Context mContext;
    @NonNull
    private final HashMap<String, ComponentsBlocker>[] mComponentsBlockers;
    @NonNull
    private final List<RulesStorageManager.Type> mTypesToImport;
    @Nullable
    private List<String> mPackagesToImport;
    @NonNull
    private final int[] userHandles;

    public RulesImporter(@NonNull List<RulesStorageManager.Type> typesToImport, @NonNull int[] userHandles) {
        mContext = AppManager.getContext();
        if (userHandles.length <= 0) {
            throw new IllegalArgumentException("Input must contain one or more user handles");
        }
        // Init CBs
        //noinspection unchecked
        mComponentsBlockers = new HashMap[userHandles.length];
        for (int i = 0; i < userHandles.length; ++i) {
            mComponentsBlockers[i] = new HashMap<>();
        }
        mTypesToImport = typesToImport;
        this.userHandles = userHandles;
    }

    public void addRulesFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(uri)) {
            try (BufferedReader TSVFile = new BufferedReader(new InputStreamReader(inputStream))) {
                StringTokenizer tokenizer;
                String dataRow;
                String packageName;
                while ((dataRow = TSVFile.readLine()) != null) {
                    tokenizer = new StringTokenizer(dataRow, "\t");
                    RulesStorageManager.Entry entry = new RulesStorageManager.Entry();
                    if (tokenizer.hasMoreElements()) {
                        packageName = tokenizer.nextElement().toString();
                    } else throw new IOException("Malformed file.");
                    if (tokenizer.hasMoreElements()) {
                        entry.name = tokenizer.nextElement().toString();
                    } else throw new IOException("Malformed file.");
                    if (tokenizer.hasMoreElements()) {
                        try {
                            entry.type = RulesStorageManager.Type.valueOf(tokenizer.nextElement().toString());
                        } catch (Exception e) {
                            entry.type = RulesStorageManager.Type.UNKNOWN;
                        }
                    } else throw new IOException("Malformed file.");
                    if (tokenizer.hasMoreElements()) {
                        entry.extra = RulesStorageManager.getExtra(entry.type, tokenizer.nextElement().toString());
                    } else throw new IOException("Malformed file.");
                    // Parse complete, now add the row to CB
                    for (int i = 0; i < userHandles.length; ++i) {
                        if (mComponentsBlockers[i].get(packageName) == null) {
                            // Get a read-only instance, commit will be called manually
                            mComponentsBlockers[i].put(packageName, ComponentsBlocker.getInstance(packageName, userHandles[i]));
                        }
                        if (mTypesToImport.contains(entry.type)) {
                            //noinspection ConstantConditions Returned ComponentsBlocker will never be null here
                            mComponentsBlockers[i].get(packageName).addEntry(entry);
                        }
                    }
                }
            }
        }
    }

    public List<String> getPackages() {
        return new ArrayList<>(mComponentsBlockers[0].keySet());
    }

    public void setPackagesToImport(List<String> packageNames) {
        mPackagesToImport = packageNames;
    }

    @WorkerThread
    public void applyRules(boolean commitChanges) {
        if (mPackagesToImport == null) mPackagesToImport = getPackages();
        // When #setPackagesToImport(List<String>) is used, ComponentBlocker can be null
        @Nullable ComponentsBlocker cb;
        for (int i = 0; i < userHandles.length; ++i) {
            for (String packageName : mPackagesToImport) {
                cb = mComponentsBlockers[i].get(packageName);
                if (cb == null) continue;
                // Set mutable, otherwise changes may not be applied properly
                cb.setMutable();
                // Apply component blocking rules
                cb.applyRules(true);
                // Apply app op and permissions
                cb.applyAppOpsAndPerms(true);
                // Store the changes or discard them
                if (commitChanges) {
                    // Commit changes
                    cb.commit();
                } else {
                    // Don't commit changes, discard the rules
                    cb.setReadOnly();
                }
            }
        }
    }

    @Override
    public void close() {
        // When #setPackagesToImport(List<String>) is used, ComponentBlocker can be null
        for (int i = 0; i < userHandles.length; ++i) {
            for (ComponentsBlocker cb : mComponentsBlockers[i].values()) {
                IOUtils.closeQuietly(cb);
            }
        }
    }
}
