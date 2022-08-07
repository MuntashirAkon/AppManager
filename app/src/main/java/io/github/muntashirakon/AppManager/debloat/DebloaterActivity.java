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
import io.github.muntashirakon.widget.MultiSelectionView;
import io.github.muntashirakon.widget.RecyclerView;

public class DebloaterActivity extends BaseActivity {
    private DebloaterViewModel mViewModel;
    private LinearProgressIndicator mProgressIndicator;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_debloater);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mViewModel = new ViewModelProvider(this).get(DebloaterViewModel.class);

        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.show();
        SwitchMaterial filterInstalled = findViewById(R.id.filter_installed_apps);
        filterInstalled.setOnCheckedChangeListener((buttonView, isChecked) -> mViewModel.setFilterInstalledApps(isChecked));

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        DebloaterRecyclerViewAdapter adapter = new DebloaterRecyclerViewAdapter(this);
        recyclerView.setAdapter(adapter);
        MultiSelectionView multiSelectionView = findViewById(R.id.selection_view);
        multiSelectionView.setAdapter(adapter);
        multiSelectionView.hide();

        mViewModel.getDebloatObjectLiveData().observe(this, debloatObjects -> {
            mProgressIndicator.hide();
            adapter.setAdapterList(debloatObjects);
        });
        mViewModel.loadPackages();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
