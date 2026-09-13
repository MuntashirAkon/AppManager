// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.muntashirakon.AppManager.R;

public final class AppDetailsTabs {
    private static final List<AppDetailsTab> DEFAULT_TABS = Collections.unmodifiableList(Arrays.asList(
            new AppDetailsTab(AppDetailsFragment.APP_INFO, R.string.app_info),
            new AppDetailsTab(AppDetailsFragment.ACTIVITIES, R.string.activities),
            new AppDetailsTab(AppDetailsFragment.SERVICES, R.string.service),
            new AppDetailsTab(AppDetailsFragment.RECEIVERS, R.string.receivers),
            new AppDetailsTab(AppDetailsFragment.PROVIDERS, R.string.providers),
            new AppDetailsTab(AppDetailsFragment.APP_OPS, R.string.app_ops),
            new AppDetailsTab(AppDetailsFragment.USES_PERMISSIONS, R.string.permissions),
            new AppDetailsTab(AppDetailsFragment.PERMISSIONS, R.string.declared_permission),
            new AppDetailsTab(AppDetailsFragment.FEATURES, R.string.uses_feature),
            new AppDetailsTab(AppDetailsFragment.CONFIGURATIONS, R.string.configurations),
            new AppDetailsTab(AppDetailsFragment.SIGNATURES, R.string.app_signing_signatures),
            new AppDetailsTab(AppDetailsFragment.SHARED_LIBRARIES, R.string.shared_libs),
            new AppDetailsTab(AppDetailsFragment.OVERLAYS, R.string.overlays)
    ));

    private AppDetailsTabs() {
    }

    @NonNull
    public static List<AppDetailsTab> getDefaultTabs() {
        return DEFAULT_TABS;
    }
}
