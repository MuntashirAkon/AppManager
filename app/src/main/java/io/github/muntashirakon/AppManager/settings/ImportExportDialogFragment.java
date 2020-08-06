package io.github.muntashirakon.AppManager.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.misc.RequestCodes;
import io.github.muntashirakon.AppManager.rules.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.utils.Tuple;

public class ImportExportDialogFragment extends DialogFragment {
    public static final String TAG = "ImportExportDialogFragment";

    private static final String MIME_JSON = "application/json";
    private static final String MIME_TSV = "text/tab-separated-values";
    private static final String MIME_XML = "text/xml";

    private SettingsActivity activity;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activity = (SettingsActivity) requireActivity();
        LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_settings_import_export, null);
        view.findViewById(R.id.export_internal).setOnClickListener(v -> {
            @SuppressLint("SimpleDateFormat")
            String fileName = "app_manager_rules_export-" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())) + ".am.tsv";
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(MIME_TSV);
            intent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(intent, RequestCodes.REQUEST_CODE_EXPORT);
        });
        view.findViewById(R.id.import_internal).setOnClickListener(v -> {
            Intent intent = new Intent()
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(MIME_TSV)
                    .setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_files)), RequestCodes.REQUEST_CODE_IMPORT);
        });
        view.findViewById(R.id.import_existing).setOnClickListener(v -> new Thread(() -> {
            List<String> packageList = new ArrayList<>();
            Handler handler = new Handler(Looper.getMainLooper());
            for (ApplicationInfo applicationInfo: requireContext().getPackageManager().getInstalledApplications(0)) {
                packageList.add(applicationInfo.packageName);
            }
            List<String> failedPackages = ExternalComponentsImporter.applyFromExistingBlockList(requireContext(), packageList);
            if (failedPackages.isEmpty()) {
                handler.post(() -> Toast.makeText(requireContext(), R.string.the_import_was_successful, Toast.LENGTH_SHORT).show());
            } else {
                handler.post(() ->
                        new MaterialAlertDialogBuilder(requireContext(), R.style.AppTheme_AlertDialog)
                            .setTitle(getResources().getQuantityString(R.plurals.failed_to_import_files, failedPackages.size(), failedPackages.size()))
                            .setItems((CharSequence[]) failedPackages.toArray(), null)
                            .setNegativeButton(android.R.string.ok, null)
                            .show());
            }
        }).start());
        view.findViewById(R.id.import_watt).setOnClickListener(v -> {
            Intent intent = new Intent()
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(MIME_XML)
                    .setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_files)), RequestCodes.REQUEST_CODE_WATT);
        });
        view.findViewById(R.id.import_blocker).setOnClickListener(v -> {
            Intent intent = new Intent()
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(MIME_JSON)
                    .setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_files)), RequestCodes.REQUEST_CODE_BLOCKER);
        });
        return new MaterialAlertDialogBuilder(activity, R.style.AppTheme_AlertDialog)
                .setView(view)
                .setTitle(R.string.pref_import_export_blocking_rules)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == RequestCodes.REQUEST_CODE_WATT) {
                if (data != null) {
                    List<Uri> uriList = new ArrayList<>();
                    if (data.getClipData() != null) {
                        for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                            uriList.add(data.getClipData().getItemAt(index).getUri());
                        }
                    } else uriList.add(data.getData());
                    Tuple<Boolean, Integer> status = ExternalComponentsImporter.applyFromWatt(
                            activity.getApplicationContext(), uriList);
                    if (!status.getFirst()) {  // Not failed
                        Toast.makeText(getContext(), R.string.the_import_was_successful,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), getResources().getQuantityString(
                                R.plurals.failed_to_import_files, status.getSecond(), status
                                        .getSecond()), Toast.LENGTH_LONG).show();
                    }
                    if (getDialog() != null) getDialog().cancel();
                }
            } else if (requestCode == RequestCodes.REQUEST_CODE_BLOCKER) {
                if (data != null) {
                    List<Uri> uriList = new ArrayList<>();
                    if (data.getClipData() != null) {
                        for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                            uriList.add(data.getClipData().getItemAt(index).getUri());
                        }
                    } else uriList.add(data.getData());
                    Tuple<Boolean, Integer> status = ExternalComponentsImporter.applyFromBlocker(
                            activity.getApplicationContext(), uriList);
                    if (!status.getFirst()) {  // Not failed
                        Toast.makeText(getContext(), R.string.the_import_was_successful,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), getResources().getQuantityString(
                                R.plurals.failed_to_import_files, status.getSecond(), status
                                        .getSecond()), Toast.LENGTH_LONG).show();
                    }
                    if (getDialog() != null) getDialog().cancel();
                }
            } else if (requestCode == RequestCodes.REQUEST_CODE_EXPORT) {
                if (data != null) {
                    RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                    Bundle args = new Bundle();
                    args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                    args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, data.getData());
                    args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null);
                    dialogFragment.setArguments(args);
                    activity.getSupportFragmentManager().popBackStackImmediate();
                    dialogFragment.show(activity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                    if (getDialog() != null) getDialog().cancel();
                }
            } else if (requestCode == RequestCodes.REQUEST_CODE_IMPORT) {
                if (data != null) {
                    RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                    Bundle args = new Bundle();
                    args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_IMPORT);
                    args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, data.getData());
                    args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, null);
                    dialogFragment.setArguments(args);
                    activity.getSupportFragmentManager().popBackStackImmediate();
                    dialogFragment.show(activity.getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                    if (getDialog() != null) getDialog().cancel();
                }
            }
        }
    }
}
