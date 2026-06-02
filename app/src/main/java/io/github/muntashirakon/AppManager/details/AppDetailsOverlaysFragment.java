package io.github.muntashirakon.AppManager.details;

import static io.github.muntashirakon.util.AdapterUtils.PAYLOAD_HIGHLIGHT_CHANGED;

import android.content.om.IOverlayManager;
import android.graphics.Color;
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
import androidx.recyclerview.widget.DiffUtil;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compat.ManifestCompat;
import io.github.muntashirakon.AppManager.compat.OverlayManagerCompact;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsItem;
import io.github.muntashirakon.AppManager.details.struct.AppDetailsOverlayItem;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.self.SelfPermissions;
import io.github.muntashirakon.AppManager.self.pref.TipsPrefs;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.ThreadUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.appearance.ColorCodes;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.view.ProgressIndicatorCompat;
import io.github.muntashirakon.widget.MaterialAlertView;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsOverlaysFragment extends AppDetailsFragment {

    private static final String TAG = AppDetailsOverlaysFragment.class.getSimpleName();
    private AppDetailsRecyclerAdapter mAdapter;
    private IOverlayManager overlayManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)) {
            overlayManager = OverlayManagerCompact.getOverlayManager();
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_app_details_overlay_actions, menu);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String emptyStringText;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            emptyStringText = getString(R.string.overlay_sdk_version_too_low);
        } else if (!SelfPermissions.checkSelfOrRemotePermission(ManifestCompat.permission.CHANGE_OVERLAY_PACKAGES)) {
            emptyStringText = getString(R.string.no_overlay_permission);
        } else {
            emptyStringText = getString(R.string.no_overlays);
        }
        emptyView.setText(emptyStringText);

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
        viewModel.get(AppDetailsFragment.OVERLAYS).observe(getViewLifecycleOwner(), appDetailsItems -> {
            if (appDetailsItems != null && mAdapter != null && viewModel.isPackageExist()) {
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
            refreshDetails();
        } else if (id == R.id.action_sort_by_name) {
            setSortBy(SORT_BY_NAME);
            menuItem.setChecked(true);
        } else if (id == R.id.action_sort_by_priority) {
            setSortBy(SORT_BY_PRIORITY);
            menuItem.setChecked(true);
        } else return false;
        return true;
    }

    private void setSortBy(@SortOrder int sortBy) {
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        if (viewModel == null) return;
        viewModel.setSortOrder(sortBy, OVERLAYS);
    }

    private void refreshDetails() {
        if (viewModel == null || overlayManager == null) return;
        ProgressIndicatorCompat.setVisibility(progressIndicator, true);
        viewModel.triggerPackageChange();
    }

    @Override
    public void onRefresh() {
        swipeRefresh.setRefreshing(false);
    }

    @Override
    protected void search(String query, int type) {
        if (viewModel != null) {
            viewModel.setSearchQuery(query, type, OVERLAYS);
        }
    }

    static class ItemCallback extends DiffUtil.ItemCallback<AppDetailsOverlayItem> {
        @Override
        public boolean areItemsTheSame(@NonNull AppDetailsOverlayItem oldItem, @NonNull AppDetailsOverlayItem newItem) {
            return Objects.equals(oldItem.name, newItem.name);
        }

        @Override
        public boolean areContentsTheSame(@NonNull AppDetailsOverlayItem oldItem, @NonNull AppDetailsOverlayItem newItem) {
            return oldItem.isEnabled() == newItem.isEnabled()
                    && oldItem.isMutable() == newItem.isMutable()
                    && oldItem.isFabricated() == newItem.isFabricated()
                    && Objects.equals(oldItem.getReadableState(), newItem.getReadableState())
                    && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                    && oldItem.getPriority() == newItem.getPriority();
        }
    }

    private @UiThread
    class AppDetailsRecyclerAdapter extends RecyclerView.ListAdapter<AppDetailsOverlayItem, AppDetailsRecyclerAdapter.ViewHolder> {
        @Nullable
        private String mConstraint;

        AppDetailsRecyclerAdapter() {
            super(new ItemCallback());
        }

        @UiThread
        void setDefaultList(List<AppDetailsItem<?>> list) {
            ThreadUtils.postOnBackgroundThread(() -> {
                String oldConstraint = mConstraint;
                mConstraint = viewModel == null ? null : viewModel.getSearchQuery();
                ArrayList<AppDetailsOverlayItem> items = new ArrayList<>(list.size());
                for (AppDetailsItem<?> item : list) {
                    items.add((AppDetailsOverlayItem) item);
                }
                ThreadUtils.postOnMainThread(() -> {
                    if (isDetached()) return;
                    ProgressIndicatorCompat.setVisibility(progressIndicator, false);
                    submitListWithScrollState(
                            items,
                            AdapterUtils.isStartingSearch(oldConstraint, mConstraint),
                            AdapterUtils.isClearingSearch(oldConstraint, mConstraint)
                    );
                    if (!Objects.equals(oldConstraint, mConstraint)) {
                        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_HIGHLIGHT_CHANGED);
                    }
                });
            });
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            return new ViewHolder(
                    LayoutInflater.from(viewGroup.getContext())
                            .inflate(R.layout.item_app_details_overlay, viewGroup, false)
            );
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty()) {
                for (Object payload : payloads) {
                    if (Objects.equals(payload, PAYLOAD_HIGHLIGHT_CHANGED)) {
                        updateTextHighlights(holder, getItem(position));
                        return;
                    }
                }
            }
            super.onBindViewHolder(holder, position, payloads);
        }

        private void updateTextHighlights(@NonNull ViewHolder holder, @NonNull AppDetailsOverlayItem item) {
            // Highlight searched query
            holder.overlayName.setText(UIUtils.getHighlightedText(item.name, mConstraint, colorQueryStringHighlight));
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.O)
        public void onBindViewHolder(@NonNull ViewHolder holder, int index) {
            AppDetailsOverlayItem overlayItem = getItem(index);
            String overlayName = overlayItem.name;
            // Highlight searched query
            holder.overlayName.setText(UIUtils.getHighlightedText(overlayName, mConstraint, colorQueryStringHighlight));
            holder.packageName.setText(overlayItem.getPackageName());
            if (overlayItem.getCategory() != null) {
                holder.overlayCategory.setVisibility(View.VISIBLE);
                String category = getString(R.string.overlay_category) + LangUtils.getSeparatorString() + overlayItem.getCategory();
                holder.overlayCategory.setText(category);
            } else {
                holder.overlayCategory.setVisibility(View.GONE);
            }
            holder.toggleSwitch.setEnabled(overlayItem.isMutable());
            holder.toggleSwitch.setClickable(true);
            holder.toggleSwitch.setChecked(overlayItem.isEnabled());

            StringBuilder sb = new StringBuilder(getString(R.string.state))
                    .append(LangUtils.getSeparatorString())
                    .append(overlayItem.getReadableState());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                sb.append(" | ")
                        .append(getString(R.string.priority))
                        .append(LangUtils.getSeparatorString())
                        .append(overlayItem.getPriority());
            }
            holder.overlayState.setText(sb);
            holder.itemView.setClickable(false);
            if (overlayItem.isMutable()) {
                holder.toggleSwitch.setClickable(true);
                holder.toggleSwitch.setOnClickListener(v -> {
                    int currentPos = holder.getBindingAdapterPosition();
                    if (currentPos == RecyclerView.NO_POSITION) return;

                    ThreadUtils.postOnBackgroundThread(() -> {
                        try {
                            // TODO: Move to ViewModel
                            boolean targetState = !overlayItem.isEnabled();
                            if (overlayItem.setEnabled(overlayManager, targetState)) {
                                List<AppDetailsOverlayItem> currentListSnapshot = new ArrayList<>(getCurrentList());
                                currentListSnapshot.set(currentPos, overlayItem);
                                ThreadUtils.postOnMainThread(() -> submitListWithScrollState(currentListSnapshot, false, false));
                            } else {
                                throw new Exception("Error Changing Overlay State " + overlayItem);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Couldn't Change Overlay State", e);
                            // On failure, flip the checked layout switch back to match the unchanged state
                            ThreadUtils.postOnMainThread(() -> {
                                holder.toggleSwitch.setChecked(overlayItem.isEnabled());
                                UIUtils.displayShortToast(R.string.failed);
                            });
                        }
                    });
                });
                holder.toggleSwitch.setVisibility(View.VISIBLE);
            } else {
                holder.toggleSwitch.setOnClickListener(null);
                holder.toggleSwitch.setClickable(false);
                holder.toggleSwitch.setVisibility(View.GONE);
            }


            if (overlayItem.isFabricated()) {
                holder.itemView.setStrokeColor(ColorCodes.getPermissionDangerousIndicatorColor(requireContext()));
            }
            holder.itemView.setStrokeColor(Color.TRANSPARENT);

        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView itemView;
            TextView overlayName;
            TextView packageName;
            TextView overlayCategory;
            TextView overlayState;
            MaterialSwitch toggleSwitch;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                this.itemView = (MaterialCardView) itemView;
                overlayName = itemView.findViewById(R.id.overlay_name);
                packageName = itemView.findViewById(R.id.overlay_package_name);
                overlayCategory = itemView.findViewById(R.id.overlay_category);
                overlayState = itemView.findViewById(R.id.overlay_state);
                toggleSwitch = itemView.findViewById(R.id.overlay_toggle_btn);
            }
        }
    }
}
