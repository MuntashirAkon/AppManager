// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.fm;

import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.collection.ArrayMap;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.FileNotFoundException;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.StorageUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.widget.MultiSelectionView;
import me.zhanghai.android.fastscroll.FastScrollerBuilder;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getSecondaryText;
import static io.github.muntashirakon.AppManager.utils.UIUtils.getSmallerText;

public class FmActivity extends BaseActivity implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    FmViewModel model;
    @Nullable
    private FmAdapter adapter;
    @Nullable
    private LinearProgressIndicator progressIndicator;
    @Nullable
    private SwipeRefreshLayout swipeRefresh;
    @Nullable
    private MultiSelectionView multiSelectionView;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_fm);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            UIUtils.setupSearchView(actionBar, this);
        }
        model = new ViewModelProvider(this).get(FmViewModel.class);
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = findViewById(R.id.list_item);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        new FastScrollerBuilder(recyclerView).useMd2Style().build();
        adapter = new FmAdapter(this);
        recyclerView.setAdapter(adapter);
        multiSelectionView = findViewById(R.id.selection_view);
        multiSelectionView.hide();
        // Set observer
        model.observeFiles().observe(this, fmItems -> {
            progressIndicator.hide();
            adapter.setFmList(fmItems);
        });
        try {
            model.loadFiles(AppPref.getSelectedDirectory());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            finish();
        }
    }

    @Override
    public void onRefresh() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_fm_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_storage) {
            new Thread(() -> {
                ArrayMap<String, Uri> storageLocations = StorageUtils.getAllStorageLocations(this, true);
                if (storageLocations.size() == 0) {
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.storage)
                                .setMessage(R.string.no_volumes_found)
                                .setNegativeButton(R.string.ok, null)
                                .show();
                    });
                    return;
                }
                Uri[] backupVolumes = new Uri[storageLocations.size()];
                CharSequence[] backupVolumesStr = new CharSequence[storageLocations.size()];
                for (int i = 0; i < storageLocations.size(); ++i) {
                    backupVolumes[i] = storageLocations.valueAt(i);
                    backupVolumesStr[i] = new SpannableStringBuilder(storageLocations.keyAt(i)).append("\n")
                            .append(getSecondaryText(this, getSmallerText(backupVolumes[i].getPath())));
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.storage)
                            .setItems(backupVolumesStr, (dialog, which) -> {
                                try {
                                    model.loadFiles(backupVolumes[which]);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            }).start();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // TODO: 3/7/21
        return false;
    }
}
