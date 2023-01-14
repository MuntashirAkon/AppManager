// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;

public abstract class ListOptions extends CapsuleBottomSheetDialogFragment {
    public static final String TAG = ListOptions.class.getSimpleName();

    public interface ListOptionActions {
        default void setReverseSort(boolean reverseSort) {
        }

        default boolean isReverseSort() {
            return false;
        }

        default void setSortBy(int sortBy) {
        }

        default int getSortBy() {
            return 0;
        }

        default boolean hasFilterFlag(int flag) {
            return false;
        }

        default void addFilterFlag(int flag) {
        }

        default void removeFilterFlag(int flag) {
        }

        default boolean isOptionSelected(int option) {
            return false;
        }

        default void onOptionSelected(int option, boolean selected) {
        }
    }

    private TextView sortText;
    private ChipGroup sortGroup;
    private MaterialCheckBox reverseSort;
    private TextView filterText;
    private ChipGroup filterOptions;
    private TextView optionsText;
    private LinearLayoutCompat optionsView;
    protected TextInputLayout profileNameText;
    protected MaterialAutoCompleteTextView profileNameInput;
    @Nullable
    private ListOptionActions listOptionActions;

    public void setListOptionActions(@Nullable ListOptionActions listOptionActions) {
        this.listOptionActions = listOptionActions;
    }

    @CallSuper
    @NonNull
    @Override
    public View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_list_options, container, false);
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sortText = view.findViewById(R.id.sort_text);
        sortGroup = view.findViewById(R.id.sort_options);
        reverseSort = view.findViewById(R.id.reverse_sort);
        filterText = view.findViewById(R.id.filter_text);
        filterOptions = view.findViewById(R.id.filter_options);
        optionsText = view.findViewById(R.id.options_text);
        optionsView = view.findViewById(R.id.options);
        profileNameText = view.findViewById(android.R.id.text1);
        profileNameInput = view.findViewById(android.R.id.input);

        init(false);
    }

    @Nullable
    public abstract SparseIntArray getSortIdLocaleMap();

    @Nullable
    public abstract SparseIntArray getFilterFlagLocaleMap();

    @Nullable
    public abstract SparseIntArray getOptionIdLocaleMap();

    public abstract boolean enableProfileNameInput();

    public void reloadUi() {
        init(true);
    }

    @NonNull
    private ListOptionActions requireListOptionActions() {
        if (listOptionActions == null) {
            throw new NullPointerException("ListOptionsActions must be set before calling init.");
        }
        return listOptionActions;
    }

    private void init(boolean reinit) {
        // Enable sorting
        SparseIntArray sortIdLocaleMap = getSortIdLocaleMap();
        boolean sortingEnabled = sortIdLocaleMap != null;
        sortText.setVisibility(sortingEnabled ? View.VISIBLE : View.GONE);
        sortGroup.setVisibility(sortingEnabled ? View.VISIBLE : View.GONE);
        reverseSort.setVisibility(sortingEnabled ? View.VISIBLE : View.GONE);
        if (sortingEnabled) {
            for (int i = 0; i < sortIdLocaleMap.size(); ++i) {
                int sortId = sortIdLocaleMap.keyAt(i);
                int sortStringRes = sortIdLocaleMap.valueAt(i);
                sortGroup.addView(getRadioChip(sortId, sortStringRes), i);
            }
            sortGroup.check(requireListOptionActions().getSortBy());
            sortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                requireListOptionActions().setSortBy(sortGroup.getCheckedChipId());
            });
            reverseSort.setChecked(requireListOptionActions().isReverseSort());
            reverseSort.setOnCheckedChangeListener((buttonView, isChecked) ->
                    requireListOptionActions().setReverseSort(isChecked));
        }

        // Enable filtering
        SparseIntArray filterFlagLocaleMap = getFilterFlagLocaleMap();
        boolean filteringEnabled = filterFlagLocaleMap != null;
        filterText.setVisibility(filteringEnabled ? View.VISIBLE : View.GONE);
        filterOptions.setVisibility(filteringEnabled ? View.VISIBLE : View.GONE);
        if (filteringEnabled) {
            for (int i = 0; i < filterFlagLocaleMap.size(); ++i) {
                int flag = filterFlagLocaleMap.keyAt(i);
                int flagStringRes = filterFlagLocaleMap.valueAt(i);
                filterOptions.addView(getFilterChip(flag, flagStringRes), i);
            }
        }

        // Enable options
        SparseIntArray optionIdLocaleMap = getOptionIdLocaleMap();
        boolean optionsEnabled = optionIdLocaleMap != null;
        optionsText.setVisibility(optionsEnabled ? View.VISIBLE : View.GONE);
        optionsView.setVisibility(optionsEnabled ? View.VISIBLE : View.GONE);
        if (optionsEnabled) {
            for (int i = 0; i < optionIdLocaleMap.size(); ++i) {
                int option = optionIdLocaleMap.keyAt(i);
                int optionStringRes = optionIdLocaleMap.valueAt(i);
                optionsView.addView(getOption(option, optionStringRes), i);
            }
        }

        // Profile
        boolean profileEnabled = enableProfileNameInput();
        profileNameText.setVisibility(profileEnabled ? View.VISIBLE : View.GONE);
        profileNameInput.setVisibility(profileEnabled ? View.VISIBLE : View.GONE);

        if (reinit) {
            return;
        }

        if (sortingEnabled && sortGroup.getChildCount() > 0) {
            sortGroup.getChildAt(0).requestFocus();
        } else if (filteringEnabled && filterOptions.getChildCount() > 0) {
            filterOptions.getChildAt(0).requestFocus();
        } else if (optionsEnabled && optionsView.getChildCount() > 0) {
            optionsView.getChildAt(0).requestFocus();
        }
    }

    @NonNull
    private MaterialSwitch getOption(int option, @StringRes int strRes) {
        MaterialSwitch materialSwitch = new MaterialSwitch(optionsView.getContext());
        materialSwitch.setFocusable(true);
        materialSwitch.setId(option);
        materialSwitch.setText(strRes);
        materialSwitch.setChecked(requireListOptionActions().isOptionSelected(option));
        materialSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                requireListOptionActions().onOptionSelected(option, isChecked));
        return materialSwitch;
    }

    @NonNull
    private Chip getFilterChip(int flag, @StringRes int strRes) {
        Chip chip = new Chip(filterOptions.getContext());
        chip.setFocusable(true);
        chip.setCloseIconVisible(false);
        chip.setId(flag);
        chip.setText(strRes);
        chip.setChecked(requireListOptionActions().hasFilterFlag(flag));
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                requireListOptionActions().addFilterFlag(flag);
            } else {
                requireListOptionActions().removeFilterFlag(flag);
            }
        });
        return chip;
    }

    @NonNull
    private Chip getRadioChip(int sortOrder, @StringRes int strRes) {
        Chip chip = new Chip(sortGroup.getContext());
        chip.setFocusable(true);
        chip.setCloseIconVisible(false);
        chip.setId(sortOrder);
        chip.setText(strRes);
        return chip;
    }
}
