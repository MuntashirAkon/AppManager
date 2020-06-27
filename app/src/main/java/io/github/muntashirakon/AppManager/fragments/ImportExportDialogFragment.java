package io.github.muntashirakon.AppManager.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.utils.Tuple;

public class ImportExportDialogFragment extends DialogFragment {
    public static final String TAG = "ImportExportDialogFragment";

    private static final int RESULT_CODE_WATT = 711;
    private static final int RESULT_CODE_BLOCKER = 459;

    private Context context;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getActivity() == null) return super.onCreateDialog(savedInstanceState);
        context = getActivity();
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_settings_import_export, null);
        view.findViewById(R.id.import_watt).setOnClickListener(v -> {
            Intent intent = new Intent()
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/xml")
                    .setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_files)), RESULT_CODE_WATT);
        });
        view.findViewById(R.id.import_blocker).setOnClickListener(v -> {
            Intent intent = new Intent()
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_files)), RESULT_CODE_BLOCKER);
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialog);
        builder.setView(view)
                .setTitle(R.string.pref_import_export)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    if (getDialog() != null) getDialog().cancel();
                });
        return builder.create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == RESULT_CODE_WATT) {
                if (data != null) {
                    List<Uri> uriList = new ArrayList<>();
                    if (data.getClipData() != null) {
                        for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                            uriList.add(data.getClipData().getItemAt(index).getUri());
                        }
                    } else uriList.add(data.getData());
                    Tuple<Boolean, Integer> status = ExternalComponentsImporter.applyFromWatt(
                            context.getApplicationContext(), uriList);
                    if (!status.getFirst()) {  // Not failed
                        Toast.makeText(getContext(), R.string.the_import_was_successful,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), String.format(Locale.getDefault(),
                                getString(R.string.failed_to_import_files), status.getSecond()),
                                Toast.LENGTH_LONG).show();
                    }
                    if (getDialog() != null) getDialog().cancel();
                }
            } else if (requestCode == RESULT_CODE_BLOCKER) {
                if (data != null) {
                    List<Uri> uriList = new ArrayList<>();
                    if (data.getClipData() != null) {
                        for (int index = 0; index < data.getClipData().getItemCount(); index++) {
                            uriList.add(data.getClipData().getItemAt(index).getUri());
                        }
                    } else uriList.add(data.getData());
                    Tuple<Boolean, Integer> status = ExternalComponentsImporter.applyFromBlocker(
                            context.getApplicationContext(), uriList);
                    if (!status.getFirst()) {  // Not failed
                        Toast.makeText(getContext(), R.string.the_import_was_successful,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), String.format(Locale.getDefault(),
                                getString(R.string.failed_to_import_files), status.getSecond()),
                                Toast.LENGTH_LONG).show();
                    }
                    if (getDialog() != null) getDialog().cancel();
                }
            }
        }
    }
}
