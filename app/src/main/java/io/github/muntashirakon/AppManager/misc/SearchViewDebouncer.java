// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;

public class SearchViewDebouncer {
    public static final int DELAY_STANDARD = 200;

    public interface OnDebouncedQueryListener {
        void onQueryDebounced(@NonNull String query);
    }

    public interface OnAdvancedDebouncedQueryListener {
        void onQueryDebounced(@NonNull String query, @AdvancedSearchView.SearchType int type);
    }

    @NonNull
    private final Handler mHandler;
    private final long mDelayMillis;
    @Nullable
    private Runnable mSearchRunnable;

    /**
     * Initializes the debouncer with a custom delay.
     * * @param delayMillis The delay in milliseconds to wait before triggering the search.
     */
    public SearchViewDebouncer(long delayMillis) {
        mHandler = new Handler(Looper.getMainLooper());
        mDelayMillis = delayMillis;
    }

    /**
     * Binds a SearchView to this debouncer.
     *
     * @param searchView The SearchView to monitor.
     * @param listener   The callback that triggers after the debounce delay.
     */
    public void bind(@NonNull SearchView searchView, @NonNull OnDebouncedQueryListener listener) {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                cancelPendingSearch();

                // Trigger immediately
                listener.onQueryDebounced(query != null ? query : "");
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                cancelPendingSearch();

                // Schedule the new search task
                mSearchRunnable = () -> listener.onQueryDebounced(newText != null ? newText : "");
                mHandler.postDelayed(mSearchRunnable, mDelayMillis);
                return true;
            }
        });
    }

    /**
     * Binds a SearchView to this debouncer.
     *
     * @param searchView The SearchView to monitor.
     * @param listener   The callback that triggers after the debounce delay.
     */
    public void bindAdvanced(@NonNull AdvancedSearchView searchView, @NonNull OnAdvancedDebouncedQueryListener listener) {
        searchView.setOnQueryTextListener(new AdvancedSearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query, @AdvancedSearchView.SearchType int type) {
                cancelPendingSearch();

                // Trigger immediately
                listener.onQueryDebounced(query != null ? query : "", type);
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText, @AdvancedSearchView.SearchType int type) {
                cancelPendingSearch();

                // Schedule the new search task
                mSearchRunnable = () -> listener.onQueryDebounced(newText != null ? newText : "", type);
                mHandler.postDelayed(mSearchRunnable, mDelayMillis);
                return true;
            }
        });
    }

    /**
     * Cancels any currently scheduled search tasks.
     * Call this in your Activity's onDestroy() or Fragment's onDestroyView() to prevent memory leaks.
     */
    public void unbind() {
        cancelPendingSearch();
    }

    private void cancelPendingSearch() {
        if (mSearchRunnable != null) {
            mHandler.removeCallbacks(mSearchRunnable);
            mSearchRunnable = null;
        }
    }
}