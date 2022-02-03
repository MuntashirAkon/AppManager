// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;

import com.android.internal.util.TextUtils;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView.ChoiceGenerator;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.VirtualFileSystem;

import static io.github.muntashirakon.AppManager.misc.AdvancedSearchView.SEARCH_TYPE_REGEX;

// Copyright 2015 Google, Inc.
public class ClassListingActivity extends BaseActivity implements AdvancedSearchView.OnQueryTextListener {
    public static final String EXTRA_APP_NAME = "EXTRA_APP_NAME";
    public static final String EXTRA_DEX_VFS_ID = "vfs_id";

    private TextView mEmptyView;
    private boolean trackerClassesOnly;
    private ClassListingAdapter mClassListingAdapter;
    private CharSequence mAppName;
    private Path dexRootPath;
    private ActionBar mActionBar;
    private LinearProgressIndicator mProgressIndicator;

    private List<String> classListAll;
    private List<String> trackerClassList = new ArrayList<>();

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_class_listing);
        setSupportActionBar(findViewById(R.id.toolbar));
        mActionBar = getSupportActionBar();
        classListAll = ScannerActivity.classListAll;
        trackerClassList = ScannerActivity.trackerClassList;
        if (classListAll == null) {
            finish();
            return;
        }
        if (trackerClassList == null) trackerClassList = Collections.emptyList();
        mAppName = getIntent().getStringExtra(EXTRA_APP_NAME);
        int dexVfsId = getIntent().getIntExtra(EXTRA_DEX_VFS_ID, 0);
        if (dexVfsId == 0) {
            finish();
            return;
        }
        dexRootPath = VirtualFileSystem.getFsRoot(dexVfsId);
        if (dexRootPath == null) {
            finish();
            return;
        }
        if (mActionBar != null) {
            mActionBar.setTitle(mAppName);
            mActionBar.setDisplayShowCustomEnabled(true);
            AdvancedSearchView searchView = UIUtils.setupAdvancedSearchView(mActionBar, this);
            searchView.removeEnabledTypes(AdvancedSearchView.SEARCH_TYPE_FUZZY);
        }

        trackerClassesOnly = false;

        ListView listView = findViewById(android.R.id.list);
        listView.setTextFilterEnabled(true);
        listView.setDividerHeight(0);
        mEmptyView = findViewById(android.R.id.empty);
        listView.setEmptyView(mEmptyView);
        mClassListingAdapter = new ClassListingAdapter(this);
        listView.setAdapter(mClassListingAdapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String className = (!trackerClassesOnly ? trackerClassList : classListAll)
                    .get((int) (parent.getAdapter()).getItemId(position));
            try {
                Intent intent = new Intent(this, ClassViewerActivity.class);
                intent.putExtra(ClassViewerActivity.EXTRA_URI, dexRootPath.findFile(className
                        .replace('.', '/') + ".smali").getUri());
                intent.putExtra(ClassViewerActivity.EXTRA_APP_NAME, mAppName);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
            }
        });

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        showProgress(true);
        setAdapterList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mClassListingAdapter != null && !TextUtils.isEmpty(mClassListingAdapter.mConstraint)) {
            mClassListingAdapter.filter();
        }
    }

    @UiThread
    private void setAdapterList() {
        if (!trackerClassesOnly) {
            mClassListingAdapter.setDefaultList(trackerClassList);
            mActionBar.setSubtitle(getString(R.string.tracker_classes));
        } else {
            mClassListingAdapter.setDefaultList(classListAll);
            mActionBar.setSubtitle(getString(R.string.all_classes));
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_class_listing_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_toggle_class_listing) {
            trackerClassesOnly = !trackerClassesOnly;
            setAdapterList();
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    private void showProgress(boolean willShow) {
        if (willShow) {
            mProgressIndicator.show();
            mEmptyView.setText(R.string.loading);
        } else {
            mProgressIndicator.hide();
            mEmptyView.setText(R.string.no_tracker_class);
        }
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
            mAdapterList.clear();
            mAdapterList.addAll(list);
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
            return mAdapterList.size();
        }

        @Override
        public String getItem(int position) {
            return mAdapterList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mDefaultList.indexOf(mAdapterList.get(position));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mLayoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            String className = mAdapterList.get(position);
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
                                (ChoiceGenerator<String>) object -> mFilterType == SEARCH_TYPE_REGEX ? object
                                        : object.toLowerCase(Locale.ROOT),
                                mFilterType);

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        mAdapterList.clear();
                        if (filterResults.values == null) {
                            mAdapterList.addAll(mDefaultList);
                        } else {
                            //noinspection unchecked
                            mAdapterList.addAll((List<String>) filterResults.values);
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}
