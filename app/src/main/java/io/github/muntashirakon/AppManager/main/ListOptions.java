/*
 * Copyright (c) 2021 Muntashir Al-Islam
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

package io.github.muntashirakon.AppManager.main;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.ArrayMap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.profiles.ProfileManager;
import io.github.muntashirakon.AppManager.types.AnyFilterArrayAdapter;
import io.github.muntashirakon.AppManager.types.RadioGroupGridLayout;
import io.github.muntashirakon.AppManager.utils.AppPref;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class ListOptions extends DialogFragment {
    public static final String TAG = "ListOptions";

    @IntDef(value = {
            SORT_BY_DOMAIN,
            SORT_BY_APP_LABEL,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_LAST_UPDATE,
            SORT_BY_SHARED_ID,
            SORT_BY_TARGET_SDK,
            SORT_BY_SHA,
            SORT_BY_DISABLED_APP,
            SORT_BY_BLOCKED_COMPONENTS,
            SORT_BY_BACKUP,
            SORT_BY_TRACKERS,
            SORT_BY_LAST_ACTION,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortOrder {
    }

    public static final int SORT_BY_DOMAIN = 0;  // User/system app
    public static final int SORT_BY_APP_LABEL = 1;
    public static final int SORT_BY_PACKAGE_NAME = 2;
    public static final int SORT_BY_LAST_UPDATE = 3;
    public static final int SORT_BY_SHARED_ID = 4;
    public static final int SORT_BY_TARGET_SDK = 5;
    public static final int SORT_BY_SHA = 6;  // Signature
    public static final int SORT_BY_DISABLED_APP = 7;
    public static final int SORT_BY_BLOCKED_COMPONENTS = 8;
    public static final int SORT_BY_BACKUP = 9;
    public static final int SORT_BY_TRACKERS = 10;
    public static final int SORT_BY_LAST_ACTION = 11;

    @IntDef(flag = true, value = {
            FILTER_NO_FILTER,
            FILTER_USER_APPS,
            FILTER_SYSTEM_APPS,
            FILTER_DISABLED_APPS,
            FILTER_APPS_WITH_RULES,
            FILTER_APPS_WITH_ACTIVITIES,
            FILTER_APPS_WITH_BACKUPS,
            FILTER_RUNNING_APPS,
            FILTER_APPS_WITH_SPLITS,
            FILTER_INSTALLED_APPS,
            FILTER_UNINSTALLED_APPS,
            FILTER_APPS_WITHOUT_BACKUPS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Filter {
    }

    public static final int FILTER_NO_FILTER = 0;
    public static final int FILTER_USER_APPS = 1;
    public static final int FILTER_SYSTEM_APPS = 1 << 1;
    public static final int FILTER_DISABLED_APPS = 1 << 2;
    public static final int FILTER_APPS_WITH_RULES = 1 << 3;
    public static final int FILTER_APPS_WITH_ACTIVITIES = 1 << 4;
    public static final int FILTER_APPS_WITH_BACKUPS = 1 << 5;
    public static final int FILTER_RUNNING_APPS = 1 << 6;
    public static final int FILTER_APPS_WITH_SPLITS = 1 << 7;
    public static final int FILTER_INSTALLED_APPS = 1 << 8;
    public static final int FILTER_UNINSTALLED_APPS = 1 << 9;
    public static final int FILTER_APPS_WITHOUT_BACKUPS = 1 << 10;

    private static final int[] SORT_ITEMS_MAP = {R.string.sort_by_domain, R.string.sort_by_app_label,
            R.string.sort_by_package_name, R.string.sort_by_last_update, R.string.sort_by_shared_user_id,
            R.string.sort_by_target_sdk, R.string.sort_by_sha, R.string.sort_by_disabled_app,
            R.string.sort_by_blocked_components, R.string.sort_by_backup, R.string.trackers, R.string.last_actions};

    private MainViewModel model;
    private final List<String> profileNames = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MainActivity activity = (MainActivity) requireActivity();
        model = activity.mModel;
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_list_options, null);
        RadioGroupGridLayout sortGroup = view.findViewById(R.id.sort_options);
        MaterialCheckBox reverseSort = view.findViewById(R.id.reverse_sort);
        RecyclerView filterView = view.findViewById(R.id.filter_options);
        MaterialAutoCompleteTextView profileNameInput = view.findViewById(android.R.id.input);
        profileNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null) {
                    String profileName = s.toString().trim();
                    if (profileNames.contains(profileName)) {
                        model.setFilterProfileName(profileName);
                        return;
                    }
                }
                model.setFilterProfileName(null);
            }
        });
        new Thread(() -> {
            profileNames.clear();
            profileNames.addAll(ProfileManager.getProfileNames());
            if (isDetached()) return;
            activity.runOnUiThread(() -> {
                profileNameInput.setAdapter(new AnyFilterArrayAdapter<>(activity, R.layout.item_checked_text_view,
                        profileNames));
                profileNameInput.setText(model.getFilterProfileName());
            });
        }).start();
        // Add radio buttons
        for (int i = 0; i < SORT_ITEMS_MAP.length; ++i) {
            MaterialRadioButton button = new MaterialRadioButton(activity);
            button.setId(i);
            button.setText(SORT_ITEMS_MAP[i]);
            GridLayout.LayoutParams sortGroupLayoutParam = new GridLayout.LayoutParams();
            sortGroupLayoutParam.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f);
            sortGroupLayoutParam.width = 0;
            sortGroup.addView(button, i, sortGroupLayoutParam);
        }
        sortGroup.check(model.getSortBy());
        sortGroup.setOnCheckedChangeListener((group, checkedId) -> model.setSortBy(checkedId));
        // Reverse sort
        reverseSort.setChecked(model.isSortReverse());
        reverseSort.setOnCheckedChangeListener((buttonView, isChecked) -> model.setSortReverse(isChecked));
        // Add filters
        filterView.setHasFixedSize(true);
        filterView.setLayoutManager(new LinearLayoutManager(activity));
        FilterRecyclerViewAdapter adapter = new FilterRecyclerViewAdapter(activity);
        filterView.setAdapter(adapter);
        adapter.setDefaultList();
        return new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.list_options)
                .setIcon(R.drawable.ic_baseline_settings_24)
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .create();
    }

    private static class FilterRecyclerViewAdapter extends RecyclerView.Adapter<FilterRecyclerViewAdapter.ViewHolder> {
        private final ArrayMap<Integer, Integer> flags = new ArrayMap<>();
        private final MainActivity activity;

        public FilterRecyclerViewAdapter(MainActivity activity) {
            this.activity = activity;
        }

        public void setDefaultList() {
            this.flags.clear();
            flags.put(FILTER_USER_APPS, R.string.filter_user_apps);
            flags.put(FILTER_SYSTEM_APPS, R.string.filter_system_apps);
            flags.put(FILTER_DISABLED_APPS, R.string.filter_disabled_apps);
            flags.put(FILTER_APPS_WITH_RULES, R.string.filter_apps_with_rules);
            flags.put(FILTER_APPS_WITH_ACTIVITIES, R.string.filter_apps_with_activities);
            flags.put(FILTER_APPS_WITH_BACKUPS, R.string.filter_apps_with_backups);
            flags.put(FILTER_APPS_WITHOUT_BACKUPS, R.string.filter_apps_without_backups);
            if (AppPref.isRootOrAdbEnabled()) {
                flags.put(FILTER_RUNNING_APPS, R.string.filter_running_apps);
            }
            flags.put(FILTER_APPS_WITH_SPLITS, R.string.filter_apps_with_splits);
            flags.put(FILTER_INSTALLED_APPS, R.string.installed_apps);
            flags.put(FILTER_UNINSTALLED_APPS, R.string.uninstalled_apps);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCheckBox checkBox = new MaterialCheckBox(activity);
            checkBox.setLayoutParams(new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(checkBox);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int flag = flags.keyAt(position);
            int stringRes = flags.valueAt(position);
            MaterialCheckBox checkBox = (MaterialCheckBox) holder.itemView;
            checkBox.setText(stringRes);
            checkBox.setChecked(activity.mModel.hasFilterFlag(flag));
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) activity.mModel.addFilterFlag(flag);
                else activity.mModel.removeFilterFlag(flag);
            });
        }

        @Override
        public int getItemCount() {
            return flags.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {

            public ViewHolder(@NonNull MaterialCheckBox itemView) {
                super(itemView);
            }
        }
    }
}
