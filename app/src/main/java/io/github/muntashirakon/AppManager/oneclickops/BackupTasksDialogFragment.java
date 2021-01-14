package io.github.muntashirakon.AppManager.oneclickops;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import io.github.muntashirakon.AppManager.R;

public class BackupTasksDialogFragment extends DialogFragment {
    public static final String TAG = "BackupTasksDialogFragment";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireActivity());
        if (inflater == null) return super.onCreateDialog(savedInstanceState);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.dialog_backup_tasks, null);
        view.findViewById(R.id.backup_all).setOnClickListener(v -> {
            // TODO(14/1/21): Backup all apps
        });
        view.findViewById(R.id.redo_existing_backups).setOnClickListener(v -> {
            // TODO(14/1/21): Redo existing backups
        });
        view.findViewById(R.id.backup_apps_without_backup).setOnClickListener(v -> {
            // TODO(14/1/21): Backup apps without any previous backups
        });
        view.findViewById(R.id.verify_and_redo_backups).setOnClickListener(v -> {
            // TODO(14/1/21): Verify integrity of the backups and back up the apps whose integrity have failed
        });
        view.findViewById(R.id.backup_apps_with_changes).setOnClickListener(v -> {
            // TODO(14/1/21): Backup apps with changes
        });
        return new MaterialAlertDialogBuilder(requireActivity())
                .setView(view)
                .setTitle(R.string.backup)
                .setNegativeButton(R.string.cancel, null)
                .create();
    }
}
