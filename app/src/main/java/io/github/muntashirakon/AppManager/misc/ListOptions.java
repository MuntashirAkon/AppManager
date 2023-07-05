// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.misc;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputLayout;

import java.util.LinkedHashMap;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment;
import io.github.muntashirakon.widget.MaterialAutoCompleteTextView;

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

    private TextView mSortText;
    private ChipGroup mSortGroup;
    private MaterialCheckBox mReverseSort;
    private TextView mFilterText;
    private ChipGroup mFilterOptions;
    private TextView mOptionsText;
    private LinearLayoutCompat mOptionsView;
    @Nullable
    private ListOptionActions mListOptionActions;
    @Nullable
    private ListOptionsViewModel mListOptionsViewModel;

    protected TextInputLayout profileNameText;
    protected MaterialAutoCompleteTextView profileNameInput;
    protected MaterialButton selectUserView;

    public void setListOptionActions(@Nullable ListOptionActions listOptionActions) {
        if (mListOptionsViewModel != null) {
            mListOptionsViewModel.setListOptionActions(listOptionActions);
        } else mListOptionActions = listOptionActions;
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
        mListOptionsViewModel = new ViewModelProvider(this).get(ListOptionsViewModel.class);
        mSortText = view.findViewById(R.id.sort_text);
        mSortGroup = view.findViewById(R.id.sort_options);
        mReverseSort = view.findViewById(R.id.reverse_sort);
        mFilterText = view.findViewById(R.id.filter_text);
        mFilterOptions = view.findViewById(R.id.filter_options);
        mOptionsText = view.findViewById(R.id.options_text);
        mOptionsView = view.findViewById(R.id.options);
        profileNameText = view.findViewById(android.R.id.text1);
        profileNameInput = view.findViewById(android.R.id.input);
        selectUserView = view.findViewById(R.id.user);

        init(false);
    }

    @Nullable
    public abstract LinkedHashMap<Integer, Integer> getSortIdLocaleMap();

    @Nullable
    public abstract LinkedHashMap<Integer, Integer> getFilterFlagLocaleMap();

    @Nullable
    public abstract LinkedHashMap<Integer, Integer> getOptionIdLocaleMap();

    public boolean enableProfileNameInput() {
        return false;
    }

    public boolean enableSelectUser() {
        return false;
    }

    public void reloadUi() {
        init(true);
    }

    @NonNull
    private ListOptionActions requireListOptionActions() {
        if (mListOptionsViewModel == null) {
            throw new NullPointerException("ViewModel is not initialized.");
        }
        if (mListOptionActions != null) {
            mListOptionsViewModel.setListOptionActions(mListOptionActions);
            mListOptionActions = null;
        }
        ListOptionActions actions = mListOptionsViewModel.getListOptionActions();
        if (actions == null) {
            throw new NullPointerException("ListOptionsActions must be set before calling init.");
        }
        return actions;
    }

    private void init(boolean reinit) {
        // Enable sorting
        LinkedHashMap<Integer, Integer> sortIdLocaleMap = getSortIdLocaleMap();
        boolean sortingEnabled = sortIdLocaleMap != null;
        mSortText.setVisibility(sortingEnabled ? View.VISIBLE : View.GONE);
        mSortGroup.setVisibility(sortingEnabled ? View.VISIBLE : View.GONE);
        mReverseSort.setVisibility(sortingEnabled ? View.VISIBLE : View.GONE);
        if (sortingEnabled) {
            int i = 0;
            for (int sortId : sortIdLocaleMap.keySet()) {
                int sortStringRes = Objects.requireNonNull(sortIdLocaleMap.get(sortId));
                mSortGroup.addView(getRadioChip(sortId, sortStringRes), i);
                ++i;
            }
            mSortGroup.check(requireListOptionActions().getSortBy());
            mSortGroup.setOnCheckedStateChangeListener((group, checkedIds) ->
                    requireListOptionActions().setSortBy(mSortGroup.getCheckedChipId()));
            mReverseSort.setChecked(requireListOptionActions().isReverseSort());
            mReverseSort.setOnCheckedChangeListener((buttonView, isChecked) ->
                    requireListOptionActions().setReverseSort(isChecked));
        }

        // Enable filtering
        LinkedHashMap<Integer, Integer> filterFlagLocaleMap = getFilterFlagLocaleMap();
        boolean filteringEnabled = filterFlagLocaleMap != null;
        mFilterText.setVisibility(filteringEnabled ? View.VISIBLE : View.GONE);
        mFilterOptions.setVisibility(filteringEnabled ? View.VISIBLE : View.GONE);
        if (filteringEnabled) {
            int i = 0;
            for (int flag : filterFlagLocaleMap.keySet()) {
                int flagStringRes = Objects.requireNonNull(filterFlagLocaleMap.get(flag));
                mFilterOptions.addView(getFilterChip(flag, flagStringRes), i);
                ++i;
            }
        }

        // Enable options
        LinkedHashMap<Integer, Integer> optionIdLocaleMap = getOptionIdLocaleMap();
        boolean optionsEnabled = optionIdLocaleMap != null;
        mOptionsText.setVisibility(optionsEnabled ? View.VISIBLE : View.GONE);
        mOptionsView.setVisibility(optionsEnabled ? View.VISIBLE : View.GONE);
        if (optionsEnabled) {
            int i = 0;
            for (int option : optionIdLocaleMap.keySet()) {
                int optionStringRes = Objects.requireNonNull(optionIdLocaleMap.get(option));
                mOptionsView.addView(getOption(option, optionStringRes), i);
                ++i;
            }
        }

        // Profile
        boolean profileEnabled = enableProfileNameInput();
        profileNameText.setVisibility(profileEnabled ? View.VISIBLE : View.GONE);
        profileNameInput.setVisibility(profileEnabled ? View.VISIBLE : View.GONE);

        // User
        boolean selectUserEnabled = enableSelectUser();
        selectUserView.setVisibility(selectUserEnabled ? View.VISIBLE : View.GONE);

        if (reinit) {
            return;
        }

        if (sortingEnabled && mSortGroup.getChildCount() > 0) {
            mSortGroup.getChildAt(0).requestFocus();
        } else if (filteringEnabled && mFilterOptions.getChildCount() > 0) {
            mFilterOptions.getChildAt(0).requestFocus();
        } else if (optionsEnabled && mOptionsView.getChildCount() > 0) {
            mOptionsView.getChildAt(0).requestFocus();
        }
    }

    @NonNull
    private MaterialSwitch getOption(int option, @StringRes int strRes) {
        MaterialSwitch materialSwitch = (MaterialSwitch) View.inflate(mOptionsView.getContext(), R.layout.item_switch, null);
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
        Chip chip = new Chip(mFilterOptions.getContext());
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
        Chip chip = new Chip(mSortGroup.getContext());
        chip.setFocusable(true);
        chip.setCloseIconVisible(false);
        chip.setId(sortOrder);
        chip.setText(strRes);
        return chip;
    }

    public static class ListOptionsViewModel extends AndroidViewModel {
        @Nullable
        private ListOptionActions mListOptionActions;

        public ListOptionsViewModel(@NonNull Application application) {
            super(application);
        }

        public void setListOptionActions(@Nullable ListOptionActions listOptionActions) {
            mListOptionActions = listOptionActions;
        }

        @Nullable
        public ListOptionActions getListOptionActions() {
            return mListOptionActions;
        }
    }
}
