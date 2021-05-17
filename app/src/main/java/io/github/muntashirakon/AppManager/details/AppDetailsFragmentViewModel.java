// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import androidx.lifecycle.ViewModel;

public class AppDetailsFragmentViewModel extends ViewModel {
    @AppDetailsFragment.Property
    private int neededProperty = AppDetailsFragment.ACTIVITIES;

    @AppDetailsFragment.Property
    public int getNeededProperty() {
        return neededProperty;
    }

    public void setNeededProperty(@AppDetailsFragment.Property int neededProperty) {
        this.neededProperty = neededProperty;
    }
}
