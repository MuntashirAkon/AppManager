/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.sysconfig;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.android.material.progressindicator.ProgressIndicator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.types.RecyclerViewWithEmptyView;

public class SysConfigActivity extends BaseActivity {
    private SysConfigRecyclerAdapter adapter;
    private ProgressIndicator progressIndicator;
    @NonNull
    @SysConfigType
    private String type = SysConfigType.TYPE_GROUP;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sys_config);
        setSupportActionBar(findViewById(R.id.toolbar));
        AppCompatSpinner spinner = findViewById(R.id.spinner);
        RecyclerViewWithEmptyView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setEmptyView(findViewById(android.R.id.empty));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);

        String[] sysConfigTypes = getResources().getStringArray(R.array.sys_config_names);
        SpinnerAdapter intervalSpinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, android.R.id.text1, sysConfigTypes);
        spinner.setAdapter(intervalSpinnerAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                adapter.setList(SysConfigWrapper.getSysConfigs(type = sysConfigTypes[position]));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        adapter = new SysConfigRecyclerAdapter();
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        new Thread(() -> {
            SystemConfig.getInstance();
            runOnUiThread(() -> {
                adapter.setList(SysConfigWrapper.getSysConfigs(type));
                progressIndicator.hide();
            });
        }).start();
    }

    public static class SysConfigRecyclerAdapter extends RecyclerView.Adapter<SysConfigRecyclerAdapter.ViewHolder> {
        private List<SysConfigInfo> list = new ArrayList<>();
        private PackageManager pm = AppManager.getInstance().getPackageManager();

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_icon_title_subtitle, parent, false);
            return new ViewHolder(view);
        }

        public void setList(Collection<SysConfigInfo> list) {
            this.list.clear();
            this.list.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            holder.icon.setImageDrawable(null);

            SysConfigInfo info = list.get(position);
            if (info.isPackage) {
                try {
                    ApplicationInfo applicationInfo = pm.getApplicationInfo(info.name, 0);
                    holder.title.setText(applicationInfo.loadLabel(pm));
                    holder.subtitle.setText(info.name);
                    holder.iconLoader = new IconLoaderThread(holder.icon, applicationInfo);
                    holder.iconLoader.start();
                } catch (PackageManager.NameNotFoundException e) {
                    holder.title.setText(info.name);
                    holder.iconLoader = new IconLoaderThread(holder.icon, null);
                    holder.iconLoader.start();
                }
            } else {
                holder.title.setText(info.name);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView title;
            public TextView subtitle;
            public ImageView icon;
            public IconLoaderThread iconLoader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                subtitle = itemView.findViewById(R.id.item_subtitle);
                icon = itemView.findViewById(R.id.item_icon);
                itemView.findViewById(R.id.item_open).setVisibility(View.GONE);
            }
        }
    }
}
