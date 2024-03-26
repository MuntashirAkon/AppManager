// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class SharedPrefsActivity extends BaseActivity implements
        SearchView.OnQueryTextListener, EditPrefItemFragment.InterfaceCommunicator {
    public static final String EXTRA_PREF_LOCATION = "loc";
    public static final String EXTRA_PREF_LABEL = "label";  // Optional

    public static final int REASONABLE_STR_SIZE = 200;

    private SharedPrefsListingAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private SharedPrefsViewModel mViewModel;
    private boolean mWriteAndExit = false;

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_shared_prefs);
        setSupportActionBar(findViewById(R.id.toolbar));
        Uri sharedPrefUri = IntentCompat.getParcelableExtra(getIntent(), EXTRA_PREF_LOCATION, Uri.class);
        String appLabel = getIntent().getStringExtra(EXTRA_PREF_LABEL);
        if (sharedPrefUri == null) {
            finish();
            return;
        }
        mViewModel = new ViewModelProvider(this).get(SharedPrefsViewModel.class);
        mViewModel.setSharedPrefsFile(Paths.get(sharedPrefUri));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(appLabel);
            actionBar.setSubtitle(mViewModel.getSharedPrefFilename());
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(actionBar, this);
        }
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        RecyclerView recyclerView = findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setEmptyView(findViewById(android.R.id.empty));
        mAdapter = new SharedPrefsListingAdapter(this);
        recyclerView.setAdapter(mAdapter);
        FloatingActionButton fab = findViewById(R.id.floatingActionButton);
        UiUtils.applyWindowInsetsAsMargin(fab);
        fab.setOnClickListener(v -> {
            DialogFragment dialogFragment = new EditPrefItemFragment();
            Bundle args = new Bundle();
            args.putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_CREATE);
            dialogFragment.setArguments(args);
            dialogFragment.show(getSupportFragmentManager(), EditPrefItemFragment.TAG);
        });
        mViewModel.getSharedPrefsMapLiveData().observe(this, sharedPrefsMap -> {
            mProgressIndicator.hide();
            mAdapter.setDefaultList(sharedPrefsMap);
        });
        mViewModel.getSharedPrefsSavedLiveData().observe(this, saved -> {
            if (saved) {
                UIUtils.displayShortToast(R.string.saved_successfully);
                if (mWriteAndExit) {
                    finish();
                    mWriteAndExit = false;
                }
            } else {
                UIUtils.displayShortToast(R.string.saving_failed);
            }
        });
        mViewModel.getSharedPrefsDeletedLiveData().observe(this, deleted -> {
            if (deleted) {
                UIUtils.displayShortToast(R.string.deleted_successfully);
                finish();
            } else {
                UIUtils.displayShortToast(R.string.deletion_failed);
            }
        });
        mViewModel.getSharedPrefsModifiedLiveData().observe(this, modified -> {
            if (modified) {
                if (actionBar != null) {
                    actionBar.setTitle("* " + mViewModel.getSharedPrefFilename());
                }
            } else {
                if (actionBar != null) {
                    actionBar.setTitle(mViewModel.getSharedPrefFilename());
                }
            }
        });
        mViewModel.loadSharedPrefs();
    }

    @Override
    public void onBackPressed() {
        if (mViewModel.isModified()) {
            displayExitPrompt();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_shared_prefs_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void sendInfo(@EditPrefItemFragment.Mode int mode, EditPrefItemFragment.PrefItem prefItem) {
        if (prefItem != null) {
            switch (mode) {
                case EditPrefItemFragment.MODE_CREATE:
                case EditPrefItemFragment.MODE_EDIT:
                    mViewModel.add(prefItem.keyName, prefItem.keyValue);
                    break;
                case EditPrefItemFragment.MODE_DELETE:
                    mViewModel.remove(prefItem.keyName);
                    break;
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem newWindow = menu.findItem(R.id.action_separate_window);
        if (newWindow != null) {
            newWindow.setEnabled(!mViewModel.isModified());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            if (mViewModel.isModified()) {
                displayExitPrompt();
            } else finish();
        } else if (id == R.id.action_discard) {
            finish();
        } else if (id == R.id.action_delete) {
            mViewModel.deleteSharedPrefFile();
        } else if (id == R.id.action_save) {
            mViewModel.writeSharedPrefs();
        } else if (id == R.id.action_separate_window) {
            if (!mViewModel.isModified()) {
                Intent intent = new Intent(getIntent());
                intent.setClass(this, SharedPrefsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(intent);
                finish();
            }
        } else return super.onOptionsItemSelected(item);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null && !TextUtils.isEmpty(mAdapter.mConstraint)) {
            mAdapter.getFilter().filter(mAdapter.mConstraint);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mAdapter != null) mAdapter.getFilter().filter(newText.toLowerCase(Locale.ROOT));
        return true;
    }

    private void displayExitPrompt() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exit_confirmation)
                .setMessage(R.string.file_modified_are_you_sure)
                .setCancelable(false)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> finish())
                .setNeutralButton(R.string.save_and_exit, (dialog, which) -> {
                    mWriteAndExit = true;
                    mViewModel.writeSharedPrefs();
                })
                .show();
    }

    private void displayEditor(@NonNull String prefName) {
        EditPrefItemFragment.PrefItem prefItem = new EditPrefItemFragment.PrefItem();
        prefItem.keyName = prefName;
        prefItem.keyValue = mViewModel.getValue(prefName);
        EditPrefItemFragment dialogFragment = new EditPrefItemFragment();
        Bundle args = new Bundle();
        args.putParcelable(EditPrefItemFragment.ARG_PREF_ITEM, prefItem);
        args.putInt(EditPrefItemFragment.ARG_MODE, EditPrefItemFragment.MODE_EDIT);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), EditPrefItemFragment.TAG);
    }

    static class SharedPrefsListingAdapter extends RecyclerView.Adapter<SharedPrefsListingAdapter.ViewHolder> implements Filterable {
        private final SharedPrefsActivity mActivity;
        private Filter mFilter;
        private String mConstraint;
        private String[] mDefaultList;
        private String[] mAdapterList;
        private Map<String, Object> mAdapterMap;

        private final int mQueryStringHighlightColor;

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView itemName;
            TextView itemValue;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                itemName = itemView.findViewById(android.R.id.title);
                itemValue = itemView.findViewById(android.R.id.summary);
                itemView.findViewById(R.id.icon_frame).setVisibility(View.GONE);
            }
        }

        SharedPrefsListingAdapter(@NonNull SharedPrefsActivity activity) {
            mActivity = activity;
            mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        }

        void setDefaultList(@NonNull Map<String, Object> list) {
            mDefaultList = list.keySet().toArray(new String[0]);
            mAdapterMap = list;
            if (!TextUtils.isEmpty(mConstraint)) {
                getFilter().filter(mConstraint);
            } else {
                int previousCount = mAdapterList != null ? mAdapterList.length : 0;
                mAdapterList = mDefaultList;
                AdapterUtils.notifyDataSetChanged(this, previousCount, mAdapterList.length);
            }
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.length;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String prefName = mAdapterList[position];
            if (mConstraint != null && prefName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.itemName.setText(UIUtils.getHighlightedText(prefName, mConstraint, mQueryStringHighlightColor));
            } else {
                holder.itemName.setText(prefName);
            }
            Object value = mAdapterMap.get(prefName);
            String strValue = (value != null) ? value.toString() : "";
            holder.itemValue.setText(strValue.length() > REASONABLE_STR_SIZE ?
                    strValue.substring(0, REASONABLE_STR_SIZE) : strValue);
            holder.itemView.setOnClickListener(v -> mActivity.displayEditor(prefName));
        }

        @Override
        public long getItemId(int position) {
            return position;
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
                        if (constraint.isEmpty()) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<String> list = new ArrayList<>(mDefaultList.length);
                        for (String item : mDefaultList) {
                            if (item.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list.toArray(new String[0]);
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        int previousCount = mAdapterList != null ? mAdapterList.length : 0;
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            mAdapterList = (String[]) filterResults.values;
                        }
                        AdapterUtils.notifyDataSetChanged(SharedPrefsListingAdapter.this, previousCount, mAdapterList.length);
                    }
                };
            return mFilter;
        }
    }
}