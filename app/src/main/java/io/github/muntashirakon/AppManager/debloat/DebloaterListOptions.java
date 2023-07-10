// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.debloat;

import android.content.Context;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.chip.Chip;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;

public class DebloaterListOptions extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = DebloaterListOptions.class.getSimpleName();

    @IntDef(flag = true, value = {
            FILTER_NO_FILTER,

            FILTER_LIST_AOSP,
            FILTER_LIST_OEM,
            FILTER_LIST_CARRIER,
            FILTER_LIST_GOOGLE,
            FILTER_LIST_MISC,
            FILTER_LIST_PENDING,

            FILTER_REMOVAL_SAFE,
            FILTER_REMOVAL_REPLACE,
            FILTER_REMOVAL_CAUTION,

            FILTER_USER_APPS,
            FILTER_SYSTEM_APPS,
            FILTER_INSTALLED_APPS,
            FILTER_UNINSTALLED_APPS,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Filter {
    }

    public static final int FILTER_NO_FILTER = 0;

    public static final int FILTER_LIST_AOSP = 1;
    public static final int FILTER_LIST_OEM = 1 << 1;
    public static final int FILTER_LIST_CARRIER = 1 << 2;
    public static final int FILTER_LIST_GOOGLE = 1 << 3;
    public static final int FILTER_LIST_MISC = 1 << 4;
    public static final int FILTER_LIST_PENDING = 1 << 5;

    public static final int FILTER_REMOVAL_SAFE = 1 << 6;
    public static final int FILTER_REMOVAL_REPLACE = 1 << 7;
    public static final int FILTER_REMOVAL_CAUTION = 1 << 8;
    // public static final int FILTER_DEPRECATED_9 = 1 << 9;

    public static final int FILTER_USER_APPS = 1 << 10;
    public static final int FILTER_SYSTEM_APPS = 1 << 11;
    public static final int FILTER_INSTALLED_APPS = 1 << 12;
    public static final int FILTER_UNINSTALLED_APPS = 1 << 13;

    private static final SparseIntArray LIST_FILTER_MAP = new SparseIntArray() {{
        put(FILTER_LIST_AOSP, R.string.debloat_list_aosp);
        put(FILTER_LIST_OEM, R.string.debloat_list_oem);
        put(FILTER_LIST_CARRIER, R.string.debloat_list_carrier);
        put(FILTER_LIST_GOOGLE, R.string.debloat_list_google);
        put(FILTER_LIST_MISC, R.string.debloat_list_misc);
        put(FILTER_LIST_PENDING, R.string.debloat_list_pending);
    }};

    private static final SparseIntArray REMOVAL_FILTER_MAP = new SparseIntArray() {{
        put(FILTER_REMOVAL_SAFE, R.string.debloat_removal_safe);
        put(FILTER_REMOVAL_REPLACE, R.string.debloat_removal_replace);
        put(FILTER_REMOVAL_CAUTION, R.string.debloat_removal_caution);
    }};

    private static final SparseIntArray NORMAL_FILTER_MAP = new SparseIntArray() {{
        put(FILTER_USER_APPS, R.string.filter_user_apps);
        put(FILTER_SYSTEM_APPS, R.string.filter_system_apps);
        put(FILTER_INSTALLED_APPS, R.string.installed_apps);
        put(FILTER_UNINSTALLED_APPS, R.string.uninstalled_apps);
    }};

    @Filter
    public static int getDefaultFilterFlags() {
       return FILTER_LIST_AOSP | FILTER_LIST_OEM | FILTER_LIST_CARRIER | FILTER_LIST_GOOGLE | FILTER_LIST_MISC
               | FILTER_LIST_PENDING | FILTER_REMOVAL_SAFE | FILTER_REMOVAL_REPLACE | FILTER_REMOVAL_CAUTION
               | FILTER_INSTALLED_APPS | FILTER_SYSTEM_APPS;
    }

    private DebloaterViewModel mModel;

    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_debloater_list_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DebloaterActivity activity = (DebloaterActivity) requireActivity();
        mModel = activity.viewModel;
        ViewGroup listTypes = view.findViewById(R.id.list_types);
        for (int i = 0; i < LIST_FILTER_MAP.size(); ++i) {
            listTypes.addView(getFilterChip(listTypes.getContext(), LIST_FILTER_MAP.keyAt(i), LIST_FILTER_MAP.valueAt(i)));
        }
        ViewGroup removalTypes = view.findViewById(R.id.removal_types);
        for (int i = 0; i < REMOVAL_FILTER_MAP.size(); ++i) {
            removalTypes.addView(getFilterChip(removalTypes.getContext(), REMOVAL_FILTER_MAP.keyAt(i), REMOVAL_FILTER_MAP.valueAt(i)));
        }
        ViewGroup filterView = view.findViewById(R.id.filter_options);
        for (int i = 0; i < NORMAL_FILTER_MAP.size(); ++i) {
            filterView.addView(getFilterChip(filterView.getContext(), NORMAL_FILTER_MAP.keyAt(i), NORMAL_FILTER_MAP.valueAt(i)));
        }
    }

    public Chip getFilterChip(@NonNull Context context, @Filter int flag, @StringRes int strRes) {
        Chip chip = new Chip(context);
        chip.setFocusable(true);
        chip.setCloseIconVisible(false);
        chip.setText(strRes);
        chip.setChecked(mModel.hasFilterFlag(flag));
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) mModel.addFilterFlag(flag);
            else mModel.removeFilterFlag(flag);
        });
        return chip;
    }
}
