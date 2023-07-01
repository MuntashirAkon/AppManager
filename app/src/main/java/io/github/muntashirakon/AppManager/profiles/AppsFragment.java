// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private AppsProfileActivity mActivity;
    private SwipeRefreshLayout mSwipeRefresh;
    private LinearProgressIndicator mProgressIndicator;
    private ProfileViewModel mModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (AppsProfileActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.pager_app_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Swipe refresh
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mSwipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = view.findViewById(R.id.scrollView);
        recyclerView.setFitsSystemWindows(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(mActivity, LinearLayoutManager.VERTICAL, false));
        final TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(R.string.no_apps);
        recyclerView.setEmptyView(emptyView);
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        view.findViewById(R.id.alert_text).setVisibility(View.GONE);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        mModel = mActivity.model;
        AppsRecyclerAdapter adapter = new AppsRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        mModel.getPackages().observe(getViewLifecycleOwner(), packages -> {
            mProgressIndicator.hide();
            adapter.setList(packages);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle(R.string.apps);
        }
        mActivity.fab.show();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        mModel.loadPackages();
    }

    public class AppsRecyclerAdapter extends RecyclerView.Adapter<AppsRecyclerAdapter.ViewHolder> {
        PackageManager pm;
        final ArrayList<String> packages = new ArrayList<>();

        private AppsRecyclerAdapter() {
            pm = mActivity.getPackageManager();
        }

        void setList(List<String> list) {
            packages.clear();
            packages.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String packageName = packages.get(position);
            ApplicationInfo info = null;
            try {
                info = pm.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException ignore) {
            }
            ImageLoader.getInstance().displayImage(packageName, info, holder.icon);
            String label;
            if (info != null) {
                label = info.loadLabel(pm).toString();
            } else label = packageName;
            holder.title.setText(label);
            if (packageName.equals(label)) {
                holder.subtitle.setVisibility(View.GONE);
            } else {
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(packageName);
            }
            holder.itemView.setOnClickListener(v -> {
            });
            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(mActivity, holder.itemView);
                popupMenu.setForceShowIcon(true);
                popupMenu.getMenu().add(R.string.delete).setIcon(R.drawable.ic_trash_can)
                        .setOnMenuItemClickListener(item -> {
                            mModel.deletePackage(packageName);
                            return true;
                        });
                popupMenu.show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return packages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title;
            TextView subtitle;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(android.R.id.icon);
                title = itemView.findViewById(android.R.id.title);
                subtitle = itemView.findViewById(android.R.id.summary);
            }
        }
    }
}
