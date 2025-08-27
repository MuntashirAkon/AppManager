// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.life.BuildExpiryChecker;
import io.github.muntashirakon.AppManager.self.life.FundingCampaignChecker;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.preference.InfoAlertPreference;
import io.github.muntashirakon.preference.WarningAlertPreference;

public class MainPreferences extends PreferenceFragment {
    @NonNull
    public static MainPreferences getInstance(@Nullable String key, boolean dualPane) {
        MainPreferences preferences = new MainPreferences();
        Bundle args = new Bundle();
        args.putString(PREF_KEY, key);
        args.putBoolean(PREF_SECONDARY, dualPane);
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
    private Preference mModePref;
    private Preference mLocalePref;
    private String[] mModes;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        MainPreferencesViewModel model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        mActivity = requireActivity();
        // Expiry notice
        WarningAlertPreference buildExpiringNotice = requirePreference("app_manager_expiring_notice");
        buildExpiringNotice.setVisible(!Boolean.FALSE.equals(BuildExpiryChecker.buildExpired()));
        // Funding campaign notice
        InfoAlertPreference fundingCampaignNotice = requirePreference("funding_campaign_notice");
        fundingCampaignNotice.setVisible(FundingCampaignChecker.campaignRunning());
        // Custom locale
        mLocalePref = requirePreference("custom_locale");
        // Mode of operation
        mModePref = requirePreference("mode_of_operations");
        mModes = getResources().getStringArray(R.array.modes);

        model.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            UIUtils.displayShortToast(R.string.the_operation_was_successful);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mModePref != null) {
            mModePref.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                    mModes[MODE_NAMES.indexOf(Ops.getMode())], Ops.getInferredMode(mActivity)));
        }
        if (mLocalePref != null) {
            mLocalePref.setSummary(getLanguageName());
        }
    }

    @Override
    public int getTitle() {
        return R.string.settings;
    }

    public CharSequence getLanguageName() {
        String langTag = Prefs.Appearance.getLanguage();
        if (LangUtils.LANG_AUTO.equals(langTag)) {
            return getString(R.string.auto);
        }
        Locale locale = Locale.forLanguageTag(langTag);
        return locale.getDisplayName(locale);
    }
}
