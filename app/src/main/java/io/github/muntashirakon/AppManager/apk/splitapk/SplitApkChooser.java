// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerViewModel;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class SplitApkChooser extends Fragment {
    public static final String TAG = SplitApkChooser.class.getSimpleName();

    private static final String EXTRA_ACTION_NAME = "name";
    private static final String EXTRA_VERSION_INFO = "version";

    @NonNull
    public static SplitApkChooser getNewInstance(@NonNull String versionInfo, @Nullable String actionName) {
        SplitApkChooser splitApkChooser = new SplitApkChooser();
        Bundle args = new Bundle();
        args.putString(EXTRA_ACTION_NAME, actionName);
        args.putString(EXTRA_VERSION_INFO, versionInfo);
        splitApkChooser.setArguments(args);
        return splitApkChooser;
    }

    private PackageInstallerViewModel mViewModel;
    private List<ApkFile.Entry> mApkEntries;
    private SearchableMultiChoiceDialogBuilder<String> mViewBuilder;
    private Set<String> mSelectedSplits;
    private final HashMap<String /* feature */, HashSet<Integer> /* seen types */> mSeenSplits = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(PackageInstallerViewModel.class);
        mSelectedSplits = mViewModel.getSelectedSplits();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ApkFile apkFile = mViewModel.getApkFile();
        if (apkFile == null) {
            throw new IllegalArgumentException("ApkFile cannot be empty.");
        }
        if (!apkFile.isSplit()) {
            throw new RuntimeException("Apk file does not contain any split.");
        }
        mApkEntries = apkFile.getEntries();
        String[] entryIds = new String[mApkEntries.size()];
        CharSequence[] entryNames = new CharSequence[mApkEntries.size()];
        for (int i = 0; i < mApkEntries.size(); ++i) {
            ApkFile.Entry entry = mApkEntries.get(i);
            entryIds[i] = entry.id;
            entryNames[i] = entry.toLocalizedString(requireActivity());
        }
        mViewBuilder = new SearchableMultiChoiceDialogBuilder<>(requireActivity(), entryIds, entryNames)
                .showSelectAll(false)
                .addDisabledItems(getUnsupportedOrRequiredSplitIds());
        mViewBuilder.create(); // Necessary to trigger multichoice dialog
        return mViewBuilder.getView();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewBuilder.addSelections(getInitialSelections())
                .setOnMultiChoiceClickListener((dialog, which, item, isChecked) -> {
                    if (isChecked) {
                        mViewBuilder.addSelections(select(which));
                    } else {
                        int[] itemsToDeselect = deselect(which);
                        if (itemsToDeselect == null) {
                            // This item can't be deselected, reselect the item
                            mViewBuilder.addSelections(new int[]{which});
                        } else {
                            mViewBuilder.removeSelections(itemsToDeselect);
                        }
                    }
                    mViewBuilder.reloadListUi();
                });
    }

    @NonNull
    public int[] getInitialSelections() {
        List<Integer> selections = new ArrayList<>();
        try {
            HashSet<String> splitNames = new HashSet<>();
            // See if the app has been installed
            if (mViewModel.getInstalledPackageInfo() != null) {
                ApplicationInfo info = mViewModel.getInstalledPackageInfo().applicationInfo;
                try (ApkFile installedApkFile = ApkSource.getApkSource(info).resolve()) {
                    for (ApkFile.Entry apkEntry : installedApkFile.getEntries()) {
                        splitNames.add(apkEntry.name);
                    }
                }
            }
            if (splitNames.size() > 0) {
                for (ApkFile.Entry apkEntry : mApkEntries) {
                    if (!splitNames.contains(apkEntry.name)) {
                        // Ignore splits that weren't selected in the previous installation
                        continue;
                    }
                    mSelectedSplits.add(apkEntry.id);
                    HashSet<Integer> types = mSeenSplits.get(apkEntry.getFeature());
                    if (types == null) {
                        types = new HashSet<>();
                        mSeenSplits.put(apkEntry.getFeature(), types);
                    }
                    types.add(apkEntry.type);
                }
                // Fall-through deliberately to see if there are any new requirements
            }
        } catch (ApkFile.ApkFileException ignored) {
        }
        // Set up features
        for (int i = 0; i < mApkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = mApkEntries.get(i);
            if (mSelectedSplits.contains(apkEntry.id)) {
                // Features already set
                selections.add(i);
                continue;
            }
            if (apkEntry.isRequired()) {
                // Required splits are selected by default
                mSelectedSplits.add(apkEntry.id);
                selections.add(i);
                HashSet<Integer> types = mSeenSplits.get(apkEntry.getFeature());
                if (types == null) {
                    types = new HashSet<>();
                    mSeenSplits.put(apkEntry.getFeature(), types);
                }
                types.add(apkEntry.type);
            }
        }
        // Select feature-dependencies based on the items selected above.
        // Only selecting the first item works because the splits are already ranked.
        for (int i = 0; i < mApkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = mApkEntries.get(i);
            if (mSelectedSplits.contains(apkEntry.id)) {
                // Already selected
                continue;
            }
            HashSet<Integer> types = mSeenSplits.get(apkEntry.getFeature());
            if (types == null) {
                // This feature was not selected earlier
                continue;
            }
            switch (apkEntry.type) {
                case ApkFile.APK_BASE:
                case ApkFile.APK_SPLIT_FEATURE:
                case ApkFile.APK_SPLIT_UNKNOWN:
                case ApkFile.APK_SPLIT:
                    // Never reached.
                    break;
                case ApkFile.APK_SPLIT_DENSITY:
                    if (!types.contains(ApkFile.APK_SPLIT_DENSITY)) {
                        types.add(ApkFile.APK_SPLIT_DENSITY);
                        selections.add(i);
                        mSelectedSplits.add(apkEntry.id);
                    }
                    break;
                case ApkFile.APK_SPLIT_ABI:
                    if (!types.contains(ApkFile.APK_SPLIT_ABI)) {
                        types.add(ApkFile.APK_SPLIT_ABI);
                        selections.add(i);
                        mSelectedSplits.add(apkEntry.id);
                    }
                    break;
                case ApkFile.APK_SPLIT_LOCALE:
                    if (!types.contains(ApkFile.APK_SPLIT_LOCALE)) {
                        types.add(ApkFile.APK_SPLIT_LOCALE);
                        selections.add(i);
                        mSelectedSplits.add(apkEntry.id);
                    }
                    break;
                default:
                    throw new RuntimeException("Invalid split type.");
            }
        }
        return ArrayUtils.convertToIntArray(selections);
    }

    @NonNull
    private List<String> getUnsupportedOrRequiredSplitIds() {
        List<String> unsupportedOrRequiredSplits = new ArrayList<>();
        for (ApkFile.Entry apkEntry : mApkEntries) {
            if (!apkEntry.supported() || apkEntry.isRequired()) {
                unsupportedOrRequiredSplits.add(apkEntry.id);
            }
        }
        return unsupportedOrRequiredSplits;
    }

    @NonNull
    private int[] select(int index) {
        List<Integer> selections = new ArrayList<>();
        ApkFile.Entry selectedEntry = mApkEntries.get(index);
        String feature = selectedEntry.getFeature();
        HashSet<Integer> types = mSeenSplits.get(feature);
        if (types == null) {
            types = new HashSet<>();
            mSeenSplits.put(feature, types);
        }
        mSelectedSplits.add(selectedEntry.id); // We don't need to add it to selections because it's already checked
        for (int i = 0; i < mApkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = mApkEntries.get(i);
            if (Objects.equals(apkEntry.getFeature(), feature) && apkEntry.type != selectedEntry.type) {
                // Match only the entries with the same feature and select at least one item for each required type.
                switch (apkEntry.type) {
                    case ApkFile.APK_BASE:
                    case ApkFile.APK_SPLIT_FEATURE:
                        // FIXME: 7/7/23 Never reached?
                        selections.add(i);
                        mSelectedSplits.add(apkEntry.id);
                        break;
                    case ApkFile.APK_SPLIT_UNKNOWN:
                    case ApkFile.APK_SPLIT:
                        break;
                    case ApkFile.APK_SPLIT_DENSITY:
                        if (!types.contains(ApkFile.APK_SPLIT_DENSITY)) {
                            types.add(ApkFile.APK_SPLIT_DENSITY);
                            selections.add(i);
                            mSelectedSplits.add(apkEntry.id);
                        }
                        break;
                    case ApkFile.APK_SPLIT_ABI:
                        if (!types.contains(ApkFile.APK_SPLIT_ABI)) {
                            types.add(ApkFile.APK_SPLIT_ABI);
                            selections.add(i);
                            mSelectedSplits.add(apkEntry.id);
                        }
                        break;
                    case ApkFile.APK_SPLIT_LOCALE:
                        if (!types.contains(ApkFile.APK_SPLIT_LOCALE)) {
                            types.add(ApkFile.APK_SPLIT_LOCALE);
                            selections.add(i);
                            mSelectedSplits.add(apkEntry.id);
                        }
                        break;
                    default:
                        throw new RuntimeException("Invalid split type.");
                }
            }
        }
        return ArrayUtils.convertToIntArray(selections);
    }

    @Nullable
    private int[] deselect(int index) {
        ApkFile.Entry deselectedEntry = mApkEntries.get(index);
        if (deselectedEntry.isRequired()) {
            // 1. This is a required split, can't be deselected
            return null;
        }
        boolean featureSplit = deselectedEntry.type == ApkFile.APK_SPLIT_FEATURE;
        String deselectedFeature = deselectedEntry.getFeature();
        if (featureSplit) {
            // 2. If this is a feature split (base.apk is always a required split), deselect all the associated splits
            List<Integer> deselectedSplits = new ArrayList<>();
            mSeenSplits.remove(deselectedFeature);
            for (int i = 0; i < mApkEntries.size(); ++i) {
                ApkFile.Entry apkEntry = mApkEntries.get(i);
                if (Objects.equals(apkEntry.getFeature(), deselectedFeature)) {
                    // Split has the same feature
                    if (mSelectedSplits.contains(apkEntry.id)) {
                        deselectedSplits.add(i);
                        mSelectedSplits.remove(apkEntry.id);
                    }
                }
            }
            return ArrayUtils.convertToIntArray(deselectedSplits);
        }
        // 3. This isn't a feature split. Find all the splits by the same type and see if at least one split is
        // selected. If not, this split can't be deselected.
        boolean selectedAnySplits = false;
        for (int i = 0; i < mApkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = mApkEntries.get(i);
            if (i != index
                    && deselectedEntry.type == apkEntry.type
                    && Objects.equals(apkEntry.getFeature(), deselectedFeature)
                    && mSelectedSplits.contains(apkEntry.id)) {
                // Split has the same type and is selected
                selectedAnySplits = true;
                break;
            }
        }
        if (selectedAnySplits) {
            // At least one item is selected, deselect the current one
            mSelectedSplits.remove(deselectedEntry.id);
            return EmptyArray.INT;
        }
        // This entry can't be deselected
        return null;
    }
}
