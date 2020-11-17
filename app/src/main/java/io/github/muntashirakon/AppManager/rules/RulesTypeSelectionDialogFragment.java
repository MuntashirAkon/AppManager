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

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.misc.Users;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;

public class RulesTypeSelectionDialogFragment extends DialogFragment {
    public static final String TAG = "RulesTypeSelectionDialogFragment";
    public static final String ARG_MODE = "ARG_MODE";  // int
    public static final String ARG_URI = "ARG_URI";  // Uri
    public static final String ARG_PKG = "ARG_PKG";  // Package Names or null (for all)

    @IntDef(value = {
            MODE_IMPORT,
            MODE_EXPORT
    })
    public @interface Mode {}
    public static final int MODE_IMPORT = 1;
    public static final int MODE_EXPORT = 2;

    public static final RulesStorageManager.Type[] types = new RulesStorageManager.Type[]{
            RulesStorageManager.Type.ACTIVITY,
            RulesStorageManager.Type.SERVICE,
            RulesStorageManager.Type.RECEIVER,
            RulesStorageManager.Type.PROVIDER,
            RulesStorageManager.Type.APP_OP,
            RulesStorageManager.Type.PERMISSION,
    };

    private FragmentActivity activity;
    private Uri mUri;
    private List<String> mPackages = null;
    private HashSet<RulesStorageManager.Type> mSelectedTypes;
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        Bundle args = requireArguments();
        @Mode int mode = args.getInt(ARG_MODE, MODE_EXPORT);
        mPackages = args.getStringArrayList(ARG_PKG);
        mUri = (Uri) args.get(ARG_URI);
        if (mUri == null) return super.onCreateDialog(savedInstanceState);
        final boolean[] checkedItems = new boolean[6];
        Arrays.fill(checkedItems, true);
        mSelectedTypes = new HashSet<>(Arrays.asList(RulesStorageManager.Type.values()));
        return new MaterialAlertDialogBuilder(activity)
                .setTitle(mode == MODE_IMPORT ? R.string.import_options : R.string.export_options)
                .setMultiChoiceItems(R.array.rule_types, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked) mSelectedTypes.add(types[which]);
                    else mSelectedTypes.remove(types[which]);
                })
                .setPositiveButton(getResources().getString(mode == MODE_IMPORT ?
                        R.string.pref_import : R.string.pref_export), (dialog1, which) -> {
                    Log.d("TestImportExport", "Types: " + mSelectedTypes.toString() + "\nURI: " + mUri.toString());
                    if (activity instanceof SettingsActivity) {
                        ((SettingsActivity) activity).progressIndicator.show();
                    }
                    if (mode == MODE_IMPORT) handleImport();
                    else handleExport();
                })
                .setNegativeButton(getResources().getString(R.string.cancel), null)
                .create();
    }

    private void handleExport() {
        new Thread(() -> {
            try {
                RulesExporter exporter = new RulesExporter(new ArrayList<>(mSelectedTypes), mPackages);
                exporter.saveRules(mUri);
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.the_export_was_successful, Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.export_failed, Toast.LENGTH_LONG).show());
            }
            if (activity instanceof SettingsActivity) {
                activity.runOnUiThread(() -> ((SettingsActivity) activity).progressIndicator.hide());
            }
        }).start();
    }

    private void handleImport() {
        new Thread(() -> {
            try (RulesImporter importer = new RulesImporter(new ArrayList<>(mSelectedTypes))) {
                importer.addRulesFromUri(mUri, Users.getCurrentUserHandle());
                if (mPackages != null) importer.setPackagesToImport(mPackages);
                importer.applyRules();
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.the_import_was_successful, Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.import_failed, Toast.LENGTH_LONG).show());
            }
            if (activity instanceof SettingsActivity) {
                activity.runOnUiThread(() -> ((SettingsActivity) activity).progressIndicator.hide());
            }
        }).start();
    }
}
