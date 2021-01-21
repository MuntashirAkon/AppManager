/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.scanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.util.TextUtils;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.scanner.reflector.Reflector;
import io.github.muntashirakon.AppManager.utils.UIUtils;

public class ClassListingActivity extends BaseActivity implements SearchView.OnQueryTextListener {
    public static final String EXTRA_APP_NAME = "EXTRA_APP_NAME";

    private TextView mEmptyView;
    private boolean trackerClassesOnly;
    private ClassListingAdapter mClassListingAdapter;
    private CharSequence mAppName;
    private ActionBar mActionBar;
    private LinearProgressIndicator mProgressIndicator;

    private List<String> classListAll;
    private List<String> trackerClassList = new ArrayList<>();
    private List<String> libClassList = new ArrayList<>();
    private DexClasses dexClasses;


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_class_listing);
        setSupportActionBar(findViewById(R.id.toolbar));
        mActionBar = getSupportActionBar();
        classListAll = ScannerActivity.classListAll;
        trackerClassList = ScannerActivity.trackerClassList;
        libClassList = ScannerActivity.libClassList;
        dexClasses = ScannerActivity.dexClasses;
        if (classListAll == null || dexClasses == null) {
            finish();
            return;
        }
        if (trackerClassList == null) trackerClassList = Collections.emptyList();
        mAppName = getIntent().getStringExtra(EXTRA_APP_NAME);
        if (mActionBar != null) {
            mActionBar.setTitle(mAppName);
            mActionBar.setDisplayShowCustomEnabled(true);

            SearchView searchView = new SearchView(mActionBar.getThemedContext());
            searchView.setOnQueryTextListener(this);
            searchView.setQueryHint(getString(R.string.search));

            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(UIUtils.getAccentColor(this));
            ((ImageView) searchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(UIUtils.getAccentColor(this));

            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            mActionBar.setCustomView(searchView, layoutParams);
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
                Reflector reflector = dexClasses.getReflector(className);

                Toast.makeText(this, reflector.generateClassData(), Toast.LENGTH_LONG).show();

                Intent intent = new Intent(this, ClassViewerActivity.class);
                intent.putExtra(ClassViewerActivity.EXTRA_CLASS_NAME, className);
                intent.putExtra(ClassViewerActivity.EXTRA_CLASS_DUMP, reflector.toString());
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

//        if (classList.isEmpty() && totalClassesScanned == 0) {
//            // FIXME: Add support for odex (using root)
//            Toast.makeText(ClassListingActivity.this, R.string.system_odex_not_supported, Toast.LENGTH_LONG).show();
//            finish();
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mClassListingAdapter != null && !TextUtils.isEmpty(mClassListingAdapter.mConstraint)) {
            mClassListingAdapter.getFilter().filter(mClassListingAdapter.mConstraint);
        }
    }

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
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mClassListingAdapter != null)
            mClassListingAdapter.getFilter().filter(newText.toLowerCase(Locale.ROOT));
        return true;
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
        } else if(id == R.id.action_toggle_class_listing) {
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
        private List<String> mDefaultList;
        private List<String> mAdapterList;

        private final int mColorTransparent;
        private final int mColorSemiTransparent;
        private final int mColorRed;

        ClassListingAdapter(@NonNull Activity activity) {
            mLayoutInflater = activity.getLayoutInflater();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(activity, R.color.semi_transparent);
            mColorRed = ContextCompat.getColor(activity, R.color.red);
        }

        void setDefaultList(List<String> list) {
            mDefaultList = list;
            mAdapterList = list;
            if (!TextUtils.isEmpty(mConstraint)) {
                getFilter().filter(mConstraint);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
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
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.size());
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<String>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }
    }
}
