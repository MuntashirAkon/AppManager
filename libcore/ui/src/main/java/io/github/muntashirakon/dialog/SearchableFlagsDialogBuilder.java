// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchableFlagsDialogBuilder<T extends Number> extends SearchableMultiChoiceDialogBuilder<T> {
    public SearchableFlagsDialogBuilder(@NonNull Context context, @NonNull List<T> items, @ArrayRes int itemNames,
                                        @NonNull T defaultFlags) {
        this(context, items, context.getResources().getTextArray(itemNames), defaultFlags);
    }

    public SearchableFlagsDialogBuilder(@NonNull Context context, @NonNull T[] items, @NonNull CharSequence[] itemNames,
                                        @NonNull T defaultFlags) {
        this(context, Arrays.asList(items), Arrays.asList(itemNames), defaultFlags);
    }

    public SearchableFlagsDialogBuilder(@NonNull Context context, @NonNull List<T> items,
                                        @NonNull CharSequence[] itemNames, @NonNull T defaultFlags) {
        this(context, items, Arrays.asList(itemNames), defaultFlags);
    }

    public SearchableFlagsDialogBuilder(@NonNull Context context, @NonNull List<T> items,
                                        @NonNull List<CharSequence> itemNames, @NonNull T defaultFlags) {
        super(context, items, itemNames);
        List<T> selectedFlags = new ArrayList<>();
        long flags = defaultFlags.longValue();
        for (T item : items) {
            if ((flags & item.longValue()) != 0) {
                selectedFlags.add(item);
            }
        }
        addSelections(selectedFlags);
    }


    @Override
    public SearchableFlagsDialogBuilder<T> setOnMultiChoiceClickListener(@Nullable OnMultiChoiceClickListener<T> onMultiChoiceClickListener) {
        super.setOnMultiChoiceClickListener(onMultiChoiceClickListener);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> addDisabledItems(@Nullable List<T> disabledItems) {
        super.addDisabledItems(disabledItems);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> addSelections(@Nullable List<T> selectedItems) {
        super.addSelections(selectedItems);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> addSelections(@Nullable int[] selectedIndexes) {
        super.addSelections(selectedIndexes);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> removeSelections(@Nullable int[] selectedIndexes) {
        super.removeSelections(selectedIndexes);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> reloadListUi() {
        super.reloadListUi();
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setTextSelectable(boolean textSelectable) {
        super.setTextSelectable(textSelectable);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setCancelable(boolean cancelable) {
        super.setCancelable(cancelable);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> hideSearchBar(boolean hide) {
        super.hideSearchBar(hide);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> showSelectAll(boolean show) {
        super.showSelectAll(show);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setTitle(@Nullable CharSequence title) {
        super.setTitle(title);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setTitle(@StringRes int title) {
        super.setTitle(title);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setTitle(@Nullable View title) {
        super.setTitle(title);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setPositiveButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        super.setPositiveButton(textId, listener);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setPositiveButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        super.setPositiveButton(text, listener);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setNegativeButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        super.setNegativeButton(textId, listener);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setNegativeButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        super.setNegativeButton(text, listener);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setNeutralButton(@StringRes int textId, @Nullable OnClickListener<T> listener) {
        super.setNeutralButton(textId, listener);
        return this;
    }

    @Override
    public SearchableFlagsDialogBuilder<T> setNeutralButton(@NonNull CharSequence text, @Nullable OnClickListener<T> listener) {
        super.setNeutralButton(text, listener);
        return this;
    }
}
