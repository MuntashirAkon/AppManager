// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.details.AppDetailsFragment;
import io.github.muntashirakon.AppManager.details.AppDetailsTab;
import io.github.muntashirakon.AppManager.details.AppDetailsTabs;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;

public class AppDetailsTabsPreferences extends Fragment {
    private TabsAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_details_tabs, container, false);
        view.setFitsSystemWindows(true);
        UiUtils.applyWindowInsetsAsPaddingNoTop(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.tabs);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new TabsAdapter();
        recyclerView.setAdapter(mAdapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                mAdapter.move(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                                 int direction) {
            }
        }).attachToRecyclerView(recyclerView);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_app_details_tabs);
    }

    private class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.ViewHolder> {
        private final List<TabItem> mItems = new ArrayList<>();
        @AppDetailsFragment.Property
        private int mInitialTab;

        TabsAdapter() {
            mInitialTab = Prefs.AppDetailsPage.getInitialTab();
            for (Integer id : Prefs.AppDetailsPage.getTabOrder()) {
                for (AppDetailsTab tab : AppDetailsTabs.getDefaultTabs()) {
                    if (tab.getId() == id) {
                        mItems.add(new TabItem(tab, Prefs.AppDetailsPage.isTabEnabled(id)));
                        break;
                    }
                }
            }
        }

        void move(int from, int to) {
            TabItem item = mItems.remove(from);
            mItems.add(to, item);
            notifyItemMoved(from, to);
            saveOrder();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_details_tab, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TabItem item = mItems.get(position);
            holder.title.setText(item.tab.getTitleRes());
            holder.enabled.setChecked(item.enabled);
            holder.home.setImageResource(item.enabled && mInitialTab == item.tab.getId()
                    ? R.drawable.ic_home_dark : R.drawable.ic_home_outlined);
            holder.enabled.setOnClickListener(v -> {
                boolean checked = holder.enabled.isChecked();
                if (!checked && countEnabledTabs() == 1) {
                    holder.enabled.setChecked(true);
                    UIUtils.displayShortToast(R.string.at_least_one_app_details_tab_required);
                    return;
                }
                item.enabled = checked;
                if (!checked && mInitialTab == item.tab.getId()) {
                    mInitialTab = findFirstEnabledTab();
                }
                saveEnabledTabs();
                notifyDataSetChanged();
            });
            holder.home.setOnClickListener(v -> {
                if (!item.enabled || mInitialTab == item.tab.getId()) return;
                int oldSelection = findTabPosition(mInitialTab);
                mInitialTab = item.tab.getId();
                Prefs.AppDetailsPage.setInitialTab(mInitialTab);
                notifyItemChanged(position);
                if (oldSelection >= 0) notifyItemChanged(oldSelection);
            });
            holder.home.setContentDescription(getString(R.string.select_home_tab));
            holder.dragHandle.setContentDescription(getString(R.string.reorder_app_details_tab));
        }

        private int findTabPosition(int id) {
            for (int i = 0; i < mItems.size(); ++i) {
                if (mItems.get(i).tab.getId() == id) return i;
            }
            return -1;
        }

        private void saveOrder() {
            List<Integer> order = new ArrayList<>(mItems.size());
            for (TabItem item : mItems) order.add(item.tab.getId());
            Prefs.AppDetailsPage.setTabOrder(order);
        }

        private void saveEnabledTabs() {
            int enabledFlags = 0;
            for (TabItem item : mItems) {
                if (item.enabled) enabledFlags |= 1 << item.tab.getId();
            }
            Prefs.AppDetailsPage.setEnabledTabFlags(enabledFlags);
            Prefs.AppDetailsPage.setInitialTab(mInitialTab);
        }

        private int countEnabledTabs() {
            int enabledTabs = 0;
            for (TabItem item : mItems) {
                if (item.enabled) ++enabledTabs;
            }
            return enabledTabs;
        }

        @AppDetailsFragment.Property
        private int findFirstEnabledTab() {
            for (TabItem item : mItems) {
                if (item.enabled) return item.tab.getId();
            }
            return AppDetailsFragment.APP_INFO;
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final MaterialCheckBox enabled;
            final ImageView home;
            final ImageView dragHandle;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.title);
                enabled = itemView.findViewById(R.id.enabled);
                home = itemView.findViewById(R.id.home);
                dragHandle = itemView.findViewById(R.id.drag_handle);
            }
        }
    }

    private static class TabItem {
        final AppDetailsTab tab;
        boolean enabled;

        TabItem(@NonNull AppDetailsTab tab, boolean enabled) {
            this.tab = tab;
            this.enabled = enabled;
        }
    }
}
