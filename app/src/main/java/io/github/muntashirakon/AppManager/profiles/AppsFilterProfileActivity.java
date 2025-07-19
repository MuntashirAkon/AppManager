// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.EditFiltersDialogFragment;
import io.github.muntashirakon.AppManager.filters.FilterItem;

public class AppsFilterProfileActivity extends AppsBaseProfileActivity implements EditFiltersDialogFragment.OnSaveDialogButtonInterface {
    @NonNull
    public static Intent getProfileIntent(@NonNull Context context, @NonNull String profileId) {
        Intent intent = new Intent(context, AppsFilterProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, profileId);
        return intent;
    }

    @NonNull
    public static Intent getNewProfileIntent(@NonNull Context context, @NonNull String profileName) {
        Intent intent = new Intent(context, AppsFilterProfileActivity.class);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, profileName);
        return intent;
    }

    @NonNull
    public static Intent getCloneProfileIntent(@NonNull Context context, @NonNull String oldProfileId,
                                               @NonNull String newProfileName) {
        Intent intent = new Intent(context, AppsFilterProfileActivity.class);
        intent.putExtra(EXTRA_PROFILE_ID, oldProfileId);
        intent.putExtra(EXTRA_NEW_PROFILE_NAME, newProfileName);
        return intent;
    }


    @Override
    public Fragment getAppsBaseFragment() {
        return new AppsFragment();
    }

    @Override
    public void loadNewProfile(@NonNull String newProfileName, @NonNull Intent intent) {
        model.loadNewAppsFilterProfile(newProfileName);
    }

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        super.onAuthenticated(savedInstanceState);
        bottomNavigationView.getMenu().removeItem(R.id.action_apps);
        fab.setImageResource(R.drawable.ic_filter_menu);
        fab.setContentDescription(getString(R.string.filters));
        fab.setOnClickListener(v -> {
            EditFiltersDialogFragment dialog = new EditFiltersDialogFragment();
            dialog.setOnSaveDialogButtonInterface(this);
            dialog.show(getSupportFragmentManager(), EditFiltersDialogFragment.TAG);
        });
    }

    @NonNull
    @Override
    public FilterItem getFilterItem() {
        return model.getFilterItem();
    }

    @Override
    public void onItemAltered(@NonNull FilterItem item) {
        model.setModified(true);
        model.loadPackages();
    }
}
