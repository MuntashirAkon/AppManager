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
import io.github.muntashirakon.AppManager.imagecache.ImageLoader;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private AppsProfileActivity activity;
    private SwipeRefreshLayout swipeRefresh;
    private LinearProgressIndicator progressIndicator;
    private ProfileViewModel model;

    private final ImageLoader imageLoader = new ImageLoader();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (AppsProfileActivity) requireActivity();
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
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this);
        RecyclerView recyclerView = view.findViewById(R.id.scrollView);
        recyclerView.setFitsSystemWindows(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false));
        final TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(R.string.no_apps);
        recyclerView.setEmptyView(emptyView);
        progressIndicator = view.findViewById(R.id.progress_linear);
        progressIndicator.setVisibilityAfterHide(View.GONE);
        progressIndicator.show();
        view.findViewById(R.id.alert_text).setVisibility(View.GONE);
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        model = activity.model;
        AppsRecyclerAdapter adapter = new AppsRecyclerAdapter();
        recyclerView.setAdapter(adapter);
        model.getPackages().observe(getViewLifecycleOwner(), packages -> {
            progressIndicator.hide();
            adapter.setList(packages);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setSubtitle(R.string.apps);
        }
        activity.fab.show();
    }

    @Override
    public void onDestroy() {
        imageLoader.close();
        super.onDestroy();
    }

    @Override
    public void onRefresh() {
        swipeRefresh.setRefreshing(false);
        model.loadPackages();
    }

    public class AppsRecyclerAdapter extends RecyclerView.Adapter<AppsRecyclerAdapter.ViewHolder> {
        PackageManager pm;
        final ArrayList<String> packages = new ArrayList<>();

        private AppsRecyclerAdapter() {
            pm = activity.getPackageManager();
        }

        void setList(List<String> list) {
            packages.clear();
            packages.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.m3_preference, parent, false);
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
            imageLoader.displayImage(packageName, info, holder.icon);
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
            holder.itemView.setOnClickListener(v -> {});
            holder.itemView.setOnLongClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(activity, holder.itemView);
                popupMenu.getMenu().add(R.string.delete).setOnMenuItemClickListener(item -> {
                    model.deletePackage(packageName);
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
