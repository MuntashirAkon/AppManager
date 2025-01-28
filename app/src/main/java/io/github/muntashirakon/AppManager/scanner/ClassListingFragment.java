// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.scanner;

import static io.github.muntashirakon.AppManager.misc.AdvancedSearchView.SEARCH_TYPE_REGEX;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.editor.CodeEditorActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class ClassListingFragment extends Fragment implements AdvancedSearchView.OnQueryTextListener, MenuProvider {
    private TextView mEmptyView;
    private boolean mTrackerClassesOnly;
    private ClassListingAdapter mClassListingAdapter;

    private List<String> mAllClasses;
    private List<String> mTrackerClasses;
    private ScannerViewModel mViewModel;
    private ScannerActivity mActivity;

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

        RecyclerView listView = view.findViewById(R.id.list_item);
        UiUtils.applyWindowInsetsAsPaddingNoTop(listView);
        mEmptyView = view.findViewById(android.R.id.empty);
        listView.setEmptyView(mEmptyView);
        mClassListingAdapter = new ClassListingAdapter(mActivity, mViewModel);
        listView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(mActivity));
        listView.setAdapter(mClassListingAdapter);
        mActivity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
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
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_class_lister_actions, menu);
        AdvancedSearchView searchView = (AdvancedSearchView) menu.findItem(R.id.action_search).getActionView();
        Objects.requireNonNull(searchView).setOnQueryTextListener(this);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_class_listing) {
            mTrackerClassesOnly = !mTrackerClassesOnly;
            setAdapterList();
        } else return false;
        return true;
    }

    private void showProgress(boolean willShow) {
        mActivity.showProgress(willShow);
        mEmptyView.setText(willShow ? R.string.loading : R.string.no_tracker_class);
    }

    static class ClassListingAdapter extends RecyclerView.Adapter<ClassListingAdapter.ViewHolder> implements Filterable {
        private Filter mFilter;
        private String mConstraint;
        @AdvancedSearchView.SearchType
        private int mFilterType = AdvancedSearchView.SEARCH_TYPE_CONTAINS;
        private List<String> mDefaultList;
        private final Activity mActivity;
        private final ScannerViewModel mViewModel;
        @NonNull
        private final List<String> mAdapterList = new ArrayList<>();
        private final int mCardColor0;
        private final int mCardColor1;
        private final int mQueryStringHighlightColor;

        ClassListingAdapter(@NonNull Activity activity, @NonNull ScannerViewModel viewModel) {
            mActivity = activity;
            mViewModel = viewModel;
            mCardColor0 = ColorCodes.getListItemColor0(activity);
            mCardColor1 = ColorCodes.getListItemColor1(activity);
            mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        }

        @UiThread
        void setDefaultList(@NonNull List<String> list) {
            mDefaultList = list;
            filter();
        }

        void filter() {
            if (!TextUtils.isEmpty(mConstraint)) {
                filter(mConstraint, mFilterType);
            } else {
                AdapterUtils.notifyDataSetChanged(this, mAdapterList, mDefaultList);
            }
        }

        void filter(String query, @AdvancedSearchView.SearchType int filterType) {
            mConstraint = query;
            mFilterType = filterType;
            getFilter().filter(mConstraint);
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        @Override
        public long getItemId(int position) {
            synchronized (mAdapterList) {
                return mDefaultList.indexOf(mAdapterList.get(position));
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String className;
            synchronized (mAdapterList) {
                className = mAdapterList.get(position);
            }
            TextView textView = holder.classNameView;
            textView.setTypeface(Typeface.MONOSPACE);
            if (mConstraint != null && className.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                textView.setText(UIUtils.getHighlightedText(className, mConstraint, mQueryStringHighlightColor));
            } else {
                textView.setText(className);
            }
            holder.itemView.setCardBackgroundColor(position % 2 == 0 ? mCardColor1 : mCardColor0);
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = CodeEditorActivity.getIntent(mActivity, mViewModel.getUriFromClassName(className), null, null, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mActivity.startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    UIUtils.displayLongToast(e.toString());
                }
            });
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
                        if (constraint.isEmpty()) {
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
                                AdapterUtils.notifyDataSetChanged(ClassListingAdapter.this, mAdapterList, mDefaultList);
                            } else {
                                //noinspection unchecked
                                AdapterUtils.notifyDataSetChanged(ClassListingAdapter.this, mAdapterList, (List<String>) filterResults.values);
                            }
                        }
                    }
                };
            return mFilter;
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            final MaterialCardView itemView;
            final TextView classNameView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                itemView.findViewById(android.R.id.title).setVisibility(View.GONE);
                itemView.findViewById(R.id.icon_frame).setVisibility(View.GONE);
                classNameView = itemView.findViewById(android.R.id.summary);
            }
        }
    }
}
