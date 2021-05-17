// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.intercept;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import com.android.internal.util.TextUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import io.github.muntashirakon.AppManager.R;

import java.io.Serializable;

import static io.github.muntashirakon.AppManager.intercept.IntentCompat.parseExtraValue;

public class AddIntentExtraFragment extends DialogFragment {
    public static final String TAG = "AddIntentExtraFragment";
    public static final String ARG_PREF_ITEM = "ARG_PREF_ITEM";
    public static final String ARG_MODE = "ARG_MODE";

    @IntDef(value = {
            MODE_EDIT,
            MODE_CREATE,
            MODE_DELETE
    })
    public @interface Mode {
    }

    public static final int MODE_EDIT = 1;  // Key name is disabled
    public static final int MODE_CREATE = 2;  // Key name is not disabled
    public static final int MODE_DELETE = 3;

    @IntDef(value = {
            TYPE_BOOLEAN,
            TYPE_COMPONENT_NAME,
            TYPE_FLOAT,
            TYPE_FLOAT_ARR,
            TYPE_FLOAT_AL,
            TYPE_INTEGER,
            TYPE_INT_ARR,
            TYPE_INT_AL,
            TYPE_LONG,
            TYPE_LONG_ARR,
            TYPE_LONG_AL,
            TYPE_NULL,
            TYPE_STRING,
            TYPE_STRING_ARR,
            TYPE_STRING_AL,
            TYPE_URI,
    })
    public @interface Type {
    }

    public static final int TYPE_BOOLEAN = 0;
    public static final int TYPE_COMPONENT_NAME = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_FLOAT_ARR = 3;
    public static final int TYPE_FLOAT_AL = 4;
    public static final int TYPE_INTEGER = 5;
    public static final int TYPE_INT_ARR = 6;
    public static final int TYPE_INT_AL = 7;
    public static final int TYPE_LONG = 8;
    public static final int TYPE_LONG_ARR = 9;
    public static final int TYPE_LONG_AL = 10;
    public static final int TYPE_NULL = 11;
    public static final int TYPE_STRING = 12;
    public static final int TYPE_STRING_ARR = 13;
    public static final int TYPE_STRING_AL = 14;
    public static final int TYPE_URI = 15;

    private static final int TYPE_COUNT = 16;

    @Nullable
    private OnSaveListener onSaveListener;

    public interface OnSaveListener {
        void onSave(@Mode int mode, ExtraItem extraItem);
    }

    public static class ExtraItem implements Serializable {
        private static final long serialVersionUID = 4815162342L;

        @Type
        public int type;
        public String keyName;
        @Nullable
        public Object keyValue;

        public ExtraItem() {
        }

        @Override
        @NonNull
        public String toString() {
            return "PrefItem{" +
                    "type=" + type +
                    ", keyName='" + keyName + '\'' +
                    ", keyValue=" + keyValue +
                    '}';
        }
    }

    private final ViewGroup[] mLayoutTypes = new ViewGroup[TYPE_COUNT];
    private final TextView[] mValues = new TextView[TYPE_COUNT];
    @Type
    private int currentType;

    public void setOnSaveListener(@Nullable OnSaveListener onSaveListener) {
        this.onSaveListener = onSaveListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        Bundle args = requireArguments();
        ExtraItem extraItem = (ExtraItem) args.getSerializable(ARG_PREF_ITEM);
        @Mode int mode = args.getInt(ARG_MODE, MODE_CREATE);

        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_edit_pref_item, null);
        Spinner spinner = view.findViewById(R.id.type_selector_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(activity,
                R.array.extras_types, R.layout.item_checked_text_view);
        spinner.setAdapter(spinnerAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, @Type int position, long id) {
                for (ViewGroup layout : mLayoutTypes) layout.setVisibility(View.GONE);
                if (position != TYPE_NULL) {
                    // We don't need a value for null
                    ViewGroup viewGroup = mLayoutTypes[position];
                    viewGroup.setVisibility(View.VISIBLE);
                    if (viewGroup instanceof TextInputLayout) {
                        ((TextInputLayout) viewGroup).setHint(spinnerAdapter.getItem(position));
                    }
                }
                currentType = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        // Set layouts
        mLayoutTypes[TYPE_BOOLEAN] = view.findViewById(R.id.layout_bool);
        mLayoutTypes[TYPE_COMPONENT_NAME] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_FLOAT] = view.findViewById(R.id.layout_float);
        mLayoutTypes[TYPE_FLOAT_ARR] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_FLOAT_AL] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_INTEGER] = view.findViewById(R.id.layout_int);
        mLayoutTypes[TYPE_INT_ARR] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_INT_AL] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_LONG] = view.findViewById(R.id.layout_long);
        mLayoutTypes[TYPE_LONG_ARR] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_LONG_AL] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_NULL] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_STRING] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_STRING_ARR] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_STRING_AL] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_URI] = view.findViewById(R.id.layout_string);
        // Set views
        mValues[TYPE_BOOLEAN] = view.findViewById(R.id.input_bool);
        mValues[TYPE_COMPONENT_NAME] = view.findViewById(R.id.input_string);
        mValues[TYPE_FLOAT] = view.findViewById(R.id.input_float);
        mValues[TYPE_FLOAT_ARR] = view.findViewById(R.id.input_string);
        mValues[TYPE_FLOAT_AL] = view.findViewById(R.id.input_string);
        mValues[TYPE_INTEGER] = view.findViewById(R.id.input_int);
        mValues[TYPE_INT_ARR] = view.findViewById(R.id.input_string);
        mValues[TYPE_INT_AL] = view.findViewById(R.id.input_string);
        mValues[TYPE_LONG] = view.findViewById(R.id.input_long);
        mValues[TYPE_LONG_ARR] = view.findViewById(R.id.input_string);
        mValues[TYPE_LONG_AL] = view.findViewById(R.id.input_string);
        mValues[TYPE_NULL] = view.findViewById(R.id.input_string);
        mValues[TYPE_STRING] = view.findViewById(R.id.input_string);
        mValues[TYPE_STRING_ARR] = view.findViewById(R.id.input_string);
        mValues[TYPE_STRING_AL] = view.findViewById(R.id.input_string);
        mValues[TYPE_URI] = view.findViewById(R.id.input_string);
        // Key name
        TextInputEditText editKeyName = view.findViewById(R.id.key_name);
        if (extraItem != null) {
            // Extra is already set
            currentType = extraItem.type;
            String keyName = extraItem.keyName;
            Object keyValue = extraItem.keyValue;
            editKeyName.setText(keyName);
            if (mode == MODE_EDIT) editKeyName.setEnabled(false);
            for (ViewGroup layout : mLayoutTypes) layout.setVisibility(View.GONE);
            if (currentType != TYPE_NULL) {
                // We don't need a value for null
                ViewGroup viewGroup = mLayoutTypes[currentType];
                viewGroup.setVisibility(View.VISIBLE);
                if (viewGroup instanceof TextInputLayout) {
                    ((TextInputLayout) viewGroup).setHint(spinnerAdapter.getItem(currentType));
                }
                if (keyValue != null) {
                    // FIXME: 25/1/21 Reformat the string to support parsing
                    TextView tv = mValues[TYPE_FLOAT];
                    if (tv instanceof SwitchMaterial) {
                        ((SwitchMaterial) tv).setChecked((boolean) keyValue);
                    } else tv.setText(keyValue.toString());
                }
            }
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setView(view)
                .setPositiveButton(mode == MODE_CREATE ? R.string.add_item : R.string.done, (dialog, which) -> {
                    if (onSaveListener == null) return;
                    if (editKeyName.getText() == null) {
                        Toast.makeText(getActivity(), R.string.key_name_cannot_be_null, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String keyName = editKeyName.getText().toString().trim();
                    ExtraItem newExtraItem;
                    if (extraItem != null) newExtraItem = extraItem;
                    else {
                        newExtraItem = new ExtraItem();
                        newExtraItem.keyName = keyName;
                    }
                    newExtraItem.type = currentType;
                    if (TextUtils.isEmpty(newExtraItem.keyName)) {
                        Toast.makeText(getActivity(), R.string.key_name_cannot_be_null, Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        if (currentType == TYPE_BOOLEAN) {
                            newExtraItem.keyValue = ((SwitchMaterial) mValues[currentType]).isChecked();
                        } else {
                            newExtraItem.keyValue = parseExtraValue(currentType, mValues[currentType].getText().toString().trim());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), R.string.error_evaluating_input, Toast.LENGTH_LONG).show();
                        return;
                    }
                    onSaveListener.onSave(mode, newExtraItem);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                });
        if (mode == MODE_EDIT) {
            builder.setNeutralButton(R.string.delete, (dialog, which) -> {
                if (onSaveListener != null) onSaveListener.onSave(MODE_DELETE, extraItem);
            });
        }
        return builder.create();
    }
}
