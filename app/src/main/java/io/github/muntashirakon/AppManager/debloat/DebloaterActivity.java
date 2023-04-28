// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.profiles.AppsProfileActivity;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.profiles.ProfileMetaManager;
import io.github.muntashirakon.AppManager.utils.StoragePermission;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.dialog.SearchableMultiChoiceDialogBuilder;
import io.github.muntashirakon.dialog.TextInputDialogBuilder;
import io.github.muntashirakon.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class DebloaterActivity extends BaseActivity implements MultiSelectionView.OnSelectionChangeListener,
        ReflowMenuViewWrapper.OnItemSelectedListener, AdvancedSearchView.OnQueryTextListener {
    DebloaterViewModel viewModel;

    private LinearProgressIndicator mProgressIndicator;
    private MultiSelectionView mMultiSelectionView;

    private final StoragePermission mStoragePermission = StoragePermission.init(this);
    private final BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mProgressIndicator != null) {
                mProgressIndicator.hide();
            }
        }
    };

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_debloater);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupAdvancedSearchView(actionBar, this);
        }
        viewModel = new ViewModelProvider(this).get(DebloaterViewModel.class);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.show();

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        DebloaterRecyclerViewAdapter adapter = new DebloaterRecyclerViewAdapter(this);
        recyclerView.setAdapter(adapter);
        mMultiSelectionView = findViewById(R.id.selection_view);
        mMultiSelectionView.setAdapter(adapter);
        mMultiSelectionView.hide();
        mMultiSelectionView.setOnItemSelectedListener(this);
        mMultiSelectionView.setOnSelectionChangeListener(this);

        viewModel.getDebloatObjectLiveData().observe(this, debloatObjects -> {
            mProgressIndicator.hide();
            adapter.setAdapterList(debloatObjects);
        });
        viewModel.loadPackages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS_COMPLETED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_debloater_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_list_options) {
            DebloaterListOptions dialog = new DebloaterListOptions();
            dialog.show(getSupportFragmentManager(), DebloaterListOptions.TAG);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSelectionChange(int selectionCount) {
        // TODO: 7/8/22
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_uninstall) {
            handleBatchOpWithWarning(BatchOpsManager.OP_UNINSTALL);
        } else if (id == R.id.action_put_back) {
            // TODO: 8/8/22
        } else if (id == R.id.action_freeze_unfreeze) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.freeze_unfreeze)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.freeze, (dialog, which) -> handleBatchOp(BatchOpsManager.OP_FREEZE))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.unfreeze, (dialog, which) -> handleBatchOp(BatchOpsManager.OP_UNFREEZE))
                    .show();
        } else if (id == R.id.action_save_apk) {
            mStoragePermission.request(granted -> {
                if (granted) handleBatchOp(BatchOpsManager.OP_BACKUP_APK);
            });
        } else if (id == R.id.action_block_unblock_trackers) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.block_unblock_trackers)
                    .setMessage(R.string.choose_what_to_do)
                    .setPositiveButton(R.string.block, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.unblock, (dialog, which) ->
                            handleBatchOp(BatchOpsManager.OP_UNBLOCK_TRACKERS))
                    .show();
        } else if (id == R.id.action_new_profile) {
            new TextInputDialogBuilder(this, R.string.input_profile_name)
                    .setTitle(R.string.new_profile)
                    .setHelperText(R.string.input_profile_name_description)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.go, (dialog, which, profName, isChecked) -> {
                        if (!TextUtils.isEmpty(profName)) {
                            //noinspection ConstantConditions
                            startActivity(AppsProfileActivity.getNewProfileIntent(this, profName.toString(),
                                    viewModel.getSelectedPackages().keySet().toArray(new String[0])));
                            mMultiSelectionView.cancel();
                        }
                    })
                    .show();
        } else if (id == R.id.action_add_to_profile) {
            List<ProfileMetaManager> profiles = ProfileManager.getProfileMetadata();
            List<CharSequence> profileNames = new ArrayList<>(profiles.size());
            for (ProfileMetaManager profileMetaManager : profiles) {
                profileNames.add(new SpannableStringBuilder(profileMetaManager.getProfileName()).append("\n")
                        .append(getSecondaryText(this, getSmallerText(profileMetaManager.toLocalizedString(this)))));
            }
            new SearchableMultiChoiceDialogBuilder<>(this, profiles, profileNames)
                    .setTitle(R.string.add_to_profile)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.add, (dialog, which, selectedItems) -> {
                        for (ProfileMetaManager metaManager : selectedItems) {
                            try {
                                metaManager.appendPackages(viewModel.getSelectedPackages().keySet());
                                mMultiSelectionView.cancel();
                                metaManager.writeProfile();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                        UIUtils.displayShortToast(R.string.done);
                    })
                    .show();
        } else return false;
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText, int type) {
        viewModel.setQuery(newText, type);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query, int type) {
        return false;
    }

    private void handleBatchOpWithWarning(@BatchOpsManager.OpType int op) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.are_you_sure)
                .setMessage(R.string.this_action_cannot_be_undone)
                .setPositiveButton(R.string.yes, (dialog, which) -> handleBatchOp(op))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        handleBatchOp(op, null);
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op, @Nullable Bundle args) {
        if (viewModel == null) return;
        if (mProgressIndicator != null) {
            mProgressIndicator.show();
        }
        Intent intent = new Intent(this, BatchOpsService.class);
        BatchOpsManager.Result input = new BatchOpsManager.Result(viewModel.getSelectedPackagesWithUsers());
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, input.getFailedPackages());
        intent.putIntegerArrayListExtra(BatchOpsService.EXTRA_OP_USERS, input.getAssociatedUserHandles());
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(this, intent);
        mMultiSelectionView.cancel();
    }
}
