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
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.misc.SearchViewDebouncer;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.io.Paths;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class SharedPrefsActivity extends BaseActivity implements EditPrefItemFragment.InterfaceCommunicator {
    public static final String EXTRA_PREF_LOCATION = "loc";
    public static final String EXTRA_PREF_LABEL = "label";  // Optional

    public static final int REASONABLE_STR_SIZE = 200;

    private SharedPrefsListingAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    private SearchViewDebouncer mSearchDebouncer;
    private SharedPrefsViewModel mViewModel;
    private boolean mWriteAndExit = false;
    private final OnBackPressedCallback mOnBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (mViewModel.isModified()) {
                new MaterialAlertDialogBuilder(SharedPrefsActivity.this)
                        .setTitle(R.string.exit_confirmation)
                        .setMessage(R.string.file_modified_are_you_sure)
                        .setCancelable(false)
                        .setPositiveButton(R.string.no, null)
                        .setNegativeButton(R.string.yes, (dialog, which) -> {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        })
                        .setNeutralButton(R.string.save_and_exit, (dialog, which) -> {
                            mWriteAndExit = true;
                            mViewModel.writeSharedPrefs();
                            setEnabled(false);
                        })
                        .show();
                return;
            }
            setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
        }
    };

    @Override
    protected void onAuthenticated(Bundle savedInstanceState) {
        setContentView(R.layout.activity_shared_prefs);
        setSupportActionBar(findViewById(R.id.toolbar));
        getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
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
            mSearchDebouncer = new SearchViewDebouncer(SearchViewDebouncer.DELAY_STANDARD);
            mSearchDebouncer.bind(UIUtils.setupSearchView(actionBar), query -> {
                if (mAdapter != null) {
                    mAdapter.setFilterConstraint(query.toLowerCase(Locale.ROOT));
                }
            });
        }
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        RecyclerView recyclerView = findViewById(android.R.id.list);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
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
                    getOnBackPressedDispatcher().onBackPressed();
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
            mOnBackPressedCallback.setEnabled(modified);
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
            getOnBackPressedDispatcher().onBackPressed();
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
            mAdapter.setFilterConstraint(mAdapter.mConstraint);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSearchDebouncer != null) {
            mSearchDebouncer.unbind();
        }
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

    static class SharedPrefsListingAdapter extends RecyclerView.ListAdapter<SharedPrefsListingAdapter.SharedPrefPair, SharedPrefsListingAdapter.ViewHolder> {
        private final SharedPrefsActivity mActivity;
        private final int mQueryStringHighlightColor;
        private final List<SharedPrefPair> mMasterList = new ArrayList<>();
        @Nullable
        private String mConstraint;

        static class SharedPrefPair {
            @NonNull
            final String key;
            @Nullable
            final Object value;

            SharedPrefPair(@NonNull String key, @Nullable Object value) {
                this.key = key;
                this.value = value;
            }
        }

        private static final DiffUtil.ItemCallback<SharedPrefPair> DIFF_CALLBACK = new DiffUtil.ItemCallback<SharedPrefPair>() {
            @Override
            public boolean areItemsTheSame(@NonNull SharedPrefPair oldItem, @NonNull SharedPrefPair newItem) {
                return Objects.equals(oldItem.key, newItem.key);
            }

            @Override
            public boolean areContentsTheSame(@NonNull SharedPrefPair oldItem, @NonNull SharedPrefPair newItem) {
                // Value is secondary
                return Objects.equals(oldItem.value, newItem.value);
            }
        };

        SharedPrefsListingAdapter(@NonNull SharedPrefsActivity activity) {
            super(DIFF_CALLBACK);
            mActivity = activity;
            mQueryStringHighlightColor = ColorCodes.getQueryStringHighlightColor(activity);
        }

        void setDefaultList(@NonNull Map<String, Object> list) {
            mMasterList.clear();
            for (Map.Entry<String, Object> entry : list.entrySet()) {
                mMasterList.add(new SharedPrefPair(entry.getKey(), entry.getValue()));
            }
            dispatchFilteredList();
        }

        void setFilterConstraint(@Nullable String constraint) {
            mConstraint = TextUtils.isEmpty(constraint) ? null : constraint.toLowerCase(Locale.ROOT);
            dispatchFilteredList();
        }

        private void dispatchFilteredList() {
            if (mConstraint == null || mConstraint.isEmpty()) {
                submitList(new ArrayList<>(mMasterList));
                return;
            }

            List<SharedPrefPair> filteredList = new ArrayList<>();
            for (SharedPrefPair pair : mMasterList) {
                if (pair.key.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                    filteredList.add(pair);
                }
            }
            submitList(filteredList);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SharedPrefPair pair = getItem(position);
            if (mConstraint != null && pair.key.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.itemName.setText(UIUtils.getHighlightedText(pair.key, mConstraint, mQueryStringHighlightColor));
            } else {
                holder.itemName.setText(pair.key);
            }
            String strValue = (pair.value != null) ? pair.value.toString() : "";
            holder.itemValue.setText(strValue.length() > REASONABLE_STR_SIZE ?
                    strValue.substring(0, REASONABLE_STR_SIZE) : strValue);
            holder.itemView.setOnClickListener(v -> mActivity.displayEditor(pair.key));
        }

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
    }
}