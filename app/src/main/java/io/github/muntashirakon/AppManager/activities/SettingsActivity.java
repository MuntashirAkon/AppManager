package io.github.muntashirakon.AppManager.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.fragments.ImportExportDialogFragment;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class SettingsActivity extends AppCompatActivity {
    private AppPref appPref;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        findViewById(R.id.progress_linear).setVisibility(View.GONE);
        appPref = AppPref.getInstance(this);

        final SwitchMaterial rootSwitcher = findViewById(R.id.root_toggle_btn);
        final SwitchMaterial blockingSwitcher = findViewById(R.id.blocking_toggle_btn);
        final SwitchMaterial usageSwitcher = findViewById(R.id.usage_toggle_btn);

        final MaterialCardView blockingView = findViewById(R.id.blocking_view);

        // Read pref
        Boolean rootEnabled = (Boolean) appPref.getPref(AppPref.PREF_ROOT_MODE_ENABLED, AppPref.TYPE_BOOLEAN);
        Boolean blockingEnabled = (Boolean) appPref.getPref(AppPref.PREF_GLOBAL_BLOCKING_ENABLED, AppPref.TYPE_BOOLEAN);
        Boolean usageEnabled = (Boolean) appPref.getPref(AppPref.PREF_USAGE_ACCESS_ENABLED, AppPref.TYPE_BOOLEAN);

        // Set changed values
        rootSwitcher.setChecked(rootEnabled);
        blockingView.setVisibility(rootEnabled ? View.VISIBLE : View.GONE);
        blockingSwitcher.setChecked(blockingEnabled);
        usageSwitcher.setChecked(usageEnabled);

        // Set listeners
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