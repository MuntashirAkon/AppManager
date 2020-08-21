/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spanned;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.text.HtmlCompat;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentUtils;
import io.github.muntashirakon.AppManager.rules.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.servermanager.AppOps;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

public class SettingsActivity extends AppCompatActivity {
    private static List<Integer> themeConst = Arrays.asList(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES);

    private AppPref appPref;
    private int currentTheme;
    public ProgressIndicator progressIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.hide();
        appPref = AppPref.getInstance();

        final SwitchMaterial rootSwitcher = findViewById(R.id.root_toggle_btn);
        final SwitchMaterial blockingSwitcher = findViewById(R.id.blocking_toggle_btn);
        final SwitchMaterial usageSwitcher = findViewById(R.id.usage_toggle_btn);

        final View globalBlockingView = findViewById(R.id.blocking_view);
        final View importExportView = findViewById(R.id.import_view);
        final View removeAllView = findViewById(R.id.remove_all_rules);
        final TextView appThemeMsg = findViewById(R.id.app_theme_msg);

        // Read pref
        boolean rootEnabled = appPref.getBoolean(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL);
        boolean adbEnabled = appPref.getBoolean(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL);
        boolean blockingEnabled = appPref.getBoolean(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL);
        boolean usageEnabled = appPref.getBoolean(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL);
        currentTheme = appPref.getInt(AppPref.PrefKey.PREF_APP_THEME_INT);

        // Set changed values
        rootSwitcher.setChecked(rootEnabled);
        globalBlockingView.setVisibility(rootEnabled ? View.VISIBLE : View.GONE);
        importExportView.setVisibility(rootEnabled || adbEnabled ? View.VISIBLE : View.GONE);
        removeAllView.setVisibility(rootEnabled || adbEnabled ? View.VISIBLE : View.GONE);
        blockingSwitcher.setChecked(blockingEnabled);
        usageSwitcher.setChecked(usageEnabled);
        final String[] themes = getResources().getStringArray(R.array.themes);
        appThemeMsg.setText(String.format(Locale.getDefault(), getString(R.string.current_theme), themes[themeConst.indexOf(currentTheme)]));

        // Set listeners
        // App theme
        findViewById(R.id.app_theme).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.select_theme)
                        .setSingleChoiceItems(themes, themeConst.indexOf(currentTheme),
                                (dialog, which) -> currentTheme = themeConst.get(which))
                        .setPositiveButton(R.string.apply, (dialog, which) -> {
                            appPref.setPref(AppPref.PrefKey.PREF_APP_THEME_INT, currentTheme);
                            AppCompatDelegate.setDefaultNightMode(currentTheme);
                            Intent intent = new Intent(this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()
                        .show());
        // Root mode switcher
        rootSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, isChecked);
            // Change server type based on root status
            AppOps.updateConfig(this);
            // Toggle GCB view based on root status
            globalBlockingView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            importExportView.setVisibility(isChecked || adbEnabled ? View.VISIBLE : View.GONE);
            removeAllView.setVisibility(isChecked || adbEnabled ? View.VISIBLE : View.GONE);
        });
        // GCB switcher
        blockingSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PrefKey.PREF_GLOBAL_BLOCKING_ENABLED_BOOL, isChecked);
            if (AppPref.isRootEnabled() && isChecked) {
                // Apply all rules immediately if GCB is true
                ComponentsBlocker.applyAllRules(this);
            }
        });
        // App usage permission toggle
        usageSwitcher.setOnCheckedChangeListener((buttonView, isChecked) ->
                appPref.setPref(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL, isChecked));
        // Import/Export view
        importExportView.setOnClickListener(v -> new ImportExportDialogFragment()
                .show(getSupportFragmentManager(), ImportExportDialogFragment.TAG));
        // Remove all rules view
        removeAllView.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pref_remove_all_rules)
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    progressIndicator.show();
                    new Thread(() -> {
                        List<String> packages = ComponentUtils.getAllPackagesWithRules();
                        for (String packageName: packages) {
                            ComponentUtils.removeAllRules(packageName);
                        }
                        runOnUiThread(() -> {
                            progressIndicator.hide();
                            Toast.makeText(this, R.string.the_operation_was_successful, Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton(R.string.no, null)
                .show());
        // About
        findViewById(R.id.about_view).setOnClickListener(v -> {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_about, null);
            ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.ROOT,
                    "%s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
            new FullscreenDialog(this).setTitle(R.string.about).setView(view).show();
            });
        // Changelog
        findViewById(R.id.changelog_view).setOnClickListener(v -> new Thread(() -> {
            final Spanned spannedChangelog = HtmlCompat.fromHtml(Utils.getContentFromAssets(this, "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
            runOnUiThread(() -> {
                View view = getLayoutInflater().inflate(R.layout.dialog_changelog, null);
                ((MaterialTextView) view.findViewById(R.id.content)).setText(spannedChangelog);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.changelog)
                        .setView(view)
                        .setNegativeButton(android.R.string.ok, null)
                        .show();
            });
        }).start());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}