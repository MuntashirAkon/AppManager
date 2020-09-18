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

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.SparseBooleanArray;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;

public class SplitApkChooser extends DialogFragment {
    public static final String TAG = "SplitApkChooser";
    public static final String EXTRA_APK_FILE_KEY = "EXTRA_APK_FILE_KEY";
    public static final String EXTRA_ACTION_NAME = "EXTRA_ACTION_NAME";
    public static final String EXTRA_APP_INFO = "EXTRA_APP_INFO";

    public interface InstallInterface {
        void triggerInstall();

        void triggerCancel();
    }

    public void setOnTriggerInstall(InstallInterface installInterface) {
        this.installInterface = installInterface;
    }

    InstallInterface installInterface;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        int apkFileKey = requireArguments().getInt(EXTRA_APK_FILE_KEY, -1);
        String actionName = requireArguments().getString(EXTRA_ACTION_NAME);
        ApplicationInfo appInfo = requireArguments().getParcelable(EXTRA_APP_INFO);
        PackageManager pm = requireActivity().getPackageManager();
        if (apkFileKey == -1 || appInfo == null) {
            throw new IllegalArgumentException("ApkFile cannot be empty.");
        }
        ApkFile apkFile = ApkFile.getInstance(apkFileKey);
        if (!apkFile.isSplit()) throw new RuntimeException("Apk file does not contain any split.");
        List<ApkFile.Entry> apkEntries = apkFile.getEntries();
        String[] entryNames = new String[apkEntries.size()];
        boolean[] choices = new boolean[apkEntries.size()];
        Arrays.fill(choices, false);
        ApkFile.Entry apkEntry;
        String name;
        SparseBooleanArray seenSplit = new SparseBooleanArray(3);
        for (int i = 0; i < apkEntries.size(); ++i) {
            apkEntry = apkEntries.get(i);
            choices[i] = apkEntry.isSelected() || apkEntry.isRequired();
            switch (apkEntry.type) {
                case ApkFile.APK_BASE:
                    name = getString(R.string.base_apk);
                    break;
                case ApkFile.APK_SPLIT_DENSITY:
                    if (!seenSplit.get(ApkFile.APK_SPLIT_DENSITY)) {
                        seenSplit.put(ApkFile.APK_SPLIT_DENSITY, choices[i] = true);
                    }
                    if (apkEntry.forFeature != null) {
                        name = getString(R.string.density_split_for_feature, apkEntry.splitSuffix, apkEntry.getDensity(), apkEntry.forFeature);
                    } else {
                        name = getString(R.string.density_split_for_base_apk, apkEntry.splitSuffix, apkEntry.getDensity());
                    }
                    break;
                case ApkFile.APK_SPLIT_ABI:
                    if (!seenSplit.get(ApkFile.APK_SPLIT_ABI)) {
                        seenSplit.put(ApkFile.APK_SPLIT_ABI, choices[i] = true);
                    }
                    if (apkEntry.forFeature != null) {
                        name = getString(R.string.abi_split_for_feature, apkEntry.getAbi(), apkEntry.forFeature);
                    } else {
                        name = getString(R.string.abi_split_for_base_apk, apkEntry.getAbi());
                    }
                    break;
                case ApkFile.APK_SPLIT_LOCALE:
                    if (!seenSplit.get(ApkFile.APK_SPLIT_LOCALE)) {
                        seenSplit.put(ApkFile.APK_SPLIT_LOCALE, choices[i] = true);
                    }
                    if (apkEntry.forFeature != null) {
                        name = getString(R.string.locale_split_for_feature, apkEntry.getLocale().getDisplayLanguage(), apkEntry.forFeature);
                    } else {
                        name = getString(R.string.locale_split_for_base_apk, apkEntry.getLocale().getDisplayLanguage());
                    }
                    break;
                case ApkFile.APK_SPLIT_FEATURE:
                    name = getString(R.string.split_feature_name, apkEntry.name);
                    break;
                case ApkFile.APK_SPLIT_UNKNOWN:
                    name = apkEntry.name;
                    break;
                case ApkFile.APK_SPLIT:
                    if (apkEntry.forFeature != null) {
                        name = getString(R.string.unknown_split_for_feature, apkEntry.name, apkEntry.forFeature);
                    } else {
                        name = getString(R.string.unknown_split_for_base_apk, apkEntry.name);
                    }
                    break;
                default:
                    throw new RuntimeException("Invalid split type.");
            }
            entryNames[i] = name;
        }
        if (installInterface == null) throw new RuntimeException("No install action has been set.");
        return new MaterialAlertDialogBuilder(requireActivity())
                .setCancelable(false)
                .setIcon(pm.getApplicationIcon(appInfo))
                .setTitle(pm.getApplicationLabel(appInfo))
                .setMultiChoiceItems(entryNames, choices, (dialog, which, isChecked) -> {
                    if (isChecked) apkFile.select(which);
                    else apkFile.deselect(which);
                })
                .setPositiveButton(actionName == null ? getString(R.string.install) : actionName,
                        (dialog, which) -> installInterface.triggerInstall())
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel())
                .create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        installInterface.triggerCancel();
    }
}
