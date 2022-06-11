// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.internal.util.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;

import static io.github.muntashirakon.AppManager.misc.AdvancedSearchView.SEARCH_TYPE_REGEX;

public class ClassListingFragment extends Fragment implements AdvancedSearchView.OnQueryTextListener {
    private TextView mEmptyView;
    private boolean mTrackerClassesOnly;
    private ClassListingAdapter mClassListingAdapter;

    private List<String> mAllClasses;
    private List<String> mTrackerClasses;
    private ScannerViewModel mViewModel;
    private ScannerActivity mActivity;

    public ClassListingFragment() {
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_class_lister, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(ScannerViewModel.class);
        mActivity = (ScannerActivity) requireActivity();
        mAllClasses = mViewModel.getAllClasses();
        mTrackerClasses = mViewModel.getTrackerClasses();
        if (mAllClasses == null) {
            mActivity.onBackPressed();
            return;
        }
        if (mTrackerClasses == null) {
            mTrackerClasses = Collections.emptyList();
        }

        mTrackerClassesOnly = false;

        ListView listView = view.findViewById(R.id.list_item);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        listView.setTextFilterEnabled(true);
        listView.setDividerHeight(0);
        mEmptyView = view.findViewById(android.R.id.empty);
        listView.setEmptyView(mEmptyView);
        mClassListingAdapter = new ClassListingAdapter(mActivity);
        listView.setAdapter(mClassListingAdapter);
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            String className = (!mTrackerClassesOnly ? mTrackerClasses : mAllClasses)
                    .get((int) (parent.getAdapter()).getItemId(position));
            try {
                Intent intent = new Intent(mActivity, ClassViewerActivity.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(ClassViewerActivity.EXTRA_URI, mViewModel.getUriFromClassName(className));
                } else {
                    intent.putExtra(ClassViewerActivity.EXTRA_CLASS_CONTENT, mViewModel.getClassContent(className));
                }
                intent.putExtra(ClassViewerActivity.EXTRA_APP_NAME, mActivity.getTitle());
                intent.putExtra(ClassViewerActivity.EXTRA_CLASS_NAME, className);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                UIUtils.displayLongToast(e.toString());
            }
        });
        showProgress(true);
        setAdapterList();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mClassListingAdapter != null && !TextUtils.isEmpty(mClassListingAdapter.mConstraint)) {
            mClassListingAdapter.filter();
        }
    }

    @UiThread
    private void setAdapterList() {
        if (!mTrackerClassesOnly) {
            mClassListingAdapter.setDefaultList(mTrackerClasses);
            mActivity.setSubtitle(getString(R.string.tracker_classes));
        } else {
            mClassListingAdapter.setDefaultList(mAllClasses);
            mActivity.setSubtitle(getString(R.string.all_classes));
        }
        showProgress(false);
    }

    @Override
    public boolean onQueryTextChange(String newText, @AdvancedSearchView.SearchType int type) {
        if (mClassListingAdapter != null) {
            mClassListingAdapter.filter(newText, type);
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_class_lister_actions, menu);
        AdvancedSearchView searchView = (AdvancedSearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setOnQueryTextListener(this);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_class_listing) {
            mTrackerClassesOnly = !mTrackerClassesOnly;
            setAdapterList();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void showProgress(boolean willShow) {
        mActivity.showProgress(willShow);
        mEmptyView.setText(willShow ? R.string.loading : R.string.no_tracker_class);
    }

    static class ClassListingAdapter extends BaseAdapter implements Filterable {
        private final LayoutInflater mLayoutInflater;
        private Filter mFilter;
        private String mConstraint;
        @AdvancedSearchView.SearchType
        private int mFilterType = AdvancedSearchView.SEARCH_TYPE_CONTAINS;
        private List<String> mDefaultList;
        @NonNull
        private final List<String> mAdapterList = new ArrayList<>();

        private final int mColorTransparent;
        private final int mColorSemiTransparent;
        private final int mColorRed;

        ClassListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        @UiThread
        void setDefaultList(@NonNull List<String> list) {
            synchronized (mAdapterList) {
                mAdapterList.clear();
                mAdapterList.addAll(list);
            }
            mDefaultList = list;
            filter();
            notifyDataSetChanged();
        }

        void filter() {
            if (!TextUtils.isEmpty(mConstraint)) {
                filter(mConstraint, mFilterType);
            }
        }

        void filter(String query, @AdvancedSearchView.SearchType int filterType) {
            mConstraint = query;
            mFilterType = filterType;
            getFilter().filter(mConstraint);
        }

        @Override
        public int getCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        @Override
        public String getItem(int position) {
            synchronized (mAdapterList) {
                return mAdapterList.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            synchronized (mAdapterList) {
                return mDefaultList.indexOf(mAdapterList.get(position));
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            String className;
            synchronized (mAdapterList) {
                className = mAdapterList.get(position);
            }
            TextView textView = (TextView) convertView;
            if (mConstraint != null && className.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                textView.setText(UIUtils.getHighlightedText(className, mConstraint, mColorRed));
            } else {
                textView.setText(className);
            }
            convertView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            return convertView;
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = mFilterType == SEARCH_TYPE_REGEX ? charSequence.toString()
                                : charSequence.toString().toLowerCase(Locale.ROOT);
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = AdvancedSearchView.matches(
                                constraint,
                                mDefaultList,
                                (AdvancedSearchView.ChoiceGenerator<String>) object -> mFilterType == SEARCH_TYPE_REGEX ? object
                                        : object.toLowerCase(Locale.ROOT),
                                mFilterType);

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        synchronized (mAdapterList) {
                            mAdapterList.clear();
                            if (filterResults.values == null) {
                                mAdapterList.addAll(mDefaultList);
                            } else {
                                //noinspection unchecked
                                mAdapterList.addAll((List<String>) filterResults.values);
                            }
                            notifyDataSetChanged();
                        }
                    }
                };
            return mFilter;
        }
    }
}
