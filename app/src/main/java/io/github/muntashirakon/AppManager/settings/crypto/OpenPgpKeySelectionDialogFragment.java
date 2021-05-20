// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.settings.crypto;

import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.text.TextUtils;

import android.widget.Button;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.ArrayUtils;

public class OpenPgpKeySelectionDialogFragment extends DialogFragment {
    public static final String TAG = "OpenPgpKeySelectionDialogFragment";

    private String mOpenPgpProvider;
    private OpenPgpServiceConnection mServiceConnection;
    private AlertDialog dialog;
    private FragmentActivity activity;
    private final ActivityResultLauncher<IntentSenderRequest> keyIdResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getData() != null) {
                    getUserId(result.getData());
                }
            });
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = requireActivity();
        mOpenPgpProvider = (String) AppPref.get(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR);
        List<ServiceInfo> serviceInfoList = OpenPgpUtils.getPgpClientServices(activity);
        CharSequence[] packageLabels = new String[serviceInfoList.size()];
        String[] packageNames = new String[serviceInfoList.size()];
        ServiceInfo serviceInfo;
        PackageManager pm = activity.getPackageManager();
        for (int i = 0; i < packageLabels.length; ++i) {
            serviceInfo = serviceInfoList.get(i);
            packageLabels[i] = serviceInfo.loadLabel(pm);
            packageNames[i] = serviceInfo.packageName;
        }
        int choice = ArrayUtils.indexOf(packageNames, mOpenPgpProvider);
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.open_pgp_provider)
                .setSingleChoiceItems(packageLabels, choice, (dialog, which) -> {
                    mOpenPgpProvider = packageNames[which];
                    AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR, mOpenPgpProvider);
                })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, null)
                .create();
        dialog.setOnShowListener(dialog1 -> {
            Button positiveButton = ((AlertDialog) dialog1).getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> chooseKey());
        });
        return dialog;
    }

    private void chooseKey() {
        // Bind to service
        mServiceConnection = new OpenPgpServiceConnection(AppManager.getContext(), mOpenPgpProvider,
                new OpenPgpServiceConnection.OnBound() {
                    @Override
                    public void onBound(IOpenPgpService2 service) {
                        getUserId(new Intent());
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(OpenPgpApi.TAG, "exception on binding!", e);
                    }
                }
        );
        mServiceConnection.bindToService();
    }

    private void getUserId(@NonNull Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_KEY_IDS);
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[]{});
        OpenPgpApi api = new OpenPgpApi(activity, mServiceConnection.getService());
        api.executeApiAsync(executor, data, null, null, result -> {
            switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                case OpenPgpApi.RESULT_CODE_SUCCESS: {
                    long[] keyIds = result.getLongArrayExtra(OpenPgpApi.EXTRA_KEY_IDS);
                    if (keyIds == null || keyIds.length == 0) {
                        // Remove encryption
                        AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR, "");
                        AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_PACKAGE_STR, "");
                    } else {
                        String[] keyIdsStr = new String[keyIds.length];
                        for (int i = 0; i < keyIds.length; ++i) {
                            keyIdsStr[i] = String.valueOf(keyIds[i]);
                        }
                        AppPref.set(AppPref.PrefKey.PREF_OPEN_PGP_USER_ID_STR, TextUtils.join(",", keyIdsStr));
                    }
                    dialog.dismiss();
                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    PendingIntent pi = Objects.requireNonNull(result.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
                    keyIdResultLauncher.launch(new IntentSenderRequest.Builder(pi).build());
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                    if (error != null) {
                        Log.e(OpenPgpApi.TAG, "RESULT_CODE_ERROR: " + error.getMessage());
                    }
                    dialog.dismiss();
                    break;
                }
            }
        });
    }
}
