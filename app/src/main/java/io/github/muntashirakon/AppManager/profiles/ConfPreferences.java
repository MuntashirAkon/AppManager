// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.backup.BackupFlags;
import io.github.muntashirakon.AppManager.profiles.struct.AppsProfile;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.AppManager.utils.Utils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.util.UiUtils;

public class ConfPreferences extends PreferenceFragmentCompat {
    private AppsProfileActivity mActivity;
    private ProfileViewModel mModel;

    @AppsProfile.ProfileState
    private final List<String> mStates = Arrays.asList(AppsProfile.STATE_ON, AppsProfile.STATE_OFF);
    @Nullable
    private String[] mComponents;
    @Nullable
    private String[] mAppOps;
    @Nullable
    private String[] mPermissions;
    @Nullable
    private AppsProfile.BackupInfo mBackupInfo;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // https://github.com/androidx/androidx/blob/androidx-main/preference/preference/res/layout/preference_recyclerview.xml
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setFitsSystemWindows(true);
        recyclerView.setClipToPadding(false);
        UiUtils.applyWindowInsetsAsPadding(recyclerView, false, true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_profile_config, rootKey);
        getPreferenceManager().setPreferenceDataStore(new ConfDataStore());
        mActivity = (AppsProfileActivity) requireActivity();
        if (mActivity.model == null) {
            // ViewModel should never be null.
            // If it's null, it means that we're on the wrong Fragment
            return;
        }
        mModel = mActivity.model;
        // Set profile ID
        Preference profileIdPref = Objects.requireNonNull(findPreference("profile_id"));
        profileIdPref.setSummary(mModel.getProfileId());
        profileIdPref.setOnPreferenceClickListener(preference -> {
            Utils.copyToClipboard(mActivity, mModel.getProfileName(), mModel.getProfileId());
            return true;
        });
        // Set comment
        Preference commentPref = Objects.requireNonNull(findPreference("comment"));
        commentPref.setSummary(mModel.getComment());
        commentPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.comment)
                    .setTitle(R.string.comment)
                    .setInputText(mModel.getComment())
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        mModel.setComment(TextUtils.isEmpty(inputText) ? null : inputText.toString());
                        commentPref.setSummary(mModel.getComment());
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Set state
        Preference statePref = Objects.requireNonNull(findPreference("state"));
        final String[] statesL = new String[]{
                getString(R.string.on),
                getString(R.string.off)
        };
        statePref.setTitle(getString(R.string.process_state, statesL[mStates.indexOf(mModel.getState())]));
        statePref.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(mActivity, mStates, statesL)
                    .setTitle(R.string.profile_state)
                    .setSelection(mModel.getState())
                    .setOnSingleChoiceClickListener((dialog, which, item, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        mModel.setState(mStates.get(which));
                        statePref.setTitle(getString(R.string.process_state, statesL[which]));
                        dialog.dismiss();
                    })
                    .show();
            return true;
        });
        // Set users
        Preference usersPref = Objects.requireNonNull(findPreference("users"));
        handleUsersPref(usersPref);
        // Set components
        Preference componentsPref = Objects.requireNonNull(findPreference("components"));
        updateComponentsPref(componentsPref);
        componentsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.input_signatures)
                    .setTitle(R.string.components)
                    .setInputText(mComponents == null ? "" : TextUtils.join(" ", mComponents))
                    .setHelperText(R.string.input_signatures_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newComponents = inputText.toString().split("\\s+");
                            mModel.setComponents(newComponents);
                        } else mModel.setComponents(null);
                        updateComponentsPref(componentsPref);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        mModel.setComponents(null);
                        updateComponentsPref(componentsPref);
                    })
                    .show();
            return true;
        });
        // Set app ops
        Preference appOpsPref = Objects.requireNonNull(findPreference("app_ops"));
        updateAppOpsPref(appOpsPref);
        appOpsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.input_app_ops)
                    .setTitle(R.string.app_ops)
                    .setInputText(mAppOps == null ? "" : TextUtils.join(" ", mAppOps))
                    .setHelperText(R.string.input_app_ops_description_profile)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newAppOps = inputText.toString().split("\\s+");
                            mModel.setAppOps(newAppOps);
                        } else mModel.setAppOps(null);
                        updateAppOpsPref(appOpsPref);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        mModel.setAppOps(null);
                        updateAppOpsPref(appOpsPref);
                    })
                    .show();
            return true;
        });
        // Set permissions
        Preference permissionsPref = Objects.requireNonNull(findPreference("permissions"));
        updatePermissionsPref(permissionsPref);
        permissionsPref.setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, R.string.input_permissions)
                    .setTitle(R.string.declared_permission)
                    .setInputText(mPermissions == null ? "" : TextUtils.join(" ", mPermissions))
                    .setHelperText(R.string.input_permissions_description)
                    .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                        if (!TextUtils.isEmpty(inputText)) {
                            String[] newPermissions = inputText.toString().split("\\s+");
                            mModel.setPermissions(newPermissions);
                        } else mModel.setPermissions(null);
                        updatePermissionsPref(permissionsPref);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, inputText, isChecked) -> {
                        mModel.setPermissions(null);
                        updatePermissionsPref(permissionsPref);
                    })
                    .show();
            return true;
        });
        Preference backupDataPref = Objects.requireNonNull(findPreference("backup_data"));
        mBackupInfo = mModel.getBackupInfo();
        backupDataPref.setSummary(mBackupInfo != null ? R.string.enabled : R.string.disabled_app);
        backupDataPref.setOnPreferenceClickListener(preference -> {
            View view = View.inflate(mActivity, R.layout.dialog_profile_backup_restore, null);
            final BackupFlags flags;
            if (mBackupInfo != null) {
                flags = new BackupFlags(mBackupInfo.flags);
            } else flags = BackupFlags.fromPref();
            final AtomicInteger backupFlags = new AtomicInteger(flags.getFlags());
            view.findViewById(R.id.dialog_button).setOnClickListener(v -> {
                List<Integer> supportedBackupFlags = BackupFlags.getSupportedBackupFlagsAsArray();
                new SearchableMultiChoiceDialogBuilder<>(requireActivity(), supportedBackupFlags,
                        BackupFlags.getFormattedFlagNames(requireContext(), supportedBackupFlags))
                        .setTitle(R.string.backup_options)
                        .addSelections(flags.flagsToCheckedIndexes(supportedBackupFlags))
                        .hideSearchBar(true)
                        .showSelectAll(false)
                        .setPositiveButton(R.string.save, (dialog, which, selectedItems) -> {
                            int flagsInt = 0;
                            for (int flag : selectedItems) {
                                flagsInt |= flag;
                            }
                            flags.setFlags(flagsInt);
                            backupFlags.set(flags.getFlags());
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
            final TextInputEditText editText = view.findViewById(android.R.id.input);
            if (mBackupInfo != null) {
                editText.setText(mBackupInfo.name);
            }
            new MaterialAlertDialogBuilder(mActivity)
                    .setTitle(R.string.backup_restore)
                    .setView(view)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        if (mBackupInfo == null) {
                            mBackupInfo = new AppsProfile.BackupInfo();
                        }
                        CharSequence backupName = editText.getText();
                        BackupFlags backupFlags1 = new BackupFlags(backupFlags.get());
                        if (!TextUtils.isEmpty(backupName)) {
                            backupFlags1.addFlag(BackupFlags.BACKUP_MULTIPLE);
                            mBackupInfo.name = backupName.toString();
                        } else {
                            backupFlags1.removeFlag(BackupFlags.BACKUP_MULTIPLE);
                            mBackupInfo.name = null;
                        }
                        mBackupInfo.flags = backupFlags1.getFlags();
                        mModel.setBackupInfo(mBackupInfo);
                        backupDataPref.setSummary(R.string.enabled);
                    })
                    .setNegativeButton(R.string.disable, (dialog, which) -> {
                        mModel.setBackupInfo(mBackupInfo = null);
                        backupDataPref.setSummary(R.string.disabled_app);
                    })
                    .show();
            return true;
        });
        // Set export rules
        Preference exportRulesPref = Objects.requireNonNull(findPreference("export_rules"));
        int rulesCount = RulesTypeSelectionDialogFragment.RULE_TYPES.length;
        List<Integer> checkedItems = new ArrayList<>(rulesCount);
        List<Integer> selectedRules = updateExportRulesPref(exportRulesPref);
        for (int i = 0; i < rulesCount; ++i) checkedItems.add(1 << i);
        exportRulesPref.setOnPreferenceClickListener(preference -> {
            new SearchableMultiChoiceDialogBuilder<>(mActivity, checkedItems, R.array.rule_types)
                    .setTitle(R.string.options)
                    .hideSearchBar(true)
                    .addSelections(selectedRules)
                    .setPositiveButton(R.string.ok, (dialog, which, selectedItems) -> {
                        int value = 0;
                        for (int item : selectedItems) value |= item;
                        if (value != 0) {
                            mModel.setExportRules(value);
                        } else mModel.setExportRules(null);
                        selectedRules.clear();
                        selectedRules.addAll(updateExportRulesPref(exportRulesPref));
                    })
                    .setNegativeButton(R.string.disable, (dialog, which, selectedItems) -> {
                        mModel.setExportRules(null);
                        selectedRules.clear();
                        selectedRules.addAll(updateExportRulesPref(exportRulesPref));
                    })
                    .show();
            return true;
        });
        // Set others
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("freeze")))
                .setChecked(mModel.getBoolean("freeze", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("force_stop")))
                .setChecked(mModel.getBoolean("force_stop", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("clear_cache")))
                .setChecked(mModel.getBoolean("clear_cache", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("clear_data")))
                .setChecked(mModel.getBoolean("clear_data", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("block_trackers")))
                .setChecked(mModel.getBoolean("block_trackers", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("save_apk")))
                .setChecked(mModel.getBoolean("save_apk", false));
        ((SwitchPreferenceCompat) Objects.requireNonNull(findPreference("allow_routine")))
                .setChecked(mModel.getBoolean("allow_routine", false));
    }

    @NonNull
    private List<Integer> updateExportRulesPref(Preference pref) {
        Integer rules = mModel.getExportRules();
        List<Integer> selectedRules = new ArrayList<>();
        if (rules == null || rules == 0) pref.setSummary(R.string.disabled_app);
        else {
            List<String> selectedRulesStr = new ArrayList<>();
            int i = 0;
            while (rules != 0) {
                int flag = (rules & (~(1 << i)));
                if (flag != rules) {
                    selectedRulesStr.add(RulesTypeSelectionDialogFragment.RULE_TYPES[i].toString());
                    rules = flag;
                    selectedRules.add(1 << i);
                }
                ++i;
            }
            pref.setSummary(TextUtils.join(", ", selectedRulesStr));
        }
        return selectedRules;
    }

    private void updateComponentsPref(Preference pref) {
        mComponents = mModel.getComponents();
        if (mComponents == null || mComponents.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", mComponents));
        }
    }

    private void updateAppOpsPref(Preference pref) {
        mAppOps = mModel.getAppOps();
        if (mAppOps == null || mAppOps.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", mAppOps));
        }
    }

    private void updatePermissionsPref(Preference pref) {
        mPermissions = mModel.getPermissions();
        if (mPermissions == null || mPermissions.length == 0) pref.setSummary(R.string.disabled_app);
        else {
            pref.setSummary(TextUtils.join(", ", mPermissions));
        }
    }

    private List<Integer> mSelectedUsers;

    private void handleUsersPref(Preference pref) {
        List<UserInfo> users = Users.getUsers();
        if (users.size() > 1) {
            pref.setVisible(true);
            CharSequence[] userNames = new String[users.size()];
            List<Integer> userHandles = new ArrayList<>(users.size());
            int i = 0;
            for (UserInfo info : users) {
                userNames[i] = info.toLocalizedString(requireContext());
                userHandles.add(info.id);
                ++i;
            }
            mSelectedUsers = new ArrayList<>();
            for (Integer user : mModel.getUsers()) {
                mSelectedUsers.add(user);
            }
            mActivity.runOnUiThread(() -> {
                pref.setSummary(TextUtilsCompat.joinSpannable(", ", getUserInfo(users, mSelectedUsers)));
                pref.setOnPreferenceClickListener(v -> {
                    new SearchableMultiChoiceDialogBuilder<>(mActivity, userHandles, userNames)
                            .setTitle(R.string.select_user)
                            .addSelections(mSelectedUsers)
                            .showSelectAll(false)
                            .setPositiveButton(R.string.ok, (dialog, which, selectedUserHandles) -> {
                                if (selectedUserHandles.isEmpty()) {
                                    mSelectedUsers = userHandles;
                                } else mSelectedUsers = selectedUserHandles;
                                pref.setSummary(TextUtilsCompat.joinSpannable(", ", getUserInfo(users, mSelectedUsers)));
                                mModel.setUsers(ArrayUtils.convertToIntArray(mSelectedUsers));
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                });
            });
        } else {
            mActivity.runOnUiThread(() -> pref.setVisible(false));
        }
    }

    @NonNull
    private List<CharSequence> getUserInfo(@NonNull List<UserInfo> userInfoList, @NonNull List<Integer> userHandles) {
        List<CharSequence> userInfoOut = new ArrayList<>();
        for (UserInfo info : userInfoList) {
            if (userHandles.contains(info.id)) {
                userInfoOut.add(info.toLocalizedString(requireContext()));
            }
        }
        return userInfoOut;
    }

    public class ConfDataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            mModel.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defValue) {
            return mModel.getBoolean(key, defValue);
        }
    }
}
