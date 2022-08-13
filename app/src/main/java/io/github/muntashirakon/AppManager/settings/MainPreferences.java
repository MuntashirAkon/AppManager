// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.ArrayMap;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.changelog.ChangelogRecyclerAdapter;
import io.github.muntashirakon.AppManager.misc.DeviceInfo2;
import io.github.muntashirakon.AppManager.misc.HelpActivity;
import io.github.muntashirakon.AppManager.servermanager.ServerConfig;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.AlertDialogBuilder;
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

    private FragmentActivity activity;
    private String currentLang;
    @Ops.Mode
    private String currentMode;
    private MainPreferencesViewModel model;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        getPreferenceManager().setPreferenceDataStore(new SettingsDataStore());
        model = new ViewModelProvider(requireActivity()).get(MainPreferencesViewModel.class);
        activity = requireActivity();
        // Custom locale
        currentLang = AppPref.getString(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR);
        ArrayMap<String, Locale> locales = LangUtils.getAppLanguages(activity);
        final CharSequence[] languages = getLanguagesL(locales);
        Preference locale = Objects.requireNonNull(findPreference("custom_locale"));
        int localeIndex = locales.indexOfKey(currentLang);
        if (localeIndex < 0) {
            localeIndex = locales.indexOfKey(LangUtils.LANG_AUTO);
        }
        locale.setSummary(languages[localeIndex]);
        locale.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.choose_language)
                    .setSingleChoiceItems(languages, locales.indexOfKey(currentLang),
                            (dialog, which) -> currentLang = locales.keyAt(which))
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_CUSTOM_LOCALE_STR, currentLang);
                        ActivityCompat.recreate(activity);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // Mode of operation
        Preference mode = Objects.requireNonNull(findPreference("mode_of_operations"));
        AlertDialog modeOfOpsAlertDialog = UIUtils.getProgressDialog(activity, getString(R.string.loading));
        final String[] modes = getResources().getStringArray(R.array.modes);
        currentMode = Ops.getMode(requireContext());
        mode.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op, modes[MODE_NAMES.indexOf(currentMode)],
                getInferredMode()));
        mode.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pref_mode_of_operations)
                    .setSingleChoiceItems(modes, MODE_NAMES.indexOf(currentMode), (dialog, which) -> {
                        String modeName = MODE_NAMES.get(which);
                        if (Ops.MODE_ADB_WIFI.equals(modeName)) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                UIUtils.displayShortToast(R.string.wireless_debugging_not_supported);
                                return;
                            }
                        } else if (Ops.MODE_ADB_OVER_TCP.equals(modeName)) {
                            ServerConfig.setAdbPort(ServerConfig.DEFAULT_ADB_PORT);
                        }
                        currentMode = modeName;
                    })
                    .setPositiveButton(R.string.apply, (dialog, which) -> {
                        AppPref.set(AppPref.PrefKey.PREF_MODE_OF_OPS_STR, currentMode);
                        mode.setSummary(modes[MODE_NAMES.indexOf(currentMode)]);
                        modeOfOpsAlertDialog.show();
                        model.setModeOfOps();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        // VT API key
        ((Preference) Objects.requireNonNull(findPreference("vt_apikey"))).setOnPreferenceClickListener(preference -> {
            new TextInputDialogBuilder(activity, null)
                    .setTitle(R.string.pref_vt_apikey)
                    .setHelperText(getString(R.string.pref_vt_apikey_description) + "\n\n" + getString(R.string.vt_disclaimer))
                    .setInputText(AppPref.getVtApiKey())
                    .setCheckboxLabel(R.string.pref_vt_prompt_before_uploading)
                    .setChecked(AppPref.getBoolean(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, (dialog, which, inputText, isChecked) -> {
                        if (inputText != null) {
                            AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_API_KEY_STR, inputText.toString());
                        }
                        AppPref.set(AppPref.PrefKey.PREF_VIRUS_TOTAL_PROMPT_BEFORE_UPLOADING_BOOL, isChecked);
                    })
                    .show();
            return true;
        });
        // About device
        ((Preference) Objects.requireNonNull(findPreference("about_device")))
                .setOnPreferenceClickListener(preference -> {
                    model.loadDeviceInfo(new DeviceInfo2(activity));
                    return true;
                });
        // About
        ((Preference) Objects.requireNonNull(findPreference("about"))).setOnPreferenceClickListener(preference -> {
            View view = View.inflate(activity, R.layout.dialog_about, null);
            ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.getDefault(),
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            new AlertDialogBuilder(activity, true)
                    .setTitle(R.string.about)
                    .setView(view)
                    .show();
            return true;
        });
        // Changelog
        ((Preference) Objects.requireNonNull(findPreference("changelog")))
                .setOnPreferenceClickListener(preference -> {
                    model.loadChangeLog();
                    return true;
                });
        // User manual
        ((Preference) Objects.requireNonNull(findPreference("user_manual")))
                .setOnPreferenceClickListener(preference -> {
                    Intent helpIntent = new Intent(requireContext(), HelpActivity.class);
                    startActivity(helpIntent);
                    return true;
                });
        // Get help
        ((Preference) Objects.requireNonNull(findPreference("get_help")))
                .setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.discussions_site)));
                    startActivity(intent);
                    return true;
                });

        // Preference loaders
        // Mode of ops
        model.getModeOfOpsStatus().observe(this, status -> {
            switch (status) {
                case Ops.STATUS_AUTO_CONNECT_WIRELESS_DEBUGGING:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        model.autoConnectAdb(Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED);
                        return;
                    } // fall-through
                case Ops.STATUS_WIRELESS_DEBUGGING_CHOOSER_REQUIRED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        modeOfOpsAlertDialog.dismiss();
                        Ops.connectWirelessDebugging(activity, model);
                        return;
                    } // fall-through
                case Ops.STATUS_ADB_CONNECT_REQUIRED:
                    modeOfOpsAlertDialog.dismiss();
                    Ops.connectAdbInput(activity, model);
                    return;
                case Ops.STATUS_ADB_PAIRING_REQUIRED:
                    modeOfOpsAlertDialog.dismiss();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Ops.pairAdbInput(activity, model);
                        return;
                    } // fall-through
                case Ops.STATUS_SUCCESS:
                case Ops.STATUS_FAILURE:
                    modeOfOpsAlertDialog.dismiss();
                    mode.setSummary(getString(R.string.mode_of_op_with_inferred_mode_of_op,
                            modes[MODE_NAMES.indexOf(currentMode)], getInferredMode()));
            }
        });
        // Changelog
        model.getChangeLog().observe(this, changelog -> {
            RecyclerView recyclerView = (RecyclerView) View.inflate(activity, R.layout.dialog_whats_new, null);
            recyclerView.setHasFixedSize(true);
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
            ChangelogRecyclerAdapter adapter = new ChangelogRecyclerAdapter();
            recyclerView.setAdapter(adapter);
            adapter.setAdapterList(changelog.getChangelogItems());
            new AlertDialogBuilder(activity, true)
                    .setTitle(R.string.changelog)
                    .setView(recyclerView)
                    .show();
        });
        // Device info
        model.getDeviceInfo().observe(this, deviceInfo -> {
            View view = View.inflate(activity, R.layout.dialog_scrollable_text_view, null);
            ((TextView) view.findViewById(android.R.id.content)).setText(deviceInfo.toLocalizedString(activity));
            view.findViewById(android.R.id.checkbox).setVisibility(View.GONE);
            new AlertDialogBuilder(activity, true).setTitle(R.string.about_device).setView(view).show();
        });
        // Hide preferences for disabled features
        if (!FeatureController.isInstallerEnabled()) {
            ((Preference) Objects.requireNonNull(findPreference("installer"))).setVisible(false);
        }
        if (!FeatureController.isLogViewerEnabled()) {
            ((Preference) Objects.requireNonNull(findPreference("log_viewer_prefs"))).setVisible(false);
        }
        model.getOperationCompletedLiveData().observe(requireActivity(), completed -> {
            if (requireActivity() instanceof SettingsActivity) {
                ((SettingsActivity) requireActivity()).progressIndicator.hide();
            }
            Toast.makeText(activity, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.settings);
    }

    @NonNull
    private CharSequence[] getLanguagesL(@NonNull ArrayMap<String, Locale> locales) {
        CharSequence[] localesL = new CharSequence[locales.size()];
        Locale locale;
        for (int i = 0; i < locales.size(); ++i) {
            locale = locales.valueAt(i);
            if (LangUtils.LANG_AUTO.equals(locales.keyAt(i))) {
                localesL[i] = activity.getString(R.string.auto);
            } else localesL[i] = locale.getDisplayName(locale);
        }
        return localesL;
    }

    @NonNull
    private CharSequence getInferredMode() {
        if (Ops.isRoot()) {
            return getString(R.string.root);
        }
        if (Ops.isAdb()) {
            return "ADB";
        }
        return getString(R.string.no_root);
    }
}
