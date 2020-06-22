package io.github.muntashirakon.AppManager.activities;

import android.os.Bundle;
import android.view.View;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.appcompat.app.AppCompatActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;

public class SettingsActivity extends AppCompatActivity {
    private AppPref appPref;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        appPref = AppPref.getInstance(this);

        final SwitchMaterial rootSwitcher = findViewById(R.id.root_toggle_btn);
        final SwitchMaterial blockingSwitcher = findViewById(R.id.blocking_toggle_btn);
        final SwitchMaterial usageSwitcher = findViewById(R.id.usage_toggle_btn);

        final MaterialCardView blockingView = findViewById(R.id.blocking_view);

        // Read pref
        Object object = appPref.getPref(AppPref.PREF_ROOT_MODE_ENABLED.getFirst(), AppPref.TYPE_BOOLEAN);
        Boolean rootEnabled = object == null ? AppPref.PREF_ROOT_MODE_ENABLED.getSecond() : (Boolean) object;
        object = appPref.getPref(AppPref.PREF_GLOBAL_BLOCKING_ENABLED.getFirst(), AppPref.TYPE_BOOLEAN);
        Boolean blockingEnabled = object == null ? AppPref.PREF_GLOBAL_BLOCKING_ENABLED.getSecond() : (Boolean) object;
        object = appPref.getPref(AppPref.PREF_USAGE_ACCESS_ENABLED.getFirst(), AppPref.TYPE_BOOLEAN);
        Boolean usageEnabled = object == null ? AppPref.PREF_USAGE_ACCESS_ENABLED.getSecond() : (Boolean) object;

        // Set changed values
        rootSwitcher.setChecked(rootEnabled);
        blockingView.setVisibility(rootEnabled ? View.VISIBLE : View.GONE);
        blockingSwitcher.setChecked(blockingEnabled);
        usageSwitcher.setChecked(usageEnabled);

        // Set listeners
        rootSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PREF_ROOT_MODE_ENABLED.getFirst(), isChecked);
            blockingView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        blockingSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PREF_GLOBAL_BLOCKING_ENABLED.getFirst(), isChecked);
            Object object1 = appPref.getPref(AppPref.PREF_ROOT_MODE_ENABLED.getFirst(), AppPref.TYPE_BOOLEAN);
            Boolean rootEnabled1 = object1 == null ? AppPref.PREF_ROOT_MODE_ENABLED.getSecond() : (Boolean) object1;
            if (rootEnabled1 && isChecked) {
                // TODO: Apply current settings
            }
        });
        usageSwitcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appPref.setPref(AppPref.PREF_USAGE_ACCESS_ENABLED.getFirst(), isChecked);
        });
    }
}