// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.reflow.ReflowMenuViewWrapper;
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class DebloaterActivity extends BaseActivity implements MultiSelectionView.OnSelectionChangeListener, ReflowMenuViewWrapper.OnItemSelectedListener {
    DebloaterViewModel viewModel;

    private LinearProgressIndicator mProgressIndicator;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_debloater);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        viewModel = new ViewModelProvider(this).get(DebloaterViewModel.class);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.show();
        SwitchMaterial filterInstalled = findViewById(R.id.filter_installed_apps);
        filterInstalled.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setFilterInstalledApps(isChecked));

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        DebloaterRecyclerViewAdapter adapter = new DebloaterRecyclerViewAdapter(this);
        recyclerView.setAdapter(adapter);
        MultiSelectionView multiSelectionView = findViewById(R.id.selection_view);
        multiSelectionView.setAdapter(adapter);
        multiSelectionView.hide();
        multiSelectionView.setOnItemSelectedListener(this);
        multiSelectionView.setOnSelectionChangeListener(this);

        viewModel.getDebloatObjectLiveData().observe(this, debloatObjects -> {
            mProgressIndicator.hide();
            adapter.setAdapterList(debloatObjects);
        });
        viewModel.loadPackages();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
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
        // TODO: 7/8/22
        return false;
    }
}
