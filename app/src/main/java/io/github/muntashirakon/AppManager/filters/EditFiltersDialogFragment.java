// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.filters.options.FilterOption;
import io.github.muntashirakon.dialog.DialogTitleBuilder;
import io.github.muntashirakon.view.TextInputLayoutCompat;
import io.github.muntashirakon.widget.RecyclerView;

public class EditFiltersDialogFragment extends DialogFragment implements EditFilterOptionFragment.OnClickDialogButtonInterface {
    public static final String TAG = EditFiltersDialogFragment.class.getSimpleName();

    public interface OnSaveDialogButtonInterface {
        @NonNull
        FilterItem getFilterItem();

        void onItemAltered(@NonNull FilterItem item);
    }

    private static final Map<String, Integer> HIGHLIGHT_MAP = new HashMap<String, Integer>() {{
        put("&", Color.RED);
        put("|", Color.RED);
        put("(", Color.RED);
        put(")", Color.RED);
        put("true", Color.BLUE);
        put("false", Color.BLUE);
    }};

    private static class ExprTester extends AbsExpressionEvaluator {
        private final FilterItem mFilterItem;

        public ExprTester(FilterItem filterItem) {
            mFilterItem = filterItem;
        }

        @Override
        protected boolean evalId(@NonNull String id) {
            if (TextUtils.isEmpty(id)) {
                return false;
            }
            // Extract ID
            int idx = id.lastIndexOf('_');
            int intId;
            if (idx >= 0 && id.length() > (idx + 1)) {
                String part2 = id.substring(idx + 1);
                if (TextUtils.isDigitsOnly(part2)) {
                    intId = Integer.parseInt(part2);
                } else intId = 0;
            } else intId = 0;
            FilterOption option = mFilterItem.getFilterOptionForId(intId);
            if (option == null) {
                lastError = "Invalid ID '" + id + "'";
            }
            return option != null;
        }
    }

    private FinderFilterAdapter mFinderFilterAdapter;
    private TextInputLayout mFinderFilterEditorLayout;
    private TextInputEditText mFinderFilterEditor;
    private FilterItem mFilterItem;
    private OnSaveDialogButtonInterface mOnSaveDialogButtonInterface;
    private boolean mFilterEditorModified = false;
    private ExprTester mExprTester;
    private final TextWatcher mFinderFilterEditorWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            updateEditorColors(s);
            mFilterEditorModified = true;
        }
    };

    public void setOnSaveDialogButtonInterface(OnSaveDialogButtonInterface onSaveDialogButtonInterface) {
        mOnSaveDialogButtonInterface = onSaveDialogButtonInterface;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        mFilterItem = Objects.requireNonNull(mOnSaveDialogButtonInterface).getFilterItem();
        mFinderFilterAdapter = new FinderFilterAdapter(mFilterItem);
        View view = View.inflate(activity, R.layout.dialog_edit_filter_item, null);
        RecyclerView recyclerView = view.findViewById(android.R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(mFinderFilterAdapter);
        mFinderFilterEditor = view.findViewById(R.id.editor);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditorLayout = TextInputLayoutCompat.fromTextInputEditText(mFinderFilterEditor);
        DialogTitleBuilder builder = new DialogTitleBuilder(activity)
                .setTitle(R.string.filters)
                .setEndIcon(R.drawable.ic_add, v -> {
                    EditFilterOptionFragment dialogFragment = new EditFilterOptionFragment();
                    Bundle args = new Bundle();
                    dialogFragment.setArguments(args);
                    dialogFragment.setOnClickDialogButtonInterface(this);
                    dialogFragment.show(getChildFragmentManager(), EditFilterOptionFragment.TAG);
                })
                .setEndIconContentDescription(R.string.add_filter_ellipsis);
        mFinderFilterAdapter.setOnItemClickListener(new FinderFilterAdapter.OnClickListener() {
            @Override
            public void onEdit(View view, int position, FilterOption filterOption) {
                displayEditor(position, filterOption);
            }

            @Override
            public void onRemove(View view, int position, FilterOption filterOption) {
                onDeleteItem(position, filterOption.id);
            }
        });
        return new MaterialAlertDialogBuilder(activity)
                .setCustomTitle(builder.build())
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    if (mFilterEditorModified && mFinderFilterEditorLayout.getError() == null) {
                        mFilterItem.setExpr(mFinderFilterEditor.getText().toString());
                    }
                    mOnSaveDialogButtonInterface.onItemAltered(mFilterItem);
                })
                .show();
    }

    private void displayEditor(int position, @NonNull FilterOption filterOption) {
        EditFilterOptionFragment dialogFragment = new EditFilterOptionFragment();
        Bundle args = new Bundle();
        args.putParcelable(EditFilterOptionFragment.ARG_OPTION, filterOption);
        args.putInt(EditFilterOptionFragment.ARG_POSITION, position);
        dialogFragment.setArguments(args);
        dialogFragment.setOnClickDialogButtonInterface(this);
        dialogFragment.show(getChildFragmentManager(), EditFilterOptionFragment.TAG);
    }

    @Override
    public void onAddItem(@NonNull FilterOption item) {
        mFinderFilterAdapter.add(item);
        mFinderFilterEditor.removeTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        updateEditorColors(mFinderFilterEditor.getText());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
    }

    @Override
    public void onUpdateItem(int position, @NonNull FilterOption item) {
        mFinderFilterAdapter.update(position, item);
        mFinderFilterEditor.removeTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        updateEditorColors(mFinderFilterEditor.getText());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
    }

    @Override
    public void onDeleteItem(int position, int id) {
        mFinderFilterAdapter.remove(position, id);
        mFinderFilterEditor.removeTextChangedListener(mFinderFilterEditorWatcher);
        mFinderFilterEditor.setText(mFilterItem.getExpr());
        updateEditorColors(mFinderFilterEditor.getText());
        mFinderFilterEditor.addTextChangedListener(mFinderFilterEditorWatcher);
    }

    private void updateEditorColors(@Nullable Editable s) {
        if (mExprTester == null) {
            mExprTester = new ExprTester(mFilterItem);
        }
        if (s == null) {
            return;
        }
        String text = s.toString();
        for (Map.Entry<String, Integer> entry : HIGHLIGHT_MAP.entrySet()) {
            String keyword = entry.getKey();
            int color = entry.getValue();
            int index = text.indexOf(keyword);
            while (index >= 0) {
                s.setSpan(new ForegroundColorSpan(color), index, index + keyword.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = text.indexOf(keyword, index + keyword.length());
            }
        }
        if (!mExprTester.evaluate(s.toString())) {
            CharSequence error = mExprTester.getLastError();
            mFinderFilterEditorLayout.setError(error);
        } else {
            mFinderFilterEditorLayout.setError(null);
        }
    }
}
