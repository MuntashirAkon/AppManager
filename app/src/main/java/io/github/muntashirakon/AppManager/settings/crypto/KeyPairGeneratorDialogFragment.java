// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.crypto.ks.KeyPair;
import io.github.muntashirakon.AppManager.crypto.ks.KeyStoreUtils;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.DateUtils;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.AppManager.utils.Utils;

public class KeyPairGeneratorDialogFragment extends DialogFragment {
    public static final String TAG = "KeyPairGeneratorDialogFragment";
    public static final List<Integer> SUPPORTED_RSA_KEY_SIZES = Arrays.asList(2048, 4096);

    public interface OnGenerateListener {
        void onGenerate(@Nullable char[] password, @Nullable KeyPair keyPair);
    }

    private OnGenerateListener listener;
    private int keySize;
    private long expiryDate;

    public void setOnGenerateListener(OnGenerateListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        FragmentActivity activity = requireActivity();
        View view = getLayoutInflater().inflate(R.layout.dialog_certificate_generator, null);
        Spinner keySizeSpinner = view.findViewById(R.id.key_size_selector_spinner);
        keySizeSpinner.setAdapter(new ArrayAdapter<>(activity, R.layout.support_simple_spinner_dropdown_item,
                SUPPORTED_RSA_KEY_SIZES));
        keySizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                keySize = SUPPORTED_RSA_KEY_SIZES.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                keySize = 2048;
            }
        });
        EditText passwordView = view.findViewById(R.id.key_password);
        EditText expiryDate = view.findViewById(R.id.expiry_date);
        expiryDate.setKeyListener(null);
        expiryDate.setOnFocusChangeListener((v, hasFocus) -> {
            if (v.isInTouchMode() && hasFocus) {
                v.performClick();
            }
        });
        expiryDate.setOnClickListener(v -> pickExpiryDate(expiryDate));
        EditText commonName = view.findViewById(R.id.common_name);
        EditText orgUnit = view.findViewById(R.id.organization_unit);
        EditText orgName = view.findViewById(R.id.organization_name);
        EditText locality = view.findViewById(R.id.locality_name);
        EditText state = view.findViewById(R.id.state_name);
        EditText country = view.findViewById(R.id.country_name);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.generate_key)
                .setView(view)
                .setPositiveButton(R.string.generate_key, null)
                .setNegativeButton(R.string.cancel, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> {
            AlertDialog dialog1 = (AlertDialog) dialog;
            Button generateButton = dialog1.getButton(AlertDialog.BUTTON_POSITIVE);
            generateButton.setOnClickListener(v -> new Thread(() -> {
                AtomicReference<KeyPair> keyPair = new AtomicReference<>(null);
                char[] pass;
                // Get password
                Editable password = passwordView.getText();
                if (!TextUtils.isEmpty(password)) {
                    pass = new char[password.length()];
                    password.getChars(0, password.length(), pass, 0);
                } else {
                    pass = null;
                }
                String formattedSubject = getFormattedSubject(commonName.getText().toString(),
                        orgUnit.getText().toString(), orgName.getText().toString(),
                        locality.getText().toString(), state.getText().toString(),
                        country.getText().toString());
                if (this.expiryDate == 0) {
                    if (isDetached()) return;
                    activity.runOnUiThread(() -> UIUtils.displayShortToast(R.string.expiry_date_cannot_be_empty));
                    return;
                }
                if (formattedSubject.isEmpty()) {
                    formattedSubject = "CN=App Manager";
                }
                try {
                    keyPair.set(KeyStoreUtils.generateRSAKeyPair(formattedSubject, keySize, this.expiryDate));
                } catch (Exception e) {
                    Log.e(TAG, e);
                } finally {
                    if (!isDetached()) {
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onGenerate(pass, keyPair.get());
                            else if (pass != null) Utils.clearChars(pass);
                            dialog.dismiss();
                        });
                    }
                }
            }).start());
        });
        return alertDialog;
    }

    @NonNull
    public String getFormattedSubject(@Nullable String commonName,
                                      @Nullable String organizationUnit,
                                      @Nullable String organizationName,
                                      @Nullable String localityName,
                                      @Nullable String stateName,
                                      @Nullable String countryName) {
        List<String> subjectArray = new ArrayList<>(6);
        if (!TextUtils.isEmpty(commonName)) subjectArray.add("CN=" + commonName);
        if (!TextUtils.isEmpty(organizationUnit)) subjectArray.add("OU=" + organizationUnit);
        if (!TextUtils.isEmpty(organizationName)) subjectArray.add("O=" + organizationName);
        if (!TextUtils.isEmpty(localityName)) subjectArray.add("L=" + localityName);
        if (!TextUtils.isEmpty(stateName)) subjectArray.add("ST=" + stateName);
        if (!TextUtils.isEmpty(countryName)) subjectArray.add("C=" + countryName);
        return TextUtils.join(", ", subjectArray);
    }

    public void pickExpiryDate(EditText expiryDate) {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.expiry_date)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            this.expiryDate = selection;
            expiryDate.setText(DateUtils.formatDate(selection));
        });
        datePicker.show(getChildFragmentManager(), "DatePicker");
    }
}
