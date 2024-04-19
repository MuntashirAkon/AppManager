// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;
import io.github.muntashirakon.AppManager.utils.LangUtils;
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
    private MainPreferencesViewModel mModel;
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
        mModes = getResources().getStringArray(R.array.modes);
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
        // Device info
        mModel.getDeviceInfo().observe(getViewLifecycleOwner(), deviceInfo -> {
            View v = View.inflate(mActivity, io.github.muntashirakon.ui.R.layout.dialog_scrollable_text_view, null);
            ((TextView) v.findViewById(android.R.id.content)).setText(deviceInfo.toLocalizedString(mActivity));
            v.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
            new AlertDialogBuilder(mActivity, true).setTitle(R.string.about_device).setView(v).show();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mModePref != null) {
            mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                    mModes[MODE_NAMES.indexOf(Ops.getMode())], Ops.getInferredMode(mActivity)));
        }
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
