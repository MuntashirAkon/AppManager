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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.preference.InfoAlertPreference;
import io.github.muntashirakon.preference.WarningAlertPreference;

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
        // Expiry notice
        WarningAlertPreference buildExpiringNotice = requirePreference("app_manager_expiring_notice");
        buildExpiringNotice.setVisible(!Boolean.FALSE.equals(BuildExpiryChecker.buildExpired()));
        // Funding campaign notice
        InfoAlertPreference fundingCampaignNotice = requirePreference("funding_campaign_notice");
        fundingCampaignNotice.setVisible(FundingCampaignChecker.campaignRunning());
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
        Preference locale = requirePreference("custom_locale");
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
        mModePref = requirePreference("mode_of_operations");
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
        // About device
        requirePreference("about_device").setOnPreferenceClickListener(preference -> {
            mModel.loadDeviceInfo(new DeviceInfo2(mActivity));
            return true;
        });

        mModel.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            Toast.makeText(mActivity, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
        });
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
