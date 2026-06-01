// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.profiles;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
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
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SwipeRefreshLayout;

public class AppsFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    public static class AppsFragmentItem {
        @NonNull
        public final String packageName;
        @Nullable
        public CharSequence label;
        public ApplicationInfo applicationInfo;
        public IFilterableAppInfo filterableAppInfo;

        public AppsFragmentItem(@NonNull String packageName) {
            this.packageName = packageName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof String) {
                return Objects.equals(packageName, o);
            }
            if (!(o instanceof AppsFragmentItem)) return false;
            AppsFragmentItem that = (AppsFragmentItem) o;
            return Objects.equals(packageName, that.packageName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName);
        }
    }

    private AppsBaseProfileActivity mActivity;
    private SwipeRefreshLayout mSwipeRefresh;
    private LinearProgressIndicator mProgressIndicator;
    private AppsProfileViewModel mModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (AppsBaseProfileActivity) requireActivity();
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
        recyclerView.setLayoutManager(UIUtils.getGridLayoutAt450Dp(mActivity));
        final TextView emptyView = view.findViewById(android.R.id.empty);
        emptyView.setText(R.string.no_apps);
        recyclerView.setEmptyView(emptyView);
        mProgressIndicator = view.findViewById(R.id.progress_linear);
        mProgressIndicator.setVisibilityAfterHide(View.GONE);
        mProgressIndicator.show();
        view.findViewById(R.id.alert_text).setVisibility(View.GONE);
        mSwipeRefresh.setOnChildScrollUpCallback((parent, child) -> recyclerView.canScrollVertically(-1));
        mModel = mActivity.model;
        AppsRecyclerAdapter adapter = new AppsRecyclerAdapter(mActivity);
        recyclerView.setAdapter(adapter);
        mModel.getPackages().observe(getViewLifecycleOwner(), packages -> {
            mProgressIndicator.hide();
            adapter.submitList(packages);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity.getSupportActionBar() != null) {
            mActivity.getSupportActionBar().setSubtitle(mModel.getPreviewTitleString());
        }
        mActivity.fab.show();
    }

    @Override
    public void onRefresh() {
        mSwipeRefresh.setRefreshing(false);
        mModel.loadPackages();
    }

    public static class AppsRecyclerAdapter extends RecyclerView.ListAdapter<AppsFragmentItem, AppsRecyclerAdapter.ViewHolder> {
        private final AppsProfileViewModel mModel;
        private final ImageLoader.DefaultImageDrawable mDefaultImage;

        private static final DiffUtil.ItemCallback<AppsFragmentItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<AppsFragmentItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull AppsFragmentItem oldItem, @NonNull AppsFragmentItem newItem) {
                return Objects.equals(oldItem.packageName, newItem.packageName);
            }

            @Override
            public boolean areContentsTheSame(@NonNull AppsFragmentItem oldItem, @NonNull AppsFragmentItem newItem) {
                return Objects.equals(oldItem.label, newItem.label)
                        && Objects.equals(oldItem.applicationInfo, newItem.applicationInfo);
            }
        };

        private AppsRecyclerAdapter(@NonNull AppsBaseProfileActivity activity) {
            super(DIFF_CALLBACK);
            mModel = activity.model;
            Drawable defaultIcon = activity.getPackageManager().getDefaultActivityIcon();
            mDefaultImage = new ImageLoader.DefaultImageDrawable("android_default_icon", defaultIcon);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(io.github.muntashirakon.ui.R.layout.m3_preference, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppsFragmentItem fragmentItem = getItem(position);
            holder.icon.setTag(fragmentItem);
            holder.icon.setImageDrawable(null);
            if (fragmentItem.applicationInfo != null) {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, fragmentItem.applicationInfo, holder.icon);
            } else {
                ImageLoader.getInstance().displayImage(fragmentItem.packageName, holder.icon, tag ->
                        new ImageLoader.ImageFetcherResult(tag, UIUtils.getBitmapFromDrawable(fragmentItem.filterableAppInfo.getAppIcon()), true, true, mDefaultImage));
            }
            CharSequence label = fragmentItem.label;
            holder.title.setText(label != null ? label : fragmentItem.packageName);
            if (label != null) {
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(fragmentItem.packageName);
            } else {
                holder.subtitle.setVisibility(View.GONE);
            }
            if (fragmentItem.applicationInfo != null) {
                holder.itemView.setOnClickListener(v -> {
                });
                holder.itemView.setOnLongClickListener(v -> {
                    PopupMenu popupMenu = new PopupMenu(holder.itemView.getContext(), holder.itemView);
                    popupMenu.setForceShowIcon(true);
                    popupMenu.getMenu().add(R.string.delete).setIcon(R.drawable.ic_trash_can)
                            .setOnMenuItemClickListener(item -> {
                                mModel.deletePackage(fragmentItem.packageName);
                                return true;
                            });
                    popupMenu.show();
                    return true;
                });
            } else {
                holder.itemView.setOnClickListener(null);
                holder.itemView.setOnLongClickListener(null);
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title;
            TextView subtitle;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(android.R.id.icon);
                icon.setContentDescription(itemView.getContext().getString(R.string.icon));
                title = itemView.findViewById(android.R.id.title);
                subtitle = itemView.findViewById(android.R.id.summary);
            }
        }
    }
}
