// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sysconfig;

import static io.github.muntashirakon.AppManager.utils.UIUtils.getStyledKeyValue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.widget.MaterialSpinner;
import io.github.muntashirakon.widget.RecyclerView;

public class SysConfigActivity extends BaseActivity {
    private SysConfigRecyclerAdapter mAdapter;
    private LinearProgressIndicator mProgressIndicator;
    @NonNull
    @SysConfigType
    private String mType = SysConfigType.TYPE_GROUP;
    private SysConfigViewModel mViewModel;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_sys_config);
        setSupportActionBar(findViewById(R.id.toolbar));
        mViewModel = new ViewModelProvider(this).get(SysConfigViewModel.class);
        MaterialSpinner spinner = findViewById(R.id.spinner);
        // Make spinner the first item to focus on
        spinner.requestFocus();
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setEmptyView(findViewById(android.R.id.empty));
        mProgressIndicator = findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);

        String[] sysConfigTypes = getResources().getStringArray(R.array.sys_config_names);
        ArrayAdapter<String> intervalSpinnerAdapter = new SelectedArrayAdapter<>(this,
                io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item_small, android.R.id.text1, sysConfigTypes);
        spinner.setAdapter(intervalSpinnerAdapter);
        spinner.setOnItemClickListener((parent, view, position, id) -> {
            mProgressIndicator.show();
            mType = sysConfigTypes[position];
            mViewModel.loadSysConfigInfo(mType);
        });

        mAdapter = new SysConfigRecyclerAdapter(this);
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(this));
        recyclerView.setAdapter(mAdapter);
        // Observe data
        mViewModel.getSysConfigInfoListLiveData().observe(this, sysConfigInfoList -> {
            Optional.ofNullable(getSupportActionBar())
                    .ifPresent(actionBar -> actionBar.setSubtitle(mType));
            mAdapter.setList(sysConfigInfoList);
            mProgressIndicator.hide();
        });

        mViewModel.loadSysConfigInfo(mType);
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
        private final List<SysConfigInfo> mList = new ArrayList<>();
        private final SysConfigActivity mActivity;
        private final PackageManager mPm;
        private final int mCardColor0;
        private final int mCardColor1;

        SysConfigRecyclerAdapter(SysConfigActivity activity) {
            mActivity = activity;
            mPm = activity.getPackageManager();
            mCardColor0 = ColorCodes.getListItemColor0(activity);
            mCardColor1 = ColorCodes.getListItemColor1(activity);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sys_config, parent, false);
            return new ViewHolder(view);
        }

        void setList(Collection<SysConfigInfo> list) {
            AdapterUtils.notifyDataSetChanged(this, mList, list);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.icon.setImageDrawable(null);

            holder.itemView.setCardBackgroundColor(position % 2 == 0 ? mCardColor1 : mCardColor0);

            SysConfigInfo info = mList.get(position);
            if (info.isPackage) {
                holder.icon.setVisibility(View.VISIBLE);
                try {
                    ApplicationInfo applicationInfo = mPm.getApplicationInfo(info.name, 0);
                    holder.title.setText(applicationInfo.loadLabel(mPm));
                    holder.packageName.setVisibility(View.VISIBLE);
                    holder.packageName.setText(info.name);
                    // Load icon
                    holder.icon.setTag(applicationInfo.packageName);
                    ImageLoader.getInstance().displayImage(applicationInfo.packageName, applicationInfo, holder.icon);
                } catch (PackageManager.NameNotFoundException e) {
                    holder.title.setText(info.name);
                    holder.packageName.setVisibility(View.GONE);
                    holder.icon.setTag(info.name);
                    ImageLoader.getInstance().displayImage(info.name, null, holder.icon);
                }
                holder.icon.setOnClickListener(v -> {
                    Intent appDetailsIntent = AppDetailsActivity.getIntent(mActivity, info.name, 0);
                    mActivity.startActivity(appDetailsIntent);
                });
            } else {
                holder.icon.setVisibility(View.GONE);
                holder.title.setText(info.name);
                holder.packageName.setVisibility(View.GONE);
            }
            setSubtitle(holder, info);
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }

        private void setSubtitle(@NonNull ViewHolder holder, @NonNull SysConfigInfo info) {
            Context context = holder.itemView.getContext();
            SpannableStringBuilder sb = new SpannableStringBuilder();
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
                    sb.append(getStyledKeyValue(context, "GID", Arrays.toString(info.gids))).append("\n");
                    sb.append(getStyledKeyValue(context, "Per user", String.valueOf(info.perUser)));
                }
                break;
                case SysConfigType.TYPE_ASSIGN_PERMISSION: {
                    // TODO: Display permission info
                    sb.append(getStyledKeyValue(context, "Permissions", ""));
                    if (info.permissions.length == 0) {
                        sb.append(" None");
                    }
                    for (String permissionName : info.permissions) {
                        sb.append("\n- ").append(permissionName);
                    }
                }
                break;
                case SysConfigType.TYPE_SPLIT_PERMISSION: {
                    sb.append(getStyledKeyValue(context, "Target SDK", String.valueOf(info.targetSdk))).append("\n");
                    sb.append(getStyledKeyValue(context, "Permissions", ""));
                    if (info.permissions.length == 0) {
                        sb.append(" None");
                    }
                    for (String permissionName : info.permissions) {
                        sb.append("\n- ").append(permissionName);
                    }
                }
                break;
                case SysConfigType.TYPE_LIBRARY: {
                    sb.append(getStyledKeyValue(context, "Filename", info.filename)).append("\n");
                    sb.append(getStyledKeyValue(context, "Dependencies", ""));
                    if (info.dependencies.length == 0) {
                        sb.append(" None");
                    }
                    for (String dependencyName : info.dependencies) {
                        sb.append("\n- ").append(dependencyName);
                    }
                }
                break;
                case SysConfigType.TYPE_FEATURE: {
                    if (info.version > 0) {
                        sb.append(getStyledKeyValue(context, "Version", String.valueOf(info.version)));
                    }
                }
                break;
                case SysConfigType.TYPE_DEFAULT_ENABLED_VR_APP: {
                    sb.append(getStyledKeyValue(context, "Components", Arrays.toString(info.classNames)));
                    if (info.classNames.length == 0) {
                        sb.append(" None");
                    }
                    for (String className : info.classNames) {
                        sb.append("\n- ").append(className);
                    }
                }
                break;
                case SysConfigType.TYPE_COMPONENT_OVERRIDE: {
                    sb.append(getStyledKeyValue(context, "Components", ""));
                    if (info.classNames.length == 0) {
                        sb.append(" None");
                    }
                    for (int i = 0; i < info.classNames.length; ++i) {
                        sb.append("\n- ")
                                .append(info.classNames[i])
                                .append(" = ")
                                .append(info.whitelist[i] ? "Enabled" : "Disabled");
                    }
                }
                break;
                case SysConfigType.TYPE_BACKUP_TRANSPORT_WHITELISTED_SERVICE: {
                    sb.append(getStyledKeyValue(context, "Services", ""));
                    if (info.classNames.length == 0) {
                        sb.append(" None");
                    }
                    for (String className : info.classNames) {
                        sb.append("\n- ").append(className);
                    }
                }
                break;
                case SysConfigType.TYPE_DISABLED_UNTIL_USED_PREINSTALLED_CARRIER_ASSOCIATED_APP: {
                    sb.append(getStyledKeyValue(context, "Associated packages", ""));
                    if (info.packages.length == 0) {
                        sb.append(" None");
                    }
                    for (int i = 0; i < info.packages.length; ++i) {
                        // TODO Display package labels
                        sb.append("\n- ")
                                .append("Package")
                                .append(LangUtils.getSeparatorString())
                                .append(info.packages[i])
                                .append(", Target SDK")
                                .append(LangUtils.getSeparatorString())
                                .append(String.valueOf(info.targetSdks[i]));
                    }
                }
                break;
                case SysConfigType.TYPE_PRIVAPP_PERMISSIONS:
                case SysConfigType.TYPE_OEM_PERMISSIONS: {
                    sb.append(getStyledKeyValue(context, "Permissions", ""));
                    if (info.permissions.length == 0) {
                        sb.append(" None");
                    }
                    for (int i = 0; i < info.permissions.length; ++i) {
                        sb.append("\n- ")
                                .append(info.permissions[i])
                                .append(" = ")
                                .append(info.whitelist[i] ? "Granted" : "Revoked");
                    }
                }
                break;
                case SysConfigType.TYPE_ALLOW_ASSOCIATION: {
                    sb.append(getStyledKeyValue(context, "Associated packages", ""));
                    if (info.packages.length == 0) {
                        sb.append(" None");
                    }
                    for (String packageName : info.packages) {
                        // TODO Display package labels
                        sb.append("\n- ").append(packageName);
                    }
                }
                break;
                case SysConfigType.TYPE_INSTALL_IN_USER_TYPE: {
                    sb.append(getStyledKeyValue(context, "User types", ""));
                    if (info.userTypes.length == 0) {
                        sb.append(" None");
                    }
                    for (int i = 0; i < info.userTypes.length; ++i) {
                        sb.append("\n- ")
                                .append(info.userTypes[i])
                                .append(" = ")
                                .append(info.whitelist[i] ? "Whitelisted" : "Blacklisted");
                    }
                }
                break;
                case SysConfigType.TYPE_NAMED_ACTOR: {
                    for (int i = 0; i < info.actors.length; ++i) {
                        sb.append("Actor")
                                .append(LangUtils.getSeparatorString())
                                .append(info.actors[i])
                                .append(", Package")
                                .append(LangUtils.getSeparatorString())
                                .append(info.packages[i]).append("\n");
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
            public MaterialCardView itemView;
            public TextView title;
            public TextView packageName;
            public TextView subtitle;
            public ImageView icon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                title = itemView.findViewById(android.R.id.title);
                packageName = itemView.findViewById(R.id.package_name);
                subtitle = itemView.findViewById(android.R.id.summary);
                icon = itemView.findViewById(android.R.id.icon);
            }
        }
    }
}
