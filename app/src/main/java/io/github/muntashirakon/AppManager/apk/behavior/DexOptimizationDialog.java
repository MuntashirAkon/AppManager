// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.apk.behavior;

import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.Editable;
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
import io.github.muntashirakon.AppManager.settings.Ops;
import io.github.muntashirakon.AppManager.utils.TextUtilsCompat;
import io.github.muntashirakon.widget.AnyFilterArrayAdapter;

public class DexOptimizationDialog extends DialogFragment {
    public static final String TAG = DexOptimizationDialog.class.getSimpleName();

    private static final String ARG_PACKAGES = "pkg";

    @NonNull
    public static DexOptimizationDialog getInstance(String[] packages) {
        DexOptimizationDialog dialog = new DexOptimizationDialog();
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

    public final DexOptimizationOptions options = new DexOptimizationOptions();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        options.packages = requireArguments().getStringArray(ARG_PACKAGES);
        options.checkProfiles = SystemProperties.getBoolean("dalvik.vm.usejitprofiles", false);
        // Inflate view
        View view = View.inflate(requireContext(), R.layout.dialog_dexopt, null);
        AutoCompleteTextView autoCompleteTextView = view.findViewById(R.id.compiler_filter);
        MaterialCheckBox compileLayoutsCheck = view.findViewById(R.id.compile_layouts);
        MaterialCheckBox clearProfileDataCheck = view.findViewById(R.id.clear_profile_data);
        MaterialCheckBox checkProfilesCheck = view.findViewById(R.id.check_profiles);
        MaterialCheckBox forceCompilationCheck = view.findViewById(R.id.force_compilation);
        MaterialCheckBox forceDexOptCheck = view.findViewById(R.id.force_dexopt);
        checkProfilesCheck.setChecked(options.checkProfiles);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            compileLayoutsCheck.setVisibility(View.GONE);
        }
        if (!Ops.isRoot()) {
            forceDexOptCheck.setVisibility(View.GONE);
        }

        // Set listeners
        autoCompleteTextView.setAdapter(new AnyFilterArrayAdapter<>(requireContext(), io.github.muntashirakon.ui.R.layout.item_checked_text_view,
                COMPILER_FILTERS));
        compileLayoutsCheck.setOnCheckedChangeListener((buttonView, isChecked) -> options.compileLayouts = isChecked);
        clearProfileDataCheck.setOnCheckedChangeListener((buttonView, isChecked) -> options.clearProfileData = isChecked);
        checkProfilesCheck.setOnCheckedChangeListener((buttonView, isChecked) -> options.checkProfiles = isChecked);
        forceCompilationCheck.setOnCheckedChangeListener((buttonView, isChecked) -> options.forceCompilation = isChecked);
        forceDexOptCheck.setOnCheckedChangeListener((buttonView, isChecked) -> options.forceDexOpt = isChecked);
        if (Ops.isRoot()) {
            forceDexOptCheck.setChecked(true);
        }

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_perform_runtime_optimization_to_apps)
                .setView(view)
                .setPositiveButton(R.string.action_run, (dialog, which) -> {
                    Editable compilerFilterRaw = autoCompleteTextView.getText();
                    if (TextUtilsCompat.isEmpty(compilerFilterRaw)) {
                        return;
                    }
                    String compilerFiler = compilerFilterRaw.toString().trim();
                    if (!COMPILER_FILTERS.contains(compilerFiler)) {
                        // Invalid compiler filter
                        return;
                    }
                    options.compilerFiler = compilerFiler;
                    launchOp();
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_to_default, (dialog, which) -> {
                    options.compilerFiler = SystemProperties.get("pm.dexopt.install", "speed-profile");
                    options.forceCompilation = true;
                    options.clearProfileData = true;
                    launchOp();
                })
                .create();
    }

    private void launchOp() {
        Bundle args = new Bundle();
        args.putParcelable(BatchOpsManager.ARG_OPTIONS, options);
        Intent intent = new Intent(requireContext(), BatchOpsService.class);
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, null);
        intent.putExtra(BatchOpsService.EXTRA_OP, BatchOpsManager.OP_DEXOPT);
        intent.putExtra(BatchOpsService.EXTRA_OP_EXTRA_ARGS, args);
        ContextCompat.startForegroundService(requireContext(), intent);
    }
}
