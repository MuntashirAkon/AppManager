// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.db.AppsDb;
import io.github.muntashirakon.AppManager.db.entity.ArchivedApp;

public class ArchivedAppsActivity extends BaseActivity {

    private ArchivedAppsAdapter adapter;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_archived_apps);
        RecyclerView recyclerView = findViewById(R.id.archived_apps_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        AppsDb.getInstance().archivedAppDao().getAll().observe(this, archivedApps -> {
            if (adapter == null) {
                adapter = new ArchivedAppsAdapter(archivedApps, this::onRestoreClicked);
                recyclerView.setAdapter(adapter);
            } else {
                adapter.updateData(archivedApps);
            }
        });
    }

    private void onRestoreClicked(ArchivedApp archivedApp) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + archivedApp.packageName));
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + archivedApp.packageName)));
        }
    }
}
