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
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.types.RecyclerViewWithEmptyView;

public class SysConfigActivity extends BaseActivity {
    private SysConfigRecyclerAdapter adapter;
    private LinearProgressIndicator progressIndicator;
    @NonNull
    @SysConfigType
    private String type = SysConfigType.TYPE_GROUP;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_sys_config);
        setSupportActionBar(findViewById(R.id.toolbar));
        AppCompatSpinner spinner = findViewById(R.id.spinner);
        RecyclerViewWithEmptyView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setEmptyView(findViewById(android.R.id.empty));
        progressIndicator = findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);

        String[] sysConfigTypes = getResources().getStringArray(R.array.sys_config_names);
        SpinnerAdapter intervalSpinnerAdapter = new ArrayAdapter<>(this,
                R.layout.item_checked_text_view, android.R.id.text1, sysConfigTypes);
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SysConfigRecyclerAdapter extends RecyclerView.Adapter<SysConfigRecyclerAdapter.ViewHolder> {
        private final List<SysConfigInfo> list = new ArrayList<>();
        private final PackageManager pm = AppManager.getInstance().getPackageManager();
        private final int mColorTransparent;
        private final int mColorSemiTransparent;

        SysConfigRecyclerAdapter() {
            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(AppManager.getContext(), R.color.semi_transparent);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sys_config, parent, false);
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

            holder.itemView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);

            SysConfigInfo info = list.get(position);
            if (info.isPackage) {
                holder.icon.setVisibility(View.VISIBLE);
                try {
                    ApplicationInfo applicationInfo = pm.getApplicationInfo(info.name, 0);
                    holder.title.setText(applicationInfo.loadLabel(pm));
                    holder.packageName.setVisibility(View.VISIBLE);
                    holder.packageName.setText(info.name);
                    // Load icon
                    holder.iconLoader = new IconLoaderThread(holder.icon, applicationInfo);
                    holder.iconLoader.start();
                } catch (PackageManager.NameNotFoundException e) {
                    holder.title.setText(info.name);
                    holder.packageName.setVisibility(View.GONE);
                    holder.iconLoader = new IconLoaderThread(holder.icon, null);
                    holder.iconLoader.start();
                }
            } else {
                holder.icon.setVisibility(View.GONE);
                holder.title.setText(info.name);
                holder.packageName.setVisibility(View.GONE);
            }
            setSubtitle(holder, info);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        private void setSubtitle(@NonNull ViewHolder holder, @NonNull SysConfigInfo info) {
            StringBuilder sb = new StringBuilder();
            switch (info.type) {
                case SysConfigType.TYPE_GROUP:
                case SysConfigType.TYPE_UNAVAILABLE_FEATURE:
                case SysConfigType.TYPE_ALLOW_IN_POWER_SAVE_EXCEPT_IDLE:
                case SysConfigType.TYPE_ALLOW_IN_POWER_SAVE:
                case SysConfigType.TYPE_ALLOW_IN_DATA_USAGE_SAVE:
                case SysConfigType.TYPE_ALLOW_UNTHROTTLED_LOCATION:
                case SysConfigType.TYPE_ALLOW_IGNORE_LOCATION_SETTINGS:
                case SysConfigType.TYPE_ALLOW_IMPLICIT_BROADCAST:
                case SysConfigType.TYPE_APP_LINK:
                case SysConfigType.TYPE_SYSTEM_USER_WHITELISTED_APP:
                case SysConfigType.TYPE_SYSTEM_USER_BLACKLISTED_APP:
                case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_APP:
                case SysConfigType.TYPE_HIDDEN_API_WHITELISTED_APP:
                case SysConfigType.TYPE_APP_DATA_ISOLATION_WHITELISTED_APP:
                case SysConfigType.TYPE_BUGREPORT_WHITELISTED:
                case SysConfigType.TYPE_ROLLBACK_WHITELISTED_APP:
                case SysConfigType.TYPE_WHITELISTED_STAGED_INSTALLER:
                    break;
                case SysConfigType.TYPE_PERMISSION: {
                    // TODO: Display permission info
                    sb.append("GID: ").append(Arrays.toString(info.gids)).append("\n");
                    sb.append("Per User: ").append(info.perUser);
                }
                break;
                case SysConfigType.TYPE_ASSIGN_PERMISSION: {
                    // TODO: Display permission info
                    sb.append("Permissions: ").append(Arrays.toString(info.permissions));
                }
                break;
                case SysConfigType.TYPE_SPLIT_PERMISSION: {
                    sb.append("Permissions: ").append(Arrays.toString(info.permissions)).append("\n");
                    sb.append("Target SDK: ").append(info.targetSdk);
                }
                break;
                case SysConfigType.TYPE_LIBRARY: {
                    sb.append("Filename: ").append(info.filename).append("\n");
                    sb.append("Dependencies: ").append(Arrays.toString(info.dependencies));
                }
                break;
                case SysConfigType.TYPE_FEATURE: {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        sb.append("Version: ").append(info.version);
                    }
                }
                break;
                case SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP: {
                    sb.append("Components: ").append(Arrays.toString(info.classNames));
                }
                break;
                case SysConfigType.TYPE_COMPONENT_OVERRIDE: {
                    sb.append("Components:\n");
                    for (int i = 0; i < info.classNames.length; ++i) {
                        sb.append(info.classNames[i]).append(" => ").append(info.whitelist[i]).append("\n");
                    }
                }
                break;
                case SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE: {
                    sb.append("Services: ").append(Arrays.toString(info.classNames));
                }
                break;
                case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP: {
                    sb.append("Associated Packages:\n");
                    for (int i = 0; i < info.packages.length; ++i) {
                        // TODO Display package labels
                        sb.append("Package: ").append(info.packages[i]).append(", Target SDK: ").append(info.targetSdks[i]).append("\n");
                    }
                }
                break;
                case SysConfigType.TYPE_PRIVAPP_PERMISSIONS:
                case SysConfigType.TYPE_OEM_PERMISSIONS: {
                    sb.append("Permissions:\n");
                    for (int i = 0; i < info.permissions.length; ++i) {
                        sb.append(info.permissions[i]).append(" => ").append(info.whitelist[i]).append("\n");
                    }
                }
                break;
                case SysConfigType.TYPE_ALLOW_ASSOCIATION: {
                    // TODO Display package labels
                    sb.append("Associated Packages: ").append(Arrays.toString(info.packages));
                }
                break;
                case SysConfigType.TYPE_INSTALL_IN_USER_TYPE: {
                    sb.append("User Types:\n");
                    for (int i = 0; i < info.userTypes.length; ++i) {
                        sb.append(info.userTypes[i]).append(" => ").append(info.whitelist[i]).append("\n");
                    }
                }
                break;
                case SysConfigType.TYPE_NAMED_ACTOR: {
                    for (int i = 0; i < info.actors.length; ++i) {
                        sb.append("Actor: ").append(info.actors[i]).append(", Package: ").append(info.packages[i]).append("\n");
                    }
                }
                break;
            }
            if (sb.length() > 0) {
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(sb);
            } else holder.subtitle.setVisibility(View.GONE);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView title;
            public TextView packageName;
            public TextView subtitle;
            public ImageView icon;
            public IconLoaderThread iconLoader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.item_title);
                packageName = itemView.findViewById(R.id.package_name);
                subtitle = itemView.findViewById(R.id.item_subtitle);
                icon = itemView.findViewById(R.id.item_icon);
            }
        }
    }
}
