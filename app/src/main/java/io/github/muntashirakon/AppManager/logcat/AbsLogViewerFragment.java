// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.logcat;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logcat.helper.PreferenceHelper;
import io.github.muntashirakon.AppManager.logcat.helper.SaveLogHelper;
import io.github.muntashirakon.AppManager.logcat.struct.LogLine;
import io.github.muntashirakon.AppManager.logcat.struct.SearchCriteria;
import io.github.muntashirakon.AppManager.settings.LogViewerPreferences;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableItemsDialogBuilder;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.SearchableSingleChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.multiselection.MultiSelectionActionsView;
import io.github.muntashirakon.widget.MultiSelectionView;

public abstract class AbsLogViewerFragment extends Fragment implements MenuProvider,
        LogViewerViewModel.LogLinesAvailableInterface,
        MultiSelectionActionsView.OnItemSelectedListener,
        LogViewerActivity.SearchingInterface, Filter.FilterListener {
    public static final String TAG = AbsLogViewerFragment.class.getSimpleName();

    protected RecyclerView mRecyclerView;
    protected MultiSelectionView mMultiSelectionView;
    protected LogViewerViewModel mViewModel;
    protected LogViewerActivity mActivity;
    protected LogViewerRecyclerAdapter mLogListAdapter;

    protected boolean mAutoscrollToBottom = true;
    @Nullable
    protected volatile SearchCriteria mSearchCriteria;

    protected final StoragePermission mStoragePermission = StoragePermission.init(this);
    protected final RecyclerView.OnScrollListener mRecyclerViewScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            // Update what the first viewable item is
            final LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                // Stop autoscroll if the bottom of the list isn't visible anymore
                mAutoscrollToBottom = layoutManager.findLastCompletelyVisibleItemPosition() ==
                        (mLogListAdapter.getItemCount() - 1);
            }
        }
    };
    private final OnBackPressedCallback mOnBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mLogListAdapter.isInSelectionMode()) {
                mMultiSelectionView.cancel();
            } else {
                setEnabled(false);
                requireActivity().onBackPressed();
            }
        }
    };

    @Nullable
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_logcat, container, false);
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(requireActivity()).get(LogViewerViewModel.class);
        mActivity = (LogViewerActivity) requireActivity();
        mActivity.setSearchingInterface(this);
        mRecyclerView = view.findViewById(R.id.list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mActivity));
        mRecyclerView.setItemAnimator(null);
        // Check for query string
        mSearchCriteria = mActivity.getSearchQuery();
        if (mSearchCriteria != null) {
            mRecyclerView.postDelayed(() -> mActivity.search(mSearchCriteria), 1000);
        }
        mLogListAdapter = new LogViewerRecyclerAdapter();
        mLogListAdapter.setClickListener(mActivity);
        mMultiSelectionView = view.findViewById(R.id.selection_view);
        mMultiSelectionView.setAdapter(mLogListAdapter);
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.hide();
        mRecyclerView.setAdapter(mLogListAdapter);
        mRecyclerView.addOnScrollListener(mRecyclerViewScrollListener);
        mActivity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        // Observers
        mViewModel.getExpandLogsLiveData().observe(getViewLifecycleOwner(), expanded -> {
            int oldFirstVisibleItem = ((LinearLayoutManager) Objects.requireNonNull(mRecyclerView.getLayoutManager())).findFirstVisibleItemPosition();
            mLogListAdapter.setCollapseMode(!expanded);
            mLogListAdapter.notifyItemRangeChanged(0, mLogListAdapter.getItemCount());
            // Scroll to bottom or the first visible item
            if (mAutoscrollToBottom) {
                mRecyclerView.scrollToPosition(mLogListAdapter.getItemCount() - 1);
            } else if (oldFirstVisibleItem != -1) {
                mRecyclerView.scrollToPosition(oldFirstVisibleItem);
            }
            mActivity.supportInvalidateOptionsMenu();
        });
        mViewModel.observeLogLevelLiveData().observe(getViewLifecycleOwner(), logLevel ->
                mLogListAdapter.setLogLevelLimit(logLevel));
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mOnBackPressedCallback);
    }

    @CallSuper
    @Override
    public void onDestroy() {
        if (mRecyclerView != null) {
            mRecyclerView.removeOnScrollListener(mRecyclerViewScrollListener);
        }
        super.onDestroy();
    }

    public abstract void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater);

    @CallSuper
    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuItem expandMenu = menu.findItem(R.id.action_expand_collapse);
        if (expandMenu != null) {
            if (mViewModel.isCollapsedMode()) {
                expandMenu.setIcon(R.drawable.ic_expand_more);
                expandMenu.setTitle(R.string.expand_all);
            } else {
                expandMenu.setIcon(R.drawable.ic_expand_less);
                expandMenu.setTitle(R.string.collapse_all);
            }
        }
    }

    @CallSuper
    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_expand_collapse) {
            mViewModel.setCollapsedMode(!mViewModel.isCollapsedMode());
        } else if (id == R.id.action_open) {
            mStoragePermission.request(granted -> {
                if (granted) {
                    displayOpenLogFileDialog();
                }
            });
        } else if (id == R.id.action_save) {
            mStoragePermission.request(granted -> {
                if (granted) {
                    displaySaveLogDialog(false);
                }
            });
        } else if (id == R.id.action_delete) {
            displayDeleteSavedLogsDialog();
        } else if (id == R.id.action_change_log_level) {
            CharSequence[] logLevelsLocalised = getResources().getStringArray(R.array.log_levels);
            new SearchableSingleChoiceDialogBuilder<>(mActivity, LogViewerPreferences.LOG_LEVEL_VALUES, logLevelsLocalised)
                    .setTitle(R.string.log_level)
                    .setSelection(mViewModel.getLogLevel())
                    .setOnSingleChoiceClickListener((dialog, which, selectedLogLevel, isChecked) -> {
                        mViewModel.setLogLevel(selectedLogLevel);
                        // Search again
                        mActivity.search(mSearchCriteria);
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        } else if (id == R.id.action_settings) {
            mActivity.displayLogViewerSettings();
        } else if (id == R.id.action_show_saved_filters) {
            mViewModel.loadFilters();
        }  else if (id == R.id.action_share) {
            displaySaveDebugLogsDialog(true, false);
        } else if (id == R.id.action_export) {
            displaySaveDebugLogsDialog(false, false);
            return true;
        } else return false;
        return true;
    }

    @CallSuper
    @Override
    public void onQuery(@Nullable SearchCriteria searchCriteria) {
        mSearchCriteria = searchCriteria;
        Filter filter = mLogListAdapter.getFilter();
        filter.filter(searchCriteria != null ? searchCriteria.query : null, this);
    }

    @Override
    public final void onFilterComplete(int count) {
        mRecyclerView.scrollToPosition(count - 1);
    }

    @NonNull
    protected final List<String> getCurrentLogsAsListOfStrings() {
        List<String> result = new ArrayList<>(mLogListAdapter.getItemCount());
        for (int i = 0; i < mLogListAdapter.getItemCount(); i++) {
            result.add(mLogListAdapter.getItem(i).getOriginalLine());
        }
        return result;
    }

    @NonNull
    protected final List<String> getSelectedLogsAsStrings() {
        List<String> result = new ArrayList<>();
        for (LogLine logLine : mLogListAdapter.getSelectedLogLines()) {
            result.add(logLine.getOriginalLine());
        }
        return result;
    }

    protected final void displaySaveLogDialog(boolean onlySelected) {
        new TextInputDialogBuilder(mActivity, R.string.filename)
                .setTitle(R.string.save_log)
                .setInputText(SaveLogHelper.createLogFilename())
                .setPositiveButton(R.string.ok, (dialog, which, inputText, isChecked) -> {
                    if (SaveLogHelper.isInvalidFilename(inputText)) {
                        UIUtils.displayShortToast(R.string.enter_good_filename);
                    } else {
                        @SuppressWarnings("ConstantConditions")
                        String filename = inputText.toString();
                        mViewModel.saveLogs(filename, onlySelected ? getSelectedLogsAsStrings() : getCurrentLogsAsListOfStrings());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    protected final void displaySaveDebugLogsDialog(boolean share, boolean onlySelected) {
        View view = View.inflate(mActivity, R.layout.dialog_send_log, null);
        CheckBox includeDeviceInfoCheckBox = view.findViewById(android.R.id.checkbox);
        CheckBox includeDmesgCheckBox = view.findViewById(R.id.checkbox_dmesg);

        includeDeviceInfoCheckBox.setChecked(PreferenceHelper.getIncludeDeviceInfoPreference(mActivity));
        includeDeviceInfoCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                PreferenceHelper.setIncludeDeviceInfoPreference(mActivity, isChecked));

        includeDmesgCheckBox.setChecked(PreferenceHelper.getIncludeDmesgPreference(mActivity));
        includeDmesgCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                PreferenceHelper.setIncludeDmesgPreference(mActivity, isChecked));

        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(share ? R.string.share : R.string.pref_export)
                .setView(view)
                .setPositiveButton(R.string.ok, (dialog, which) ->
                        shareOrSaveLogs(share, onlySelected, includeDeviceInfoCheckBox.isChecked(), includeDmesgCheckBox.isChecked()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void shareOrSaveLogs(boolean share, boolean onlySelected, boolean includeDeviceInfo, boolean includeDmesg) {
        mActivity.setLogsToBeShared(share, includeDeviceInfo || includeDmesg);
        mViewModel.prepareLogsToBeSent(includeDeviceInfo, includeDmesg, onlySelected ? getSelectedLogsAsStrings() : getCurrentLogsAsListOfStrings());
    }

    private void displayOpenLogFileDialog() {
        List<Path> logFiles = SaveLogHelper.getLogFiles();
        if (logFiles.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_saved_logs);
            return;
        }
        new SearchableItemsDialogBuilder<>(mActivity, SaveLogHelper.getFormattedFilenames(mActivity, logFiles))
                .setTitle(R.string.open)
                .setOnItemClickListener((dialog, which, item) -> mActivity.openLogFile(logFiles.get(which)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void displayDeleteSavedLogsDialog() {
        List<Path> logFiles = SaveLogHelper.getLogFiles();
        if (logFiles.isEmpty()) {
            UIUtils.displayShortToast(R.string.no_saved_logs);
            return;
        }
        CharSequence[] filenameArray = SaveLogHelper.getFormattedFilenames(mActivity, logFiles);
        new SearchableMultiChoiceDialogBuilder<>(mActivity, logFiles, filenameArray)
                .setTitle(R.string.manage_saved_logs)
                .setPositiveButton(R.string.delete, (dialog, which, selectedFiles) -> {
                    final int deleteCount = selectedFiles.size();
                    if (deleteCount == 0) return;
                    new MaterialAlertDialogBuilder(mActivity)
                            .setTitle(R.string.delete_saved_log)
                            .setCancelable(true)
                            .setMessage(getResources().getQuantityString(R.plurals.file_deletion_confirmation,
                                    deleteCount, deleteCount))
                            .setPositiveButton(android.R.string.ok, (dialog1, which1) -> {
                                for (Path selectedFile : selectedFiles) {
                                    SaveLogHelper.deleteLogIfExists(selectedFile.getName());
                                }
                                UIUtils.displayShortToast(R.string.deleted_successfully);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
