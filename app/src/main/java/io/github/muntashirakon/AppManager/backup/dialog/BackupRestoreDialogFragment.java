// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.backup.dialog;

import android.annotation.UserIdInt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.UserHandleHidden;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchBackupOptions;
import io.github.muntashirakon.AppManager.types.UserPackagePair;
import io.github.muntashirakon.AppManager.users.UserInfo;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.dialog.BottomSheetBehavior;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;

public class BackupRestoreDialogFragment extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = BackupRestoreDialogFragment.class.getSimpleName();

    private static final String ARG_PACKAGE_PAIRS = "pkg_pairs";
    private static final String ARG_CUSTOM_MODE = "custom_mode";
    private static final String ARG_PREFERRED_USER_FOR_RESTORE = "pref_user_restore";

    @NonNull
    public static BackupRestoreDialogFragment getInstance(@NonNull List<UserPackagePair> userPackagePairs) {
        BackupRestoreDialogFragment fragment = new BackupRestoreDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_PACKAGE_PAIRS, new ArrayList<>(userPackagePairs));
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    public static BackupRestoreDialogFragment getInstanceWithPref(@NonNull List<UserPackagePair> userPackagePairs, @UserIdInt int preferredUserForRestore) {
        BackupRestoreDialogFragment fragment = new BackupRestoreDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_PACKAGE_PAIRS, new ArrayList<>(userPackagePairs));
        args.putInt(ARG_PREFERRED_USER_FOR_RESTORE, preferredUserForRestore);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    public static BackupRestoreDialogFragment getInstance(@NonNull List<UserPackagePair> userPackagePairs, @ActionMode int mode) {
        BackupRestoreDialogFragment fragment = new BackupRestoreDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_PACKAGE_PAIRS, new ArrayList<>(userPackagePairs));
        args.putInt(ARG_CUSTOM_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    @IntDef(flag = true, value = {
            MODE_BACKUP,
            MODE_RESTORE,
            MODE_DELETE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionMode {
    }

    public static final int MODE_BACKUP = 1;
    public static final int MODE_RESTORE = 1 << 1;
    public static final int MODE_DELETE = 1 << 2;

    public interface ActionCompleteInterface {
        void onActionComplete(@ActionMode int mode, @NonNull String[] failedPackages);
    }

    public interface ActionBeginInterface {
        void onActionBegin(@ActionMode int mode);
    }

    @Nullable
    private ActionCompleteInterface mActionCompleteInterface;
    @Nullable
    private ActionBeginInterface mActionBeginInterface;
    @ActionMode
    private int mMode = MODE_BACKUP;
    private FragmentActivity mActivity;
    private BackupRestoreDialogViewModel mViewModel;
    private Fragment[] mTabFragments;
    private TypedArray mTabTitles;
    private DialogTitleBuilder mDialogTitleBuilder;
    private int mCustomModes;

    private final StoragePermission mStoragePermission = StoragePermission.init(this);
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mActionCompleteInterface != null) {
                ArrayList<String> failedPackages = intent.getStringArrayListExtra(BatchOpsService.EXTRA_FAILED_PKG);
                mActionCompleteInterface.onActionComplete(mMode, failedPackages != null ? failedPackages.toArray(new String[0]) : new String[0]);
            }
            mActivity.unregisterReceiver(mBatchOpsBroadCastReceiver);
        }
    };

    public void setOnActionCompleteListener(@NonNull ActionCompleteInterface actionCompleteInterface) {
        mActionCompleteInterface = actionCompleteInterface;
    }

    public void setOnActionBeginListener(@NonNull ActionBeginInterface actionBeginInterface) {
        mActionBeginInterface = actionBeginInterface;
    }

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_backup_restore, container, false);
    }

    @Override
    public boolean displayLoaderByDefault() {
        return true;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = requireActivity();
        mStoragePermission.request();
    }

    @Override
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
        mViewModel = new ViewModelProvider(this).get(BackupRestoreDialogViewModel.class);
        mActivity = requireActivity();
        Bundle args = requireArguments();
        List<UserPackagePair> targetPackages = args.getParcelableArrayList(ARG_PACKAGE_PAIRS);
        mCustomModes = args.getInt(ARG_CUSTOM_MODE, MODE_BACKUP | MODE_RESTORE | MODE_DELETE);
        int preferredUserForRestore = args.getInt(ARG_PREFERRED_USER_FOR_RESTORE, -1);
        if (preferredUserForRestore >= 0) {
            mViewModel.setPreferredUserForRestore(preferredUserForRestore);
        }

        mDialogTitleBuilder = new DialogTitleBuilder(requireContext())
                .setTitle(R.string.backup_restore)
                .setStartIcon(R.drawable.ic_backup_restore);
        setHeader(mDialogTitleBuilder.build());

        mViewModel.getBackupInfoStateLiveData().observe(this, this::loadBody);
        mViewModel.getBackupOperationLiveData().observe(this, this::startOperation);
        mViewModel.getUserSelectionLiveData().observe(this, this::handleCustomUsers);
        mViewModel.processPackages(targetPackages);
    }

    private void loadBody(@BackupInfoState int state) {
        state = getRealState(state);
        Log.d(TAG, "Backup dialog state: " + state);
        switch (state) {
            default:
            case BackupInfoState.NONE:
                showBackupOptionsUnavailable();
                break;
            case BackupInfoState.BACKUP_MULTIPLE:
                loadMultipleBackupFragment();
                break;
            case BackupInfoState.RESTORE_MULTIPLE:
                loadMultipleRestoreFragment();
                break;
            case BackupInfoState.BOTH_MULTIPLE:
                loadMultipleBackupRestoreViewPager();
                break;
            case BackupInfoState.BACKUP_SINGLE:
                loadSingleBackupFragment();
                break;
            case BackupInfoState.RESTORE_SINGLE:
                loadSingleRestoreFragment();
                break;
            case BackupInfoState.BOTH_SINGLE:
                loadSingleBackupRestoreViewPager();
                break;
        }
    }

    @BackupInfoState
    private int getRealState(@BackupInfoState int state) {
        boolean singleMode = state == BackupInfoState.BACKUP_SINGLE || state == BackupInfoState.RESTORE_SINGLE ||
                state == BackupInfoState.BOTH_SINGLE;
        switch (state) {
            default:
            case BackupInfoState.NONE:
                return state;
            case BackupInfoState.BACKUP_MULTIPLE:
            case BackupInfoState.BACKUP_SINGLE:
                if ((mCustomModes & MODE_BACKUP) == 0) {
                    return BackupInfoState.NONE;
                }
                break;
            case BackupInfoState.BOTH_MULTIPLE:
            case BackupInfoState.BOTH_SINGLE:
                boolean canBackup = (mCustomModes & MODE_BACKUP) != 0;
                boolean canRestore = (mCustomModes & MODE_RESTORE) != 0;
                if (!canBackup && !canRestore) {
                    return BackupInfoState.NONE;
                }
                if (!canRestore) {
                    return singleMode ? BackupInfoState.BACKUP_SINGLE : BackupInfoState.BACKUP_MULTIPLE;
                }
                if (!canBackup) {
                    return singleMode ? BackupInfoState.RESTORE_SINGLE : BackupInfoState.RESTORE_MULTIPLE;
                }
                break;
            case BackupInfoState.RESTORE_MULTIPLE:
            case BackupInfoState.RESTORE_SINGLE:
                if ((mCustomModes & MODE_RESTORE) == 0) {
                    return BackupInfoState.NONE;
                }
                break;
        }
        return state;
    }

    private void showBackupOptionsUnavailable() {
        getBody().findViewById(R.id.message).setVisibility(View.VISIBLE);
        getBody().findViewById(R.id.fragment_container_view_tag).setVisibility(View.GONE);
        finishLoading();
    }

    public BackupFragment getBackupFragment() {
        return BackupFragment.getInstance(mViewModel.allowCustomUsersInBackup());
    }

    private void loadMultipleBackupFragment() {
        mDialogTitleBuilder.setTitle(R.string.backup);
        setHeader(mDialogTitleBuilder.build());
        finishLoading();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container_view_tag, getBackupFragment())
                .commit();
    }

    private void loadMultipleRestoreFragment() {
        mDialogTitleBuilder.setTitle(R.string.restore);
        updateMultipleRestoreHeader();
        finishLoading();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container_view_tag, RestoreMultipleFragment.getInstance())
                .commit();
    }

    private void loadMultipleBackupRestoreViewPager() {
        updateMultipleRestoreHeader();

        mTabTitles = getResources().obtainTypedArray(R.array.backup_restore_tabs_multiple);
        mTabFragments = new Fragment[mTabTitles.length()];
        mTabFragments[0] = getBackupFragment();
        mTabFragments[1] = RestoreMultipleFragment.getInstance();
        getBody().findViewById(R.id.container).setVisibility(View.VISIBLE);
        ViewPager2 viewPager = getBody().findViewById(R.id.pager);
        TabLayout tabLayout = getBody().findViewById(R.id.tab_layout);
        viewPager.setOffscreenPageLimit(1);
        viewPager.registerOnPageChangeCallback(new ViewPagerUpdateScrollingChildListener(viewPager, getBehavior()));
        finishLoading();
        viewPager.setAdapter(new BackupDialogFragmentPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(mTabTitles.getString(position)))
                .attach();
    }

    public void updateMultipleRestoreHeader() {
        // Display delete button
        mDialogTitleBuilder.setEndIcon(R.drawable.ic_trash_can, v -> handleDeleteBaseBackup())
                .setEndIconContentDescription(R.string.delete_backup);
        setHeader(mDialogTitleBuilder.build());
    }

    private void loadSingleBackupFragment() {
        mDialogTitleBuilder.setTitle(R.string.backup);
        updateSingleBackupHeader();
        finishLoading();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container_view_tag, getBackupFragment())
                .commit();
    }

    private void loadSingleRestoreFragment() {
        mDialogTitleBuilder.setTitle(R.string.restore_dots);
        updateSingleBackupHeader();
        finishLoading();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container_view_tag, RestoreSingleFragment.getInstance())
                .commit();
    }

    private void loadSingleBackupRestoreViewPager() {
        updateSingleBackupHeader();

        mTabTitles = getResources().obtainTypedArray(R.array.backup_restore_tabs_single);
        mTabFragments = new Fragment[mTabTitles.length()];
        mTabFragments[0] = getBackupFragment();
        mTabFragments[1] = RestoreSingleFragment.getInstance();
        getBody().findViewById(R.id.container).setVisibility(View.VISIBLE);
        ViewPager2 viewPager = getBody().findViewById(R.id.pager);
        TabLayout tabLayout = getBody().findViewById(R.id.tab_layout);
        viewPager.setOffscreenPageLimit(1);
        viewPager.registerOnPageChangeCallback(new ViewPagerUpdateScrollingChildListener(viewPager, getBehavior()));
        finishLoading();
        viewPager.setAdapter(new BackupDialogFragmentPagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(mTabTitles.getString(position)))
                .attach();
    }

    private void updateSingleBackupHeader() {
        mDialogTitleBuilder.setSubtitle(mViewModel.getBackupInfo().getAppLabel());
        setHeader(mDialogTitleBuilder.build());
    }

    public void handleCustomUsers(@NonNull BackupRestoreDialogViewModel.OperationInfo operationInfo) {
        // NonNull check is added because we are only here when there are more than one users
        List<UserInfo> users = Objects.requireNonNull(operationInfo.userInfoList);
        CharSequence[] userNames = new String[users.size()];
        List<Integer> userHandles = new ArrayList<>(users.size());
        int i = 0;
        for (UserInfo info : users) {
            userNames[i] = info.toLocalizedString(requireContext());
            userHandles.add(info.id);
            ++i;
        }

        new SearchableMultiChoiceDialogBuilder<>(mActivity, userHandles, userNames)
                .setTitle(R.string.select_user)
                .addSelections(Collections.singletonList(UserHandleHidden.myUserId()))
                .showSelectAll(false)
                .setPositiveButton(R.string.ok, (dialog, which, selectedUsers) -> {
                    if (!selectedUsers.isEmpty()) {
                        operationInfo.selectedUsers = ArrayUtils.convertToIntArray(selectedUsers);
                    }
                    mViewModel.prepareForOperation(operationInfo);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleDeleteBaseBackup() {
        // TODO: 5/7/22 Clarify the message by including base backup in the message.
        // TODO: 5/7/22 Display a check box that will include all the backups instead of only base backups.
        new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.delete_backup)
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    BackupRestoreDialogViewModel.OperationInfo operationInfo = new BackupRestoreDialogViewModel.OperationInfo();
                    operationInfo.mode = BackupRestoreDialogFragment.MODE_DELETE;
                    operationInfo.op = BatchOpsManager.OP_DELETE_BACKUP;
                    mViewModel.prepareForOperation(operationInfo);
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @UiThread
    private void startOperation(@NonNull BackupRestoreDialogViewModel.OperationInfo operationInfo) {
        mMode = operationInfo.mode;
        if (mActionBeginInterface != null) {
            mActionBeginInterface.onActionBegin(operationInfo.mode);
        }
        ContextCompat.registerReceiver(mActivity, mBatchOpsBroadCastReceiver,
                new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED), ContextCompat.RECEIVER_NOT_EXPORTED);
        // Start batch ops service
        Intent intent = new Intent(mActivity, BatchOpsService.class);
        BatchBackupOptions options = new BatchBackupOptions(operationInfo.flags, operationInfo.backupNames);
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(operationInfo.op,
                operationInfo.packageList, operationInfo.userIdListMappedToPackageList, options);
        intent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, queueItem);
        ContextCompat.startForegroundService(mActivity, intent);
        dismiss();
    }

    private class BackupDialogFragmentPagerAdapter extends FragmentStateAdapter {
        public BackupDialogFragmentPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return mTabFragments[position];
        }

        @Override
        public int getItemCount() {
            return mTabTitles.length();
        }
    }

    private static class ViewPagerUpdateScrollingChildListener extends ViewPager2.OnPageChangeCallback {
        private final ViewPager2 mViewPager;
        private final BottomSheetBehavior<FrameLayout> mBehavior;

        private ViewPagerUpdateScrollingChildListener(ViewPager2 viewPager, BottomSheetBehavior<FrameLayout> behavior) {
            mViewPager = viewPager;
            mBehavior = behavior;
        }

        @Override
        public void onPageSelected(int position) {
            mViewPager.post(mBehavior::updateScrollingChild);
        }
    }
}
