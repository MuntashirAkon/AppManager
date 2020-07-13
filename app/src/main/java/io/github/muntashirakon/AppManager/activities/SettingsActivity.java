package io.github.muntashirakon.AppManager.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.fragments.ImportExportDialogFragment;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class SettingsActivity extends AppCompatActivity {
    private static List<Integer> themeConst = Arrays.asList(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES);

    private AppPref appPref;
    private int currentTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        appPref = AppPref.getInstance();

        final SwitchMaterial rootSwitcher = findViewById(R.id.root_toggle_btn);
        final SwitchMaterial blockingSwitcher = findViewById(R.id.blocking_toggle_btn);
        final SwitchMaterial usageSwitcher = findViewById(R.id.usage_toggle_btn);

        final View blockingView = findViewById(R.id.blocking_view);
        final TextView appThemeMsg = findViewById(R.id.app_theme_msg);

        // Read pref
        Boolean rootEnabled = (Boolean) appPref.getPref(AppPref.PREF_ROOT_MODE_ENABLED, AppPref.TYPE_BOOLEAN);
        Boolean blockingEnabled = (Boolean) appPref.getPref(AppPref.PREF_GLOBAL_BLOCKING_ENABLED, AppPref.TYPE_BOOLEAN);
        Boolean usageEnabled = (Boolean) appPref.getPref(AppPref.PREF_USAGE_ACCESS_ENABLED, AppPref.TYPE_BOOLEAN);
        currentTheme = (int) appPref.getPref(AppPref.PREF_APP_THEME, AppPref.TYPE_INTEGER);

        // Set changed values
        rootSwitcher.setChecked(rootEnabled);
        blockingView.setVisibility(rootEnabled ? View.VISIBLE : View.GONE);
        blockingSwitcher.setChecked(blockingEnabled);
        usageSwitcher.setChecked(usageEnabled);
        final String[] themes = getResources().getStringArray(R.array.themes);
        appThemeMsg.setText(String.format(Locale.getDefault(), getString(R.string.current_theme), themes[themeConst.indexOf(currentTheme)]));

        // Set listeners
        findViewById(R.id.app_theme).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                        .setTitle(R.string.select_theme)
                        .setSingleChoiceItems(themes, themeConst.indexOf(currentTheme),
                                (dialog, which) -> currentTheme = themeConst.get(which))
                        .setPositiveButton(R.string.apply, (dialog, which) -> {
                            appPref.setPref(AppPref.PREF_APP_THEME, currentTheme);
                            AppCompatDelegate.setDefaultNightMode(currentTheme);
                            Intent intent = new Intent(this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .create()
                        .show());
        rootSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PREF_ROOT_MODE_ENABLED, isChecked);
            blockingView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        blockingSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PREF_GLOBAL_BLOCKING_ENABLED, isChecked);
            if (AppPref.isRootEnabled() && isChecked) {
                ComponentsBlocker.applyAllRules(this);
            }
        });
        usageSwitcher.setOnCheckedChangeListener((buttonView, isChecked) ->
                appPref.setPref(AppPref.PREF_USAGE_ACCESS_ENABLED, isChecked));

        // Import/Export
        if ((Boolean) appPref.getPref(AppPref.PREF_ROOT_MODE_ENABLED, AppPref.TYPE_BOOLEAN)) {
            findViewById(R.id.import_view).setOnClickListener(v ->
                    (new ImportExportDialogFragment()).show(getSupportFragmentManager(),
                            ImportExportDialogFragment.TAG));
        } else {
            findViewById(R.id.import_view).setVisibility(View.GONE);
        }

        findViewById(R.id.about_view).setOnClickListener(v -> {
                View view = getLayoutInflater().inflate(R.layout.dialog_about, null);
                try {
                    PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    String version = packageInfo.versionName;
                    long versionCode;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        versionCode = packageInfo.getLongVersionCode();
                    } else versionCode = packageInfo.versionCode;
                    ((TextView) view.findViewById(R.id.version)).setText(String.format(Locale.ROOT,
                            "%s (%d)", version, versionCode));
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                new FullscreenDialog(this)
                        .setTitle(R.string.about)
                        .setView(view)
                        .show();
            });
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