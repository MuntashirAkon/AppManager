package io.github.muntashirakon.AppManager.details;

import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsOverlayItem;
import io.github.muntashirakon.AppManager.self.pref.TipsPrefs;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsOverlaysFragment extends AppDetailsFragment {

    private String mPackageName;
    private AppDetailsRecyclerAdapter mAdapter;
    private OverlayManager overlayManager;

    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assert viewModel != null;
        mPackageName = viewModel.getPackageName();
        overlayManager = requireContext().getSystemService(OverlayManager.class);
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_app_details_refresh_actions, menu);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emptyView.setText(R.string.require_no_permission);
        mAdapter = new AppDetailsRecyclerAdapter();
        recyclerView.setAdapter(mAdapter);
        alertView.setEndIconOnClickListener(v -> {
            alertView.hide();
            TipsPrefs.getInstance().setDisplayInOverlaysTab(false);
        });
        if (TipsPrefs.getInstance().displayInOverlaysTab()) {
            alertView.postDelayed(() -> alertView.hide(), 15_000);
        } else {
            alertView.setVisibility(View.GONE);
        }
        if (viewModel == null) return;
        mPackageName = viewModel.getPackageName();
        viewModel.get(AppDetailsFragment.OVERLAYS).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && mAdapter !=null && viewModel.isPackageExist()) {
                mPackageName = viewModel.getPackageName();
                mAdapter.setDefaultList(appDetailsItems);
            } else ProgressIndicatorCompat.setVisibility(progressIndicator, false);
        });
        viewModel.getRuleApplicationStatus().observe(getViewLifecycleOwner(), status -> {
            alertView.setAlertType(MaterialAlertView.ALERT_TYPE_WARN);
            if (status == AppDetailsViewModel.RULE_NOT_APPLIED) {
                alertView.show();
           } else alertView.hide();
        });

    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_refresh_details) {
            if (viewModel == null) return false;
            ProgressIndicatorCompat.setVisibility(progressIndicator, true);
            viewModel.triggerPackageChange();
        } else return false;
        return true;
    }

    @Override
    public void onRefresh() {
        swipeRefresh.setRefreshing(false);
    }

    @Override
    public boolean onQueryTextChange(String newText, int type) {
        if (viewModel !=null) {
            viewModel.setSearchQuery(newText, type, OVERLAYS);
        }
        return true;
    }

    private class AppDetailsRecyclerAdapter extends RecyclerView.Adapter<AppDetailsOverlaysFragment.AppDetailsRecyclerAdapter.ViewHolder> {

        @NonNull
        private final List<AppDetailsItem<?>> mAdapterList;
        @Nullable
        private String mConstraint;

        @UiThread
        void setDefaultList(List<AppDetailsItem<?>> list) {
            ThreadUtils.postOnBackgroundThread(() -> {
                mConstraint = viewModel == null ? null : viewModel.getSearchQuery();
                ThreadUtils.postOnMainThread(() -> {
                    if (isDetached()) return;
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false);
                    synchronized (mAdapterList) {
                        AdapterUtils.notifyDataSetChanged(this, mAdapterList, list);
                    }
                });
            });
        }
        AppDetailsRecyclerAdapter() {
            mAdapterList = new ArrayList<>();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new AppDetailsRecyclerAdapter.ViewHolder(
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.item_app_details_perm, viewGroup, false)
            );
        }

        @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @Override
        public void onBindViewHolder(@NonNull AppDetailsRecyclerAdapter.ViewHolder holder, int index) {
            AppDetailsOverlayItem overlayItem;
            synchronized (mAdapterList) {
                overlayItem = (AppDetailsOverlayItem) mAdapterList.get(index);
            }
            @NonNull OverlayInfo overlayInfo = overlayItem.item;
            final String overlayName = overlayItem.name;

            if (mConstraint != null && overlayName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.overlayName.setText(UIUtils.getHighlightedText(overlayName, mConstraint, colorQueryStringHighlight));
            } else holder.overlayName.setText(overlayName);
            holder.packageName.setText(overlayItem.getPackageName());
            holder.overlayTarget.setText(getString(R.string.overlay_target, overlayInfo.getTargetOverlayableName()));
            holder.overlayCategory.setText(getString(R.string.overlay_category, overlayItem.getCategory()));
            holder.toggleSwitch.setEnabled(true);
            holder.toggleSwitch.setChecked(overlayItem.isEnabled());
            holder.overlayState.setText(getString(R.string.overlay_state, overlayItem.getReadableState(), overlayItem.getPriority()));
            holder.toggleSwitch.setOnCheckedChangeListener((i, o)-> {
                holder.toggleSwitch.setChecked(overlayItem.setEnabled(overlayManager, o));
            });

        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            synchronized (mAdapterList) {
                return mAdapterList.size();
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView itemView;
            TextView overlayName;
            TextView packageName;
            TextView overlayCategory;
            TextView overlayTarget;
            TextView overlayState;
            MaterialSwitch toggleSwitch;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                overlayName = itemView.findViewById(R.id.overlay_name);
                packageName = itemView.findViewById(R.id.overlay_package_name);
                overlayCategory = itemView.findViewById(R.id.overlay_category);
                overlayTarget = itemView.findViewById(R.id.overlay_target);
                overlayState = itemView.findViewById(R.id.overlay_state);
                toggleSwitch = itemView.findViewById(R.id.overlay_toggle_btn);
            }
        }
    }
}
