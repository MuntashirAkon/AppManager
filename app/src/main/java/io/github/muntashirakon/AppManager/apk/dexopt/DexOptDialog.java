// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.dexopt;

import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.batchops.BatchQueueItem;
import io.github.muntashirakon.AppManager.batchops.struct.BatchDexOptOptions;
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.users.Users;
import io.github.muntashirakon.adapters.AnyFilterArrayAdapter;

public class DexOptDialog extends DialogFragment {
    public static final String TAG = DexOptDialog.class.getSimpleName();

    private static final String ARG_PACKAGES = "pkg";

    @NonNull
    public static DexOptDialog getInstance(@Nullable String[] packages) {
        DexOptDialog dialog = new DexOptDialog();
        Bundle args = new Bundle();
        args.putStringArray(ARG_PACKAGES, packages);
        dialog.setArguments(args);
        return dialog;
    }

    private static final List<String> COMPILER_FILTERS = new ArrayList<String>() {{
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            add("verify-none"); // = assume-verified
            add("verify-at-runtime"); // = extract
            add("verify-profile"); // = verify
            add("interpret-only"); // = quicken
            add("time"); // = space
            add("balanced"); // speed
        } else {
            add("assume-verified");
            add("extract");
            add("verify");
            add("quicken");
        }
        add("space");
        add("space-profile");
        add("speed");
        add("speed-profile");
        add("everything");
        add("everything-profile");
    }};

    private final DexOptOptions mOptions = DexOptOptions.getDefault();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        mOptions.packages = requireArguments().getStringArray(ARG_PACKAGES);
        int uid = Users.getSelfOrRemoteUid();
        boolean isRootOrSystem = uid == Ops.SYSTEM_UID || uid == Ops.ROOT_UID;
        // Inflate view
        View view = View.inflate(requireContext(), R.layout.dialog_dexopt, null);
        AutoCompleteTextView compilerFilterSelectionView = view.findViewById(R.id.compiler_filter);
        MaterialCheckBox compileLayoutsCheck = view.findViewById(R.id.compile_layouts);
        MaterialCheckBox clearProfileDataCheck = view.findViewById(R.id.clear_profile_data);
        MaterialCheckBox checkProfilesCheck = view.findViewById(R.id.check_profiles);
        MaterialCheckBox forceCompilationCheck = view.findViewById(R.id.force_compilation);
        MaterialCheckBox forceDexOptCheck = view.findViewById(R.id.force_dexopt);
        compilerFilterSelectionView.setText(mOptions.compilerFiler);
        checkProfilesCheck.setChecked(mOptions.checkProfiles);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Compile layout options was introduced in Android 10 and removed in Android 12
            compileLayoutsCheck.setVisibility(View.GONE);
        }
        if (!isRootOrSystem) {
            // clearProfileData and forceDexOpt can only be run as root/system
            clearProfileDataCheck.setVisibility(View.GONE);
            forceDexOptCheck.setVisibility(View.GONE);
        }

        // Set listeners
        compilerFilterSelectionView.setAdapter(new AnyFilterArrayAdapter<>(requireContext(), io.github.muntashirakon.ui.R.layout.auto_complete_dropdown_item,
                COMPILER_FILTERS));
        compileLayoutsCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.compileLayouts = isChecked);
        clearProfileDataCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.clearProfileData = isChecked);
        checkProfilesCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.checkProfiles = isChecked);
        forceCompilationCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.forceCompilation = isChecked);
        forceDexOptCheck.setOnCheckedChangeListener((buttonView, isChecked) -> mOptions.forceDexOpt = isChecked);
        if (isRootOrSystem) {
            forceDexOptCheck.setChecked(true);
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_perform_runtime_optimization_to_apps)
                .setView(view)
                .setPositiveButton(R.string.action_run, (dialog, which) -> {
                    Editable compilerFilterRaw = compilerFilterSelectionView.getText();
                    if (TextUtils.isEmpty(compilerFilterRaw)) {
                        return;
                    }
                    String compilerFiler = compilerFilterRaw.toString().trim();
                    if (!COMPILER_FILTERS.contains(compilerFiler)) {
                        // Invalid compiler filter
                        return;
                    }
                    mOptions.compilerFiler = compilerFiler;
                    launchOp();
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                    mOptions.compilerFiler = DexOptOptions.getDefaultCompilerFilterForInstallation();
                    mOptions.forceCompilation = true;
                    mOptions.clearProfileData = true;
                    launchOp();
                })
                .create();
    }

    private void launchOp() {
        BatchDexOptOptions options = new BatchDexOptOptions(mOptions);
        BatchQueueItem queueItem = BatchQueueItem.getBatchOpQueue(
                BatchOpsManager.OP_DEXOPT, null, null, options);
        Intent intent = new Intent(requireContext(), BatchOpsService.class);
        intent.putExtra(BatchOpsService.EXTRA_QUEUE_ITEM, queueItem);
        ContextCompat.startForegroundService(requireContext(), intent);
    }
}
