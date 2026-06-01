// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.resources.MaterialAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.misc.SearchViewDebouncer;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SearchView;

public class ChangeLanguageFragment extends Fragment {
    private RecyclerView mRecyclerView;
    private FrameLayout mViewContainer;
    @Nullable
    private SearchableRecyclerViewAdapter<String> mAdapter;
    private SearchViewDebouncer mSearchDouncer;
    private String mCurrentLang;
    private boolean mIsTextSelectable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(io.github.muntashirakon.ui.R.layout.dialog_searchable_single_choice, container, false);
        mRecyclerView = view.findViewById(android.R.id.list);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRecyclerView.setScrollIndicators(0);
        }
        mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.VERTICAL, false));
        mViewContainer = view.findViewById(io.github.muntashirakon.ui.R.id.container);
        SearchView searchView = view.findViewById(io.github.muntashirakon.ui.R.id.action_search);
        mSearchDouncer = new SearchViewDebouncer(SearchViewDebouncer.DELAY_STANDARD);
        mSearchDouncer.bind(searchView, query -> {
            if (mAdapter != null) {
                mAdapter.setFilteredItems(query);
            }
        });
        view.setFitsSystemWindows(true);
        boolean secondary = false;
        if (getArguments() != null) {
            secondary = requireArguments().getBoolean(PreferenceFragment.PREF_SECONDARY);
            requireArguments().remove(PreferenceFragment.PREF_KEY);
            requireArguments().remove(PreferenceFragment.PREF_SECONDARY);
        }
        if (secondary) {
            UiUtils.applyWindowInsetsAsPadding(view, false, true, false, true);
        } else UiUtils.applyWindowInsetsAsPaddingNoTop(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCurrentLang = Prefs.Appearance.getLanguage();
        Map<String, Locale> locales = LangUtils.getAppLanguages(requireActivity());
        final CharSequence[] languageNames = getLanguagesL(locales);
        final String[] languages = new String[languageNames.length];
        int i = 0;
        int localeIndex = 0;
        for (Map.Entry<String, Locale> localeEntry : locales.entrySet()) {
            languages[i] = localeEntry.getKey();
            if (languages[i].equals(mCurrentLang)) {
                localeIndex = i;
            }
            ++i;
        }
        @SuppressLint({"RestrictedApi", "PrivateResource"})
        int layoutId = MaterialAttributes.resolveInteger(requireContext(), androidx.appcompat.R.attr.singleChoiceItemLayout,
                com.google.android.material.R.layout.mtrl_alert_select_dialog_singlechoice);
        mAdapter = new SearchableRecyclerViewAdapter<>(Arrays.asList(languageNames), Arrays.asList(languages), layoutId);
        mAdapter.setOnSelectionChangedListener((masterIndex, rawItem, isSelected) -> {
            if (rawItem != null && isSelected) {
                mCurrentLang = rawItem;
                Prefs.Appearance.setLanguage(mCurrentLang);
                AppearanceUtils.applyConfigurationChangesToActivities();
            }
        });
        mAdapter.setSelectedIndex(localeIndex, false);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.choose_language);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSearchDouncer != null) {
            mSearchDouncer.unbind();
        }
    }

    @NonNull
    private CharSequence[] getLanguagesL(@NonNull Map<String, Locale> locales) {
        CharSequence[] localesL = new CharSequence[locales.size()];
        Locale locale;
        int i = 0;
        for (Map.Entry<String, Locale> localeEntry : locales.entrySet()) {
            locale = localeEntry.getValue();
            if (LangUtils.LANG_AUTO.equals(localeEntry.getKey())) {
                localesL[i] = getString(R.string.auto);
            } else localesL[i] = locale.getDisplayName(locale);
            ++i;
        }
        return localesL;
    }

    static class SingleChoiceItem<E> {
        final int id;
        @NonNull
        final CharSequence name;
        @NonNull
        final E rawItem;
        boolean isSelected;
        boolean isDisabled;

        SingleChoiceItem(int id, @NonNull CharSequence name, @NonNull E rawItem) {
            this.id = id;
            this.name = name;
            this.rawItem = rawItem;
            this.isSelected = false;
            this.isDisabled = false;
        }
    }

    public interface OnSelectionChangedListener<E> {
        void onSelectionChanged(int masterIndex, E rawItem, boolean isSelected);
    }

    private static class ChoiceItemCallback<E> extends DiffUtil.ItemCallback<SingleChoiceItem<E>> {
        @Override
        public boolean areItemsTheSame(@NonNull SingleChoiceItem<E> oldItem, @NonNull SingleChoiceItem<E> newItem) {
            return oldItem.id == newItem.id && Objects.equals(oldItem.rawItem, newItem.rawItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull SingleChoiceItem<E> oldItem, @NonNull SingleChoiceItem<E> newItem) {
            return oldItem.isSelected == newItem.isSelected
                    && oldItem.isDisabled == newItem.isDisabled
                    && Objects.equals(oldItem.name.toString(), newItem.name.toString());
        }
    }

    static class SearchableRecyclerViewAdapter<T> extends RecyclerView.ListAdapter<SingleChoiceItem<T>, SearchableRecyclerViewAdapter.ViewHolder> {
        @NonNull
        final List<SingleChoiceItem<T>> mMasterList = new ArrayList<>();
        @LayoutRes
        private final int mLayoutId;
        @Nullable
        private String mCurrentQuery = null;
        @Nullable
        private T mSelectedRawItem = null;
        @Nullable
        private OnSelectionChangedListener<T> mOnSelectionChangedListener;
        private boolean mIsTextSelectable;

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            super(new ChoiceItemCallback<>());
            mLayoutId = layoutId;

            int size = Math.min(itemNames.size(), items.size());
            for (int i = 0; i < size; ++i) {
                mMasterList.add(new SingleChoiceItem<>(i, itemNames.get(i), items.get(i)));
            }
            dispatchFilteredList();
        }

        public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener<T> listener) {
            mOnSelectionChangedListener = listener;
        }

        public void setTextSelectable(boolean selectable) {
            mIsTextSelectable = selectable;
        }

        void setFilteredItems(String constraint) {
            mCurrentQuery = TextUtils.isEmpty(constraint) ? null : constraint.toLowerCase(Locale.ROOT);
            dispatchFilteredList();
        }

        private void dispatchFilteredList() {
            if (mCurrentQuery == null) {
                submitList(new ArrayList<>(mMasterList));
                return;
            }

            Locale locale = Locale.getDefault();
            List<SingleChoiceItem<T>> filteredList = new ArrayList<>();
            for (SingleChoiceItem<T> item : mMasterList) {
                if (item.name.toString().toLowerCase(locale).contains(mCurrentQuery)
                        || item.rawItem.toString().toLowerCase(Locale.ROOT).contains(mCurrentQuery)) {
                    filteredList.add(item);
                }
            }
            submitList(filteredList);
        }

        @Nullable
        T getSelection() {
            return mSelectedRawItem;
        }

        void setSelection(@Nullable T selectedItem, boolean triggerListener) {
            if (Objects.equals(mSelectedRawItem, selectedItem)) return;

            mSelectedRawItem = selectedItem;
            for (SingleChoiceItem<T> item : mMasterList) {
                boolean targetSelectionState = Objects.equals(item.rawItem, selectedItem);
                if (item.isSelected != targetSelectionState) {
                    item.isSelected = targetSelectionState;
                    if (triggerListener && mOnSelectionChangedListener != null) {
                        mOnSelectionChangedListener.onSelectionChanged(item.id, item.rawItem, targetSelectionState);
                    }
                }
            }
            dispatchFilteredList();
        }

        void setSelectedIndex(int selectedIndex, boolean triggerListener) {
            SingleChoiceItem<T> targetItem = (selectedIndex >= 0 && selectedIndex < mMasterList.size())
                    ? mMasterList.get(selectedIndex) : null;
            setSelection(targetItem != null ? targetItem.rawItem : null, triggerListener);
        }

        void addDisabledItems(@Nullable List<T> disabledItems) {
            if (disabledItems == null) return;
            for (T item : disabledItems) {
                for (SingleChoiceItem<T> wrapper : mMasterList) {
                    if (Objects.equals(wrapper.rawItem, item)) {
                        wrapper.isDisabled = true;
                        break;
                    }
                }
            }
            dispatchFilteredList();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SingleChoiceItem<T> itemWrapper = getItem(position);

            holder.item.setText(itemWrapper.name);
            holder.item.setTextIsSelectable(mIsTextSelectable);
            holder.item.setEnabled(!itemWrapper.isDisabled);
            holder.item.setChecked(itemWrapper.isSelected);

            holder.item.setOnClickListener(v -> {
                if (itemWrapper.isSelected) {
                    // Already selected, do nothing
                    return;
                }
                mSelectedRawItem = itemWrapper.rawItem;
                for (SingleChoiceItem<T> target : mMasterList) {
                    boolean wasSelected = target.isSelected;
                    target.isSelected = (target.id == itemWrapper.id);
                    if (wasSelected != target.isSelected && mOnSelectionChangedListener != null) {
                        // Trigger only if this item wasn't selected before
                        mOnSelectionChangedListener.onSelectionChanged(target.id, target.rawItem, target.isSelected);
                    }
                }
                dispatchFilteredList();
            });
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckedTextView item;

            @SuppressLint("RestrictedApi")
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                item = itemView.findViewById(android.R.id.text1);
                int textAppearanceBodyLarge = MaterialAttributes.resolveInteger(item.getContext(), com.google.android.material.R.attr.textAppearanceBodyLarge, 0);
                TextViewCompat.setTextAppearance(item, textAppearanceBodyLarge);
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                item.setTextColor(MaterialColors.getColor(item.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, -1));
            }
        }
    }
}
