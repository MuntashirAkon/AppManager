// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.Manifest;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Collection;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.widget.MultiSelectionView;

public class MainBatchOpsHandler implements MultiSelectionView.OnSelectionChangeListener {
    private final MainViewModel mViewModel;
    private final MenuItem mUninstallMenu;
    private final MenuItem mFreezeUnfreezeMenu;
    private final MenuItem mForceStopMenu;
    private final MenuItem mClearDataCacheMenu;
    private final MenuItem mSaveApkMenu;
    private final MenuItem mBackupRestoreMenu;
    private final MenuItem mPreventBackgroundMenu;
    private final MenuItem mBlockUnblockTrackersMenu;
    private final MenuItem mNetPolicyMenu;
    private final MenuItem mExportRulesMenu;
    private final MenuItem mExportAppListMenu;
    private final MenuItem mOptimizeMenu;
    private final MenuItem mAddToProfileMenu;

    private boolean mCanFreezeUnfreezePackages;
    private boolean mCanForceStopPackages;
    private boolean mCanClearData;
    private boolean mCanClearCache;
    private boolean mCanModifyAppOpMode;
    private boolean mCanModifyNetPolicy;
    private boolean mCanModifyComponentState;

    public MainBatchOpsHandler(MultiSelectionView multiSelectionView, MainViewModel viewModel) {
        Menu selectionMenu = multiSelectionView.getMenu();
        mViewModel = viewModel;
        mUninstallMenu = selectionMenu.findItem(R.id.action_uninstall);
        mFreezeUnfreezeMenu = selectionMenu.findItem(R.id.action_freeze_unfreeze);
        mForceStopMenu = selectionMenu.findItem(R.id.action_force_stop);
        mClearDataCacheMenu = selectionMenu.findItem(R.id.action_clear_data_cache);
        mSaveApkMenu = selectionMenu.findItem(R.id.action_save_apk);
        mBackupRestoreMenu = selectionMenu.findItem(R.id.action_backup);
        mPreventBackgroundMenu = selectionMenu.findItem(R.id.action_disable_background);
        mBlockUnblockTrackersMenu = selectionMenu.findItem(R.id.action_block_unblock_trackers);
        mNetPolicyMenu = selectionMenu.findItem(R.id.action_net_policy);
        mExportRulesMenu = selectionMenu.findItem(R.id.action_export_blocking_rules);
        mExportAppListMenu = selectionMenu.findItem(R.id.action_export_app_list);
        mOptimizeMenu = selectionMenu.findItem(R.id.action_optimize);
        mAddToProfileMenu = selectionMenu.findItem(R.id.action_add_to_profile);
        updateConstraints();
    }

    public void updateConstraints() {
        mCanFreezeUnfreezePackages = SelfPermissions.canFreezeUnfreezePackages();
        mCanForceStopPackages = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.FORCE_STOP_PACKAGES);
        mCanClearData = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CLEAR_APP_USER_DATA);
        mCanClearCache = SelfPermissions.canClearAppCache();
        mCanModifyAppOpMode = SelfPermissions.canModifyAppOpMode();
        mCanModifyNetPolicy = SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.MANAGE_NETWORK_POLICY);
        mCanModifyComponentState = SelfPermissions.checkSelfOrRemotePermission(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
    }

    @Override
    public boolean onSelectionChange(int selectionCount) {
        Collection<ApplicationItem> selectedItems = mViewModel.getSelectedApplicationItems();
        boolean nonZeroSelection = selectedItems.size() > 0;
        // It was ensured that the algorithm is greedy
        // Best case: O(1)
        // Worst case: O(n)
        boolean areAllInstalled = true;
        boolean areAllUninstalledSystem = true;
        boolean doAllUninstalledhaveBackup = true;
        for (ApplicationItem item : selectedItems) {
            if (item.isInstalled) continue;
            areAllInstalled = false;
            if (!doAllUninstalledhaveBackup && !areAllUninstalledSystem) {
                // No need to check further
                break;
            }
            if (areAllUninstalledSystem && item.isUser) areAllUninstalledSystem = false;
            if (doAllUninstalledhaveBackup && item.backup == null) doAllUninstalledhaveBackup = false;
        }
        /* === Enable/Disable === */
        // Enable “Uninstall” action iff all selections are installed
        mUninstallMenu.setEnabled(nonZeroSelection && areAllInstalled);
        mFreezeUnfreezeMenu.setEnabled(nonZeroSelection && areAllInstalled);
        mForceStopMenu.setEnabled(nonZeroSelection && areAllInstalled);
        mClearDataCacheMenu.setEnabled(nonZeroSelection && areAllInstalled);
        mPreventBackgroundMenu.setEnabled(nonZeroSelection && areAllInstalled);
        mNetPolicyMenu.setEnabled(nonZeroSelection && areAllInstalled);
        mBlockUnblockTrackersMenu.setEnabled(nonZeroSelection && areAllInstalled);
        // Enable “Save APK” action iff all selections are installed or the uninstalled apps are all system apps
        mSaveApkMenu.setEnabled(nonZeroSelection && (areAllInstalled || areAllUninstalledSystem));
        // Enable “Backup/restore” action iff all selections are installed or all the uninstalled apps have backups
        mBackupRestoreMenu.setEnabled(nonZeroSelection && (areAllInstalled || doAllUninstalledhaveBackup));
        // Rests are enabled by default
        mExportRulesMenu.setEnabled(nonZeroSelection);
        mExportAppListMenu.setEnabled(nonZeroSelection);
        mOptimizeMenu.setEnabled(nonZeroSelection);
        mAddToProfileMenu.setEnabled(nonZeroSelection);
        /* === Visible/Invisible === */
        mFreezeUnfreezeMenu.setVisible(mCanFreezeUnfreezePackages);
        mForceStopMenu.setVisible(mCanForceStopPackages);
        mClearDataCacheMenu.setVisible(mCanClearData || mCanClearCache);
        mPreventBackgroundMenu.setVisible(mCanModifyAppOpMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        mNetPolicyMenu.setVisible(mCanModifyNetPolicy);
        mBlockUnblockTrackersMenu.setVisible(mCanModifyComponentState);
        mOptimizeMenu.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        return true;
    }
}
