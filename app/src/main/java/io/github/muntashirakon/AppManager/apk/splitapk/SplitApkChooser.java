// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.splitapk;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import aosp.libcore.util.EmptyArray;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkFile;
import io.github.muntashirakon.AppManager.apk.installer.PackageInstallerViewModel;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class SplitApkChooser extends DialogFragment {
    public static final String TAG = SplitApkChooser.class.getSimpleName();

    private static final String EXTRA_ACTION_NAME = "name";
    private static final String EXTRA_VERSION_INFO = "version";

    @NonNull
    public static SplitApkChooser getNewInstance(@NonNull String versionInfo, @Nullable String actionName,
                                                 @NonNull OnTriggerInstallInterface installInterface) {
        SplitApkChooser splitApkChooser = new SplitApkChooser();
        Bundle args = new Bundle();
        args.putString(EXTRA_ACTION_NAME, actionName);
        args.putString(EXTRA_VERSION_INFO, versionInfo);
        splitApkChooser.setArguments(args);
        splitApkChooser.setCancelable(false);
        splitApkChooser.setOnTriggerInstall(installInterface);
        return splitApkChooser;
    }

    public interface OnTriggerInstallInterface {
        void triggerInstall();

        void triggerCancel();
    }

    @Nullable
    private OnTriggerInstallInterface mInstallInterface;
    private PackageInstallerViewModel mViewModel;
    private View mDialogView;
    private ApkFile mApkFile;
    private final HashMap<String /* feature */, HashSet<Integer> /* seen types */> mSeenSplits = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(PackageInstallerViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String actionName = requireArguments().getString(EXTRA_ACTION_NAME);
        String versionInfo = requireArguments().getString(EXTRA_VERSION_INFO);
        ApplicationInfo appInfo = mViewModel.getNewPackageInfo().applicationInfo;
        PackageManager pm = requireActivity().getPackageManager();
        mApkFile = mViewModel.getApkFile();
        if (mApkFile == null) {
            throw new IllegalArgumentException("ApkFile cannot be empty.");
        }
        if (!mApkFile.isSplit()) {
            throw new RuntimeException("Apk file does not contain any split.");
        }
        List<ApkFile.Entry> apkEntries = mApkFile.getEntries();
        CharSequence[] entryNames = new CharSequence[apkEntries.size()];
        for (int i = 0; i < apkEntries.size(); ++i) {
            entryNames[i] = apkEntries.get(i).toLocalizedString(requireActivity());
        }
        if (mInstallInterface == null) {
            throw new RuntimeException("No install action has been set.");
        }
        SearchableMultiChoiceDialogBuilder<ApkFile.Entry> builder = new SearchableMultiChoiceDialogBuilder<>(
                requireActivity(), apkEntries, entryNames)
                .setTitle(UIUtils.getDialogTitle(requireActivity(), pm.getApplicationLabel(appInfo),
                        pm.getApplicationIcon(appInfo), versionInfo))
                .showSelectAll(false)
                .addSelections(getInitialSelections(apkEntries))
                .addDisabledItems(getUnsupportedOrRequiredSplits())
                .setPositiveButton(actionName == null ? getString(R.string.install) : actionName, (dialog, which, selectedItems) ->
                        mInstallInterface.triggerInstall())
                .setNegativeButton(R.string.cancel, (dialog, which, selectedItems) -> onCancel(dialog))
                .setCancelable(false);
        builder.setOnMultiChoiceClickListener((dialog, which, item, isChecked) -> {
            if (isChecked) {
                builder.addSelections(select(which));
            } else {
                int[] itemsToDeselect = deselect(which);
                if (itemsToDeselect == null) {
                    // Reselect the item
                    builder.addSelections(new int[]{which});
                } else {
                    builder.removeSelections(itemsToDeselect);
                }
            }
            builder.reloadListUi();
        });
        mDialogView = builder.getView();
        return builder.create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return mDialogView;
    }

    @Override
    public void onDestroy() {
        mApkFile.close();
        super.onDestroy();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        Objects.requireNonNull(mInstallInterface).triggerCancel();
    }

    public void setOnTriggerInstall(@Nullable OnTriggerInstallInterface installInterface) {
        mInstallInterface = installInterface;
    }

    @NonNull
    public int[] getInitialSelections(@NonNull final List<ApkFile.Entry> apkEntries) {
        List<Integer> selections = new ArrayList<>();
        try {
            HashSet<String> splitNames = new HashSet<>();
            // See if the app has been installed
            if (mViewModel.getInstalledPackageInfo() != null) {
                ApplicationInfo info = mViewModel.getInstalledPackageInfo().applicationInfo;
                try (ApkFile installedApkFile = new ApkFile.ApkSource(info).resolve()) {
                    for (ApkFile.Entry apkEntry : installedApkFile.getEntries()) {
                        splitNames.add(apkEntry.name);
                    }
                }
            }
            if (splitNames.size() > 0) {
                for (int i = 0; i < apkEntries.size(); ++i) {
                    ApkFile.Entry apkEntry = apkEntries.get(i);
                    if (splitNames.contains(apkEntry.name) || apkEntry.type == ApkFile.APK_BASE) {
                        HashSet<Integer> types = mSeenSplits.get(apkEntry.getFeature());
                        if (types == null) {
                            types = new HashSet<>();
                            mSeenSplits.put(apkEntry.getFeature(), types);
                        }
                        types.add(apkEntry.type);
                        mApkFile.select(i);
                    }
                }
                // Fall-through deliberately to see if there are any new requirements
            }
        } catch (ApkFile.ApkFileException ignored) {
        }
        // Set up features
        for (int i = 0; i < apkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = apkEntries.get(i);
            if (apkEntry.isSelected() || apkEntry.isRequired()) {
                selections.add(i);
                if (apkEntry.type == ApkFile.APK_BASE || apkEntry.type == ApkFile.APK_SPLIT_FEATURE) {
                    // Set feature
                    if (mSeenSplits.get(apkEntry.getFeature()) == null) {
                        mSeenSplits.put(apkEntry.getFeature(), new HashSet<>());
                    }
                }
            }
        }
        // Only selecting the first item works because the splits are already ranked.
        for (int i = 0; i < apkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = apkEntries.get(i);
            if (apkEntry.isSelected() || apkEntry.isRequired()) {
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
                    break;
                case ApkFile.APK_SPLIT_DENSITY:
                    if (!types.contains(ApkFile.APK_SPLIT_DENSITY)) {
                        types.add(ApkFile.APK_SPLIT_DENSITY);
                        selections.add(i);
                        mApkFile.select(i);
                    }
                    break;
                case ApkFile.APK_SPLIT_ABI:
                    if (!types.contains(ApkFile.APK_SPLIT_ABI)) {
                        types.add(ApkFile.APK_SPLIT_ABI);
                        selections.add(i);
                        mApkFile.select(i);
                    }
                    break;
                case ApkFile.APK_SPLIT_LOCALE:
                    if (!types.contains(ApkFile.APK_SPLIT_LOCALE)) {
                        types.add(ApkFile.APK_SPLIT_LOCALE);
                        selections.add(i);
                        mApkFile.select(i);
                    }
                    break;
                default:
                    throw new RuntimeException("Invalid split type.");
            }
        }
        return ArrayUtils.convertToIntArray(selections);
    }

    @NonNull
    private List<ApkFile.Entry> getUnsupportedOrRequiredSplits() {
        List<ApkFile.Entry> apkEntries = mApkFile.getEntries();
        List<ApkFile.Entry> unsupportedOrRequiredSplits = new ArrayList<>();
        for (ApkFile.Entry apkEntry : apkEntries) {
            if (!apkEntry.supported() || apkEntry.isRequired()) {
                unsupportedOrRequiredSplits.add(apkEntry);
            }
        }
        return unsupportedOrRequiredSplits;
    }

    @NonNull
    private int[] select(int index) {
        List<Integer> selections = new ArrayList<>();
        mApkFile.select(index);
        List<ApkFile.Entry> apkEntries = mApkFile.getEntries();
        ApkFile.Entry entry = apkEntries.get(index);
        String feature = entry.getFeature();
        HashSet<Integer> types = mSeenSplits.get(feature);
        if (types == null) {
            types = new HashSet<>();
            mSeenSplits.put(feature, types);
        }
        for (int i = 0; i < apkEntries.size(); ++i) {
            ApkFile.Entry apkEntry = apkEntries.get(i);
            if (Objects.equals(apkEntry.getFeature(), feature) && apkEntry.type != entry.type) {
                if (apkEntry.isSelected()) {
                    // Deselect unwanted items
                    mApkFile.deselect(i);
                }
                // Select wanted items only
                switch (apkEntry.type) {
                    case ApkFile.APK_BASE:
                    case ApkFile.APK_SPLIT_FEATURE:
                        mApkFile.select(i);
                        break;
                    case ApkFile.APK_SPLIT_UNKNOWN:
                    case ApkFile.APK_SPLIT:
                        break;
                    case ApkFile.APK_SPLIT_DENSITY:
                        if (!types.contains(ApkFile.APK_SPLIT_DENSITY)) {
                            types.add(ApkFile.APK_SPLIT_DENSITY);
                            selections.add(i);
                            mApkFile.select(i);
                        }
                        break;
                    case ApkFile.APK_SPLIT_ABI:
                        if (!types.contains(ApkFile.APK_SPLIT_ABI)) {
                            types.add(ApkFile.APK_SPLIT_ABI);
                            selections.add(i);
                            mApkFile.select(i);
                        }
                        break;
                    case ApkFile.APK_SPLIT_LOCALE:
                        if (!types.contains(ApkFile.APK_SPLIT_LOCALE)) {
                            types.add(ApkFile.APK_SPLIT_LOCALE);
                            selections.add(i);
                            mApkFile.select(i);
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
        List<ApkFile.Entry> apkEntries = mApkFile.getEntries();
        ApkFile.Entry entry = apkEntries.get(index);
        if (entry.isRequired()) {
            // 1. This is a required split, can't be unselected
            return null;
        }
        boolean featureSplit = entry.type == ApkFile.APK_SPLIT_FEATURE;
        String feature = entry.getFeature();
        if (featureSplit) {
            // 2. If this is a feature split (base.apk is always a required split), unselect all the associated splits
            List<Integer> deselectedSplits = new ArrayList<>();
            mSeenSplits.remove(feature);
            for (int i = 0; i < apkEntries.size(); ++i) {
                ApkFile.Entry apkEntry = apkEntries.get(i);
                if (Objects.equals(apkEntry.getFeature(), feature)) {
                    // Split has the same feature
                    if (apkEntry.isSelected()) {
                        deselectedSplits.add(i);
                        mApkFile.deselect(i);
                    }
                }
            }
            return ArrayUtils.convertToIntArray(deselectedSplits);
        } else {
            // 3. This isn't a feature split. Find all the splits by the same type and see if at least one split is
            // selected. If not, this split can't be unselected.
            boolean selectedAnySplits = false;
            for (int i = 0; i < apkEntries.size(); ++i) {
                ApkFile.Entry apkEntry = apkEntries.get(i);
                if (i != index
                        && entry.type == apkEntry.type
                        && Objects.equals(apkEntry.getFeature(), feature)
                        && apkEntry.isSelected()) {
                    // Split has the same type and is selected
                    selectedAnySplits = true;
                    break;
                }
            }
            if (selectedAnySplits) {
                // At least one item is selected, deselect the current one
                mApkFile.deselect(index);
                return EmptyArray.INT;
            }
            return null;
        }
    }
}
