// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import com.android.internal.util.TextUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.crypto.ImportExportKeyStoreDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class AdvancedPreferences extends PreferenceFragment {
    public static final String[] APK_NAME_FORMATS = new String[] {
            "%label%",
            "%package_name%",
            "%version%",
            "%version_code%",
            "%min_sdk%",
            "%target_sdk%",
            "%datetime%"
    };

    private int threadCount;
    private MainPreferencesViewModel model;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_advanced, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        // Selected users
        Preference usersPref = Objects.requireNonNull(findPreference("selected_users"));
        usersPref.setOnPreferenceClickListener(preference -> {
            model.loadAllUsers();
            return true;
        });
        // Saved apk name format
        Preference savedApkFormatPref = Objects.requireNonNull(findPreference("saved_apk_format"));
        savedApkFormatPref.setOnPreferenceClickListener(preference -> {
            View view = getLayoutInflater().inflate(R.layout.dialog_set_apk_format, null);
            TextInputEditText inputApkNameFormat = view.findViewById(R.id.input_apk_name_format);
            inputApkNameFormat.setText(AppPref.getString(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR));
            ChipGroup apkNameFormats = view.findViewById(R.id.apk_name_formats);
            for (String apkNameFormatStr : APK_NAME_FORMATS) {
                if ("%min_sdk%".equals(apkNameFormatStr) && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // Old devices does not support min SDK
                    continue;
                }
                addChip(apkNameFormats, apkNameFormatStr).setOnClickListener(v -> {
                    Editable apkFormat = inputApkNameFormat.getText();
                    if (apkFormat != null) {
                        apkFormat.insert(inputApkNameFormat.getSelectionStart(), ((Chip) v).getText());
                    }
                });
            }
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_saved_apk_name_format)
                    .setView(view)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        Editable apkFormat = inputApkNameFormat.getText();
                        if (!TextUtils.isEmpty(apkFormat)) {
                            AppPref.set(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR, apkFormat.toString().trim());
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Thread count
        Preference threadCountPref = Objects.requireNonNull(findPreference("thread_count"));
        threadCount = MultithreadedExecutor.getThreadCount();
        threadCountPref.setSummary(getResources().getQuantityString(R.plurals.pref_thread_count_msg, threadCount, threadCount));
        threadCountPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(requireActivity(), null)
                    .setTitle(R.string.pref_thread_count)
                    .setHelperText(getString(R.string.pref_thread_count_hint, Utils.getTotalCores()))
                    .setInputText(String.valueOf(threadCount))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null && TextUtils.isDigitsOnly(inputText)) {
                            int c = Integer.decode(inputText.toString());
                            AppPref.set(AppPref.PrefKey.PREF_CONCURRENCY_THREAD_COUNT_INT, c);
                            threadCount = MultithreadedExecutor.getThreadCount();
                            threadCountPref.setSummary(getResources().getQuantityString(R.plurals.pref_thread_count_msg, threadCount, threadCount));
                        }
                    })
                    .show();
            return true;
        });
        // Import/export App Manager's KeyStore
        ((Preference) Objects.requireNonNull(findPreference("import_export_keystore")))
                .setOnPreferenceClickListener(preference -> {
                    DialogFragment fragment = new ImportExportKeyStoreDialogFragment();
                    fragment.show(getParentFragmentManager(), ImportExportKeyStoreDialogFragment.TAG);
                    return true;
                });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        model.selectUsers().observe(getViewLifecycleOwner(), users -> {
            if (users == null) return;
            int[] selectedUsers = AppPref.getSelectedUsers();
            int[] userIds = new int[users.size()];
            boolean[] choices = new boolean[users.size()];
            Arrays.fill(choices, false);
            CharSequence[] userInfo = new CharSequence[users.size()];
            for (int i = 0; i < users.size(); ++i) {
                userIds[i] = users.get(i).id;
                userInfo[i] = userIds[i] + " (" + users.get(i).name + ")";
                if (selectedUsers == null || ArrayUtils.contains(selectedUsers, userIds[i])) {
                    choices[i] = true;
                }
            }
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_selected_users)
                    .setMultiChoiceItems(userInfo, choices, (dialog, which, isChecked) -> choices[which] = isChecked)
                    .setPositiveButton(R.string.save, (dialog, which) -> {
                        List<Integer> selectedUserIds = new ArrayList<>(users.size());
                        for (int i = 0; i < choices.length; ++i) {
                            if (choices[i]) {
                                selectedUserIds.add(userIds[i]);
                            }
                        }
                        if (selectedUserIds.size() > 0) {
                            AppPref.setSelectedUsers(ArrayUtils.convertToIntArray(selectedUserIds));
                        } else AppPref.setSelectedUsers(null);
                        Utils.relaunchApp(requireActivity());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.use_default, (dialog, which) -> {
                        AppPref.setSelectedUsers(null);
                        Utils.relaunchApp(requireActivity());
                    })
                    .show();
        });
    }

    @Override
    public int getTitle() {
        return R.string.pref_cat_advanced;
    }

    @NonNull
    private static Chip addChip(@NonNull ChipGroup apkFormats, @NonNull CharSequence text) {
        Chip chip = new Chip(apkFormats.getContext());
        chip.setText(text);
        apkFormats.addView(chip);
        return chip;
    }
}
