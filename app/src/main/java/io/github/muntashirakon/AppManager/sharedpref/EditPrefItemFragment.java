// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.sharedpref;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.adapters.SelectedArrayAdapter;
import io.github.muntashirakon.widget.MaterialSpinner;

public class EditPrefItemFragment extends DialogFragment {
    public static final String TAG = EditPrefItemFragment.class.getSimpleName();
    public static final String ARG_PREF_ITEM = "ARG_PREF_ITEM";
    public static final String ARG_MODE = "ARG_MODE";

    @IntDef(value = {
            MODE_EDIT,
            MODE_CREATE,
            MODE_DELETE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mode {
    }

    public static final int MODE_EDIT = 1;  // Key name is disabled
    public static final int MODE_CREATE = 2;  // Key name is not disabled
    public static final int MODE_DELETE = 3;

    @IntDef(value = {
            TYPE_BOOLEAN,
            TYPE_FLOAT,
            TYPE_INTEGER,
            TYPE_LONG,
            TYPE_STRING,
            TYPE_SET
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
    }

    private static final int TYPE_BOOLEAN = 0;
    private static final int TYPE_FLOAT = 1;
    private static final int TYPE_INTEGER = 2;
    private static final int TYPE_LONG = 3;
    private static final int TYPE_STRING = 4;
    private static final int TYPE_SET = 5;

    private InterfaceCommunicator mInterfaceCommunicator;

    public interface InterfaceCommunicator {
        void sendInfo(@Mode int mode, PrefItem prefItem);
    }

    public static class PrefItem implements Parcelable {
        public String keyName;
        public Object keyValue;

        public PrefItem() {
        }

        protected PrefItem(@NonNull Parcel in) {
            keyName = in.readString();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(keyName);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<PrefItem> CREATOR = new Creator<PrefItem>() {
            @Override
            public PrefItem createFromParcel(Parcel in) {
                return new PrefItem(in);
            }

            @Override
            public PrefItem[] newArray(int size) {
                return new PrefItem[size];
            }
        };
    }

    private final ViewGroup[] mLayoutTypes = new ViewGroup[6];
    private final TextView[] mValues = new TextView[6];
    @Type
    private int mCurrentType;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        Bundle args = requireArguments();
        PrefItem prefItem = BundleCompat.getParcelable(args, ARG_PREF_ITEM, PrefItem.class);
        @Mode int mode = args.getInt(ARG_MODE);

        LayoutInflater inflater = LayoutInflater.from(activity);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        View view = inflater.inflate(R.layout.dialog_edit_pref_item, null);
        MaterialSpinner spinner = view.findViewById(R.id.type_selector_spinner);
        ArrayAdapter<CharSequence> spinnerAdapter = SelectedArrayAdapter.createFromResource(activity,
                R.array.shared_pref_types, io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setOnItemClickListener((parent, view1, position, id) -> {
            for (ViewGroup layout : mLayoutTypes) layout.setVisibility(View.GONE);
            mLayoutTypes[position].setVisibility(View.VISIBLE);
            mCurrentType = position;
        });
        // Set layouts
        mLayoutTypes[TYPE_BOOLEAN] = view.findViewById(R.id.layout_bool);
        mLayoutTypes[TYPE_FLOAT] = view.findViewById(R.id.layout_float);
        mLayoutTypes[TYPE_INTEGER] = view.findViewById(R.id.layout_int);
        mLayoutTypes[TYPE_LONG] = view.findViewById(R.id.layout_long);
        mLayoutTypes[TYPE_STRING] = view.findViewById(R.id.layout_string);
        mLayoutTypes[TYPE_SET] = view.findViewById(R.id.layout_string);
        // Set views
        mValues[TYPE_BOOLEAN] = view.findViewById(R.id.input_bool);
        mValues[TYPE_FLOAT] = view.findViewById(R.id.input_float);
        mValues[TYPE_INTEGER] = view.findViewById(R.id.input_int);
        mValues[TYPE_LONG] = view.findViewById(R.id.input_long);
        mValues[TYPE_STRING] = view.findViewById(R.id.input_string);
        mValues[TYPE_SET] = view.findViewById(R.id.input_string);
        // Key name
        TextInputEditText editKeyName = view.findViewById(R.id.key_name);
        if (prefItem != null) {
            String keyName = prefItem.keyName;
            Object keyValue = prefItem.keyValue;
            editKeyName.setText(keyName);
            if (mode == MODE_EDIT) {
                editKeyName.setKeyListener(null);
            }
            // Key value
            if (keyValue instanceof Boolean) {
                mCurrentType = TYPE_BOOLEAN;
                mLayoutTypes[TYPE_BOOLEAN].setVisibility(View.VISIBLE);
                ((MaterialSwitch) mValues[TYPE_BOOLEAN]).setChecked((Boolean) keyValue);
                spinner.setSelection(TYPE_BOOLEAN);
            } else if (keyValue instanceof Float) {
                mCurrentType = TYPE_FLOAT;
                mLayoutTypes[TYPE_FLOAT].setVisibility(View.VISIBLE);
                mValues[TYPE_FLOAT].setText(keyValue.toString());
                spinner.setSelection(TYPE_FLOAT);
            } else if (keyValue instanceof Integer) {
                mCurrentType = TYPE_INTEGER;
                mLayoutTypes[TYPE_INTEGER].setVisibility(View.VISIBLE);
                mValues[TYPE_INTEGER].setText(keyValue.toString());
                spinner.setSelection(TYPE_INTEGER);
            } else if (keyValue instanceof Long) {
                mCurrentType = TYPE_LONG;
                mLayoutTypes[TYPE_LONG].setVisibility(View.VISIBLE);
                mValues[TYPE_LONG].setText(keyValue.toString());
                spinner.setSelection(TYPE_LONG);
            } else if (keyValue instanceof String) {
                mCurrentType = TYPE_STRING;
                mLayoutTypes[TYPE_STRING].setVisibility(View.VISIBLE);
                mValues[TYPE_STRING].setText((String) keyValue);
                spinner.setSelection(TYPE_STRING);
            } else if (keyValue instanceof Set) {
                mCurrentType = TYPE_SET;
                mLayoutTypes[TYPE_SET].setVisibility(View.VISIBLE);
                //noinspection unchecked
                mValues[TYPE_SET].setText(SharedPrefsUtil.flattenToString((Set<String>) keyValue));
                spinner.setSelection(TYPE_SET);
            }
        }
        mInterfaceCommunicator = (InterfaceCommunicator) activity;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setView(view)
                .setPositiveButton(mode == MODE_CREATE ? R.string.add : R.string.done, (dialog, which) -> {
                    PrefItem newPrefItem;
                    if (prefItem != null) newPrefItem = prefItem;
                    else {
                        newPrefItem = new PrefItem();
                        newPrefItem.keyName = editKeyName.getText().toString();
                    }
                    if (newPrefItem.keyName == null) {
                        Toast.makeText(getActivity(), R.string.key_name_cannot_be_null, Toast.LENGTH_LONG).show();
                        return;
                    }

                    try {
                        switch (mCurrentType) {
                            case TYPE_BOOLEAN:
                                newPrefItem.keyValue = ((MaterialSwitch) mValues[mCurrentType]).isChecked();
                                break;
                            case TYPE_FLOAT:
                                newPrefItem.keyValue = Float.valueOf(mValues[mCurrentType].getText().toString());
                                break;
                            case TYPE_INTEGER:
                                newPrefItem.keyValue = Integer.valueOf(mValues[mCurrentType].getText().toString());
                                break;
                            case TYPE_LONG:
                                newPrefItem.keyValue = Long.valueOf(mValues[mCurrentType].getText().toString());
                                break;
                            case TYPE_STRING:
                                newPrefItem.keyValue = mValues[mCurrentType].getText().toString();
                                break;
                            case TYPE_SET:
                                newPrefItem.keyValue = SharedPrefsUtil.unflattenToSet(mValues[mCurrentType].getText().toString());
                                break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), R.string.error_evaluating_input, Toast.LENGTH_LONG).show();
                        return;
                    }
                    mInterfaceCommunicator.sendInfo(mode, newPrefItem);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                });
        if (mode == MODE_EDIT) builder.setNeutralButton(R.string.delete,
                (dialog, which) -> mInterfaceCommunicator.sendInfo(MODE_DELETE, prefItem));
        return builder.create();
    }
}
