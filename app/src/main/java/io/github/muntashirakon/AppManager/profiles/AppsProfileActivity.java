// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class AppsProfileActivity extends AppsBaseProfileActivity {
    @NonNull
    public static Intent getProfileIntent(@NonNull Context context, @NonNull String profileId) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        return intent;
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @NonNull String profileName) {
        return getNewProfileIntent(context, profileName, null);
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @NonNull String profileName, @Nullable String[] initialPackages) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, profileName);
        if (initialPackages != null) {
            intent.putExtra(EXTRA_NEW_PROFILE_PACKAGES, initialPackages);
        }
        return intent;
    }

    @NonNull
    public static Intent getCloneProfileIntent(@NonNull Context context, @NonNull String oldProfileId,
                                               @NonNull String newProfileName) {
        Intent intent = new Intent(context, AppsProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, oldProfileId);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName);
        return intent;
    }

    private static final String EXTRA_NEW_PROFILE_PACKAGES = "new_prof_pkgs";
    private static final String EXTRA_SHORTCUT_TYPE = "shortcut";

    @Override
    public Fragment getAppsBaseFragment() {
        return new AppsFragment();
    }

    @Override
    public void loadNewProfile(@NonNull String newProfileName, @NonNull Intent intent) {
        @Nullable String[] initialPackages = intent.getStringArrayExtra(EXTRA_NEW_PROFILE_PACKAGES);
        model.loadNewAppsProfile(newProfileName, initialPackages);
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        super.onAuthenticated(savedInstanceState);
        bottomNavigationView.getMenu().removeItem(R.id.action_preview);
        if (getIntent().hasExtra(EXTRA_SHORTCUT_TYPE)) {
            // Compatibility mode for shortcut
            @ProfileApplierActivity.ShortcutType String shortcutType = getIntent().getStringExtra(EXTRA_SHORTCUT_TYPE);
            @Nullable String profileState = getIntent().getStringExtra(EXTRA_STATE);
            if (shortcutType != null && profileId != null) {
                ProfileApplierActivity.getShortcutIntent(this, profileId, shortcutType, profileState);
            }
            // Finish regardless of whether the profile applier launched or not
            finish();
            return;
        }
        fab.setImageResource(R.drawable.ic_add);
        fab.setContentDescription(getString(R.string.add_item));
        fab.setOnClickListener(v -> {
            progressIndicator.show();
            model.loadInstalledApps();
        });
        model.observeInstalledApps().observe(this, itemPairs -> {
            ArrayList<String> items = new ArrayList<>(itemPairs.size());
            ArrayList<CharSequence> itemNames = new ArrayList<>(itemPairs.size());
            for (Pair<CharSequence, ApplicationInfo> itemPair : itemPairs) {
                items.add(itemPair.second.packageName);
                boolean isSystem = (itemPair.second.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                itemNames.add(new SpannableStringBuilder(itemPair.first)
                        .append("\n")
                        .append(getSmallerText(getString(isSystem ? R.string.system : R.string.user))));
            }
            progressIndicator.hide();
            new SearchableMultiChoiceDialogBuilder<>(this, items, itemNames)
                    .addSelections(model.getCurrentPackages())
                    .setTitle(R.string.apps)
                    .setPositiveButton(R.string.ok, (d, i, selectedItems) -> model.setPackages(selectedItems))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }
}
