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
import androidx.collection.ArraySet;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.resources.MaterialAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.utils.LangUtils;
import io.github.muntashirakon.AppManager.utils.appearance.AppearanceUtils;
import io.github.muntashirakon.util.AdapterUtils;
import io.github.muntashirakon.util.UiUtils;
import io.github.muntashirakon.widget.RecyclerView;
import io.github.muntashirakon.widget.SearchView;

public class ChangeLanguageFragment extends Fragment {
    private SearchView mSearchView;
    private RecyclerView mRecyclerView;
    private FrameLayout mViewContainer;
    @Nullable
    private SearchableRecyclerViewAdapter<String> mAdapter;
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
        mSearchView = view.findViewById(io.github.muntashirakon.ui.R.id.action_search);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (mAdapter != null) mAdapter.setFilteredItems(newText);
                return true;
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
        mAdapter.setSelectedIndex(localeIndex, false);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.choose_language);
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

    private void triggerSingleChoiceClickListener(int index, boolean isChecked) {
        if (mAdapter == null) {
            return;
        }
        String selectedItem = mAdapter.mItems.get(index);
        if (selectedItem != null && isChecked) {
            mCurrentLang = selectedItem;
            Prefs.Appearance.setLanguage(mCurrentLang);
            AppearanceUtils.applyConfigurationChangesToActivities();
        }
    }

    class SearchableRecyclerViewAdapter<T> extends RecyclerView.Adapter<SearchableRecyclerViewAdapter<T>.ViewHolder> {
        @NonNull
        private final List<CharSequence> mItemNames;
        @NonNull
        private final List<T> mItems;
        @NonNull
        private final ArrayList<Integer> mFilteredItems = new ArrayList<>();
        private int mSelectedItem = -1;
        private final Set<Integer> mDisabledItems = new ArraySet<>();
        @LayoutRes
        private final int mLayoutId;

        SearchableRecyclerViewAdapter(@NonNull List<CharSequence> itemNames, @NonNull List<T> items, int layoutId) {
            mItemNames = itemNames;
            mItems = items;
            mLayoutId = layoutId;
            synchronized (mFilteredItems) {
                for (int i = 0; i < items.size(); ++i) {
                    mFilteredItems.add(i);
                }
            }
        }

        void setFilteredItems(String constraint) {
            constraint = TextUtils.isEmpty(constraint) ? null : constraint.toLowerCase(Locale.ROOT);
            Locale locale = Locale.getDefault();
            synchronized (mFilteredItems) {
                int previousCount = mFilteredItems.size();
                mFilteredItems.clear();
                for (int i = 0; i < mItems.size(); ++i) {
                    if (constraint == null
                            || mItemNames.get(i).toString().toLowerCase(locale).contains(constraint)
                            || mItems.get(i).toString().toLowerCase(Locale.ROOT).contains(constraint)) {
                        mFilteredItems.add(i);
                    }
                }
                AdapterUtils.notifyDataSetChanged(this, previousCount, mFilteredItems.size());
            }
        }

        @Nullable
        T getSelection() {
            if (mSelectedItem >= 0) {
                return mItems.get(mSelectedItem);
            }
            return null;
        }

        void setSelection(@Nullable T selectedItem, boolean triggerListener) {
            if (selectedItem != null) {
                int index = mItems.indexOf(selectedItem);
                if (index != -1) {
                    setSelectedIndex(index, triggerListener);
                }
            }
        }

        void setSelectedIndex(int selectedIndex, boolean triggerListener) {
            if (selectedIndex == mSelectedItem) {
                // Do nothing
                return;
            }
            updateSelection(false, triggerListener);
            mSelectedItem = selectedIndex;
            updateSelection(true, triggerListener);
            mRecyclerView.setSelection(selectedIndex);
        }

        void addDisabledItems(@Nullable List<T> disabledItems) {
            if (disabledItems != null) {
                for (T item : disabledItems) {
                    int index = mItems.indexOf(item);
                    if (index != -1) {
                        synchronized (mDisabledItems) {
                            mDisabledItems.add(index);
                        }
                    }
                }
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Integer index;
            synchronized (mFilteredItems) {
                index = mFilteredItems.get(position);
            }
            final AtomicBoolean selected = new AtomicBoolean(mSelectedItem == index);
            holder.item.setText(mItemNames.get(index));
            holder.item.setTextIsSelectable(mIsTextSelectable);
            synchronized (mDisabledItems) {
                holder.item.setEnabled(!mDisabledItems.contains(index));
            }
            holder.item.setChecked(selected.get());
            holder.item.setOnClickListener(v -> {
                if (selected.get()) {
                    // Already selected, do nothing
                    return;
                }
                // Unselect the previous and select this one
                updateSelection(false, true);
                mSelectedItem = index;
                // Update selection manually
                selected.set(!selected.get());
                holder.item.setChecked(selected.get());
                triggerSingleChoiceClickListener(index, selected.get());
            });
        }

        @Override
        public int getItemCount() {
            synchronized (mFilteredItems) {
                return mFilteredItems.size();
            }
        }

        private void updateSelection(boolean selected, boolean triggerListener) {
            if (mSelectedItem < 0) {
                return;
            }
            int position;
            synchronized (mFilteredItems) {
                position = mFilteredItems.indexOf(mSelectedItem);
            }
            if (position >= 0) {
                notifyItemChanged(position, AdapterUtils.STUB);
            }
            if (triggerListener) {
                triggerSingleChoiceClickListener(mSelectedItem, selected);
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
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
