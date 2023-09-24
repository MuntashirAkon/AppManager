// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;

public class MainPreferences extends PreferenceFragment {
    @NonNull
    public static MainPreferences getInstance(@Nullable String key) {
        MainPreferences preferences = new MainPreferences();
        Bundle args = new Bundle();
        args.putString(PREF_KEY, key);
        preferences.setArguments(args);
        return preferences;
    }

    private static final List<String> MODE_NAMES = Arrays.asList(
            Ops.MODE_AUTO,
            Ops.MODE_ROOT,
            Ops.MODE_ADB_OVER_TCP,
            Ops.MODE_ADB_WIFI,
            Ops.MODE_NO_ROOT);

    private FragmentActivity mActivity;
    private String mCurrentLang;
    @Ops.Mode
    private String mCurrentMode;
    private MainPreferencesViewModel mModel;
    private AlertDialog mModeOfOpsAlertDialog;
    private Preference mModePref;
    private String[] mModes;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        mModel = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = requireActivity();
        // Custom locale
        mCurrentLang = Prefs.Appearance.getLanguage();
        ArrayMap<String, Locale> locales = LangUtils.getAppLanguages(mActivity);
        final CharSequence[] languageNames = getLanguagesL(locales);
        final String[] languages = new String[languageNames.length];
        for (int i = 0; i < locales.size(); ++i) {
            languages[i] = locales.keyAt(i);
        }
        int localeIndex = locales.indexOfKey(mCurrentLang);
        if (localeIndex < 0) {
            localeIndex = locales.indexOfKey(LangUtils.LANG_AUTO);
        }
        Preference locale = Objects.requireNonNull(findPreference("custom_locale"));
        locale.setSummary(languageNames[localeIndex]);
        int finalLocaleIndex = localeIndex;
        locale.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(mActivity, languages, languageNames)
                    .setTitle(R.string.choose_language)
                    .setSelectionIndex(finalLocaleIndex)
                    .setPositiveButton(R.string.apply, (dialog, which, selectedItem) -> {
                        if (selectedItem != null) {
                            mCurrentLang = selectedItem;
                            Prefs.Appearance.setLanguage(mCurrentLang);
                            AppearanceUtils.applyConfigurationChangesToActivities();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Mode of operation
        mModePref = Objects.requireNonNull(findPreference("mode_of_operations"));
        mModeOfOpsAlertDialog = UIUtils.getProgressDialog(mActivity, getString(R.string.loading), true);
        mModes = getResources().getStringArray(R.array.modes);
        mCurrentMode = Ops.getMode();
        mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op, mModes[MODE_NAMES.indexOf(mCurrentMode)],
                Ops.getInferredMode(mActivity)));
        mModePref.setOnPreferenceClickListener(preference -> {
            new SearchableSingleChoiceDialogBuilder<>(mActivity, MODE_NAMES, mModes)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSelection(mCurrentMode)
                    .addDisabledItems(Build.VERSION.SDK_INT < Build.VERSION_CODES.R ?
                            Collections.singletonList(Ops.MODE_ADB_WIFI) : Collections.emptyList())
                    .setPositiveButton(R.string.apply, (dialog, which, selectedItem) -> {
                        if (selectedItem != null) {
                            mCurrentMode = selectedItem;
                            if (Ops.MODE_ADB_OVER_TCP.equals(mCurrentMode)) {
                                ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
                            }
                            Ops.setMode(mCurrentMode);
                            mModePref.setSummary(mModes[MODE_NAMES.indexOf(mCurrentMode)]);
                            mModeOfOpsAlertDialog.show();
                            mModel.setModeOfOps();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // VT API key
        ((Preference) Objects.requireNonNull(findPreference("vt_apikey"))).setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(mActivity, null)
                    .setTitle(R.string.pref_vt_apikey)
                    .setHelperText(getString(R.string.pref_vt_apikey_description) + "\n\n" + getString(R.string.vt_disclaimer))
                    .setInputText(Prefs.VirusTotal.getApiKey())
                    .setCheckboxLabel(R.string.pref_vt_prompt_before_uploading)
                    .setChecked(Prefs.VirusTotal.promptBeforeUpload())
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null) {
                            Prefs.VirusTotal.setApiKey(inputText.toString());
                        }
                        Prefs.VirusTotal.setPromptBeforeUpload(isChecked);
                    })
                    .show();
            return true;
        });
        // About device
        ((Preference) Objects.requireNonNull(findPreference("about_device")))
                .setOnPreferenceClickListener(preference -> {
                    mModel.loadDeviceInfo(new DeviceInfo2(mActivity));
                    return true;
                });

        // Hide preferences for disabled features
        if (!FeatureController.isInstallerEnabled()) {
            ((Preference) Objects.requireNonNull(findPreference("installer"))).setVisible(false);
        }
        if (!FeatureController.isLogViewerEnabled()) {
            ((Preference) Objects.requireNonNull(findPreference("log_viewer_prefs"))).setVisible(false);
        }
        mModel.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            Toast.makeText(mActivity, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, true));
        setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.Z, false));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Preference loaders
        // Mode of ops
        mModel.getModeOfOpsStatus().observe(getViewLifecycleOwner(), status -> {
            switch (status) {
                case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mModel.autoConnectAdb(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED);
                        return;
                    } // fall-through
                case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mModeOfOpsAlertDialog.dismiss();
                        Ops.connectWirelessDebugging(mActivity, mModel);
                        return;
                    } // fall-through
                case Ops.STATUS_ADB_CONNECT_REQUIRED:
                    mModeOfOpsAlertDialog.dismiss();
                    Ops.connectAdbInput(mActivity, mModel);
                    return;
                case Ops.STATUS_ADB_PAIRING_REQUIRED:
                    mModeOfOpsAlertDialog.dismiss();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.pairAdbInput(mActivity, mModel);
                        return;
                    } // fall-through
                case Ops.STATUS_FAILURE_ADB_NEED_MORE_PERMS:
                    Ops.displayIncompleteUsbDebuggingMessage(requireActivity());
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILURE:
                    mModeOfOpsAlertDialog.dismiss();
                    mCurrentMode = Ops.getMode();
                    mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                            mModes[MODE_NAMES.indexOf(mCurrentMode)], Ops.getInferredMode(mActivity)));
            }
        });
        // Device info
        mModel.getDeviceInfo().observe(getViewLifecycleOwner(), deviceInfo -> {
            View v = View.inflate(mActivity, io.github.muntashirakon.ui.R.layout.dialog_scrollable_text_view, null);
            ((TextView) v.findViewById(android.R.id.content)).setText(deviceInfo.toLocalizedString(mActivity));
            v.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
            new AlertDialogBuilder(mActivity, true).setTitle(R.string.about_device).setView(v).show();
        });
    }

    @Override
    public int getTitle() {
        return R.string.settings;
    }

    @NonNull
    private CharSequence[] getLanguagesL(@NonNull ArrayMap<String, Locale> locales) {
        CharSequence[] localesL = new CharSequence[locales.size()];
        Locale locale;
        for (int i = 0; i < locales.size(); ++i) {
            locale = locales.valueAt(i);
            if (LangUtils.LANG_AUTO.equals(locales.keyAt(i))) {
                localesL[i] = mActivity.getString(R.string.auto);
            } else localesL[i] = locale.getDisplayName(locale);
        }
        return localesL;
    }
}
