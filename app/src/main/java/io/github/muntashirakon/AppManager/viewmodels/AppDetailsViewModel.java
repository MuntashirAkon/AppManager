package io.github.muntashirakon.AppManager.viewmodels;

import androidx.lifecycle.ViewModel;
import io.github.muntashirakon.AppManager.fragments.AppDetailsFragment;

public class AppDetailsViewModel extends ViewModel {
    private @AppDetailsFragment.Property int neededProperty = 0;  // ACTIVITY
    private @AppDetailsFragment.SortOrder int sortBy = 0;  // SORT_BY_NAME

    public int getNeededProperty() {
        return neededProperty;
    }
    public void setNeededProperty(int neededProperty) {
        this.neededProperty = neededProperty;
    }
    public int getSortBy() {
        return sortBy;
    }
    public void setSortBy(int sortBy) {
        this.sortBy = sortBy;
    }
}
