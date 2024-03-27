// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.settings.crypto.ImportExportKeyStoreDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.MultithreadedExecutor;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

public class AdvancedPreferences extends PreferenceFragment {
    public static final String[] APK_NAME_FORMATS = new String[]{
            "%label%",
            "%package_name%",
            "%version%",
            "%version_code%",
            "%min_sdk%",
            "%target_sdk%",
            "%datetime%"
    };

    private int mThreadCount;
    private MainPreferencesViewModel mModel;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_advanced, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        // Selected users
        Preference usersPref = Objects.requireNonNull(findPreference("selected_users"));
        usersPref.setOnPreferenceClickListener(preference -> {
            mModel.loadAllUsers();
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
            AlertDialog dialog = new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.pref_saved_apk_name_format)
                    .setView(view)
                    .setPositiveButton(R.string.save, (dialog1, which) -> {
                        Editable apkFormat = inputApkNameFormat.getText();
                        if (!TextUtils.isEmpty(apkFormat)) {
                            AppPref.set(AppPref.PrefKey.PREF_SAVED_APK_FORMAT_STR, apkFormat.toString().trim());
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(dialog1 -> inputApkNameFormat.postDelayed(() -> {
                inputApkNameFormat.requestFocus();
                inputApkNameFormat.requestFocusFromTouch();
                inputApkNameFormat.setSelection(inputApkNameFormat.length());
                UiUtils.showKeyboard(inputApkNameFormat);
            }, 200));
            dialog.show();
            return true;
        });
        // Thread count
        Preference threadCountPref = Objects.requireNonNull(findPreference("thread_count"));
        mThreadCount = MultithreadedExecutor.getThreadCount();
        threadCountPref.setSummary(getResources().getQuantityString(R.plurals.pref_thread_count_msg, mThreadCount, mThreadCount));
        threadCountPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(requireActivity(), null)
                    .setTitle(R.string.pref_thread_count)
                    .setHelperText(getString(R.string.pref_thread_count_hint, Utils.getTotalCores()))
                    .setInputText(String.valueOf(mThreadCount))
                    .setInputInputType(InputType.TYPE_CLASS_NUMBER)
                    .setInputImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null && TextUtils.isDigitsOnly(inputText)) {
                            int c = Integer.decode(inputText.toString());
                            MultithreadedExecutor.setThreadCount(c);
                            mThreadCount = MultithreadedExecutor.getThreadCount();
                            threadCountPref.setSummary(getResources().getQuantityString(R.plurals.pref_thread_count_msg, mThreadCount, mThreadCount));
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
        // Send notifications to the connected device
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("send_notifications_to_connected_devices")))
                .setChecked(Prefs.Misc.sendNotificationsToConnectedDevices());
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
        mModel.selectUsers().observe(getViewLifecycleOwner(), users -> {
            if (users == null) return;
            int[] selectedUsers = Prefs.Misc.getSelectedUsers();
            Integer[] userIds = new Integer[users.size()];
            CharSequence[] userInfo = new CharSequence[users.size()];
            List<Integer> preselectedUserIds = new ArrayList<>();
            for (int i = 0; i < users.size(); ++i) {
                userIds[i] = users.get(i).id;
                userInfo[i] = users.get(i).toLocalizedString(requireContext());
                if (selectedUsers == null || ArrayUtils.contains(selectedUsers, userIds[i])) {
                    preselectedUserIds.add(userIds[i]);
                }
            }
            new SearchableMultiChoiceDialogBuilder<>(requireActivity(), userIds, userInfo)
                    .setTitle(R.string.pref_selected_users)
                    .addSelections(preselectedUserIds)
                    .setPositiveButton(R.string.save, (dialog, which, selectedUserIds) -> {
                        if (!selectedUserIds.isEmpty()) {
                            Prefs.Misc.setSelectedUsers(ArrayUtils.convertToIntArray(selectedUserIds));
                        } else Prefs.Misc.setSelectedUsers(null);
                        Utils.relaunchApp(requireActivity());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.use_default, (dialog, which, selectedUserIds) -> {
                        Prefs.Misc.setSelectedUsers(null);
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
